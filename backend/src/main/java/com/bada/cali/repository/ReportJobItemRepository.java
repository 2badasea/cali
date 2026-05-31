package com.bada.cali.repository;

import com.bada.cali.common.enums.JobType;
import com.bada.cali.entity.ReportJobItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ReportJobItemRepository extends JpaRepository<ReportJobItem, Long> {

    /** 배치 id로 소속 item 전체 조회 */
    List<ReportJobItem> findByBatchId(Long batchId);

    /** fileUuid로 item 단건 조회 (미들웨어 파일 다운로드 식별에 사용) */
    Optional<ReportJobItem> findByFileUuid(String fileUuid);

    /**
     * 특정 성적서에 대해 현재 READY 또는 PROGRESS 상태인 배치(활성 배치)가 존재하는지 확인.
     *
     * write_status / work_status = PROGRESS 이지만 실제로 처리 중인 배치가 있는지 판별하는 데 사용한다.
     * 활성 배치가 없다면 고착(stuck) 상태로 간주하여 자동 FAIL 리셋 후 재작업을 허용한다.
     *
     * @param reportId 대상 성적서 id
     * @param jobType  작업 유형 (JobType enum)
     * @return 활성 배치가 1건 이상 존재하면 true
     */
    @Query("""
        SELECT COUNT(i) > 0
          FROM ReportJobItem i
          JOIN ReportJobBatch b ON i.batchId = b.id
         WHERE i.reportId = :reportId
           AND b.jobType  = :jobType
           AND b.status  IN ('READY', 'PROGRESS')
    """)
    boolean existsActiveBatchForReport(
            @Param("reportId") Long reportId,
            @Param("jobType")  JobType jobType
    );
}