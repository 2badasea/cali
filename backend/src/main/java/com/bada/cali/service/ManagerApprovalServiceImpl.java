package com.bada.cali.service;

import com.bada.cali.common.enums.AppStatus;
import com.bada.cali.common.enums.BatchStatus;
import com.bada.cali.common.enums.ReportStatus;
import com.bada.cali.common.enums.YnType;
import com.bada.cali.dto.ManagerApprovalDTO;
import com.bada.cali.dto.TuiGridDTO;
import com.bada.cali.entity.Log;
import com.bada.cali.entity.Report;
import com.bada.cali.entity.ReportJobBatch;
import com.bada.cali.repository.FileInfoRepository;
import com.bada.cali.repository.LogRepository;
import com.bada.cali.repository.ReportJobBatchRepository;
import com.bada.cali.repository.ReportRepository;
import com.bada.cali.repository.projection.ManagerApprovalListRow;
import com.bada.cali.security.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Log4j2
@RequiredArgsConstructor
public class ManagerApprovalServiceImpl {

    private final ReportRepository reportRepository;
    private final FileInfoRepository fileInfoRepository;
    private final ReportJobBatchRepository reportJobBatchRepository;
    private final LogRepository logRepository;

    /**
     * 기술책임자결재 목록 조회
     * - work_status = 'SUCCESS' 고정 필터
     * - approvalStatus 미전달 시 SUCCESS 제외 전체 (대기 목록 기본)
     */
    @Transactional(readOnly = true)
    public TuiGridDTO.ResData<ManagerApprovalListRow> getList(ManagerApprovalDTO.ListReq req) {
        int page = Math.max(req.getPage() - 1, 0);
        int perPage = req.getPerPage() > 0 ? req.getPerPage() : 25;
        Pageable pageable = PageRequest.of(page, perPage);

        // approvalStatus: 빈값 → null (SUCCESS 제외 전체)
        String approvalStatus = req.getApprovalStatus();
        if (approvalStatus == null || approvalStatus.isBlank()) approvalStatus = null;

        // 중/소분류: 0 → null
        Long middleItemCodeId = req.getMiddleItemCodeId();
        if (middleItemCodeId != null && middleItemCodeId == 0L) middleItemCodeId = null;
        Long smallItemCodeId = req.getSmallItemCodeId();
        if (smallItemCodeId != null && smallItemCodeId == 0L) smallItemCodeId = null;

        // 날짜 타입
        String dateType = (req.getDateType() == null || req.getDateType().isBlank()) ? "cali" : req.getDateType();
        String startDate = (req.getStartDate() == null || req.getStartDate().isBlank()) ? null : req.getStartDate();
        String endDate = (req.getEndDate() == null || req.getEndDate().isBlank()) ? null : req.getEndDate();

        // 검색타입: 빈값 → 'all'
        String searchType = req.getSearchType();
        if (searchType == null || searchType.isBlank()) searchType = "all";

        String keyword = req.getKeyword();
        keyword = (keyword == null) ? "" : keyword.trim();

        List<ManagerApprovalListRow> items = reportRepository.searchManagerApprovalList(
                approvalStatus, middleItemCodeId, smallItemCodeId,
                dateType, startDate, endDate,
                searchType, keyword, pageable
        );

        long totalCount = reportRepository.countManagerApprovalList(
                approvalStatus, middleItemCodeId, smallItemCodeId,
                dateType, startDate, endDate,
                searchType, keyword
        );

        TuiGridDTO.Pagination pagination = TuiGridDTO.Pagination.builder()
                .page(req.getPage())
                .totalCount((int) totalCount)
                .build();

        return TuiGridDTO.ResData.<ManagerApprovalListRow>builder()
                .contents(items)
                .pagination(pagination)
                .build();
    }

