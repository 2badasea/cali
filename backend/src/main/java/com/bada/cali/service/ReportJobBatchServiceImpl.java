package com.bada.cali.service;

import com.bada.cali.common.enums.AppStatus;
import com.bada.cali.common.enums.BatchStatus;
import com.bada.cali.common.enums.JobItemStatus;
import com.bada.cali.common.enums.JobType;
import com.bada.cali.common.enums.ReportType;
import com.bada.cali.common.enums.YnType;
import com.bada.cali.dto.ReportJobBatchDTO;
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
import com.bada.cali.security.CustomUserDetails;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Log4j2
@RequiredArgsConstructor
public class ReportJobBatchServiceImpl {

    private final ReportRepository            reportRepository;
    private final ReportJobBatchRepository    batchRepository;
    private final ReportJobItemRepository     itemRepository;
    private final LogRepository               logRepository;
    private final FileInfoRepository          fileInfoRepository;
    private final DemoJobProcessorService     demoProcessor;

    // ── 작업서버 연동 설정 (@Value 주입) ──────────────────────────────────────

    /** 작업서버 베이스 URL. 비어 있으면 트리거 생략(개발 모드). */
    @Value("${app.worker.url:}")
    private String workerUrl;

    /** CALI ↔ 작업서버 공용 API 인증 키 */
    @Value("${app.worker.api-key:}")
    private String workerApiKey;

    /** CALI 서버의 콜백 베이스 URL (작업서버가 이 주소로 콜백 요청) */
    @Value("${app.cali.callback-base-url:http://localhost:8050}")
    private String callbackBaseUrl;

    // NCP 오브젝트 스토리지 접속 정보 — 트리거 페이로드에 포함
    @Value("${ncp.storage.endpoint}")       private String storageEndpoint;
    @Value("${ncp.storage.bucket-name}")    private String storageBucketName;
    @Value("${ncp.storage.root-dir}")       private String storageRootDir;
    @Value("${ncp.storage.access-key}")     private String storageAccessKey;
    @Value("${ncp.storage.secret-key}")     private String storageSecretKey;

    /** 작업서버 트리거 요청에 사용하는 HTTP 클라이언트 */
    private final RestClient restClient = RestClient.create();

