package com.bada.cali.entity;

import com.bada.cali.common.enums.JobItemStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * 성적서 작업 개별 항목 엔티티 (report_job_item)
 *
 * 배치(ReportJobBatch) 내 성적서 1건 = item 1건.
 * item 단위로 독립 트랜잭션 처리 → 1건 실패가 전체 롤백으로 이어지지 않음.
 *
 * step: 작업서버가 처리 중 현재 단계를 갱신하여 UI에서 진행상황 표시 가능.
 * 단계 예시 (WRITE):  DOWNLOADING_TEMPLATE → FILLING_DATA → UPLOADING_ORIGIN → DONE
 */
@Entity
@Table(
        name = "report_job_item",
        indexes = {
                @Index(name = "idx_batch_id",  columnList = "batch_id"),
                @Index(name = "idx_report_id", columnList = "report_id")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReportJobItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 소속 배치 id (report_job_batch.id) */
    @Column(name = "batch_id", nullable = false)
    private Long batchId;

    /** 처리 대상 성적서 id (report.id) */
    @Column(name = "report_id", nullable = false)
    private Long reportId;

    /**
     * ExcelWork 미들웨어 파일 다운로드 식별 UUID.
     * WRITE 배치 생성(createJob) 시 item별로 UUID가 부여된다.
     * 미들웨어는 이 UUID로 GET /api/excelwork/file/{fileUuid} 를 호출해 파일을 다운로드한다.
     * cali-worker 방식 배치는 NULL.
     */
    @Column(name = "file_uuid", length = 36, unique = true)
    private String fileUuid;

    /**
     * 개별 처리 상태
     * READY → PROGRESS → SUCCESS / FAIL
     * 취소 시: CANCELED
     */
    @Column(name = "status", nullable = false, length = 30)
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private JobItemStatus status = JobItemStatus.READY;

    /**
     * 현재 처리 단계 상세명.
     * 작업서버가 진행 중 직접 업데이트하여 UI에서 실시간 표시 가능.
     * (예: DOWNLOADING_TEMPLATE, FILLING_DATA, UPLOADING_ORIGIN 등)
     */
    @Column(name = "step", length = 50)
    private String step;

    /** 실패 사유 또는 처리 결과 메시지 (실패 시 작업서버가 기록) */
    @Column(name = "message", columnDefinition = "TEXT")
    private String message;

    /** 재시도 횟수 (실패 후 수동 재시도 시 증가) */
    @Column(name = "retry_count", nullable = false)
    @Builder.Default
    private Integer retryCount = 0;

    /** 이 item 처리 시작 시각 */
    @Column(name = "start_datetime")
    private LocalDateTime startDatetime;

    /** 이 item 처리 완료 시각 (SUCCESS / FAIL 전환 시 갱신) */
    @Column(name = "end_datetime")
    private LocalDateTime endDatetime;
}