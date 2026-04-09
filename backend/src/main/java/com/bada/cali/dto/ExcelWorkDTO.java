package com.bada.cali.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.Map;

/**
 * ExcelWork 미들웨어 연동 DTO.
 *
 * 흐름 요약:
 *   브라우저 → POST /api/excelwork/batches (CreateJobReq)
 *   → 미들웨어 기동 (excelwork://process?token=...&serverUrl=...)
 *   → 미들웨어 → GET /api/excelwork/job/{token} (JobDetailRes — 모든 셀 데이터 포함)
 *   → 미들웨어 → POST /api/excelwork/callback/ready
 *   → 미들웨어 → GET /api/excelwork/file/{fileUuid}  (item별 on-demand 파일 다운로드)
 *   → 미들웨어 → POST /api/excelwork/callback/item-done (multipart: token + itemId + file)
 *   → 미들웨어 → POST /api/excelwork/callback/done
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
     *
     * 미들웨어가 처리에 필요한 모든 정보를 1회 조회로 전달받는다.
     *   - sheetSettings: 엑셀 셀 주소·형식 설정 (fieldCode → {cell, format})
     *   - items: 성적서별 파일 UUID + 셀 삽입 데이터 (fieldCode → 값)
     *
     * 미들웨어 처리 흐름:
     *   1. callbackReady 호출
     *   2. 각 item 순서대로:
     *      a. GET /api/excelwork/file/{fileUuid} → 샘플 파일 다운로드
     *      b. 엑셀 열기 → sheetSettings + data 로 셀 삽입
     *      c. POST /api/excelwork/callback/item-done → 완성 파일 업로드
     *   3. callbackDone 호출
     */
    public record JobDetailRes(
            @Schema(description = "잡 토큰") String token,
            @Schema(description = "배치 id") Long batchId,
            @Schema(description = "작업 유형 (report_write 등)") String action,
            @Schema(description = "CALI 서버 URL (콜백 전송 대상)") String serverUrl,
            @Schema(description = "엑셀 셀 주소·형식 설정 맵 (fieldCode → {cell, format})") Map<String, SheetFieldSetting> sheetSettings,
            @Schema(description = "처리 대상 성적서 목록") List<ItemDetail> items
    ) {}

    /**
     * 셀 위치·형식 설정 (sheetSettings 맵의 값).
     * env.sheet_info_setting JSON 의 개별 항목과 동일한 구조.
     *
     * format 값 의미:
     *   null              → 값 그대로 삽입
     *   "dense"           → 이름 공백 제거 (이바다)
     *   "first_indent"    → 성(첫 글자) 뒤에만 공백 (이 바다)
     *   "indent"          → 모든 글자 사이 공백 (이 바 다)
     *   "yyyy. m. d" 등   → 날짜 형식 변환
     */
    public record SheetFieldSetting(
            @Schema(description = "엑셀 셀 주소 (예: B5). null이면 미사용") String cell,
            @Schema(description = "값 형식 (null/dense/indent/first_indent/날짜포맷)") String format
    ) {}

    /**
     * 성적서별 item 상세.
     * 미들웨어는 fileUuid로 파일을 다운로드하고, data 맵의 값을 sheetSettings 셀 주소에 삽입한다.
     *
     * data 값 규칙:
     *   - 문자열 필드: 값 그대로 (null 가능 → 미들웨어가 빈 문자열로 처리)
     *   - 날짜(LocalDate): ISO 8601 문자열 "yyyy-MM-dd" (미들웨어가 format에 따라 변환)
     *   - 정수(itemCaliCycle): 숫자 문자열 (예: "12")
     */
    public record ItemDetail(
            @Schema(description = "report_job_item.id") Long itemId,
            @Schema(description = "성적서 id") Long reportId,
            @Schema(description = "성적서번호 (없으면 null)") String reportNum,
            @Schema(description = "파일 다운로드 식별 UUID") String fileUuid,
            @Schema(description = "파일 다운로드 URL (GET /api/excelwork/file/{fileUuid})") String fileDownloadUrl,
            @Schema(description = "셀 삽입 데이터 (fieldCode → 값 문자열)") Map<String, String> data
    ) {}

    // ── 미들웨어 → 서버 콜백 요청 ────────────────────────────────────────────

    /** READY 콜백: 미들웨어가 잡을 수신하고 처리 시작 직전에 호출 */
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

    @Getter
    @Setter
    @Schema(description = "비정상 종료 복구 — 선택한 성적서의 활성 배치를 CANCELED 로 전환하고 성적서 상태를 IDLE 로 원복")
    public static class ResetReq {

        @NotEmpty
        @Schema(description = "복구 대상 성적서 id 목록", example = "[1, 2, 3]")
        private List<Long> reportIds;
    }
}