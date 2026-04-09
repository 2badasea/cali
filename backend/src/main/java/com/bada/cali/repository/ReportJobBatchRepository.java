package com.bada.cali.repository;

import com.bada.cali.entity.ReportJobBatch;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ReportJobBatchRepository extends JpaRepository<ReportJobBatch, Long> {

    /** ExcelWork 미들웨어 token으로 배치 조회 */
    Optional<ReportJobBatch> findByToken(String token);

    /**
     * 주어진 성적서 id 목록 중 하나라도 포함하면서 READY 또는 PROGRESS 상태인 배치 목록 조회.
     * 비정상 종료 복구(reset) 시 성적서 id 기반으로 활성 배치를 찾는 데 사용한다.
     *
     * @param reportIds 성적서 id 목록
     * @return READY/PROGRESS 상태이고 해당 성적서를 포함하는 배치 목록 (중복 제거)
     */
    @Query("""
            SELECT DISTINCT b FROM ReportJobBatch b
            JOIN ReportJobItem i ON i.batchId = b.id
            WHERE i.reportId IN :reportIds
              AND b.status IN ('READY', 'PROGRESS')
            """)
    List<ReportJobBatch> findActiveByReportIds(@Param("reportIds") List<Long> reportIds);
}