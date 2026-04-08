package com.bada.cali.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * ExcelWork 미들웨어 연동 DTO.
 *
 * 흐름 요약:
 *   브라우저 → POST /api/excelwork/batches (CreateJobReq)
 *   → 미들웨어 기동 (excelwork:// URI)
 *   → 미들웨어 → GET /api/excelwork/job/{token} (JobDetailRes)
 *   → 미들웨어 → GET /api/excelwork/file/{token}/sample 등 (파일 스트리밍)
 *   → 미들웨어 → POST /api/excelwork/callback/ready (ReadyCallbackReq)
 *   → 미들웨어 → POST /api/excelwork/callback/item-done (multipart: token + itemId + file)
 *   → 미들웨어 → POST /api/excelwork/callback/done (DoneCallbackReq)
 */
public class ExcelWorkDTO {

    // ── createJob 요청 (브라우저 → 서버) ─────────────────────────────────────

    @Getter
    @Setter
    @Schema(description = "성적서작성 작업 요청")
    public static class CreateJobReq {

        @NotEmpty
        @Schema(description = "처리 대상 성적서 id 목록", example = "[1, 2, 3]")
        private List<Long> reportIds;

        @NotNull
        @Schema(description = "샘플 sample.id (WRITE 타입 전용)", example = "5")
        private Long sampleId;

        /**
         * 미들웨어가 콜백을 보낼 서버 URL.
         * null이면 서버의 app.cali.callback-base-url 설정값 사용.
         */
        @Schema(description = "미들웨어가 콜백을 보낼 CALI 서버 URL (null 허용, 서버 설정값 폴백)",
                example = "https://mycali.com")
        private String serverUrl;
    }

    /** createJob 응답 — 브라우저가 excelworkUri를 URI 스킴으로 실행 */
    public record CreateJobRes(
            @Schema(description = "생성된 배치 id") Long batchId,
            @Schema(description = "미들웨어 인증 토큰 (UUID)") String token,
            @Schema(description = "미들웨어 실행 URI (excelwork://process?token=...&serverUrl=...)") String excelworkUri,
            @Schema(description = "총 처리 건수") int totalCount
    ) {}

    // ── 미들웨어용 잡 조회 응답 ───────────────────────────────────────────────

    /**
     * GET /api/excelwork/job/{token} 응답.
     * 미들웨어가 처리할 성적서 목록과 파일 중계 URL을 포함한다.
     */
    public record JobDetailRes(
            @Schema(description = "잡 토큰") String token,
            @Schema(description = "작업 유형 (WRITE / WORK_APPROVAL / MANAGER_APPROVAL)") String jobType,
            @Schema(description = "샘플 id (WRITE 전용, 그 외 null)") Long sampleId,
            @Schema(description = "처리 대상 성적서 목록") List<ItemDetail> items
    ) {}

    /**
     * 성적서별 item 상세 (미들웨어 잡 조회 응답의 일부).
     * fileUrl 은 서버 중계 경로이며, 미들웨어는 이 URL 로 파일을 다운로드한다.
     */
    public record ItemDetail(
            @Schema(description = "report_job_item.id") Long itemId,
            @Schema(description = "성적서 id") Long reportId,
            @Schema(description = "성적서번호 (없으면 null)") String reportNum,
            @Schema(description = "샘플 파일 중계 URL (WRITE 전용, 미들웨어 다운로드용)") String sampleFileUrl,
            @Schema(description = "원본 파일 중계 URL (결재 전용, 미들웨어 다운로드용)") String originFileUrl
    ) {}

    // ── 미들웨어 → 서버 콜백 요청 ────────────────────────────────────────────

    /** READY 콜백: 미들웨어가 파일 다운로드를 완료하고 작업 시작 직전에 호출 */
    public record ReadyCallbackReq(
            @NotBlank
            @Schema(description = "잡 토큰") String token
    ) {}

    /**
     * item-done 콜백은 multipart/form-data 로 수신.
     * @RequestParam String token
     * @RequestParam Long itemId
     * @RequestPart MultipartFile file
     */

    /** done 콜백: 모든 item 처리 완료 후 호출 */
    public record DoneCallbackReq(
            @NotBlank
            @Schema(description = "잡 토큰") String token
    ) {}

    // ── 상태 초기화 요청 (비정상 종료 복구) ───────────────────────────────────

    /**
     * PATCH /api/excelwork/reset 요청.
     * READY/PROGRESS 상태로 고착된 배치를 CANCELED 로 전환하고
     * 연관 성적서의 writeStatus 를 IDLE 로 복구한다.
     */
    @Getter
    @Setter
    @Schema(description = "비정상 종료 복구 — 선택한 배치를 CANCELED 로 전환하고 성적서 상태를 IDLE 로 원복")
    public static class ResetReq {

        @NotEmpty
        @Schema(description = "초기화할 배치 id 목록", example = "[10, 11]")
        private List<Long> batchIds;
    }
}