    /**
     * 성적서작성 배치 생성 (WRITE 타입)
     *
     * 처리 순서:
     *  1. 대상 성적서 유효성 검증
     *     - 존재 여부, soft delete 여부, SELF 타입 여부, 소분류 설정 여부
     *     - write_status 가 PROGRESS 인 경우(이미 처리 중) 제외하고 경고
     *  2. ReportJobBatch 생성 (status=READY)
     *  3. 각 성적서에 대해 ReportJobItem 생성 (status=READY)
     *  4. 대상 성적서의 write_status → PROGRESS 업데이트
     *  5. 작업 로그 기록
     *  6. 배치 id + 총 건수 반환
     *
     * NOTE: 작업서버 트리거(POST /api/jobs/execute)는 현재 미구현.
     *       배치/아이템을 DB에 READY 상태로만 생성해두고,
     *       추후 작업서버 연동 시 이 메서드 하단에 트리거 호출을 추가한다.
     *
     * @param req  성적서 id 목록 + 샘플 id
     * @param user 요청자 (로그인 사용자)
     * @return 생성된 배치 id + 총 건수
     */
    @Transactional
    public ReportJobBatchDTO.CreateBatchRes createWriteBatch(
            ReportJobBatchDTO.CreateWriteBatchReq req,
            CustomUserDetails user
    ) {
        List<Long> reportIds = req.getReportIds();

        // ── 1. 대상 성적서 조회 및 유효성 검증 ────────────────────────────────
        List<Report> reports = reportRepository.findAllById(reportIds);

        // 요청한 id 수와 실제 조회된 수가 다르면 일부 id가 존재하지 않는 것
        if (reports.size() != reportIds.size()) {
            throw new EntityNotFoundException("존재하지 않는 성적서가 포함되어 있습니다.");
        }

        // 각 성적서에 대한 세부 검증
        List<Long> validatedIds = new ArrayList<>();
        for (Report report : reports) {
            // soft delete 된 성적서 제외
            if (report.getIsVisible() != YnType.y) {
                throw new IllegalArgumentException(
                        String.format("삭제된 성적서가 포함되어 있습니다. (id: %d)", report.getId()));
            }
            // 자체성적서(SELF)만 처리 가능
            if (report.getReportType() != ReportType.SELF) {
                throw new IllegalArgumentException(
                        String.format("자체성적서(SELF)만 성적서작성이 가능합니다. (id: %d)", report.getId()));
            }
            // 소분류 미설정 성적서는 처리 불가
            if (report.getSmallItemCodeId() == null) {
                throw new IllegalArgumentException(
                        String.format("소분류가 설정되지 않은 성적서가 포함되어 있습니다. (id: %d)", report.getId()));
            }
            // 이미 성적서작성 진행 중인 경우 처리
            if (report.getWriteStatus() == AppStatus.PROGRESS) {
                // 실제 활성 배치(READY/PROGRESS)가 존재하는지 확인 ─────────────────
                // 존재하면: 실제 처리 중 → 중복 실행 차단
                // 없으면: 서버 재시작·치명적 오류 등으로 고착 상태 → 자동 FAIL 리셋 후 재작업 허용
                boolean hasActiveBatch = itemRepository.existsActiveBatchForReport(
                        report.getId(), "WRITE");
                if (hasActiveBatch) {
                    throw new IllegalArgumentException(
                            String.format("이미 성적서작성이 진행 중인 성적서가 포함되어 있습니다. (id: %d, 성적서번호: %s)",
                                    report.getId(), report.getReportNum()));
                }
                // 활성 배치 없음 → 고착 상태 자동 리셋
                log.warn("성적서 write_status=PROGRESS 이지만 활성 배치 없음 — FAIL로 자동 리셋 (reportId: {}, reportNum: {})",
                        report.getId(), report.getReportNum());
                report.setWriteStatus(AppStatus.FAIL);
            }
            // 실무자결재가 대기(READY) 또는 진행 중(PROGRESS)인 경우 성적서작성 차단
            // (결재 진행 중 원본 파일 교체 방지)
            if (report.getWorkStatus() == AppStatus.READY || report.getWorkStatus() == AppStatus.PROGRESS) {
                throw new IllegalArgumentException(
                        String.format("실무자결재가 진행 중인 성적서가 포함되어 있습니다. (id: %d, 성적서번호: %s)",
                                report.getId(), report.getReportNum()));
            }
            // 기술책임자결재가 대기(READY) 또는 진행 중(PROGRESS)인 경우 성적서작성 차단
            if (report.getApprovalStatus() == AppStatus.READY || report.getApprovalStatus() == AppStatus.PROGRESS) {
                throw new IllegalArgumentException(
                        String.format("기술책임자결재가 진행 중인 성적서가 포함되어 있습니다. (id: %d, 성적서번호: %s)",
                                report.getId(), report.getReportNum()));
            }
            validatedIds.add(report.getId());
        }

        // ── 2. ReportJobBatch 생성 ────────────────────────────────────────────
        ReportJobBatch batch = ReportJobBatch.builder()
                .jobType(JobType.WRITE)
                .requestMemberId(user.getId())
                .sampleId(req.getSampleId())
                .totalCount(validatedIds.size())
                .status(BatchStatus.READY)
                .createDatetime(LocalDateTime.now())
                .build();
        batch = batchRepository.save(batch);
        final Long batchId = batch.getId();

        // ── 3. ReportJobItem 생성 (성적서 1건 = item 1건, 초기 상태: READY) ──
        List<ReportJobItem> items = validatedIds.stream()
                .map(reportId -> ReportJobItem.builder()
                        .batchId(batchId)
                        .reportId(reportId)
                        .build()  // status 기본값: READY
                )
                .collect(Collectors.toList());
        itemRepository.saveAll(items);

        // ── 4. 대상 성적서 write_status → PROGRESS 업데이트 ──────────────────
        for (Report report : reports) {
            report.setWriteStatus(AppStatus.PROGRESS);
        }
        // dirty checking으로 flush → @Transactional 범위 내에서 자동 반영

        // ── 5. 작업 로그 기록 ──────────────────────────────────────────────────
        String logContent = String.format("성적서작성 배치 생성 (batchId: %d) — 대상 성적서 id - %s",
                batchId,
                validatedIds.toString());
        Log logEntry = Log.builder()
                .logIp(null)  // 컨트롤러에서 IP를 넘겨줘도 되지만 현재는 생략
                .workerName(user.getName())   // name: 직원 실명 (username은 로그인 id)
                .logContent(logContent)
                .logType("i")
                .refTable("report_job_batch")
                .refTableId(batchId)
                .createDatetime(LocalDateTime.now())
                .createMemberId(user.getId())
                .build();
        logRepository.save(logEntry);

        log.info("성적서작성 배치 생성 완료 — batchId: {}, 대상 건수: {}, 요청자: {}",
                batchId, validatedIds.size(), user.getName());

        // WRITE 타입은 workerSignImgKey 없음
        return new ReportJobBatchDTO.CreateBatchRes(batchId, validatedIds.size(), null);
    }

