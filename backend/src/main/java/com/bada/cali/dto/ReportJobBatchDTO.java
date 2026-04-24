package com.bada.cali.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 성적서 작업 배치(report_job_batch) 관련 DTO
 */
public class ReportJobBatchDTO {

    /**
     * 성적서작성 배치 생성 요청 DTO (WRITE 타입 전용)
     *
     * 프론트에서 reportWrite 모달 내 샘플 행 클릭 후
     * 확인 시 이 DTO를 POST /api/report/jobs/batches 로 전송한다.
     */
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "성적서작성 배치 생성 요청")
    public static class CreateWriteBatchReq {

        /**
         * 성적서작성 대상 성적서 id 목록 (1건 이상)
         * workApproval 페이지에서 체크박스로 선택한 행들의 id,
         * 또는 reportModify 모달에서 단건으로 호출 시 1개 원소 리스트
         */
        @NotEmpty(message = "대상 성적서를 1건 이상 선택해야 합니다.")
        @Schema(description = "대상 성적서 id 목록", example = "[1, 2, 3]")
        private List<Long> reportIds;

        /**
         * 선택한 샘플 id (sample.id)
         * 작업서버는 이 샘플 파일을 스토리지에서 다운로드하여 성적서를 생성한다.
         */
        @NotNull(message = "샘플을 선택해야 합니다.")
        @Schema(description = "선택한 샘플 id (sample.id)", example = "10")
        private Long sampleId;
    }

    /**
     * 실무자결재 배치 생성 요청 DTO (WORK_APPROVAL 타입 전용)
     *
     * workApproval 페이지에서 결재 아이콘 클릭 시(단건) 또는 다중 선택 후 결재 시
     * POST /api/report/jobs/batches/work-approval 로 전송한다.
     */
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "실무자결재 배치 생성 요청")
    public static class CreateWorkApprovalBatchReq {

        /**
         * 실무자결재 대상 성적서 id 목록 (1건 이상).
         * 단건 결재 아이콘 클릭 시 1개 원소 리스트, 다중 선택 시 N개.
         */
        @NotEmpty(message = "대상 성적서를 1건 이상 선택해야 합니다.")
        @Schema(description = "대상 성적서 id 목록", example = "[1, 2, 3]")
        private List<Long> reportIds;
    }

    /**
     * 성적서 작업 배치 생성 응답 DTO (WRITE / WORK_APPROVAL 공용)
     * 생성된 배치 id, 대상 건수, 그리고 작업서버 트리거에 사용되는 부가 정보를 반환한다.
     */
    @Getter
    @AllArgsConstructor
    @Schema(description = "성적서 작업 배치 생성 응답")
    public static class CreateBatchRes {

        @Schema(description = "생성된 배치 id", example = "123")
        private Long batchId;

        @Schema(description = "대상 성적서 건수", example = "3")
        private Integer totalCount;

        /**
         * WORK_APPROVAL 전용: 실무자 서명 이미지 스토리지 objectKey.
         * 트리거 요청에 포함하여 워커가 서명 이미지를 다운로드하는 데 사용.
         * WRITE 타입에서는 null.
         */
        @Schema(description = "실무자 서명 이미지 objectKey (WORK_APPROVAL 전용)", nullable = true)
        private String workerSignImgKey;
    }

    // ── Polling / 작업서버 배치 조회 응답 ────────────────────────────────────

    /**
     * 배치 진행상황 응답 DTO (브라우저 Polling 및 작업서버 조회 공용)
     *
     * GET /api/report/jobs/batches/{batchId}  — 브라우저 Polling
     * GET /api/worker/batches/{batchId}        — 작업서버 배치+item 조회
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

    // ── 작업서버 콜백 요청 ────────────────────────────────────────────────────

    /**
     * 작업서버 → CALI item 처리 결과 콜백 요청 DTO
     *
     * POST /api/worker/items/{itemId}/callback
     * 작업서버는 item 처리 시작(PROGRESS)·성공(SUCCESS)·실패(FAIL) 마다 이 DTO를 전송한다.
     */
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "작업서버의 item 처리 결과 콜백 요청")
    public static class ItemCallbackReq {

        @NotNull(message = "status는 필수입니다.")
        @Schema(description = "처리 상태 (PROGRESS / SUCCESS / FAIL)", example = "SUCCESS")
        private String status;

        @Schema(description = "현재 처리 단계 (예: FILLING_DATA, UPLOADING_ORIGIN)", example = "UPLOADING_ORIGIN")
        private String step;

        @Schema(description = "처리 결과 메시지 (실패 사유 등)", example = "샘플 파일을 찾을 수 없습니다.")
        private String message;
    }

    // ── CALI → 작업서버 트리거 요청 ──────────────────────────────────────────

    /**
     * CALI → 작업서버 트리거 요청 DTO
     *
     * POST {app.worker.url}/api/jobs/execute
     * 배치 생성 후 작업서버에 처리 시작을 요청할 때 전송하는 페이로드.
     * 스토리지 접속정보가 포함되므로 DB에 저장하지 않고 요청 시에만 사용한다.
     */
    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "CALI가 작업서버로 보내는 작업 트리거 요청")
    public static class WorkerTriggerReq {

        @Schema(description = "처리할 배치 id", example = "123")
        private Long batchId;

        @Schema(description = "작업 완료 후 콜백할 CALI 베이스 URL", example = "http://localhost:8050")
        private String callbackBaseUrl;

        @Schema(description = "CALI ↔ 작업서버 공용 API 인증 키")
        private String workerApiKey;

        @Schema(description = "스토리지 엔드포인트 URL")
        private String storageEndpoint;

        @Schema(description = "스토리지 버킷명")
        private String storageBucketName;

        @Schema(description = "스토리지 루트 디렉토리 (dev / prod)")
        private String storageRootDir;

        @Schema(description = "스토리지 액세스 키")
        private String storageAccessKey;

        @Schema(description = "스토리지 시크릿 키")
        private String storageSecretKey;

        /**
         * WORK_APPROVAL 전용: 실무자 서명 이미지 스토리지 objectKey.
         * 워커가 이 key로 스토리지에서 서명 이미지를 다운로드하여 엑셀에 삽입한다.
         * WRITE 타입에서는 null.
         */
        @Schema(description = "실무자 서명 이미지 objectKey (WORK_APPROVAL 전용)", nullable = true)
        private String workerSignImgKey;
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