package com.bada.cali.entity;

import com.bada.cali.common.enums.BatchStatus;
import com.bada.cali.common.enums.JobType;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * 성적서 작업 배치 엔티티 (report_job_batch)
 *
 * 사용자의 버튼 클릭 1번 = 배치 1건.
 * 배치 안에 N개의 ReportJobItem이 포함되며, item 단위로 독립 처리된다.
 *
 * job_type 으로 성적서작성/실무자결재/기술책임자결재를 구분한다.
 * WRITE 타입일 때만 sample_id를 사용하며, 나머지 타입은 NULL.
 */
@Entity
@Table(name = "report_job_batch")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReportJobBatch {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 작업 유형 (WRITE / WORK_APPROVAL / MANAGER_APPROVAL) */
    @Column(name = "job_type", nullable = false, length = 30)
    @Enumerated(EnumType.STRING)
    private JobType jobType;

    /** 작업 요청자 member.id */
    @Column(name = "request_member_id", nullable = false)
    private Long requestMemberId;

    /**
     * WRITE 타입에만 사용. 선택한 샘플 sample.id.
     * WORK_APPROVAL / MANAGER_APPROVAL 타입은 NULL.
     */
    @Column(name = "sample_id")
    private Long sampleId;

    /** 전체 처리 대상 건수 (item 수) */
    @Column(name = "total_count", nullable = false)
    @Builder.Default
    private Integer totalCount = 0;

    /** 성공 건수 (작업서버가 item 완료 시마다 증가) */
    @Column(name = "success_count", nullable = false)
    @Builder.Default
    private Integer successCount = 0;

    /** 실패 건수 (작업서버가 item 실패 시마다 증가) */
    @Column(name = "fail_count", nullable = false)
    @Builder.Default
    private Integer failCount = 0;

    /**
     * 배치 전체 상태
     * READY → PROGRESS → SUCCESS / FAIL
     * 취소 흐름: CANCEL_REQUESTED → CANCELED
     */
    @Column(name = "status", nullable = false, length = 30)
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private BatchStatus status = BatchStatus.READY;

    /**
     * ExcelWork 미들웨어 인증 토큰 (UUID).
     * createJob 시 발급되며 미들웨어가 잡 조회·콜백 요청 시 식별자로 사용.
     * cali-worker 방식 배치는 NULL.
     */
    @Column(name = "token", length = 64, unique = true)
    private String token;

    /** 배치 생성일시 */
    @Column(name = "create_datetime", nullable = false)
    private LocalDateTime createDatetime;

    /** 작업서버에서 처리를 시작한 시각 (첫 item PROGRESS 전환 시 갱신) */
    @Column(name = "start_datetime")
    private LocalDateTime startDatetime;

    /** 배치 전체 처리 완료 시각 (마지막 item 완료 후 갱신) */
    @Column(name = "end_datetime")
    private LocalDateTime endDatetime;
}