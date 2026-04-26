package com.bada.cali.service;

import com.bada.cali.common.enums.AppStatus;
import com.bada.cali.common.enums.BatchStatus;
import com.bada.cali.common.enums.JobItemStatus;
import com.bada.cali.common.enums.JobType;
import com.bada.cali.common.enums.ReportType;
import com.bada.cali.common.enums.YnType;
import com.bada.cali.config.NcpStorageProperties;
import com.bada.cali.dto.ExcelWorkDTO;
import com.bada.cali.dto.WorkerDataDTO;
import com.bada.cali.entity.FileInfo;
import com.bada.cali.entity.Log;
import com.bada.cali.entity.Report;
import com.bada.cali.entity.ReportJobBatch;
import com.bada.cali.entity.ReportJobItem;
import com.bada.cali.repository.FileInfoRepository;
import com.bada.cali.repository.LogRepository;
import com.bada.cali.repository.ReportJobBatchRepository;
import com.bada.cali.repository.ReportJobItemRepository;
import com.bada.cali.repository.ReportRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.ObjectCannedACL;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * ExcelWork 미들웨어 연동 서비스.
 *
 * 역할 범위:
 *   - createJob        : 배치·item 생성 + token/fileUuid 발급 + excelwork:// URI 생성
 *   - getJobByToken    : 미들웨어용 잡 상세 조회 (셀 설정 + 성적서별 데이터 포함 — 완전한 JSON)
 *   - streamFile       : token 기반 파일 스트리밍 (하위 호환용)
 *   - streamFileByUuid : fileUuid 기반 파일 스트리밍 (미들웨어 on-demand 다운로드)
 *   - callbackReady    : 미들웨어 시작 알림 → batch/item/성적서 PROGRESS 전환
 *   - callbackItemDone : 완성 파일 스토리지 업로드 + item SUCCESS + 로그
 *   - callbackDone     : 배치 전체 완료 처리
 *   - resetBatches     : 비정상 종료 복구 (READY/PROGRESS → CANCELED + 성적서 IDLE)
 *   - previewRecover   : 스마트 복구 미리보기 — 스토리지 파일 존재 여부 확인만 (DB 변경 없음)
 *   - smartRecover     : 스마트 복구 실행 — 파일 있음 → SUCCESS, 파일 없음 → IDLE
 */
@Service
@Log4j2
@RequiredArgsConstructor
public class ExcelWorkServiceImpl {

    private final ReportRepository         reportRepository;
    private final ReportJobBatchRepository batchRepository;
    private final ReportJobItemRepository  itemRepository;
    private final FileInfoRepository       fileInfoRepository;
    private final LogRepository            logRepository;
    private final S3Client                 ncloudS3Client;
    private final NcpStorageProperties     storageProps;

    // WorkerDataServiceImpl 재활용 — 셀 설정 + 성적서 데이터 조합
    private final WorkerDataServiceImpl    workerDataService;

    // ── ExcelWork 전용 설정 ───────────────────────────────────────────────────

    /** ExcelWork 미들웨어 콜백 인증 키. 비어 있으면 개발 모드 (검증 생략). */
    @Value("${app.excelwork.callback-key:}")
    private String excelworkCallbackKey;

    /** 미들웨어가 콜백을 보낼 CALI 서버 URL (CreateJobReq.serverUrl 미입력 시 폴백) */
    @Value("${app.cali.callback-base-url:http://localhost:8050}")
    private String callbackBaseUrl;