    // ── 배치 진행상황 조회 (Polling / 작업서버 배치 조회 공용) ─────────────────

    /**
     * 배치 진행상황 조회.
     * 브라우저 Polling(GET /api/report/jobs/batches/{id})과
     * 작업서버 배치+item 조회(GET /api/worker/batches/{id}) 모두에서 호출한다.
     *
     * @param batchId 조회할 배치 id
     * @return 배치 상태 + 소속 item 목록
     */
    @Transactional(readOnly = true)
    public ReportJobBatchDTO.BatchStatusRes getBatchStatus(Long batchId) {
        ReportJobBatch batch = batchRepository.findById(batchId)
                .orElseThrow(() -> new EntityNotFoundException("존재하지 않는 배치입니다. (id: " + batchId + ")"));

        List<ReportJobItem> items = itemRepository.findByBatchId(batchId);

        List<ReportJobBatchDTO.ItemStatusRes> itemResList = items.stream()
                .map(item -> new ReportJobBatchDTO.ItemStatusRes(
                        item.getId(),
                        item.getReportId(),
                        item.getStatus().name(),
                        item.getStep(),
                        item.getMessage(),
                        item.getStartDatetime(),
                        item.getEndDatetime()
                ))
                .toList();

        return new ReportJobBatchDTO.BatchStatusRes(
                batch.getId(),
                batch.getJobType().name(),
                batch.getStatus().name(),
                batch.getTotalCount(),
                batch.getSuccessCount(),
                batch.getFailCount(),
                batch.getSampleId(),
                batch.getCreateDatetime(),
                batch.getStartDatetime(),
                batch.getEndDatetime(),
                itemResList
        );
    }

    // ── 작업서버 콜백 처리 ────────────────────────────────────────────────────

