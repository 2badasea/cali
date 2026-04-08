package com.bada.cali.service;

import com.bada.cali.common.enums.AppStatus;
import com.bada.cali.common.enums.BatchStatus;
import com.bada.cali.common.enums.JobItemStatus;
import com.bada.cali.common.enums.JobType;
import com.bada.cali.common.enums.ReportType;
import com.bada.cali.common.enums.YnType;
import com.bada.cali.config.NcpStorageProperties;
import com.bada.cali.dto.ExcelWorkDTO;
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
import org.springframework.http.ContentDisposition;
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
import software.amazon.awssdk.services.s3.model.ObjectCannedACL;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * ExcelWork 미들웨어 연동 서비스.
 *
 * 역할 범위:
 *   - createJob: 배치·item 생성 + token 발급 + excelwork:// URI 생성
 *   - getJobByToken: 미들웨어용 잡 상세 조회 (파일 중계 URL 포함)
 *   - streamFile: 스토리지 파일을 서버 중계로 스트리밍
 *   - callbackReady: 미들웨어 READY → batch/item PROGRESS 전환
 *   - callbackItemDone: 완성 파일 스토리지 업로드 + item SUCCESS
 *   - callbackDone: 배치 전체 완료 처리
 *   - resetBatches: 비정상 종료 복구 (READY/PROGRESS → CANCELED + 성적서 IDLE)
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

    // ── ExcelWork 전용 설정 ───────────────────────────────────────────────────

    /** ExcelWork 미들웨어 콜백 인증 키. 비어 있으면 개발 모드 (검증 생략). */
    @Value("${app.excelwork.callback-key:}")
    private String excelworkCallbackKey;

    /** 미들웨어가 콜백을 보낼 CALI 서버 URL (CreateJobReq.serverUrl 미입력 시 폴백) */
    @Value("${app.cali.callback-base-url:http://localhost:8050}")
    private String callbackBaseUrl;

    // ─────────────────────────────────────────────────────────────────────────
    // 1. createJob — 배치 생성 + token 발급
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * 성적서작성 작업 요청 처리.
     *
     * 처리 순서:
     *  1. 성적서 유효성 검증
     *  2. ReportJobBatch 생성 (status=READY, token=UUID)
     *  3. ReportJobItem 목록 생성
     *  4. 대상 성적서 writeStatus → READY (미들웨어 대기)
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
            // (활성 배치 존재 여부 확인 — 고착 상태 자동 복구는 resetBatches 에서 처리)
            if (report.getWriteStatus() == AppStatus.READY || report.getWriteStatus() == AppStatus.PROGRESS) {
                boolean hasActive = itemRepository.existsActiveBatchForReport(report.getId(), "WRITE");
                if (hasActive) {
                    throw new IllegalArgumentException(
                            String.format("이미 작업이 진행 중인 성적서입니다. (id: %d, 번호: %s)",
                                    report.getId(), report.getReportNum()));
                }
                // 활성 배치 없음 → 이전 비정상 종료 흔적 — FAIL 리셋 후 재작업 허용
                log.warn("writeStatus={} 이지만 활성 배치 없음 — FAIL 자동 리셋 (reportId: {})",
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

        // ── 3. ReportJobItem 생성 ─────────────────────────────────────────────
        List<ReportJobItem> items = reportIds.stream()
                .map(reportId -> ReportJobItem.builder()
                        .batchId(batch.getId())
                        .reportId(reportId)
                        .build())  // status 기본값: READY
                .collect(Collectors.toList());
        itemRepository.saveAll(items);

        // ── 4. 성적서 writeStatus → READY (미들웨어 대기 상태) ────────────────
        reports.forEach(r -> r.setWriteStatus(AppStatus.READY));
        // @Transactional 범위 내 dirty checking 으로 자동 반영

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
        String serverUrl = (req.getServerUrl() != null && !req.getServerUrl().isBlank())
                ? req.getServerUrl()
                : callbackBaseUrl;
        String excelworkUri = "excelwork://process?token=" + token + "&serverUrl=" + serverUrl;

        return new ExcelWorkDTO.CreateJobRes(batch.getId(), token, excelworkUri, reportIds.size());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 2. getJobByToken — 미들웨어용 잡 상세 조회
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * token 으로 배치 + 성적서 목록 + 파일 중계 URL 을 조회한다.
     * 미들웨어가 작업 시작 전 호출하며, 이 응답의 fileUrl 로 파일을 다운로드한다.
     *
     * @param token     잡 토큰
     * @param serverUrl 파일 중계 URL 생성에 사용할 서버 베이스 URL
     */
    @Transactional(readOnly = true)
    public ExcelWorkDTO.JobDetailRes getJobByToken(String token, String serverUrl) {
        ReportJobBatch batch = findBatchByToken(token);

        List<ReportJobItem> items = itemRepository.findByBatchId(batch.getId());

        // 서버 베이스 URL (파일 중계 경로 앞에 붙임)
        String base = (serverUrl != null && !serverUrl.isBlank()) ? serverUrl : callbackBaseUrl;

        List<ExcelWorkDTO.ItemDetail> itemDetails = items.stream().map(item -> {
            Report report = reportRepository.findById(item.getReportId())
                    .orElseThrow(() -> new EntityNotFoundException(
                            "성적서를 찾을 수 없습니다. (id: " + item.getReportId() + ")"));

            // WRITE: 샘플 파일 중계 URL (item마다 동일하지만 통일성을 위해 포함)
            String sampleFileUrl = (batch.getSampleId() != null)
                    ? base + "/api/excelwork/file/" + token + "/sample"
                    : null;

            // WORK_APPROVAL: 원본 파일 중계 URL (현재 WRITE만 구현, 결재 확장 시 사용)
            String originFileUrl = null;

            return new ExcelWorkDTO.ItemDetail(
                    item.getId(),
                    report.getId(),
                    report.getReportNum(),
                    sampleFileUrl,
                    originFileUrl
            );
        }).toList();

        return new ExcelWorkDTO.JobDetailRes(
                token,
                batch.getJobType().name(),
                batch.getSampleId(),
                itemDetails
        );
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 3. streamFile — 스토리지 파일 서버 중계 스트리밍
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * 미들웨어가 요청한 파일을 스토리지에서 스트리밍으로 중계한다.
     * 미들웨어는 NCP 자격증명 없이 이 엔드포인트로만 파일을 다운로드한다.
     *
     * fileType 허용값:
     *   - "sample" : WRITE 전용 — sampleId 기반 샘플 엑셀 파일
     *   - "origin" : 결재 전용 — report/{reportId}/origin.xlsx (향후 확장)
     *
     * @param token    잡 토큰 (유효성 확인용)
     * @param fileType 파일 종류
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

    /**
     * 샘플 파일 스트리밍.
     * file_info 에서 refTableName='sample', refTableId=sampleId 로 파일 조회 후 스트리밍.
     */
    private ResponseEntity<Resource> streamSampleFile(ReportJobBatch batch) {
        if (batch.getSampleId() == null) {
            throw new IllegalArgumentException("이 배치는 sampleId 가 없습니다. (batchId: " + batch.getId() + ")");
        }

        // sample 에 연결된 파일 조회 (첫 번째 visible 파일 사용)
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
    // 4. callbackReady — 미들웨어 READY 콜백
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * 미들웨어가 파일 다운로드 완료 후 작업 시작 직전에 호출.
     * batch status READY → PROGRESS, startDatetime 기록.
     * item status READY → PROGRESS (일괄).
     */
    @Transactional
    public void callbackReady(String token) {
        ReportJobBatch batch = findBatchByToken(token);

        if (batch.getStatus() != BatchStatus.READY) {
            log.warn("callbackReady 호출 시 배치 상태가 READY 가 아님 — 무시 (token: {}, status: {})",
                    token, batch.getStatus());
            return;
        }

        batch.setStatus(BatchStatus.PROGRESS);
        batch.setStartDatetime(LocalDateTime.now());

        // item 전체 PROGRESS 전환
        List<ReportJobItem> items = itemRepository.findByBatchId(batch.getId());
        items.forEach(item -> {
            item.setStatus(JobItemStatus.PROGRESS);
            item.setStartDatetime(LocalDateTime.now());
        });

        // 연관 성적서 writeStatus → PROGRESS (미들웨어 실제 작업 시작)
        items.forEach(item -> reportRepository.findById(item.getReportId())
                .ifPresent(r -> r.setWriteStatus(AppStatus.PROGRESS)));

        log.info("callbackReady 처리 완료 — batchId: {}, token: {}", batch.getId(), token);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 5. callbackItemDone — 미들웨어 단건 완료 콜백
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
     *  6. 전체 완료 여부 확인 (마지막 item이면 batch 최종 처리)
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

        // 토큰의 배치와 item 의 배치가 일치하는지 검증
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
            log.info("성적서 파일 업로드 완료 — key: {}", objectKey);

            // ── item SUCCESS 처리 ─────────────────────────────────────────────
            item.setStatus(JobItemStatus.SUCCESS);
            item.setEndDatetime(now);

            // ── Report writeStatus → SUCCESS ──────────────────────────────────
            report.setWriteStatus(AppStatus.SUCCESS);
            report.setWriteDatetime(now);

            // ── batch successCount++ ──────────────────────────────────────────
            batch.setSuccessCount(batch.getSuccessCount() + 1);

        } catch (Exception e) {
            log.error("callbackItemDone 처리 실패 — itemId: {}, reportId: {}: {}",
                    itemId, report.getId(), e.getMessage(), e);

            item.setStatus(JobItemStatus.FAIL);
            item.setMessage(e.getMessage());
            item.setEndDatetime(now);

            report.setWriteStatus(AppStatus.FAIL);
            batch.setFailCount(batch.getFailCount() + 1);
        }

        // ── 전체 완료 여부 확인 (마지막 item 이면 배치 최종 처리) ────────────
        int processed = batch.getSuccessCount() + batch.getFailCount();
        if (processed >= batch.getTotalCount()) {
            finalizeBatch(batch);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 6. callbackDone — 전체 완료 콜백
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * 미들웨어가 모든 item 처리를 완료한 후 호출.
     * item-done 콜백에서 이미 finalizeBatch 가 호출됐을 수 있지만,
     * 미들웨어 측에서 명시적으로 완료를 알리는 용도로 추가적으로 처리한다.
     */
    @Transactional
    public void callbackDone(String token) {
        ReportJobBatch batch = findBatchByToken(token);

        // 이미 완료된 경우 무시
        if (batch.getStatus() == BatchStatus.SUCCESS || batch.getStatus() == BatchStatus.FAIL) {
            log.info("callbackDone: 이미 완료 상태 — 무시 (batchId: {}, status: {})",
                    batch.getId(), batch.getStatus());
            return;
        }

        finalizeBatch(batch);
        log.info("callbackDone 처리 완료 — batchId: {}, token: {}", batch.getId(), token);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 7. resetBatches — 비정상 종료 복구
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * READY 또는 PROGRESS 상태로 고착된 배치를 CANCELED 로 전환하고
     * 연관 성적서의 writeStatus 를 IDLE 로 복구한다.
     *
     * 이미 SUCCESS 인 item 의 성적서는 SUCCESS 상태를 유지하여 중복 처리를 방지한다.
     *
     * @param batchIds 초기화할 배치 id 목록
     * @param memberId 요청자 id (로그 기록용)
     */
    @Transactional
    public void resetBatches(List<Long> batchIds, Long memberId) {
        List<ReportJobBatch> batches = batchRepository.findAllById(batchIds);
        if (batches.isEmpty()) {
            throw new EntityNotFoundException("초기화할 배치가 없습니다.");
        }

        LocalDateTime now = LocalDateTime.now();
        for (ReportJobBatch batch : batches) {
            if (batch.getStatus() != BatchStatus.READY && batch.getStatus() != BatchStatus.PROGRESS) {
                log.warn("resetBatches: READY/PROGRESS 가 아닌 배치 제외 (batchId: {}, status: {})",
                        batch.getId(), batch.getStatus());
                continue;
            }

            // 배치 CANCELED 처리
            batch.setStatus(BatchStatus.CANCELED);
            batch.setEndDatetime(now);

            // item 별 처리
            List<ReportJobItem> items = itemRepository.findByBatchId(batch.getId());
            for (ReportJobItem item : items) {
                // 이미 SUCCESS 인 item 은 유지 (파일이 스토리지에 올라가 있음)
                if (item.getStatus() == JobItemStatus.SUCCESS) continue;

                item.setStatus(JobItemStatus.CANCELED);
                item.setEndDatetime(now);

                // 성적서 writeStatus IDLE 복구 (SUCCESS 아닌 것만)
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
    // 내부 유틸 메서드
    // ─────────────────────────────────────────────────────────────────────────

    /** token 으로 배치 조회 — 존재하지 않으면 EntityNotFoundException */
    private ReportJobBatch findBatchByToken(String token) {
        return batchRepository.findByToken(token)
                .orElseThrow(() -> new EntityNotFoundException(
                        "유효하지 않은 token 입니다. (token: " + token + ")"));
    }

    /**
     * 배치 최종 완료 처리.
     * successCount/failCount 기준으로 batch status 를 SUCCESS 또는 FAIL 로 확정.
     * endDatetime 을 현재 시각으로 설정.
     */
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
}