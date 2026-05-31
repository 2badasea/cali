package com.bada.cali.service;

import com.bada.cali.common.enums.JobType;
import com.bada.cali.dto.ReportJobBatchDTO;
import com.bada.cali.entity.Report;
import com.bada.cali.entity.ReportJobBatch;
import com.bada.cali.entity.ReportJobItem;
import com.bada.cali.repository.FileInfoRepository;
import com.bada.cali.repository.ReportJobBatchRepository;
import com.bada.cali.repository.ReportJobItemRepository;
import com.bada.cali.repository.ReportRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@Log4j2
@RequiredArgsConstructor
public class ReportJobBatchServiceImpl {

    private final ReportRepository            reportRepository;
    private final ReportJobBatchRepository    batchRepository;
    private final ReportJobItemRepository     itemRepository;
    private final FileInfoRepository          fileInfoRepository;

    // ── 배치 진행상황 조회 (Polling) ─────────────────────────────────────────

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
                boolean hasActiveBatch = itemRepository.existsActiveBatchForReport(id, JobType.WORK_APPROVAL);
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

}