    /**
     * 작업서버로부터 item 처리 결과를 수신하여 DB 상태를 업데이트한다.
     *
     * 처리 순서:
     *  1. item 조회
     *  2. 연관 batch 조회
     *  3. status 파싱 및 item 상태 업데이트
     *  4. PROGRESS: 배치 상태를 READY → PROGRESS로 전환 (최초 1회)
     *     SUCCESS:  report.write_status=SUCCESS, write_member_id, write_datetime 업데이트. batch.successCount++
     *     FAIL:     report.write_status=FAIL. batch.failCount++
     *  5. 모든 item 처리 완료 시 배치 최종 상태(SUCCESS/FAIL) 결정
     *
     * item 1건 = 독립 트랜잭션 → 1건 실패가 다른 item에 영향 없음.
     *
     * @param itemId 처리된 item id
     * @param req    작업서버가 전송한 콜백 요청 (status, step, message)
     */
    @Transactional
    public void handleItemCallback(Long itemId, ReportJobBatchDTO.ItemCallbackReq req) {
        // ── 1. item 조회 ─────────────────────────────────────────────────────
        ReportJobItem item = itemRepository.findById(itemId)
                .orElseThrow(() -> new EntityNotFoundException("존재하지 않는 item입니다. (id: " + itemId + ")"));

        // ── 2. 연관 배치 조회 ─────────────────────────────────────────────────
        ReportJobBatch batch = batchRepository.findById(item.getBatchId())
                .orElseThrow(() -> new EntityNotFoundException(
                        "연관 배치를 찾을 수 없습니다. (batchId: " + item.getBatchId() + ")"));

        // ── 3. status 파싱 ────────────────────────────────────────────────────
        JobItemStatus newStatus;
        try {
            newStatus = JobItemStatus.valueOf(req.getStatus());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("유효하지 않은 status 값입니다: " + req.getStatus()
                    + " (허용값: PROGRESS / SUCCESS / FAIL)");
        }

        // ── 4. item 상태 업데이트 ─────────────────────────────────────────────
        item.setStatus(newStatus);
        if (req.getStep()    != null) item.setStep(req.getStep());
        if (req.getMessage() != null) item.setMessage(req.getMessage());

        LocalDateTime now = LocalDateTime.now();

        switch (newStatus) {
            case PROGRESS -> {
                // 처음 PROGRESS 진입 시에만 시작일시 기록 (중간 단계 업데이트 시 덮어쓰지 않음)
                if (item.getStartDatetime() == null) {
                    item.setStartDatetime(now);
                }
                // 배치: READY → PROGRESS (첫 번째 item이 처리 시작될 때 1회만 전환)
                if (batch.getStatus() == BatchStatus.READY) {
                    batch.setStatus(BatchStatus.PROGRESS);
                    batch.setStartDatetime(now);
                }
            }
            case SUCCESS -> {
                item.setEndDatetime(now);
                Report report = reportRepository.findById(item.getReportId())
                        .orElseThrow(() -> new EntityNotFoundException(
                                "성적서를 찾을 수 없습니다. (id: " + item.getReportId() + ")"));
                batch.setSuccessCount(batch.getSuccessCount() + 1);

                if (batch.getJobType() == JobType.WORK_APPROVAL) {
                    // ── WORK_APPROVAL 성공 처리 ────────────────────────────────
                    // workMemberId 는 성적서에 원래 지정된 실무자 — 결재 처리로 변경하지 않음
                    report.setWorkStatus(AppStatus.SUCCESS);
                    report.setWorkDatetime(now);

                    // 기존 signed_xlsx / signed_pdf file_info 소프트삭제 (재결재 시 중복 방지)
                    // origin file_info는 보존 → softDeleteByRefAndNames 사용
                    fileInfoRepository.softDeleteByRefAndNames(
                            "report",
                            item.getReportId(),
                            List.of("signed_xlsx", "signed_pdf"),
                            YnType.n,
                            now,
                            batch.getRequestMemberId()
                    );

                    // signed.xlsx file_info 등록 (excelFileId 표시 조건)
                    String reportDir = "report/" + item.getReportId() + "/";
                    fileInfoRepository.save(FileInfo.builder()
                            .refTableName("report")
                            .refTableId(item.getReportId())
                            .originName("signed.xlsx")
                            .name("signed_xlsx")
                            .extension("xlsx")
                            .fileSize(0L)
                            .contentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
                            .dir(reportDir)
                            .createMemberId(batch.getRequestMemberId())
                            .createDatetime(now)
                            .build());

                    // signed.pdf file_info 등록 (pdfFileId 표시 조건)
                    fileInfoRepository.save(FileInfo.builder()
                            .refTableName("report")
                            .refTableId(item.getReportId())
                            .originName("signed.pdf")
                            .name("signed_pdf")
                            .extension("pdf")
                            .fileSize(0L)
                            .contentType("application/pdf")
                            .dir(reportDir)
                            .createMemberId(batch.getRequestMemberId())
                            .createDatetime(now)
                            .build());

                    log.debug("실무자결재 file_info 생성 완료 — reportId: {}", item.getReportId());

                } else {
                    // ── WRITE 성공 처리 ────────────────────────────────────────
                    report.setWriteStatus(AppStatus.SUCCESS);
                    report.setWriteMemberId(batch.getRequestMemberId());
                    report.setWriteDatetime(now);

                    // 기존 원본 file_info 소프트삭제 후 재등록 (재작성 시 중복 방지)
                    String reportDir = "report/" + item.getReportId() + "/";
                    fileInfoRepository.softDeleteVisibleByRefAndDir(
                            "report",
                            item.getReportId(),
                            reportDir,
                            YnType.n,
                            now,
                            batch.getRequestMemberId()
                    );
                    fileInfoRepository.save(FileInfo.builder()
                            .refTableName("report")
                            .refTableId(item.getReportId())
                            .originName("origin.xlsx")
                            .name("origin")
                            .extension("xlsx")
                            .fileSize(0L)
                            .contentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
                            .dir(reportDir)
                            .createMemberId(batch.getRequestMemberId())
                            .createDatetime(now)
                            .build());
                    log.debug("성적서 원본 file_info 생성 완료 — reportId: {}", item.getReportId());
                }
            }
            case FAIL -> {
                item.setEndDatetime(now);
                Report report = reportRepository.findById(item.getReportId())
                        .orElseThrow(() -> new EntityNotFoundException(
                                "성적서를 찾을 수 없습니다. (id: " + item.getReportId() + ")"));
                // jobType 에 따라 복원할 status 필드가 다름
                if (batch.getJobType() == JobType.WORK_APPROVAL) {
                    report.setWorkStatus(AppStatus.FAIL);
                } else {
                    report.setWriteStatus(AppStatus.FAIL);
                }
                batch.setFailCount(batch.getFailCount() + 1);
            }
        }

        // ── 5. 배치 완료 여부 체크 ───────────────────────────────────────────
        // SUCCESS 또는 FAIL 전환 시에만 완료 건수를 재계산한다
        if (newStatus == JobItemStatus.SUCCESS || newStatus == JobItemStatus.FAIL) {
            int doneCount = batch.getSuccessCount() + batch.getFailCount();
            if (doneCount >= batch.getTotalCount()) {
                // 모든 item 처리 완료 → 배치 최종 상태 결정
                batch.setEndDatetime(now);
                batch.setStatus(batch.getFailCount() > 0 ? BatchStatus.FAIL : BatchStatus.SUCCESS);
                log.info("배치 처리 완료 — batchId: {}, success: {}, fail: {}",
                        batch.getId(), batch.getSuccessCount(), batch.getFailCount());
            }
            // SUCCESS 케이스에서 softDeleteVisibleByRefAndDir(clearAutomatically=true)가 실행되면
            // EntityManager 캐시가 클리어되어 batch 가 detached 상태가 된다.
            // dirty-checking 으로 변경사항이 반영되지 않을 수 있으므로 명시적으로 save(merge) 호출.
            batchRepository.save(batch);
        }

        log.info("item 콜백 처리 완료 — itemId: {}, status: {}, step: {}, batchId: {}",
                itemId, newStatus, req.getStep(), batch.getId());
    }

