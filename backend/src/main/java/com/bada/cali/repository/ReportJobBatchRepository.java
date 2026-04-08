package com.bada.cali.repository;

import com.bada.cali.entity.ReportJobBatch;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ReportJobBatchRepository extends JpaRepository<ReportJobBatch, Long> {

    /** ExcelWork 미들웨어 token으로 배치 조회 */
    Optional<ReportJobBatch> findByToken(String token);
}