    /**
     * 성적서 반려 처리
     *
     * 검증: approval_status IN ('READY', 'PROGRESS', 'SUCCESS') 인 성적서는 반려 불가
     * 처리:
     *   - report_status = 'REJECTED', status_remark = rejectReason
     *   - work_status, work_datetime, approval_status, approval_datetime 초기화
     *   - file_info (type='signed_xlsx' OR 'signed_pdf') → is_visible = 'n'
     *   - report_job_batch 중 PROGRESS/READY 배치 → CANCELED 처리
     */
    @Transactional
    public String rejectReports(ManagerApprovalDTO.RejectReq req, CustomUserDetails user) {
        LocalDateTime now = LocalDateTime.now();
        Long userId = user.getId();

        List<Report> reports = reportRepository.findAllById(req.getReportIds());

        // 반려 불가 케이스 필터
        List<String> blocked = reports.stream()
                .filter(r -> {
                    AppStatus as = r.getApprovalStatus();
                    return as == AppStatus.READY || as == AppStatus.PROGRESS || as == AppStatus.SUCCESS;
                })
                .map(r -> r.getReportNum() + " (결재상태: " + r.getApprovalStatus().name() + ")")
                .collect(Collectors.toList());

        if (!blocked.isEmpty()) {
            return "반려 불가 성적서가 포함되어 있습니다: " + String.join(", ", blocked);
        }

        List<Long> targetIds = reports.stream().map(Report::getId).collect(Collectors.toList());

        for (Report r : reports) {
            r.setReportStatus(ReportStatus.REJECTED);
            r.setStatusRemark(req.getRejectReason());
            r.setWorkStatus(AppStatus.IDLE);
            r.setWorkDatetime(null);
            r.setApprovalStatus(AppStatus.IDLE);
            r.setApprovalDatetime(null);
            r.setUpdateMemberId(userId);
        }

        // signed_xlsx, signed_pdf 파일 소프트삭제 (origin 파일은 보존)
        fileInfoRepository.softDeleteByRefTableIdsAndNames(
                "report", targetIds,
                List.of("signed_xlsx", "signed_pdf"),
                YnType.n, now, userId
        );

        // 활성 배치(READY/PROGRESS) → CANCELED
        List<ReportJobBatch> activeBatches = reportJobBatchRepository.findActiveByReportIds(targetIds);
        for (ReportJobBatch batch : activeBatches) {
            batch.setStatus(BatchStatus.CANCELED);
            batch.setEndDatetime(now);
        }

        // 로그
        List<Long> ids = targetIds;
        Log rejectLog = Log.builder()
                .logType("u")
                .createMemberId(userId)
                .createDatetime(now)
                .workerName(user.getName())
                .refTableId(ids.get(0))
                .refTable("report")
                .logContent(String.format("[기술책임자결재 반려] 고유번호 - %s / 반려사유: %s",
                        ids.toString(), req.getRejectReason()))
                .build();
        logRepository.save(rejectLog);

        return null; // null = 성공
    }

    /**
     * 결재 취소 처리
     *
     * 대상: approval_status = 'SUCCESS' 인 성적서만
     * 처리: approval_status → IDLE, approval_datetime → NULL (approval_member_id 유지)
     */
    @Transactional
    public String cancelApproval(ManagerApprovalDTO.CancelApprovalReq req, CustomUserDetails user) {
        LocalDateTime now = LocalDateTime.now();
        Long userId = user.getId();

        List<Report> reports = reportRepository.findAllById(req.getReportIds());

        // SUCCESS 아닌 성적서 필터링
        List<Report> targets = reports.stream()
                .filter(r -> r.getApprovalStatus() == AppStatus.SUCCESS)
                .collect(Collectors.toList());

        if (targets.isEmpty()) {
            return "취소 가능한 성적서(결재완료 상태)가 없습니다.";
        }

        for (Report r : targets) {
            r.setApprovalStatus(AppStatus.IDLE);
            r.setApprovalDatetime(null);
            r.setUpdateMemberId(userId);
        }

        List<Long> targetIds = targets.stream().map(Report::getId).collect(Collectors.toList());

        Log cancelLog = Log.builder()
                .logType("u")
                .createMemberId(userId)
                .createDatetime(now)
                .workerName(user.getName())
                .refTableId(targetIds.get(0))
                .refTable("report")
                .logContent(String.format("[기술책임자결재 취소] 고유번호 - %s", targetIds.toString()))
                .build();
        logRepository.save(cancelLog);

        return null; // null = 성공
    }
}