    /**
     * 실무자결재 배치 생성 (WORK_APPROVAL 타입)
     *
     * 처리 순서:
     *  1. 대상 성적서 유효성 검증
     *     - 존재 여부, soft delete 여부, SELF 타입 여부, write_status=SUCCESS(원본 파일 존재)
     *     - work_status 가 PROGRESS 인 경우 중복 방지
     *     - approval_status 가 READY/PROGRESS 인 경우 차단
     *  2. 요청자(실무자)의 서명 이미지 file_info 조회 → objectKey 계산
     *  3. ReportJobBatch 생성 (status=READY, jobType=WORK_APPROVAL)
     *  4. 각 성적서에 대해 ReportJobItem 생성 (status=READY)
     *  5. 대상 성적서의 work_status → PROGRESS 업데이트
     *  6. 작업 로그 기록
     *  7. 배치 id + 총 건수 + 서명 이미지 objectKey 반환
     *
     * @param req  성적서 id 목록
     * @param user 요청자 (로그인 사용자 = 실무자)
     * @return 생성된 배치 id + 총 건수 + workerSignImgKey
     */
    @Transactional
    public ReportJobBatchDTO.CreateBatchRes createWorkApprovalBatch(
            ReportJobBatchDTO.CreateWorkApprovalBatchReq req,
            CustomUserDetails user
    ) {
        List<Long> reportIds = req.getReportIds();

        // ── 1. 대상 성적서 조회 및 유효성 검증 ────────────────────────────────
        List<Report> reports = reportRepository.findAllById(reportIds);

        if (reports.size() != reportIds.size()) {
            throw new EntityNotFoundException("존재하지 않는 성적서가 포함되어 있습니다.");
        }

        List<Long> validatedIds = new ArrayList<>();
        Long batchWorkMemberId = null; // 배치 내 모든 성적서가 공유하는 실무자 id
        for (Report report : reports) {
            if (report.getIsVisible() != YnType.y) {
                throw new IllegalArgumentException(
                        String.format("삭제된 성적서가 포함되어 있습니다. (id: %d)", report.getId()));
            }
            if (report.getReportType() != ReportType.SELF) {
                throw new IllegalArgumentException(
                        String.format("자체성적서(SELF)만 실무자결재가 가능합니다. (id: %d)", report.getId()));
            }
            // 원본 파일이 생성된 성적서만 결재 가능 (성적서작성 완료 여부 체크)
            if (report.getWriteStatus() != AppStatus.SUCCESS) {
                throw new IllegalArgumentException(
                        String.format("성적서작성이 완료되지 않은 성적서가 포함되어 있습니다. (id: %d, 성적서번호: %s)",
                                report.getId(), report.getReportNum()));
            }
            // 이미 실무자결재 진행 중인 경우 처리
            if (report.getWorkStatus() == AppStatus.PROGRESS) {
                // 실제 활성 배치(READY/PROGRESS)가 존재하는지 확인 ─────────────────
                boolean hasActiveBatch = itemRepository.existsActiveBatchForReport(
                        report.getId(), "WORK_APPROVAL");
                if (hasActiveBatch) {
                    throw new IllegalArgumentException(
                            String.format("이미 실무자결재가 진행 중인 성적서가 포함되어 있습니다. (id: %d, 성적서번호: %s)",
                                    report.getId(), report.getReportNum()));
                }
                // 활성 배치 없음 → 고착 상태 자동 리셋
                log.warn("성적서 work_status=PROGRESS 이지만 활성 배치 없음 — FAIL로 자동 리셋 (reportId: {}, reportNum: {})",
                        report.getId(), report.getReportNum());
                report.setWorkStatus(AppStatus.FAIL);
            }
            // 기술책임자결재가 진행 중인 경우 차단
            if (report.getApprovalStatus() == AppStatus.READY || report.getApprovalStatus() == AppStatus.PROGRESS) {
                throw new IllegalArgumentException(
                        String.format("기술책임자결재가 진행 중인 성적서가 포함되어 있습니다. (id: %d, 성적서번호: %s)",
                                report.getId(), report.getReportNum()));
            }
            // 실무자 id 검증: 배치 내 모든 성적서의 실무자가 동일해야 함 (서명 이미지 단일 공유)
            if (report.getWorkMemberId() == null) {
                throw new IllegalArgumentException(
                        String.format("실무자가 지정되지 않은 성적서가 포함되어 있습니다. (id: %d, 성적서번호: %s)",
                                report.getId(), report.getReportNum()));
            }
            if (batchWorkMemberId == null) {
                batchWorkMemberId = report.getWorkMemberId();
            } else if (!batchWorkMemberId.equals(report.getWorkMemberId())) {
                throw new IllegalArgumentException(
                        "배치 내 성적서의 실무자가 서로 다릅니다. 같은 실무자의 성적서만 한 번에 결재할 수 있습니다.");
            }
            validatedIds.add(report.getId());
        }

        // ── 2. 실무자 서명 이미지 objectKey 조회 ──────────────────────────────
        // 데모 모드(workerUrl 미설정)에서는 서명 이미지 조회를 생략한다.
        // 데모 프로세서가 서명 삽입 없이 샘플 파일을 그대로 업로드하므로 objectKey 불필요.
        final String signObjectKey;
        if (workerUrl == null || workerUrl.isBlank()) {
            // 데모 모드: 서명 이미지 조회 생략
            signObjectKey = null;
            log.info("[createWorkApprovalBatch] 데모 모드 — 서명 이미지 조회 생략. workMemberId={}", batchWorkMemberId);
        } else {
            // 실제 모드: 배치 내 성적서에 지정된 실무자(workMemberId) 기준으로 서명 이미지 조회
            // file_info: ref_table_name='member', ref_table_id=workMemberId, is_visible='y'
            // objectKey 규칙: {rootDir}/{dir}{fileId}.{extension}
            log.debug("[createWorkApprovalBatch] 서명 이미지 조회. workMemberId={}", batchWorkMemberId);
            List<FileInfo> signFiles = fileInfoRepository.findByRefTableNameAndRefTableIdAndIsVisible(
                    "member", batchWorkMemberId, YnType.y);
            if (signFiles.isEmpty()) {
                throw new IllegalArgumentException(
                        "실무자 서명 이미지가 등록되어 있지 않습니다. 해당 실무자의 서명 이미지를 먼저 등록해 주세요. (workMemberId=" + batchWorkMemberId + ")");
            }
            FileInfo signFile = signFiles.get(0);
            // objectKey 조합 (FileServiceImpl.downloadFile() 과 동일한 규칙)
            final String signDir = signFile.getDir();
            if (signDir.endsWith("/")) {
                signObjectKey = storageRootDir + "/" + signDir + signFile.getId() + "." + signFile.getExtension();
            } else {
                signObjectKey = storageRootDir + "/" + signDir + "/" + signFile.getId() + "." + signFile.getExtension();
            }
        }

        // ── 3. ReportJobBatch 생성 ────────────────────────────────────────────
        ReportJobBatch batch = ReportJobBatch.builder()
                .jobType(JobType.WORK_APPROVAL)
                .requestMemberId(user.getId())
                .totalCount(validatedIds.size())
                .status(BatchStatus.READY)
                .createDatetime(LocalDateTime.now())
                .build();
        batch = batchRepository.save(batch);
        final Long batchId = batch.getId();

        // ── 4. ReportJobItem 생성 ─────────────────────────────────────────────
        List<ReportJobItem> items = validatedIds.stream()
                .map(reportId -> ReportJobItem.builder()
                        .batchId(batchId)
                        .reportId(reportId)
                        .build()
                )
                .collect(Collectors.toList());
        itemRepository.saveAll(items);

        // ── 5. 대상 성적서 work_status → PROGRESS 업데이트 ───────────────────
        for (Report report : reports) {
            report.setWorkStatus(AppStatus.PROGRESS);
        }

        // ── 6. 작업 로그 기록 ──────────────────────────────────────────────────
        String logContent = String.format("실무자결재 배치 생성 (batchId: %d) — 대상 성적서 id - %s",
                batchId, validatedIds.toString());
        Log logEntry = Log.builder()
                .workerName(user.getName())
                .logContent(logContent)
                .logType("i")
                .refTable("report_job_batch")
                .refTableId(batchId)
                .createDatetime(LocalDateTime.now())
                .createMemberId(user.getId())
                .build();
        logRepository.save(logEntry);

        log.info("실무자결재 배치 생성 완료 — batchId: {}, 대상 건수: {}, 요청자: {}",
                batchId, validatedIds.size(), user.getName());

        return new ReportJobBatchDTO.CreateBatchRes(batchId, validatedIds.size(), signObjectKey);
    }