    // ─────────────────────────────────────────────────────────────────────────
    // 1. createJob — 배치 생성 + token/fileUuid 발급
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * 성적서작성 작업 요청 처리.
     *
     * 처리 순서:
     *  1. 성적서 유효성 검증
     *  2. ReportJobBatch 생성 (status=READY, token=UUID)
     *  3. ReportJobItem 목록 생성 — 각 item에 fileUuid(UUID) 부여
     *  4. 대상 성적서 writeStatus → READY
     *  5. 로그 기록
     *  6. excelwork:// URI + 응답 반환
     *
     * @param req       성적서 id 목록 + sampleId + 선택 serverUrl
     * @param memberId  요청 사용자 id
     */
    @Transactional
    public ExcelWorkDTO.CreateJobRes createJob(ExcelWorkDTO.CreateJobReq req, Long memberId) {

        List<Long> reportIds = req.getReportIds();

        // ── 1. 성적서 조회 및 유효성 검증 ────────────────────────────────────
        List<Report> reports = reportRepository.findAllById(reportIds);
        if (reports.size() != reportIds.size()) {
            throw new EntityNotFoundException("존재하지 않는 성적서가 포함되어 있습니다.");
        }

        for (Report report : reports) {
            if (report.getIsVisible() != YnType.y) {
                throw new IllegalArgumentException(
                        String.format("삭제된 성적서가 포함되어 있습니다. (id: %d)", report.getId()));
            }
            if (report.getReportType() != ReportType.SELF) {
                throw new IllegalArgumentException(
                        String.format("자체성적서(SELF)만 처리 가능합니다. (id: %d)", report.getId()));
            }
            if (report.getSmallItemCodeId() == null) {
                throw new IllegalArgumentException(
                        String.format("소분류가 설정되지 않은 성적서입니다. (id: %d)", report.getId()));
            }
            // 이미 READY/PROGRESS 상태인 경우 중복 요청 차단
            if (report.getWriteStatus() == AppStatus.READY || report.getWriteStatus() == AppStatus.PROGRESS) {
                boolean hasActive = itemRepository.existsActiveBatchForReport(report.getId(), "WRITE");
                if (hasActive) {
                    throw new IllegalArgumentException(
                            String.format("이미 작업이 진행 중인 성적서입니다. (id: %d, 번호: %s)",
                                    report.getId(), report.getReportNum()));
                }
                // 활성 배치 없음 → 이전 비정상 종료 흔적 — 자동 리셋 후 재작업 허용
                log.warn("writeStatus={} 이지만 활성 배치 없음 — 자동 리셋 (reportId: {})",
                        report.getWriteStatus(), report.getId());
                report.setWriteStatus(AppStatus.FAIL);
            }
            // 결재가 진행 중이면 성적서작성 차단 (원본 파일 교체 방지)
            if (report.getWorkStatus() == AppStatus.READY || report.getWorkStatus() == AppStatus.PROGRESS
                    || report.getApprovalStatus() == AppStatus.READY || report.getApprovalStatus() == AppStatus.PROGRESS) {
                throw new IllegalArgumentException(
                        String.format("결재가 진행 중인 성적서입니다. (id: %d)", report.getId()));
            }
        }

        // ── 2. ReportJobBatch 생성 ────────────────────────────────────────────
        String token = UUID.randomUUID().toString().replace("-", "");  // 32자 hex
        ReportJobBatch batch = batchRepository.save(ReportJobBatch.builder()
                .jobType(JobType.WRITE)
                .requestMemberId(memberId)
                .sampleId(req.getSampleId())
                .totalCount(reportIds.size())
                .status(BatchStatus.READY)
                .token(token)
                .createDatetime(LocalDateTime.now())
                .build());

        // ── 3. ReportJobItem 생성 — item별 fileUuid 부여 ──────────────────────
        // fileUuid: 미들웨어가 GET /api/excelwork/file/{fileUuid} 로 파일을 on-demand 다운로드할 때 사용.
        // UUID v4 문자열(36자, 하이픈 포함) 을 각 item에 독립적으로 부여한다.
        List<ReportJobItem> items = reportIds.stream()
                .map(reportId -> ReportJobItem.builder()
                        .batchId(batch.getId())
                        .reportId(reportId)
                        .fileUuid(UUID.randomUUID().toString())  // 36자 UUID (하이픈 포함)
                        .build())
                .collect(Collectors.toList());
        itemRepository.saveAll(items);

        // ── 4. 성적서 writeStatus → READY ────────────────────────────────────
        reports.forEach(r -> r.setWriteStatus(AppStatus.READY));

        // ── 5. 로그 기록 ──────────────────────────────────────────────────────
        logRepository.save(Log.builder()
                .workerName("system")
                .logContent(String.format(
                        "ExcelWork 성적서작성 배치 생성 (batchId: %d, token: %s) — 고유번호 - %s",
                        batch.getId(), token, reportIds))
                .logType("i")
                .refTable("report_job_batch")
                .refTableId(batch.getId())
                .createDatetime(LocalDateTime.now())
                .createMemberId(memberId)
                .build());

        log.info("ExcelWork 배치 생성 완료 — batchId: {}, token: {}, 건수: {}",
                batch.getId(), token, reportIds.size());

        // ── 6. excelwork:// URI 생성 ─────────────────────────────────────────
        // callbackKey: URI에 포함하면 미들웨어가 appsettings.json 설정 없이
        // 해당 환경(로컬/개발/운영)의 키를 자동으로 사용할 수 있다.
        String serverUrl = resolveServerUrl(req.getServerUrl());
        String excelworkUri = "excelwork://process?token=" + token
                + "&serverUrl=" + URLEncoder.encode(serverUrl, StandardCharsets.UTF_8)
                + "&callbackKey=" + URLEncoder.encode(excelworkCallbackKey, StandardCharsets.UTF_8);

        return new ExcelWorkDTO.CreateJobRes(batch.getId(), token, excelworkUri, reportIds.size());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 1-2. createWorkApprovalJob — 실무자결재 배치 생성 + excelwork:// URI 발급
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * 실무자결재 작업 요청 처리.
     *
     * 처리 순서:
     *  1. 성적서 유효성 검증 (writeStatus=SUCCESS, workStatus 중복 아님, 실무자 지정 확인)
     *  2. 요청자가 해당 성적서들의 workMember인지 확인 (서명 이미지 주체 일치)
     *  3. ReportJobBatch 생성 (status=READY, token=UUID, jobType=WORK_APPROVAL)
     *  4. ReportJobItem 목록 생성 — 각 item에 fileUuid 부여
     *  5. 대상 성적서 workStatus → READY
     *  6. 로그 기록
     *  7. excelwork:// URI + 응답 반환
     *
     * @param req      성적서 id 목록 + 선택 serverUrl
     * @param memberId 요청 사용자 id (실무자 본인이어야 함 — 서명 이미지 주체)
     */
    @Transactional
    public ExcelWorkDTO.CreateJobRes createWorkApprovalJob(ExcelWorkDTO.CreateWorkApprovalJobReq req, Long memberId) {

        List<Long> reportIds = req.getReportIds();

        // ── 1. 성적서 조회 및 유효성 검증 ────────────────────────────────────
        List<Report> reports = reportRepository.findAllById(reportIds);
        if (reports.size() != reportIds.size()) {
            throw new EntityNotFoundException("존재하지 않는 성적서가 포함되어 있습니다.");
        }

        java.util.Set<Long> workMemberIds = new java.util.HashSet<>();
        for (Report report : reports) {
            if (report.getIsVisible() != YnType.y) {
                throw new IllegalArgumentException(
                        String.format("삭제된 성적서가 포함되어 있습니다. (id: %d)", report.getId()));
            }
            if (report.getReportType() != ReportType.SELF) {
                throw new IllegalArgumentException(
                        String.format("자체성적서(SELF)만 처리 가능합니다. (id: %d)", report.getId()));
            }
            if (report.getWriteStatus() != AppStatus.SUCCESS) {
                throw new IllegalArgumentException(
                        String.format("성적서작성이 완료되지 않은 성적서입니다. (id: %d)", report.getId()));
            }
            if (report.getWorkStatus() == AppStatus.READY || report.getWorkStatus() == AppStatus.PROGRESS) {
                throw new IllegalArgumentException(
                        String.format("이미 실무자결재가 진행 중인 성적서입니다. (id: %d)", report.getId()));
            }
            if (report.getApprovalStatus() == AppStatus.READY || report.getApprovalStatus() == AppStatus.PROGRESS
                    || report.getApprovalStatus() == AppStatus.SUCCESS) {
                throw new IllegalArgumentException(
                        String.format("기술책임자결재가 진행 중이거나 완료된 성적서입니다. (id: %d)", report.getId()));
            }
            if (report.getWorkMemberId() == null) {
                throw new IllegalArgumentException(
                        String.format("실무자가 지정되지 않은 성적서입니다. (id: %d)", report.getId()));
            }
            workMemberIds.add(report.getWorkMemberId());
        }

        // ── 2. 모든 실무자의 서명 이미지 존재 확인 ──────────────────────────────────
        // 서명 이미지는 각 성적서의 workMemberId 기준으로 item별로 삽입되므로
        // 배치 내 등장하는 모든 실무자의 서명 이미지가 등록되어 있어야 한다.
        List<FileInfo> signFileList = fileInfoRepository
                .findByRefTableNameAndRefTableIdInAndIsVisible("member", workMemberIds, YnType.y);
        java.util.Set<Long> memberIdsWithSign = signFileList.stream()
                .map(FileInfo::getRefTableId)
                .collect(java.util.stream.Collectors.toSet());
        for (Report report : reports) {
            if (!memberIdsWithSign.contains(report.getWorkMemberId())) {
                throw new IllegalArgumentException(
                        String.format("실무자 서명 이미지가 등록되어 있지 않습니다. 해당 실무자의 서명 이미지를 먼저 등록해 주세요. (workMemberId=%d)",
                                report.getWorkMemberId()));
            }
        }

        // ── 3. ReportJobBatch 생성 ────────────────────────────────────────────
        String token = UUID.randomUUID().toString().replace("-", "");
        ReportJobBatch batch = batchRepository.save(ReportJobBatch.builder()
                .jobType(JobType.WORK_APPROVAL)
                .requestMemberId(memberId)
                .sampleId(null)   // WORK_APPROVAL은 샘플 파일 불필요
                .totalCount(reportIds.size())
                .status(BatchStatus.READY)
                .token(token)
                .createDatetime(LocalDateTime.now())
                .build());

        // ── 4. ReportJobItem 생성 — item별 fileUuid 부여 ──────────────────────
        List<ReportJobItem> items = reportIds.stream()
                .map(reportId -> ReportJobItem.builder()
                        .batchId(batch.getId())
                        .reportId(reportId)
                        .fileUuid(UUID.randomUUID().toString())
                        .build())
                .collect(Collectors.toList());
        itemRepository.saveAll(items);

        // ── 5. 성적서 workStatus → READY ─────────────────────────────────────
        reports.forEach(r -> r.setWorkStatus(AppStatus.READY));

        // ── 6. 로그 기록 ──────────────────────────────────────────────────────
        logRepository.save(Log.builder()
                .workerName("system")
                .logContent(String.format(
                        "ExcelWork 실무자결재 배치 생성 (batchId: %d, token: %s) — 고유번호 - %s",
                        batch.getId(), token, reportIds))
                .logType("i")
                .refTable("report_job_batch")
                .refTableId(batch.getId())
                .createDatetime(LocalDateTime.now())
                .createMemberId(memberId)
                .build());

        log.info("ExcelWork 실무자결재 배치 생성 완료 — batchId: {}, token: {}, 건수: {}",
                batch.getId(), token, reportIds.size());

        // ── 7. excelwork:// URI 생성 ─────────────────────────────────────────
        String serverUrl = resolveServerUrl(req.getServerUrl());
        String excelworkUri = "excelwork://process?token=" + token
                + "&serverUrl=" + URLEncoder.encode(serverUrl, StandardCharsets.UTF_8)
                + "&callbackKey=" + URLEncoder.encode(excelworkCallbackKey, StandardCharsets.UTF_8);

        return new ExcelWorkDTO.CreateJobRes(batch.getId(), token, excelworkUri, reportIds.size());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 2. getJobByToken — 미들웨어용 잡 상세 조회 (완전한 JSON)
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * token 으로 잡 상세를 조회하여 미들웨어가 처리에 필요한 모든 데이터를 반환한다.
     *
     * 반환 내용:
     *   - action      : "report_write" (작업 유형 식별자)
     *   - sheetSettings : Env.sheetInfoSetting 전체 (fieldCode → {cell, format})
     *   - items        : 성적서별 fileUuid + fileDownloadUrl + 셀 삽입 데이터 맵
     *
     * 미들웨어는 이 응답 1건만으로 N건의 성적서 처리를 수행할 수 있다.
     *
     * @param token     잡 토큰
     * @param serverUrl 파일 다운로드 URL 생성에 사용할 서버 베이스 URL
     */
    @Transactional(readOnly = true)
    public ExcelWorkDTO.JobDetailRes getJobByToken(String token, String serverUrl) {
        ReportJobBatch batch = findBatchByToken(token);

        List<ReportJobItem> items = itemRepository.findByBatchId(batch.getId());

        // 서버 베이스 URL (파일 다운로드 URL 앞에 붙임)
        String base = resolveServerUrl(serverUrl);

        // ── 셀 설정 조회 (한 번만, 모든 item 공용) ───────────────────────────
        WorkerDataDTO.SheetSettingRes sheetSettingRes = workerDataService.getSheetSetting();
        // WorkerDataDTO.SheetFieldSetting → ExcelWorkDTO.SheetFieldSetting 변환
        Map<String, ExcelWorkDTO.SheetFieldSetting> sheetSettings = new LinkedHashMap<>();
        sheetSettingRes.getSettings().forEach((fieldCode, setting) ->
                sheetSettings.put(fieldCode,
                        new ExcelWorkDTO.SheetFieldSetting(setting.getCell(), setting.getFormat())));

        // ── 성적서별 데이터 조합 ──────────────────────────────────────────────
        List<ExcelWorkDTO.ItemDetail> itemDetails = items.stream().map(item -> {
            Report report = reportRepository.findById(item.getReportId())
                    .orElseThrow(() -> new EntityNotFoundException(
                            "성적서를 찾을 수 없습니다. (id: " + item.getReportId() + ")"));

            Map<String, String> data = new LinkedHashMap<>();
            // WRITE 타입만 셀 삽입 데이터 필요 (WORK_APPROVAL은 빈 맵으로 전달)
            if (batch.getJobType() == com.bada.cali.common.enums.JobType.WRITE) {
                WorkerDataDTO.ReportFillDataRes fillData =
                        workerDataService.getReportFillData(item.getReportId(), batch.getSampleId());
                data = buildDataMap(fillData);
            }

            // 파일 다운로드 URL: GET /api/excelwork/file/{fileUuid}
            // → streamFileByUuid 에서 배치 jobType에 따라 샘플 파일 또는 origin.xlsx 분기
            String fileDownloadUrl = base + "/api/excelwork/file/" + item.getFileUuid();

            // WORK_APPROVAL: item별 서명 이미지 URL — 해당 성적서의 workMemberId 기준
            // GET /api/excelwork/sign-image/member/{workMemberId}
            String itemSignImgUrl = null;
            if (batch.getJobType() == com.bada.cali.common.enums.JobType.WORK_APPROVAL
                    && report.getWorkMemberId() != null) {
                itemSignImgUrl = base + "/api/excelwork/sign-image/member/" + report.getWorkMemberId();
            }

            return new ExcelWorkDTO.ItemDetail(
                    item.getId(),
                    report.getId(),
                    report.getReportNum(),
                    item.getFileUuid(),
                    fileDownloadUrl,
                    data,
                    itemSignImgUrl
            );
        }).toList();

        // 작업 유형 → 미들웨어 action 문자열 변환
        String action = switch (batch.getJobType()) {
            case WRITE           -> "report_write";
            case WORK_APPROVAL   -> "work_approval";
            case MANAGER_APPROVAL -> "manager_approval";
        };

        // WORK_APPROVAL 전용: 배치 레벨 서명 이미지 URL (하위 호환용, token 기반)
        // 미들웨어는 item.signImgUrl(per-item)을 우선 사용하고, 없을 경우 이 URL을 fallback으로 사용
        String signImgUrl = null;
        if (batch.getJobType() == com.bada.cali.common.enums.JobType.WORK_APPROVAL) {
            signImgUrl = base + "/api/excelwork/sign-image/" + token;
        }

        return new ExcelWorkDTO.JobDetailRes(token, batch.getId(), action, base, sheetSettings, itemDetails, signImgUrl);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 3. streamFile — token 기반 파일 스트리밍 (하위 호환용)
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * 미들웨어가 요청한 파일을 스토리지에서 스트리밍으로 중계한다.
     * fileUuid 기반 다운로드(streamFileByUuid)가 주 방식이며,
     * 이 메서드는 하위 호환 또는 내부 테스트용으로 유지한다.
     *
     * @param token    잡 토큰
     * @param fileType 파일 종류 ("sample" 만 허용)
     */
    @Transactional(readOnly = true)
    public ResponseEntity<Resource> streamFile(String token, String fileType) {
        ReportJobBatch batch = findBatchByToken(token);

        return switch (fileType) {
            case "sample" -> streamSampleFile(batch);
            default -> throw new IllegalArgumentException(
                    "유효하지 않은 fileType: " + fileType + " (허용값: sample)");
        };
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 4. streamFileByUuid — fileUuid 기반 파일 스트리밍 (미들웨어 on-demand 다운로드)
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * fileUuid 로 item을 조회하여 해당 배치의 샘플 파일을 스트리밍으로 중계한다.
     *
     * 미들웨어는 item별 fileUuid 를 JSON에서 받아 이 엔드포인트를 호출하여
     * 처리 시점에 파일을 on-demand 로 다운로드한다.
     * 미들웨어가 처리하는 시점에만 다운로드하므로 N건을 미리 내려받지 않아도 된다.
     *
     * @param fileUuid item별 파일 식별 UUID
     */
    @Transactional(readOnly = true)
    public ResponseEntity<Resource> streamFileByUuid(String fileUuid) {
        // fileUuid → item → batch → 샘플 파일 순서로 조회
        ReportJobItem item = itemRepository.findByFileUuid(fileUuid)
                .orElseThrow(() -> new EntityNotFoundException(
                        "유효하지 않은 fileUuid 입니다. (fileUuid: " + fileUuid + ")"));

        ReportJobBatch batch = batchRepository.findById(item.getBatchId())
                .orElseThrow(() -> new EntityNotFoundException(
                        "배치를 찾을 수 없습니다. (batchId: " + item.getBatchId() + ")"));

        log.info("파일 스트리밍 요청 — fileUuid: {}, batchId: {}, reportId: {}, jobType: {}",
                fileUuid, batch.getId(), item.getReportId(), batch.getJobType());

        // jobType 분기: WRITE → 샘플 파일, WORK_APPROVAL → origin.xlsx
        if (batch.getJobType() == com.bada.cali.common.enums.JobType.WORK_APPROVAL) {
            String objectKey = storageProps.getRootDir() + "/report/" + item.getReportId() + "/origin.xlsx";
            return streamFromStorage(objectKey, "origin.xlsx",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        }
        return streamSampleFile(batch);
    }

    /**
     * 샘플 파일 스트리밍.
     * file_info 에서 refTableName='sample', refTableId=sampleId 로 파일 조회 후 스트리밍.
     */
    private ResponseEntity<Resource> streamSampleFile(ReportJobBatch batch) {
        if (batch.getSampleId() == null) {
            throw new IllegalArgumentException("이 배치는 sampleId 가 없습니다. (batchId: " + batch.getId() + ")");
        }

        List<FileInfo> sampleFiles = fileInfoRepository
                .findByRefTableNameAndRefTableIdAndIsVisible("sample", batch.getSampleId(), YnType.y);
        if (sampleFiles.isEmpty()) {
            throw new EntityNotFoundException(
                    "샘플 파일이 존재하지 않습니다. (sampleId: " + batch.getSampleId() + ")");
        }
        FileInfo fileInfo = sampleFiles.get(0);

        // objectKey 구성: {rootDir}/{dir}/{fileInfoId}.{extension}
        String rootDir = storageProps.getRootDir();
        String dir = fileInfo.getDir();
        String objectKey = rootDir + "/";
        objectKey += (dir.endsWith("/") ? dir : dir + "/") + fileInfo.getId();
        if (fileInfo.getExtension() != null && !fileInfo.getExtension().isBlank()) {
            objectKey += "." + fileInfo.getExtension();
        }

        return streamFromStorage(objectKey,
                fileInfo.getOriginName() != null ? fileInfo.getOriginName() : "sample.xlsx",
                fileInfo.getContentType());
    }

    /** 스토리지 objectKey 를 ResponseEntity 스트리밍으로 변환하는 공통 메서드 */
    private ResponseEntity<Resource> streamFromStorage(String objectKey, String fileName, String contentType) {
        try {
            ResponseInputStream<GetObjectResponse> s3is = ncloudS3Client.getObject(
                    GetObjectRequest.builder()
                            .bucket(storageProps.getBucketName())
                            .key(objectKey)
                            .build());

            long contentLength = s3is.response().contentLength();
            MediaType mediaType;
            try {
                mediaType = (contentType != null && !contentType.isBlank())
                        ? MediaType.parseMediaType(contentType)
                        : MediaType.APPLICATION_OCTET_STREAM;
            } catch (Exception e) {
                mediaType = MediaType.APPLICATION_OCTET_STREAM;
            }

            String encodedName = URLEncoder.encode(fileName, StandardCharsets.UTF_8).replaceAll("\\+", "%20");
            return ResponseEntity.ok()
                    .contentType(mediaType)
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename=\"" + encodedName + "\"; filename*=UTF-8''" + encodedName)
                    .contentLength(contentLength)
                    .body(new InputStreamResource(s3is));

        } catch (S3Exception e) {
            if (e.statusCode() == 404) {
                throw new EntityNotFoundException("스토리지에서 파일을 찾을 수 없습니다. key=" + objectKey);
            }
            throw new IllegalStateException("파일 중계 중 스토리지 오류 발생", e);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 5. callbackReady — 미들웨어 READY 콜백
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * 미들웨어가 잡 JSON 수신 후 처리 시작 직전에 호출.
     * batch status READY → PROGRESS, startDatetime 기록.
     * item 전체 + 연관 성적서 writeStatus → PROGRESS.
     */
    @Transactional
    public void callbackReady(String token) {
        ReportJobBatch batch = findBatchByToken(token);

        if (batch.getStatus() != BatchStatus.READY) {
            log.warn("callbackReady: 배치 상태가 READY 가 아님 — 무시 (token: {}, status: {})",
                    token, batch.getStatus());
            return;
        }

        batch.setStatus(BatchStatus.PROGRESS);
        batch.setStartDatetime(LocalDateTime.now());

        List<ReportJobItem> items = itemRepository.findByBatchId(batch.getId());
        items.forEach(item -> {
            item.setStatus(JobItemStatus.PROGRESS);
            item.setStartDatetime(LocalDateTime.now());
        });

        // jobType에 따라 성적서의 다른 상태 필드를 PROGRESS로 전환
        boolean isWorkApproval = batch.getJobType() == com.bada.cali.common.enums.JobType.WORK_APPROVAL;
        items.forEach(item -> reportRepository.findById(item.getReportId()).ifPresent(r -> {
            if (isWorkApproval) {
                r.setWorkStatus(AppStatus.PROGRESS);
            } else {
                r.setWriteStatus(AppStatus.PROGRESS);
            }
        }));

        log.info("callbackReady 처리 완료 — batchId: {}, 건수: {}, jobType: {}",
                batch.getId(), items.size(), batch.getJobType());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 6. callbackItemDone — 미들웨어 단건 완료 콜백
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * 미들웨어가 성적서 1건 처리를 완료했을 때 호출.
     *
     * 처리 순서:
     *  1. item 조회 + token → batch 검증
     *  2. 완성 파일을 스토리지에 업로드 ({rootDir}/report/{reportId}/origin.xlsx)
     *  3. item status → SUCCESS + endDatetime
     *  4. Report.writeStatus → SUCCESS + writeDatetime
     *  5. batch.successCount++
     *  6. log 테이블 기록
     *  7. 전체 완료 여부 확인 (마지막 item이면 batch 최종 처리)
     *
     * @param token   잡 토큰 (배치 소속 확인용)
     * @param itemId  처리 완료된 item id
     * @param file    완성된 엑셀 파일 (multipart)
     */
    @Transactional
    public void callbackItemDone(String token, Long itemId, MultipartFile file) {
        ReportJobBatch batch = findBatchByToken(token);

        ReportJobItem item = itemRepository.findById(itemId)
                .orElseThrow(() -> new EntityNotFoundException("item 을 찾을 수 없습니다. (id: " + itemId + ")"));

        if (!item.getBatchId().equals(batch.getId())) {
            throw new IllegalArgumentException(
                    "token 의 배치와 item 의 배치가 일치하지 않습니다. (token batchId: "
                            + batch.getId() + ", item batchId: " + item.getBatchId() + ")");
        }

        Report report = reportRepository.findById(item.getReportId())
                .orElseThrow(() -> new EntityNotFoundException(
                        "성적서를 찾을 수 없습니다. (id: " + item.getReportId() + ")"));

        LocalDateTime now = LocalDateTime.now();

        try {
            // ── 완성 파일 스토리지 업로드 ────────────────────────────────────
            // 성적서 파일 고정 경로: {rootDir}/report/{reportId}/origin.xlsx
            String objectKey = storageProps.getRootDir() + "/report/" + report.getId() + "/origin.xlsx";
            try (InputStream is = file.getInputStream()) {
                ncloudS3Client.putObject(
                        PutObjectRequest.builder()
                                .bucket(storageProps.getBucketName())
                                .key(objectKey)
                                .acl(ObjectCannedACL.PUBLIC_READ)
                                .contentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
                                .build(),
                        RequestBody.fromInputStream(is, file.getSize())
                );
            }
            log.info("성적서 파일 업로드 완료 — reportId: {}, key: {}", report.getId(), objectKey);

            // ── file_info(origin) 등록 ────────────────────────────────────────
            // 재작성 시 중복 방지: 기존 origin file_info 소프트삭제 후 재등록
            String reportDir = "report/" + report.getId() + "/";
            fileInfoRepository.softDeleteByRefAndNames(
                    "report", report.getId(),
                    java.util.List.of("origin"),
                    YnType.n,
                    now,
                    batch.getRequestMemberId()
            );
            fileInfoRepository.save(FileInfo.builder()
                    .refTableName("report").refTableId(report.getId())
                    .originName("origin.xlsx").name("origin").extension("xlsx")
                    .fileSize(file.getSize()).contentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
                    .dir(reportDir).isVisible(YnType.y)
                    .createDatetime(now).createMemberId(batch.getRequestMemberId())
                    .build());
            log.debug("성적서 원본 file_info 등록 완료 — reportId: {}", report.getId());

            // ── item SUCCESS 처리 ─────────────────────────────────────────────
            item.setStatus(JobItemStatus.SUCCESS);
            item.setEndDatetime(now);

            // ── Report writeStatus → SUCCESS ──────────────────────────────────
            report.setWriteStatus(AppStatus.SUCCESS);
            report.setWriteDatetime(now);
            report.setWriteMemberId(batch.getRequestMemberId());

            // ── batch successCount++ ──────────────────────────────────────────
            batch.setSuccessCount(batch.getSuccessCount() + 1);

            // ── log 테이블 기록 ────────────────────────────────────────────────
            logRepository.save(Log.builder()
                    .workerName("excelwork")
                    .logContent(String.format(
                            "ExcelWork 성적서작성 완료 — reportId: %d, 성적서번호: %s, batchId: %d",
                            report.getId(), report.getReportNum(), batch.getId()))
                    .logType("u")
                    .refTable("report")
                    .refTableId(report.getId())
                    .createDatetime(now)
                    .createMemberId(batch.getRequestMemberId())
                    .build());

            // ── detach된 엔티티 재병합 ─────────────────────────────────────────
            // softDeleteByRefAndNames의 @Modifying(clearAutomatically=true)가 영속성 컨텍스트를
            // 초기화하므로, report/item/batch 가 detach 된 상태. 명시적 save()로 재병합해야
            // 위 setter 변경사항(writeStatus, writeMemberId 등)이 커밋 시 DB에 반영된다.
            reportRepository.save(report);
            itemRepository.save(item);
            batch = batchRepository.save(batch);

        } catch (Exception e) {
            log.error("callbackItemDone 처리 실패 — itemId: {}, reportId: {}: {}",
                    itemId, report.getId(), e.getMessage(), e);

            item.setStatus(JobItemStatus.FAIL);
            item.setMessage(e.getMessage());
            item.setEndDatetime(now);

            report.setWriteStatus(AppStatus.FAIL);
            batch.setFailCount(batch.getFailCount() + 1);

            // ── 실패 로그 기록 ─────────────────────────────────────────────────
            logRepository.save(Log.builder()
                    .workerName("excelwork")
                    .logContent(String.format(
                            "ExcelWork 성적서작성 실패 — reportId: %d, 성적서번호: %s, 오류: %s",
                            report.getId(), report.getReportNum(), e.getMessage()))
                    .logType("e")
                    .refTable("report")
                    .refTableId(report.getId())
                    .createDatetime(now)
                    .createMemberId(batch.getRequestMemberId())
                    .build());

            // detach된 엔티티 재병합 (실패 경로)
            reportRepository.save(report);
            itemRepository.save(item);
            batch = batchRepository.save(batch);
        }

        // ── 전체 완료 여부 확인 ──────────────────────────────────────────────
        int processed = batch.getSuccessCount() + batch.getFailCount();
        if (processed >= batch.getTotalCount()) {
            finalizeBatch(batch);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 7. callbackDone — 전체 완료 콜백
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * 미들웨어가 모든 item 처리를 완료한 후 호출.
     * item-done 콜백에서 이미 finalizeBatch 가 호출됐을 수 있으나
     * 미들웨어 측 명시적 완료 알림으로 재처리 (멱등).
     */
    @Transactional
    public void callbackDone(String token) {
        ReportJobBatch batch = findBatchByToken(token);

        if (batch.getStatus() == BatchStatus.SUCCESS || batch.getStatus() == BatchStatus.FAIL) {
            log.info("callbackDone: 이미 완료 상태 — 무시 (batchId: {}, status: {})",
                    batch.getId(), batch.getStatus());
            return;
        }

        finalizeBatch(batch);
        log.info("callbackDone 처리 완료 — batchId: {}", batch.getId());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 8. resetBatches — 비정상 종료 복구
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * 주어진 성적서 id 목록 기준으로 READY/PROGRESS 상태의 활성 배치를 찾아
     * CANCELED 로 전환하고 연관 성적서의 writeStatus 를 IDLE 로 복구한다.
     * 이미 SUCCESS 인 item 의 성적서는 SUCCESS 상태를 유지한다.
     *
     * @param reportIds 복구 대상 성적서 id 목록
     * @param memberId  요청자 id (로그 기록용)
     */
    @Transactional
    public void resetBatches(List<Long> reportIds, Long memberId) {
        // 성적서 id로 활성 배치(READY/PROGRESS) 조회
        List<ReportJobBatch> batches = batchRepository.findActiveByReportIds(reportIds);
        if (batches.isEmpty()) {
            throw new EntityNotFoundException("복구할 활성 배치가 없습니다. (READY/PROGRESS 상태인 배치 없음)");
        }

        LocalDateTime now = LocalDateTime.now();
        for (ReportJobBatch batch : batches) {
            if (batch.getStatus() != BatchStatus.READY && batch.getStatus() != BatchStatus.PROGRESS) {
                log.warn("resetBatches: READY/PROGRESS 가 아닌 배치 제외 (batchId: {}, status: {})",
                        batch.getId(), batch.getStatus());
                continue;
            }

            batch.setStatus(BatchStatus.CANCELED);
            batch.setEndDatetime(now);

            List<ReportJobItem> items = itemRepository.findByBatchId(batch.getId());
            for (ReportJobItem item : items) {
                if (item.getStatus() == JobItemStatus.SUCCESS) continue;

                item.setStatus(JobItemStatus.CANCELED);
                item.setEndDatetime(now);

                reportRepository.findById(item.getReportId()).ifPresent(r -> {
                    if (r.getWriteStatus() != AppStatus.SUCCESS) {
                        r.setWriteStatus(AppStatus.IDLE);
                    }
                });
            }

            log.info("배치 리셋 완료 — batchId: {}, 요청자: {}", batch.getId(), memberId);
            logRepository.save(Log.builder()
                    .workerName("system")
                    .logContent(String.format(
                            "ExcelWork 배치 상태 초기화 (batchId: %d) — CANCELED 처리", batch.getId()))
                    .logType("u")
                    .refTable("report_job_batch")
                    .refTableId(batch.getId())
                    .createDatetime(now)
                    .createMemberId(memberId)
                    .build());
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 9. previewRecover — 스마트 복구 미리보기 (DB 변경 없음)
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * 복구 대상 성적서 목록에 대해 스토리지 파일 존재 여부를 확인하여
     * "완료 처리될 목록(파일 있음)"과 "초기화될 목록(파일 없음)"을 미리 반환한다.
     * DB 변경 없음 — 조회 전용.
     *
     * @param reportIds 대상 성적서 id 목록 (writeStatus READY/PROGRESS 인 것만 처리)
     */
    @Transactional(readOnly = true)
    public ExcelWorkDTO.RecoverPreviewRes previewRecover(List<Long> reportIds) {
        List<ExcelWorkDTO.RecoverItemInfo> successItems = new ArrayList<>();
        List<ExcelWorkDTO.RecoverItemInfo> idleItems    = new ArrayList<>();

        // 활성 배치(READY/PROGRESS)의 startDatetime을 경과 시간 표시용으로 조회
        List<ReportJobBatch> activeBatches = batchRepository.findActiveByReportIds(reportIds);
        Map<Long, LocalDateTime> reportStartMap = buildReportStartMap(activeBatches);

        for (Long reportId : reportIds) {
            Report report = reportRepository.findById(reportId).orElse(null);
            if (report == null) continue;
            // 이미 완료된 성적서는 복구 대상 아님
            if (report.getWriteStatus() == AppStatus.SUCCESS) continue;

            String objectKey = storageProps.getRootDir() + "/report/" + reportId + "/origin.xlsx";
            boolean fileExists = headObject(objectKey) != null;

            // 배치 시작 경과 분 (배치가 없거나 아직 READY면 null)
            Long minutesAgo = null;
            LocalDateTime startDt = reportStartMap.get(reportId);
            if (startDt != null) {
                minutesAgo = ChronoUnit.MINUTES.between(startDt, LocalDateTime.now());
            }

            var info = new ExcelWorkDTO.RecoverItemInfo(reportId, report.getReportNum(), minutesAgo);
            if (fileExists) successItems.add(info);
            else            idleItems.add(info);
        }

        return new ExcelWorkDTO.RecoverPreviewRes(successItems, idleItems);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 10. smartRecover — 스마트 복구 실행
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * 파일 존재 여부를 기준으로 각 성적서를 스마트하게 복구한다.
     *
     *   파일 있음 (origin.xlsx 스토리지에 존재):
     *     callbackItemDone 재현 → writeStatus=SUCCESS, file_info 재등록
     *   파일 없음:
     *     writeStatus=IDLE, writeMemberId/writeDatetime 초기화, file_info soft-delete
     *
     * 배치 최종 상태: 전체 SUCCESS → SUCCESS / 전체 CANCELED → CANCELED / 혼합 → FAIL
     *
     * @param reportIds 복구 대상 성적서 id 목록
     * @param memberId  요청자 id (로그·file_info createMemberId 기록용)
     */
    @Transactional
    public ExcelWorkDTO.SmartRecoverRes smartRecover(List<Long> reportIds, Long memberId) {
        List<String> successNums = new ArrayList<>();
        List<String> idleNums    = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();

        // 활성 배치 목록 + reportId → batchId 매핑 (item 처리용)
        List<ReportJobBatch> activeBatches = batchRepository.findActiveByReportIds(reportIds);
        Map<Long, Long> reportToBatchId = buildReportToBatchIdMap(activeBatches);

        for (Long reportId : reportIds) {
            String objectKey = storageProps.getRootDir() + "/report/" + reportId + "/origin.xlsx";
            HeadObjectResponse head = headObject(objectKey);
            boolean fileExists = head != null;

            // softDeleteByRefAndNames 는 clearAutomatically=true → PC 초기화.
            // 이후 모든 엔티티 조작은 재로드 또는 명시적 save() 사용.
            fileInfoRepository.softDeleteByRefAndNames(
                    "report", reportId, List.of("origin"), YnType.n, now, memberId);

            if (fileExists) {
                // 파일 있음: file_info 재등록 (callbackItemDone 재현)
                fileInfoRepository.save(FileInfo.builder()
                        .refTableName("report").refTableId(reportId)
                        .originName("origin.xlsx").name("origin").extension("xlsx")
                        .fileSize(head.contentLength())
                        .contentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
                        .dir("report/" + reportId + "/").isVisible(YnType.y)
                        .createDatetime(now).createMemberId(memberId)
                        .build());
            }

            // PC 초기화 후 report 재로드
            Report report = reportRepository.findById(reportId).orElse(null);
            if (report == null) continue;
            if (report.getWriteStatus() == AppStatus.SUCCESS) continue; // 이미 완료

            if (fileExists) {
                report.setWriteStatus(AppStatus.SUCCESS);
                report.setWriteDatetime(now);
                report.setWriteMemberId(memberId);
                successNums.add(report.getReportNum());
            } else {
                report.setWriteStatus(AppStatus.IDLE);
                report.setWriteDatetime(null);
                report.setWriteMemberId(null);
                idleNums.add(report.getReportNum());
            }
            reportRepository.save(report);

            // item 처리
            Long batchId = reportToBatchId.get(reportId);
            if (batchId != null) {
                JobItemStatus targetItemStatus = fileExists ? JobItemStatus.SUCCESS : JobItemStatus.CANCELED;
                itemRepository.findByBatchId(batchId).stream()
                        .filter(i -> i.getReportId().equals(reportId)
                                  && i.getStatus() != JobItemStatus.SUCCESS)
                        .forEach(item -> {
                            item.setStatus(targetItemStatus);
                            item.setEndDatetime(now);
                            itemRepository.save(item);
                        });
            }
        }

        // 배치 최종 상태 결정
        for (ReportJobBatch origBatch : activeBatches) {
            ReportJobBatch batch = batchRepository.findById(origBatch.getId()).orElse(null);
            if (batch == null) continue;

            List<ReportJobItem> allItems = itemRepository.findByBatchId(batch.getId());
            long successCnt  = allItems.stream().filter(i -> i.getStatus() == JobItemStatus.SUCCESS).count();
            long canceledCnt = allItems.stream().filter(i -> i.getStatus() == JobItemStatus.CANCELED).count();

            if (successCnt == allItems.size()) {
                batch.setStatus(BatchStatus.SUCCESS);
                batch.setSuccessCount((int) successCnt);
            } else if (canceledCnt == allItems.size()) {
                batch.setStatus(BatchStatus.CANCELED);
            } else {
                // 성공/취소 혼합
                batch.setStatus(BatchStatus.FAIL);
                batch.setSuccessCount((int) successCnt);
                batch.setFailCount((int) (allItems.size() - successCnt - canceledCnt));
            }
            batch.setEndDatetime(now);
            batchRepository.save(batch);
        }

        // 로그 기록
        String logContent = String.format(
                "스마트 복구 — 완료처리: %d건 [%s], 초기화: %d건 [%s]",
                successNums.size(), String.join(", ", successNums),
                idleNums.size(),    String.join(", ", idleNums));
        logRepository.save(Log.builder()
                .workerName("system")
                .logContent(logContent)
                .logType("u")
                .refTable("report")
                .refTableId(reportIds.isEmpty() ? 0L : reportIds.get(0))
                .createDatetime(now)
                .createMemberId(memberId)
                .build());

        log.info("스마트 복구 완료 — {}", logContent);
        return new ExcelWorkDTO.SmartRecoverRes(
                successNums.size(), idleNums.size(), successNums, idleNums);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 11. callbackApprovalItemDone — 실무자결재 단건 완료 콜백
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * 미들웨어가 실무자결재 1건 처리 완료 후 signed.xlsx + signed.pdf 와 함께 호출.
     *
     * 처리 순서:
     *  1. item 조회 + token → batch 검증 + WORK_APPROVAL 타입 확인
     *  2. 기존 signed_xlsx / signed_pdf file_info 소프트삭제
     *  3. signed.xlsx → 스토리지 {rootDir}/report/{reportId}/signed.xlsx 업로드
     *  4. signed.pdf  → 스토리지 {rootDir}/report/{reportId}/signed.pdf  업로드
     *  5. file_info 2건 신규 등록
     *  6. item status → SUCCESS + endDatetime
     *  7. Report.workStatus → SUCCESS + workDatetime
     *  8. batch.successCount++ + 전체 완료 여부 확인
     */
    @Transactional
    public void callbackApprovalItemDone(String token, Long itemId,
                                         MultipartFile xlsxFile, MultipartFile pdfFile) {
        ReportJobBatch batch = findBatchByToken(token);

        if (batch.getJobType() != com.bada.cali.common.enums.JobType.WORK_APPROVAL) {
            throw new IllegalArgumentException(
                    "WORK_APPROVAL 배치가 아닙니다. (batchId: " + batch.getId() + ")");
        }

        ReportJobItem item = itemRepository.findById(itemId)
                .orElseThrow(() -> new EntityNotFoundException("item을 찾을 수 없습니다. (id: " + itemId + ")"));

        if (!item.getBatchId().equals(batch.getId())) {
            throw new IllegalArgumentException(
                    "token의 배치와 item의 배치가 일치하지 않습니다.");
        }

        Report report = reportRepository.findById(item.getReportId())
                .orElseThrow(() -> new EntityNotFoundException(
                        "성적서를 찾을 수 없습니다. (id: " + item.getReportId() + ")"));

        LocalDateTime now = LocalDateTime.now();
        String bucket   = storageProps.getBucketName();
        String rootDir  = storageProps.getRootDir();
        String CT_XLSX  = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
        String CT_PDF   = "application/pdf";
        String reportDir = "report/" + report.getId() + "/";

        try {
            // ── 기존 signed 파일 file_info 소프트삭제 ─────────────────────────
            fileInfoRepository.softDeleteByRefAndNames(
                    "report", report.getId(),
                    java.util.List.of("signed_xlsx", "signed_pdf"),
                    com.bada.cali.common.enums.YnType.n,
                    now,
                    batch.getRequestMemberId()
            );

            // ── signed.xlsx 업로드 ────────────────────────────────────────────
            String xlsxKey = rootDir + "/report/" + report.getId() + "/signed.xlsx";
            try (InputStream is = xlsxFile.getInputStream()) {
                ncloudS3Client.putObject(
                        PutObjectRequest.builder()
                                .bucket(bucket).key(xlsxKey)
                                .acl(ObjectCannedACL.PUBLIC_READ)
                                .contentType(CT_XLSX)
                                .build(),
                        RequestBody.fromInputStream(is, xlsxFile.getSize())
                );
            }
            fileInfoRepository.save(FileInfo.builder()
                    .refTableName("report").refTableId(report.getId())
                    .originName("signed.xlsx").name("signed_xlsx").extension("xlsx")
                    .fileSize(xlsxFile.getSize()).contentType(CT_XLSX)
                    .dir(reportDir).isVisible(com.bada.cali.common.enums.YnType.y)
                    .createDatetime(now).createMemberId(batch.getRequestMemberId())
                    .build());

            // ── signed.pdf 업로드 ─────────────────────────────────────────────
            String pdfKey = rootDir + "/report/" + report.getId() + "/signed.pdf";
            try (InputStream is = pdfFile.getInputStream()) {
                ncloudS3Client.putObject(
                        PutObjectRequest.builder()
                                .bucket(bucket).key(pdfKey)
                                .acl(ObjectCannedACL.PUBLIC_READ)
                                .contentType(CT_PDF)
                                .build(),
                        RequestBody.fromInputStream(is, pdfFile.getSize())
                );
            }
            fileInfoRepository.save(FileInfo.builder()
                    .refTableName("report").refTableId(report.getId())
                    .originName("signed.pdf").name("signed_pdf").extension("pdf")
                    .fileSize(pdfFile.getSize()).contentType(CT_PDF)
                    .dir(reportDir).isVisible(com.bada.cali.common.enums.YnType.y)
                    .createDatetime(now).createMemberId(batch.getRequestMemberId())
                    .build());

            log.info("실무자결재 파일 업로드 완료 — reportId: {}, xlsxKey: {}, pdfKey: {}",
                    report.getId(), xlsxKey, pdfKey);

            // ── item / report / batch 상태 업데이트 ───────────────────────────
            item.setStatus(JobItemStatus.SUCCESS);
            item.setEndDatetime(now);
            report.setWorkStatus(AppStatus.SUCCESS);
            report.setWorkDatetime(now);
            batch.setSuccessCount(batch.getSuccessCount() + 1);

            logRepository.save(Log.builder()
                    .workerName("excelwork")
                    .logContent(String.format(
                            "ExcelWork 실무자결재 완료 — reportId: %d, 성적서번호: %s, batchId: %d",
                            report.getId(), report.getReportNum(), batch.getId()))
                    .logType("u")
                    .refTable("report")
                    .refTableId(report.getId())
                    .createDatetime(now)
                    .createMemberId(batch.getRequestMemberId())
                    .build());

            // ── detach된 엔티티 재병합 ─────────────────────────────────────────
            // softDeleteByRefAndNames의 @Modifying(clearAutomatically=true)가 영속성 컨텍스트를
            // 초기화하므로, report/item/batch 가 detach 된 상태. 명시적 save()로 재병합해야
            // 위 setter 변경사항(workStatus, workDatetime 등)이 커밋 시 DB에 반영된다.
            reportRepository.save(report);
            itemRepository.save(item);
            batch = batchRepository.save(batch);

        } catch (Exception e) {
            log.error("callbackApprovalItemDone 처리 실패 — itemId: {}, reportId: {}: {}",
                    itemId, report.getId(), e.getMessage(), e);
            item.setStatus(JobItemStatus.FAIL);
            item.setMessage(e.getMessage());
            item.setEndDatetime(now);
            report.setWorkStatus(AppStatus.FAIL);
            batch.setFailCount(batch.getFailCount() + 1);

            logRepository.save(Log.builder()
                    .workerName("excelwork")
                    .logContent(String.format(
                            "ExcelWork 실무자결재 실패 — reportId: %d, 성적서번호: %s, 오류: %s",
                            report.getId(), report.getReportNum(), e.getMessage()))
                    .logType("e")
                    .refTable("report")
                    .refTableId(report.getId())
                    .createDatetime(now)
                    .createMemberId(batch.getRequestMemberId())
                    .build());

            // detach된 엔티티 재병합 (실패 경로)
            reportRepository.save(report);
            itemRepository.save(item);
            batch = batchRepository.save(batch);
        }

        int processed = batch.getSuccessCount() + batch.getFailCount();
        if (processed >= batch.getTotalCount()) {
            finalizeBatch(batch);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 10. streamSignImage — 실무자 서명 이미지 스트리밍
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * token으로 배치를 조회하여 requestMember의 서명 이미지를 스토리지에서 스트리밍한다.
     *
     * 미들웨어가 WORK_APPROVAL 처리 시 sign 이미지를 다운로드할 때 사용.
     * 인증: X-Callback-Key 헤더 (컨트롤러 레벨).
     *
     * @param token 잡 토큰 (배치 조회 + requestMemberId 특정)
     */
    @Transactional(readOnly = true)
    public ResponseEntity<Resource> streamSignImage(String token) {
        ReportJobBatch batch = findBatchByToken(token);

        // 서명 이미지는 배치 requestMember(로그인 사용자)가 아닌 성적서의 workMember 기준으로 조회한다.
        List<ReportJobItem> signItems = itemRepository.findByBatchId(batch.getId());
        if (signItems.isEmpty()) {
            throw new EntityNotFoundException("배치에 처리 항목이 없습니다. (batchId: " + batch.getId() + ")");
        }
        Report signReport = reportRepository.findById(signItems.get(0).getReportId())
                .orElseThrow(() -> new EntityNotFoundException("성적서를 찾을 수 없습니다."));
        Long memberId = signReport.getWorkMemberId();
        if (memberId == null) {
            throw new IllegalArgumentException("실무자가 지정되지 않은 성적서입니다. (reportId: " + signReport.getId() + ")");
        }

        // 해당 멤버의 서명 이미지 file_info 조회 (최신 1건)
        java.util.List<FileInfo> signFiles = fileInfoRepository
                .findByRefTableNameAndRefTableIdAndIsVisible(
                        "member", memberId, com.bada.cali.common.enums.YnType.y);

        if (signFiles.isEmpty()) {
            throw new EntityNotFoundException(
                    "서명 이미지를 찾을 수 없습니다. (memberId: " + memberId + ")");
        }
        // 최신 파일 우선 (createDatetime 내림차순이 없으므로 마지막 항목 사용)
        FileInfo fileInfo = signFiles.get(signFiles.size() - 1);

        // objectKey 구성: {rootDir}/{dir}/{fileInfo.id}.{extension}
        String rootDir = storageProps.getRootDir();
        String dir = fileInfo.getDir();
        String objectKey = rootDir + "/" +
                (dir.endsWith("/") ? dir : dir + "/") + fileInfo.getId();
        if (fileInfo.getExtension() != null && !fileInfo.getExtension().isBlank()) {
            objectKey += "." + fileInfo.getExtension();
        }

        log.info("서명 이미지 스트리밍 — memberId: {}, fileInfoId: {}, key: {}",
                memberId, fileInfo.getId(), objectKey);

        return streamFromStorage(objectKey,
                fileInfo.getOriginName() != null ? fileInfo.getOriginName() : "sign.png",
                fileInfo.getContentType());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 11. streamSignImageByWorkMemberId — workMemberId 기반 서명 이미지 스트리밍 (per-item)
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * workMemberId로 해당 실무자의 서명 이미지를 스토리지에서 스트리밍한다.
     *
     * WORK_APPROVAL 처리 시 item별 서명 이미지를 다운로드할 때 사용.
     * 성적서별 실무자가 다를 수 있으므로 token 기반이 아닌 workMemberId 기반으로 직접 조회한다.
     * 인증: X-Callback-Key 헤더 (컨트롤러 레벨).
     *
     * @param workMemberId 서명 이미지를 조회할 실무자 member id
     */
    @Transactional(readOnly = true)
    public ResponseEntity<Resource> streamSignImageByWorkMemberId(Long workMemberId) {
        java.util.List<FileInfo> signFiles = fileInfoRepository
                .findByRefTableNameAndRefTableIdAndIsVisible(
                        "member", workMemberId, com.bada.cali.common.enums.YnType.y);

        if (signFiles.isEmpty()) {
            throw new EntityNotFoundException(
                    "서명 이미지를 찾을 수 없습니다. (workMemberId: " + workMemberId + ")");
        }
        FileInfo fileInfo = signFiles.get(signFiles.size() - 1);

        String rootDir = storageProps.getRootDir();
        String dir = fileInfo.getDir();
        String objectKey = rootDir + "/" +
                (dir.endsWith("/") ? dir : dir + "/") + fileInfo.getId();
        if (fileInfo.getExtension() != null && !fileInfo.getExtension().isBlank()) {
            objectKey += "." + fileInfo.getExtension();
        }

        log.info("서명 이미지 스트리밍 (per-item) — workMemberId: {}, fileInfoId: {}, key: {}",
                workMemberId, fileInfo.getId(), objectKey);

        return streamFromStorage(objectKey,
                fileInfo.getOriginName() != null ? fileInfo.getOriginName() : "sign.png",
                fileInfo.getContentType());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 내부 유틸 메서드
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * 스토리지 객체 존재 여부 및 메타데이터 확인.
     * 파일이 없으면 null 반환, 있으면 HeadObjectResponse 반환.
     */
    private HeadObjectResponse headObject(String objectKey) {
        try {
            return ncloudS3Client.headObject(
                    HeadObjectRequest.builder()
                            .bucket(storageProps.getBucketName())
                            .key(objectKey)
                            .build()
            );
        } catch (NoSuchKeyException e) {
            return null; // 파일 없음
        } catch (S3Exception e) {
            // 404 외의 S3 오류도 없음으로 처리 (파일 확인 실패 시 복구 차단 방지)
            log.warn("headObject 실패 (없음으로 처리) — key: {}, status: {}", objectKey, e.statusCode());
            return null;
        }
    }

    /**
     * 활성 배치 목록에서 reportId → batchId 매핑을 빌드한다.
     * 하나의 배치에 여러 reportId가 포함될 수 있으므로 item을 순회해 매핑.
     */
    private Map<Long, Long> buildReportToBatchIdMap(List<ReportJobBatch> batches) {
        Map<Long, Long> map = new HashMap<>();
        for (ReportJobBatch batch : batches) {
            itemRepository.findByBatchId(batch.getId())
                    .forEach(item -> map.put(item.getReportId(), batch.getId()));
        }
        return map;
    }

    /**
     * 활성 배치 목록에서 reportId → 배치 startDatetime 매핑을 빌드한다.
     * 미리보기 시 배치 경과 시간 표시에 사용.
     */
    private Map<Long, LocalDateTime> buildReportStartMap(List<ReportJobBatch> batches) {
        Map<Long, LocalDateTime> map = new HashMap<>();
        for (ReportJobBatch batch : batches) {
            if (batch.getStartDatetime() == null) continue; // READY 상태 (아직 시작 안 함)
            itemRepository.findByBatchId(batch.getId())
                    .forEach(item -> map.put(item.getReportId(), batch.getStartDatetime()));
        }
        return map;
    }

    /** token 으로 배치 조회 — 존재하지 않으면 EntityNotFoundException */
    private ReportJobBatch findBatchByToken(String token) {
        return batchRepository.findByToken(token)
                .orElseThrow(() -> new EntityNotFoundException(
                        "유효하지 않은 token 입니다. (token: " + token + ")"));
    }

    /** 배치 최종 완료 처리. successCount/failCount 기준으로 SUCCESS/FAIL 확정. */
    private void finalizeBatch(ReportJobBatch batch) {
        batch.setEndDatetime(LocalDateTime.now());
        batch.setStatus(batch.getFailCount() > 0 ? BatchStatus.FAIL : BatchStatus.SUCCESS);
        log.info("배치 최종 완료 — batchId: {}, status: {}, 성공: {}, 실패: {}",
                batch.getId(), batch.getStatus(), batch.getSuccessCount(), batch.getFailCount());
    }

    /**
     * 콜백 키 검증.
     * app.excelwork.callback-key 가 비어 있으면 개발 모드 (검증 생략).
     */
    public boolean isValidCallbackKey(String callbackKey) {
        if (excelworkCallbackKey == null || excelworkCallbackKey.isBlank()) {
            log.warn("app.excelwork.callback-key 미설정 — 콜백 키 검증 생략 (개발 모드)");
            return true;
        }
        return excelworkCallbackKey.equals(callbackKey);
    }

    /** serverUrl 파라미터가 비어있으면 설정값 폴백 */
    private String resolveServerUrl(String serverUrl) {
        return (serverUrl != null && !serverUrl.isBlank()) ? serverUrl : callbackBaseUrl;
    }

    /**
     * ReportFillDataRes 를 fieldCode → 값(String) 맵으로 변환.
     *
     * 변환 규칙:
     *   - LocalDate : ISO 8601 문자열 "yyyy-MM-dd" (미들웨어가 format 에 따라 재변환)
     *   - Integer   : 숫자 문자열 (예: "12")
     *   - null 값   : null 그대로 포함 (미들웨어가 빈 문자열로 처리)
     *   - 내부 메타데이터(sampleFileId 등)는 포함하지 않음
     */
    private Map<String, String> buildDataMap(WorkerDataDTO.ReportFillDataRes d) {
        Map<String, String> map = new LinkedHashMap<>();

        // 신청업체
        map.put("custAgent",      d.getCustAgent());
        map.put("custAgentEn",    d.getCustAgentEn());
        map.put("custAgentAddr",  d.getCustAgentAddr());
        map.put("custAgentAddrEn", d.getCustAgentAddrEn());

        // 접수/성적서 번호
        map.put("orderNum",  d.getOrderNum());
        map.put("reportNum", d.getReportNum());

        // 분류코드
        map.put("middleItemCodeNum", d.getMiddleItemCodeNum());
        map.put("smallItemCodeNum",  d.getSmallItemCodeNum());

        // 기기 정보
        map.put("itemName",    d.getItemName());
        map.put("itemNameEn",  d.getItemNameEn());
        map.put("makeAgent",   d.getMakeAgent());
        map.put("makeAgentEn", d.getMakeAgentEn());
        map.put("format",      d.getFormat());
        map.put("itemNum",     d.getItemNum());
        map.put("assetNum",    d.getAssetNum());

        // 교정 정보
        map.put("caliAddress",   d.getCaliAddress());
        map.put("siteAddr",      d.getSiteAddr());
        map.put("siteAddrEn",    d.getSiteAddrEn());
        map.put("caliDate",      localDateToStr(d.getCaliDate()));
        map.put("itemCaliCycle", d.getItemCaliCycle() != null ? d.getItemCaliCycle().toString() : null);
        map.put("approvalDate",  localDateToStr(d.getApprovalDate()));

        // 환경 데이터
        map.put("tempMin", d.getTempMin());
        map.put("tempMax", d.getTempMax());
        map.put("humMin",  d.getHumMin());
        map.put("humMax",  d.getHumMax());
        map.put("preMin",  d.getPreMin());
        map.put("preMax",  d.getPreMax());

        // 담당자
        map.put("worker",      d.getWorker());
        map.put("workerEn",    d.getWorkerEn());
        map.put("approval",    d.getApproval());
        map.put("approvalEn",  d.getApprovalEn());

        // 소급성 문구
        map.put("traceStatement",    d.getTraceStatement());
        map.put("traceStatement2",   d.getTraceStatement2());
        map.put("traceStatement3",   d.getTraceStatement3());
        map.put("traceStatementEn",  d.getTraceStatementEn());
        map.put("traceStatementEn2", d.getTraceStatementEn2());
        map.put("traceStatementEn3", d.getTraceStatementEn3());

        // 성적서 언어
        map.put("reportLang", d.getReportLang());

        return map;
    }

    /** LocalDate → ISO 8601 문자열 ("yyyy-MM-dd"). null이면 null 반환. */
    private String localDateToStr(LocalDate date) {
        return date != null ? date.toString() : null;
    }
}