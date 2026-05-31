package com.bada.cali.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 성적서 작업 배치(report_job_batch) 관련 DTO
 */
public class ReportJobBatchDTO {

    // ── Polling 응답 ────────────────────────────────────────────────────────

    /**
     * 배치 진행상황 응답 DTO (브라우저 Polling용)
     *
     * GET /api/report/jobs/batches/{batchId}  — 브라우저 Polling
     */
    @Getter
    @AllArgsConstructor
    @Schema(description = "배치 진행상황 응답")
    public static class BatchStatusRes {

        @Schema(description = "배치 id", example = "123")
        private Long batchId;

        @Schema(description = "작업 타입 (WRITE / WORK_APPROVAL / MANAGER_APPROVAL)")
        private String jobType;

        @Schema(description = "배치 전체 상태 (READY / PROGRESS / SUCCESS / FAIL / CANCELED)")
        private String status;

        @Schema(description = "총 처리 대상 건수", example = "5")
        private Integer totalCount;

        @Schema(description = "성공 건수", example = "3")
        private Integer successCount;

        @Schema(description = "실패 건수", example = "1")
        private Integer failCount;

        @Schema(description = "선택된 샘플 id (WRITE 타입 전용)", example = "10")
        private Long sampleId;

        @Schema(description = "배치 생성일시")
        private LocalDateTime createDatetime;

        @Schema(description = "처리 시작일시 (첫 item PROGRESS 진입 시 기록)")
        private LocalDateTime startDatetime;

        @Schema(description = "처리 완료일시 (마지막 item 종료 후 기록)")
        private LocalDateTime endDatetime;

        @Schema(description = "개별 item 상태 목록")
        private List<ItemStatusRes> items;
    }

    /**
     * 배치 내 개별 item 상태 DTO (BatchStatusRes에 포함)
     */
    @Getter
    @AllArgsConstructor
    @Schema(description = "개별 item 상태")
    public static class ItemStatusRes {

        @Schema(description = "item id", example = "456")
        private Long itemId;

        @Schema(description = "성적서 id (report.id)", example = "78")
        private Long reportId;

        @Schema(description = "item 상태 (READY / PROGRESS / SUCCESS / FAIL / CANCELED)")
        private String status;

        @Schema(description = "현재 처리 단계 (예: DOWNLOADING_TEMPLATE, FILLING_DATA)", example = "UPLOADING_ORIGIN")
        private String step;

        @Schema(description = "처리 결과 메시지 (실패 시 사유)", example = "샘플 파일을 찾을 수 없습니다.")
        private String message;

        @Schema(description = "처리 시작일시")
        private LocalDateTime startDatetime;

        @Schema(description = "처리 완료일시")
        private LocalDateTime endDatetime;
    }

    // ── 성적서 업로드 검증 요청 ──────────────────────────────────────────────

    /**
     * 성적서 업로드(원본 교체) 사전 검증 요청 DTO
     *
     * POST /api/report/upload/validate
     * 파일명(확장자 제거) 기반으로 성적서를 조회하여 업로드/결재 가능 여부를 반환한다.
     */
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "성적서 업로드 사전 검증 요청")
    public static class ValidateUploadReq {

        @NotEmpty(message = "파일명을 1건 이상 전달해야 합니다.")
        @Schema(description = "업로드할 파일의 파일명 목록 (확장자 제거)", example = "[\"BD26-0004-0001\", \"BD26-0004-0002\"]")
        private List<String> reportNums;

        /**
         * 검증 모드
         * - "upload"   : 업로드만 허용 여부 검증
         * - "approval" : 업로드 후 결재 가능 여부도 함께 검증
         */
        @NotNull(message = "mode는 필수입니다.")
        @Schema(description = "검증 모드 (upload / approval)", example = "approval")
        private String mode;
    }

    // ── 실무자결재 사전 검증 ──────────────────────────────────────────────────

    /**
     * 실무자결재 사전 검증 요청 DTO
     *
     * POST /api/report/jobs/validateWorkApproval
     * 배치 생성 전 대상 성적서들의 결재 가능 여부를 순수하게 조회한다(사이드이펙트 없음).
     */
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "실무자결재 사전 검증 요청")
    public static class ValidateWorkApprovalReq {

        @NotEmpty(message = "대상 성적서를 1건 이상 선택해야 합니다.")
        @Schema(description = "검증할 성적서 id 목록", example = "[1, 2, 3]")
        private List<Long> reportIds;
    }

    /**
     * 검증 통과/실패 결과 응답 DTO
     *
     * validateWorkApproval / ReportUploadService.validateUpload 에서 공통으로 사용.
     */
    @Getter
    @AllArgsConstructor
    @Schema(description = "결재/업로드 검증 결과 응답")
    public static class ValidateRes {

        @Schema(description = "결재/업로드 가능한 성적서 목록")
        private List<ValidateItem> valid;

        @Schema(description = "결재/업로드 불가 성적서 목록 (이유 포함)")
        private List<InvalidItem> invalid;
    }

    @Getter
    @AllArgsConstructor
    @Schema(description = "검증 통과 성적서 정보")
    public static class ValidateItem {

        @Schema(description = "성적서 id", example = "1")
        private Long id;

        @Schema(description = "성적서번호", example = "BD26-0004-0001")
        private String reportNum;
    }

    @Getter
    @AllArgsConstructor
    @Schema(description = "검증 실패 성적서 정보")
    public static class InvalidItem {

        @Schema(description = "성적서 id (파일명 기반 조회 실패 시 null)", nullable = true)
        private Long id;

        @Schema(description = "성적서번호 또는 파일명", example = "BD26-0004-0001")
        private String reportNum;

        @Schema(description = "실패 사유", example = "기술책임자결재가 완료된 성적서입니다.")
        private String reason;
    }
}