    // ── 실무자결재 사전 검증 ───────────────────────────────────────────────────

    /**
     * 실무자결재 사전 검증 (사이드이펙트 없음 — 순수 조회).
     *
     * 다중결재 버튼 클릭 시 배치 생성 전에 호출하여,
     * 통과/실패 결과를 프론트에서 사용자에게 먼저 보여주기 위해 사용한다.
     *
     * 검증 항목:
     *  - 성적서 존재 여부 (is_visible = 'y')
     *  - SELF 타입 여부
     *  - write_status = SUCCESS (원본 파일 존재 여부 — 다중결재는 원본이 이미 있어야 함)
     *  - work_status 가 READY/PROGRESS 이면 이미 진행 중
     *  - approval_status 가 READY/PROGRESS/SUCCESS 이면 기술책임자결재 진행/완료 상태
     *  - workMemberId 설정 여부
     *  - workMember 서명 이미지 존재 여부
     *
     * @param reportIds 검증할 성적서 id 목록
     * @return valid/invalid 분류 결과
     */
    @Transactional(readOnly = true)
    public ReportJobBatchDTO.ValidateRes validateWorkApproval(List<Long> reportIds) {
        List<Report> reports = reportRepository.findAllById(reportIds);

        // 요청 id → Report 매핑 (존재하지 않는 id 감지용)
        java.util.Map<Long, Report> reportMap = reports.stream()
                .collect(java.util.stream.Collectors.toMap(Report::getId, r -> r));

        // workMember 서명 이미지 일괄 조회 (N+1 방지)
        java.util.Set<Long> workMemberIds = reports.stream()
                .filter(r -> r.getWorkMemberId() != null)
                .map(Report::getWorkMemberId)
                .collect(java.util.stream.Collectors.toSet());
        java.util.Set<Long> memberIdsWithSign = fileInfoRepository
                .findByRefTableNameAndRefTableIdInAndIsVisible("member", workMemberIds,
                        com.bada.cali.common.enums.YnType.y)
                .stream()
                .map(com.bada.cali.entity.FileInfo::getRefTableId)
                .collect(java.util.stream.Collectors.toSet());

        List<ReportJobBatchDTO.ValidateItem>  valid   = new java.util.ArrayList<>();
        List<ReportJobBatchDTO.InvalidItem>   invalid = new java.util.ArrayList<>();

        for (Long id : reportIds) {
            Report report = reportMap.get(id);

            if (report == null || report.getIsVisible() != com.bada.cali.common.enums.YnType.y) {
                invalid.add(new ReportJobBatchDTO.InvalidItem(id, null, "존재하지 않는 성적서입니다."));
                continue;
            }

            final String num = report.getReportNum();

            if (report.getReportType() != com.bada.cali.common.enums.ReportType.SELF) {
                invalid.add(new ReportJobBatchDTO.InvalidItem(id, num, "자체성적서(SELF)만 결재 가능합니다."));
                continue;
            }
            if (report.getWriteStatus() != com.bada.cali.common.enums.AppStatus.SUCCESS) {
                invalid.add(new ReportJobBatchDTO.InvalidItem(id, num, "성적서작성이 완료되지 않은 성적서입니다."));
                continue;
            }
            if (report.getWorkStatus() == com.bada.cali.common.enums.AppStatus.READY
                    || report.getWorkStatus() == com.bada.cali.common.enums.AppStatus.PROGRESS) {
                // 실제 활성 배치가 있을 때만 차단 — 없으면 고착 상태로 간주하여 valid 통과
                // (createWorkApprovalJob 에서 자동 리셋 후 재배치)
                boolean hasActiveBatch = itemRepository.existsActiveBatchForReport(id, "WORK_APPROVAL");
                if (hasActiveBatch) {
                    invalid.add(new ReportJobBatchDTO.InvalidItem(id, num, "이미 실무자결재가 진행 중인 성적서입니다."));
                    continue;
                }
            }
            if (report.getApprovalStatus() == com.bada.cali.common.enums.AppStatus.READY
                    || report.getApprovalStatus() == com.bada.cali.common.enums.AppStatus.PROGRESS
                    || report.getApprovalStatus() == com.bada.cali.common.enums.AppStatus.SUCCESS) {
                invalid.add(new ReportJobBatchDTO.InvalidItem(id, num, "기술책임자결재가 진행 중이거나 완료된 성적서입니다."));
                continue;
            }
            if (report.getWorkMemberId() == null) {
                invalid.add(new ReportJobBatchDTO.InvalidItem(id, num, "실무자가 지정되지 않은 성적서입니다."));
                continue;
            }
            if (!memberIdsWithSign.contains(report.getWorkMemberId())) {
                invalid.add(new ReportJobBatchDTO.InvalidItem(id, num, "실무자 서명 이미지가 등록되어 있지 않습니다."));
                continue;
            }

            valid.add(new ReportJobBatchDTO.ValidateItem(id, num));
        }

        return new ReportJobBatchDTO.ValidateRes(valid, invalid);
    }

    // ── 작업서버 트리거 ────────────────────────────────────────────────────────

    /**
     * 작업서버 트리거 호출.
     *
     * createWriteBatch() / createWorkApprovalBatch() 트랜잭션이 커밋된 후
     * 컨트롤러에서 별도로 호출된다.
     * (같은 트랜잭션 내에서 호출하면 트리거 실패 시 배치 자체가 롤백되어 복구 불가)
     *
     * app.worker.url이 비어 있으면 개발 모드로 간주하고 트리거를 생략한다.
     * 트리거 실패 시: 배치 FAIL, item CANCELED, report 작업 status IDLE 복원 후 예외 throw.
     *
     * @param batchId          트리거할 배치 id (이미 DB에 커밋된 상태)
     * @param workerSignImgKey WORK_APPROVAL 전용: 실무자 서명 이미지 objectKey (WRITE 타입은 null)
     */
    @Transactional
    public void triggerWorkerServer(Long batchId, String workerSignImgKey) {
        // ── 데모 모드: app.worker.url 미설정 시 cali-worker 대신 데모 프로세서 실행 ─
        // 데모 프로세서는 etc/ 경로의 고정 샘플 파일을 스토리지에 업로드하여
        // 실제 처리된 것처럼 동작한다. 폴링 UI는 그대로 동작한다.
        // 실제 cali-worker 전환: app.worker.url=http://localhost:8060 설정
        if (workerUrl == null || workerUrl.isBlank()) {
            log.info("app.worker.url 미설정 — 데모 모드로 처리 (batchId: {})", batchId);
            demoProcessor.process(batchId);
            return;
        }

        ReportJobBatch batch = batchRepository.findById(batchId)
                .orElseThrow(() -> new EntityNotFoundException("배치를 찾을 수 없습니다. (id: " + batchId + ")"));

        // ── 트리거 페이로드 구성 ─────────────────────────────────────────────
        ReportJobBatchDTO.WorkerTriggerReq triggerReq = ReportJobBatchDTO.WorkerTriggerReq.builder()
                .batchId(batchId)
                .callbackBaseUrl(callbackBaseUrl)
                .workerApiKey(workerApiKey)
                .storageEndpoint(storageEndpoint)
                .storageBucketName(storageBucketName)
                .storageRootDir(storageRootDir)
                .storageAccessKey(storageAccessKey)
                .storageSecretKey(storageSecretKey)
                .workerSignImgKey(workerSignImgKey) // WORK_APPROVAL 전용 (WRITE는 null)
                .build();

        try {
            restClient.post()
                    .uri(workerUrl + "/api/jobs/execute")
                    .header("X-Worker-Api-Key", workerApiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(triggerReq)
                    .retrieve()
                    .toBodilessEntity();

            log.info("작업서버 트리거 성공 — batchId: {}, workerUrl: {}", batchId, workerUrl);

        } catch (Exception e) {
            log.error("작업서버 트리거 실패 — batchId: {}, workerUrl: {}", batchId, workerUrl, e);

            // ── 트리거 실패 복구 ──────────────────────────────────────────────
            batch.setStatus(BatchStatus.FAIL);

            List<ReportJobItem> items = itemRepository.findByBatchId(batchId);
            List<Long> reportIds = items.stream().map(ReportJobItem::getReportId).toList();

            for (ReportJobItem item : items) {
                item.setStatus(JobItemStatus.CANCELED);
                item.setMessage("작업서버 트리거 실패로 자동 취소됨");
            }
            List<Report> reports = reportRepository.findAllById(reportIds);
            for (Report report : reports) {
                // jobType 에 따라 복원할 status 필드가 다름
                if (batch.getJobType() == JobType.WORK_APPROVAL) {
                    report.setWorkStatus(AppStatus.IDLE);
                } else {
                    report.setWriteStatus(AppStatus.IDLE);
                }
            }

            throw new RuntimeException("작업서버에 연결하지 못했습니다. 잠시 후 다시 시도해 주세요. (batchId: " + batchId + ")");
        }
    }
}