package com.bada.cali.api;

import com.bada.cali.common.ResMessage;
import com.bada.cali.dto.ExcelWorkDTO;
import com.bada.cali.security.CustomUserDetails;
import com.bada.cali.service.ExcelWorkServiceImpl;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/**
 * ExcelWork 미들웨어 연동 API.
 *
 * 경로: /api/excelwork/**
 *
 * 인증 구분:
 *   브라우저 호출 (createJob, reset, getBatches): Spring Security 세션 인증
 *   미들웨어 콜백 (/callback/**): 세션 없음, X-Callback-Key 헤더로 컨트롤러 레벨 검증
 *   미들웨어 잡 조회 (/job/**, /file/**): 세션 없음, token 으로 식별
 *
 * SecurityConfig 에서 /api/excelwork/callback/**, /api/excelwork/job/**, /api/excelwork/file/**
 * 를 permitAll 로 설정해야 한다.
 */
@Tag(name = "ExcelWork 미들웨어 연동", description = "성적서작성/결재 ExcelWork 미들웨어 연동 API")
@RestController("ApiExcelWorkController")
@RequestMapping("/api/excelwork")
@Log4j2
@RequiredArgsConstructor
public class ExcelWorkController {

    private final ExcelWorkServiceImpl excelWorkService;

    // ─────────────────────────────────────────────────────────────────────────
    // 브라우저 → 서버 (세션 인증 필요)
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * 성적서작성 작업 요청 — createJob.
     *
     * 브라우저에서 성적서 N건을 선택하고 샘플을 지정한 뒤 호출.
     * 응답의 excelworkUri 를 window.location.href 로 실행하면 미들웨어가 기동된다.
     */
    @Operation(
            summary = "성적서작성 작업 요청 (ExcelWork)",
            description = "성적서 id 목록과 샘플 id를 받아 배치를 생성하고 미들웨어 실행 URI를 반환. " +
                    "응답의 excelworkUri(excelwork://...)를 브라우저에서 실행하면 미들웨어가 기동됨"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "배치 생성 성공"),
            @ApiResponse(responseCode = "400", description = "요청 파라미터 오류 또는 유효하지 않은 성적서",
                    content = @Content(schema = @Schema(implementation = ResMessage.class))),
            @ApiResponse(responseCode = "404", description = "존재하지 않는 성적서 또는 샘플",
                    content = @Content(schema = @Schema(implementation = ResMessage.class))),
            @ApiResponse(responseCode = "500", description = "서버 오류",
                    content = @Content(schema = @Schema(implementation = ResMessage.class)))
    })
    @PostMapping("/batches")
    public ResponseEntity<ResMessage<ExcelWorkDTO.CreateJobRes>> createJob(
            @Valid @RequestBody ExcelWorkDTO.CreateJobReq req,
            @AuthenticationPrincipal CustomUserDetails user
    ) {
        ExcelWorkDTO.CreateJobRes res = excelWorkService.createJob(req, user.getId());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new ResMessage<>(1, String.format(
                        "성적서작성 작업이 준비되었습니다. (%d건)", res.totalCount()), res));
    }

    /**
     * 실무자결재 작업 요청 — createWorkApprovalJob.
     *
     * 브라우저에서 결재 대상 성적서를 선택하고 호출.
     * 응답의 excelworkUri를 window.location.href로 실행하면
     * ExcelWorkApp이 기동되어 서명 삽입 + PDF 변환을 처리한다.
     *
     * 배치 내 모든 성적서는 동일한 workMember이어야 한다.
     * (서명 이미지는 성적서의 workMember 기준으로 조회됨)
     */
    @Operation(
            summary = "실무자결재 작업 요청 (ExcelWork)",
            description = "결재 대상 성적서 id 목록을 받아 WORK_APPROVAL 배치를 생성하고 미들웨어 실행 URI를 반환. " +
                    "서명 이미지는 로그인 사용자가 아닌 각 성적서의 workMember 기준으로 item별 삽입됨. " +
                    "응답의 excelworkUri(excelwork://...)를 브라우저에서 실행하면 ExcelWorkApp이 서명 삽입 + PDF 변환을 처리함"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "배치 생성 성공"),
            @ApiResponse(responseCode = "400", description = "요청 파라미터 오류, 유효하지 않은 성적서, 또는 실무자 서명 이미지 없음",
                    content = @Content(schema = @Schema(implementation = ResMessage.class))),
            @ApiResponse(responseCode = "404", description = "존재하지 않는 성적서",
                    content = @Content(schema = @Schema(implementation = ResMessage.class))),
            @ApiResponse(responseCode = "500", description = "서버 오류",
                    content = @Content(schema = @Schema(implementation = ResMessage.class)))
    })
    @PostMapping("/batches/work-approval")
    public ResponseEntity<ResMessage<ExcelWorkDTO.CreateJobRes>> createWorkApprovalJob(
            @Valid @RequestBody ExcelWorkDTO.CreateWorkApprovalJobReq req,
            @AuthenticationPrincipal CustomUserDetails user
    ) {
        ExcelWorkDTO.CreateJobRes res = excelWorkService.createWorkApprovalJob(req, user.getId());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new ResMessage<>(1, String.format(
                        "실무자결재 작업이 준비되었습니다. (%d건)", res.totalCount()), res));
    }

    /**
     * 비정상 종료 복구 — 고착 배치 초기화.
     *
     * READY/PROGRESS 상태로 고착된 배치를 CANCELED 로 전환하고
     * 연관 성적서의 writeStatus 를 IDLE 로 원복한다.
     * 이미 SUCCESS 인 item 의 성적서는 유지.
     */
    @Operation(
            summary = "ExcelWork 배치 상태 초기화 (비정상 종료 복구)",
            description = "성적서 id 목록 기준으로 READY/PROGRESS 활성 배치를 CANCELED 로 전환하고 성적서 writeStatus 를 IDLE 로 복구. " +
                    "SUCCESS 인 item 은 유지됨"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "초기화 성공"),
            @ApiResponse(responseCode = "400", description = "요청 파라미터 오류",
                    content = @Content(schema = @Schema(implementation = ResMessage.class))),
            @ApiResponse(responseCode = "404", description = "존재하지 않는 배치 id 포함",
                    content = @Content(schema = @Schema(implementation = ResMessage.class))),
            @ApiResponse(responseCode = "500", description = "서버 오류",
                    content = @Content(schema = @Schema(implementation = ResMessage.class)))
    })
    @PatchMapping("/reset")
    public ResponseEntity<ResMessage<Void>> resetBatches(
            @Valid @RequestBody ExcelWorkDTO.ResetReq req,
            @AuthenticationPrincipal CustomUserDetails user
    ) {
        excelWorkService.resetBatches(req.getReportIds(), user.getId());
        return ResponseEntity.ok(new ResMessage<>(1, "배치 상태 초기화 완료", null));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 미들웨어 → 서버 (세션 없음, token/X-Callback-Key 인증)
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * 잡 상세 조회 — 미들웨어가 작업 시작 전 호출.
     *
     * token 으로 배치·성적서 목록·파일 중계 URL 을 반환한다.
     * SecurityConfig 에서 permitAll 경로.
     */
    @Operation(
            summary = "잡 상세 조회 (미들웨어용)",
            description = "token 으로 배치와 성적서 목록, 파일 중계 URL 을 반환. " +
                    "미들웨어가 작업 시작 전 호출하여 처리할 파일 경로를 확인함. 세션 인증 불필요"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "404", description = "유효하지 않은 token",
                    content = @Content(schema = @Schema(implementation = ResMessage.class))),
            @ApiResponse(responseCode = "500", description = "서버 오류",
                    content = @Content(schema = @Schema(implementation = ResMessage.class)))
    })
    @GetMapping("/job/{token}")
    public ResponseEntity<ResMessage<ExcelWorkDTO.JobDetailRes>> getJob(
            @Parameter(description = "잡 토큰", example = "a1b2c3d4...") @PathVariable String token,
            @Parameter(description = "파일 중계 URL 생성에 사용할 서버 베이스 URL (선택)",
                    example = "https://mycali.com")
            @RequestParam(required = false) String serverUrl
    ) {
        ExcelWorkDTO.JobDetailRes res = excelWorkService.getJobByToken(token, serverUrl);
        return ResponseEntity.ok(new ResMessage<>(1, "잡 조회 성공", res));
    }

    /**
     * 파일 다운로드 (fileUuid 기반 — 미들웨어 on-demand 다운로드 주 방식).
     *
     * 미들웨어는 잡 JSON의 items[].fileUuid 를 사용하여 item 처리 시점에 파일을 다운로드한다.
     * fileUuid → report_job_item 조회 → 배치 샘플 파일 스트리밍.
     * 세션 인증 불필요 (SecurityConfig permitAll 경로).
     */
    @Operation(
            summary = "파일 다운로드 — fileUuid 기반 (미들웨어 on-demand)",
            description = "잡 JSON의 fileUuid 로 파일을 다운로드. " +
                    "미들웨어가 item 처리 시점에 1건씩 호출. 세션 인증 불필요"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "파일 스트리밍 성공"),
            @ApiResponse(responseCode = "404", description = "유효하지 않은 fileUuid 또는 파일 없음",
                    content = @Content(schema = @Schema(implementation = ResMessage.class))),
            @ApiResponse(responseCode = "500", description = "서버 오류",
                    content = @Content(schema = @Schema(implementation = ResMessage.class)))
    })
    @GetMapping("/file/{fileUuid}")
    public ResponseEntity<Resource> streamFileByUuid(
            @Parameter(description = "item 파일 식별 UUID (잡 JSON의 items[].fileUuid)")
            @PathVariable String fileUuid
    ) {
        return excelWorkService.streamFileByUuid(fileUuid);
    }

    /**
     * 파일 중계 다운로드 (token + fileType 기반 — 하위 호환용).
     *
     * fileUuid 기반 다운로드가 주 방식이며, 이 엔드포인트는 하위 호환 목적으로 유지.
     * fileType 허용값: sample
     */
    @Operation(
            summary = "파일 중계 다운로드 — token 기반 (하위 호환용)",
            description = "token + fileType 으로 파일을 다운로드. fileType: sample(샘플 엑셀). " +
                    "신규 미들웨어는 fileUuid 기반 엔드포인트를 사용. 세션 인증 불필요"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "파일 스트리밍 성공"),
            @ApiResponse(responseCode = "400", description = "유효하지 않은 fileType",
                    content = @Content(schema = @Schema(implementation = ResMessage.class))),
            @ApiResponse(responseCode = "404", description = "token 또는 파일 없음",
                    content = @Content(schema = @Schema(implementation = ResMessage.class))),
            @ApiResponse(responseCode = "500", description = "서버 오류",
                    content = @Content(schema = @Schema(implementation = ResMessage.class)))
    })
    @GetMapping("/file/{token}/{fileType}")
    public ResponseEntity<Resource> streamFile(
            @Parameter(description = "잡 토큰") @PathVariable String token,
            @Parameter(description = "파일 종류 (sample)", example = "sample") @PathVariable String fileType
    ) {
        return excelWorkService.streamFile(token, fileType);
    }

    /**
     * READY 콜백 — 미들웨어가 파일 다운로드 완료 후 작업 시작 직전에 호출.
     *
     * batch/item status: READY → PROGRESS, 성적서 writeStatus → PROGRESS.
     * 인증: X-Callback-Key 헤더.
     */
    @Operation(
            summary = "READY 콜백 (미들웨어→서버)",
            description = "미들웨어가 파일 다운로드 완료 후 작업 시작 직전에 호출. " +
                    "배치·item·성적서 상태를 PROGRESS 로 전환. 세션 인증 불필요, X-Callback-Key 인증"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "상태 전환 성공"),
            @ApiResponse(responseCode = "403", description = "X-Callback-Key 불일치",
                    content = @Content(schema = @Schema(implementation = ResMessage.class))),
            @ApiResponse(responseCode = "404", description = "유효하지 않은 token",
                    content = @Content(schema = @Schema(implementation = ResMessage.class))),
            @ApiResponse(responseCode = "500", description = "서버 오류",
                    content = @Content(schema = @Schema(implementation = ResMessage.class)))
    })
    @PostMapping("/callback/ready")
    public ResponseEntity<ResMessage<Void>> callbackReady(
            @Valid @RequestBody ExcelWorkDTO.ReadyCallbackReq req,
            @RequestHeader(value = "X-Callback-Key", defaultValue = "") String callbackKey
    ) {
        if (!excelWorkService.isValidCallbackKey(callbackKey)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(new ResMessage<>(-1, "유효하지 않은 콜백 키입니다.", null));
        }
        excelWorkService.callbackReady(req.token());
        return ResponseEntity.ok(new ResMessage<>(1, "READY 처리 완료", null));
    }

    /**
     * item-done 콜백 — 미들웨어가 성적서 1건 처리 완료 후 완성 파일과 함께 호출.
     *
     * 완성 파일을 스토리지에 업로드하고 item/Report 상태를 SUCCESS 로 갱신.
     * 인증: X-Callback-Key 헤더.
     */
    @Operation(
            summary = "item 완료 콜백 (미들웨어→서버, multipart)",
            description = "미들웨어가 성적서 1건 처리 완료 후 완성 파일(xlsx)과 함께 호출. " +
                    "파일을 스토리지에 업로드하고 item/성적서 상태를 SUCCESS 로 갱신. " +
                    "세션 인증 불필요, X-Callback-Key 인증"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "파일 업로드 및 상태 갱신 성공"),
            @ApiResponse(responseCode = "400", description = "파일 누락 또는 파라미터 오류",
                    content = @Content(schema = @Schema(implementation = ResMessage.class))),
            @ApiResponse(responseCode = "403", description = "X-Callback-Key 불일치",
                    content = @Content(schema = @Schema(implementation = ResMessage.class))),
            @ApiResponse(responseCode = "404", description = "유효하지 않은 token 또는 itemId",
                    content = @Content(schema = @Schema(implementation = ResMessage.class))),
            @ApiResponse(responseCode = "500", description = "서버 오류",
                    content = @Content(schema = @Schema(implementation = ResMessage.class)))
    })
    @PostMapping("/callback/item-done")
    public ResponseEntity<ResMessage<Void>> callbackItemDone(
            @RequestParam String token,
            @RequestParam Long itemId,
            @RequestPart MultipartFile file,
            @RequestHeader(value = "X-Callback-Key", defaultValue = "") String callbackKey
    ) {
        if (!excelWorkService.isValidCallbackKey(callbackKey)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(new ResMessage<>(-1, "유효하지 않은 콜백 키입니다.", null));
        }
        excelWorkService.callbackItemDone(token, itemId, file);
        return ResponseEntity.ok(new ResMessage<>(1, "item 완료 처리 성공", null));
    }

    /**
     * 실무자결재 item-done 콜백 — signed.xlsx + signed.pdf 두 파일을 함께 수신.
     *
     * 두 파일을 스토리지에 업로드하고 file_info를 교체한 뒤
     * item/Report 상태를 SUCCESS 로 갱신.
     * 인증: X-Callback-Key 헤더.
     */
    @Operation(
            summary = "실무자결재 item 완료 콜백 (미들웨어→서버, multipart)",
            description = "미들웨어가 실무자결재 1건 완료 후 signed.xlsx + signed.pdf 와 함께 호출. " +
                    "두 파일을 스토리지에 업로드하고 file_info를 교체한 뒤 workStatus=SUCCESS 로 갱신. " +
                    "세션 인증 불필요, X-Callback-Key 인증"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "파일 업로드 및 상태 갱신 성공"),
            @ApiResponse(responseCode = "400", description = "파일 누락 또는 파라미터 오류",
                    content = @Content(schema = @Schema(implementation = ResMessage.class))),
            @ApiResponse(responseCode = "403", description = "X-Callback-Key 불일치",
                    content = @Content(schema = @Schema(implementation = ResMessage.class))),
            @ApiResponse(responseCode = "404", description = "유효하지 않은 token 또는 itemId",
                    content = @Content(schema = @Schema(implementation = ResMessage.class))),
            @ApiResponse(responseCode = "500", description = "서버 오류",
                    content = @Content(schema = @Schema(implementation = ResMessage.class)))
    })
    @PostMapping("/callback/approval-item-done")
    public ResponseEntity<ResMessage<Void>> callbackApprovalItemDone(
            @RequestParam String token,
            @RequestParam Long itemId,
            @RequestPart MultipartFile xlsxFile,
            @RequestPart MultipartFile pdfFile,
            @RequestHeader(value = "X-Callback-Key", defaultValue = "") String callbackKey
    ) {
        if (!excelWorkService.isValidCallbackKey(callbackKey)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(new ResMessage<>(-1, "유효하지 않은 콜백 키입니다.", null));
        }
        excelWorkService.callbackApprovalItemDone(token, itemId, xlsxFile, pdfFile);
        return ResponseEntity.ok(new ResMessage<>(1, "실무자결재 item 완료 처리 성공", null));
    }

    /**
     * 서명 이미지 스트리밍 — 미들웨어 WORK_APPROVAL 처리 시 서명 이미지 다운로드.
     *
     * token으로 배치를 조회하여 requestMember의 서명 이미지를 스토리지에서 스트리밍한다.
     * SecurityConfig: /api/excelwork/sign-image/** permitAll.
     * 인증: X-Callback-Key 헤더.
     */
    @Operation(
            summary = "서명 이미지 스트리밍 (미들웨어용)",
            description = "token으로 배치를 조회하여 실무자 서명 이미지를 스토리지에서 스트리밍. " +
                    "미들웨어가 WORK_APPROVAL 처리 시 서명 이미지를 다운로드할 때 사용. " +
                    "세션 인증 불필요, X-Callback-Key 인증"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "이미지 스트리밍 성공"),
            @ApiResponse(responseCode = "403", description = "X-Callback-Key 불일치",
                    content = @Content(schema = @Schema(implementation = ResMessage.class))),
            @ApiResponse(responseCode = "404", description = "token 또는 서명 이미지 없음",
                    content = @Content(schema = @Schema(implementation = ResMessage.class))),
            @ApiResponse(responseCode = "500", description = "서버 오류",
                    content = @Content(schema = @Schema(implementation = ResMessage.class)))
    })
    @GetMapping("/sign-image/{token}")
    public ResponseEntity<Resource> streamSignImage(
            @Parameter(description = "잡 토큰") @PathVariable String token,
            @RequestHeader(value = "X-Callback-Key", defaultValue = "") String callbackKey
    ) {
        if (!excelWorkService.isValidCallbackKey(callbackKey)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        return excelWorkService.streamSignImage(token);
    }

    /**
     * 서명 이미지 스트리밍 — workMemberId 기반 (per-item).
     *
     * WORK_APPROVAL 처리 시 item별로 서명 이미지를 다운로드할 때 사용.
     * 성적서별 실무자가 다를 수 있으므로 token 대신 workMemberId를 직접 받아 조회한다.
     * SecurityConfig /api/excelwork/sign-image/** permitAll 적용.
     * 인증: X-Callback-Key 헤더.
     */
    @Operation(
            summary = "서명 이미지 스트리밍 — workMemberId 기반 (미들웨어 per-item 처리용)",
            description = "workMemberId로 해당 실무자의 서명 이미지를 스토리지에서 스트리밍. " +
                    "WORK_APPROVAL 처리 시 item별 서명 이미지를 다운로드할 때 사용. " +
                    "세션 인증 불필요, X-Callback-Key 인증"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "이미지 스트리밍 성공"),
            @ApiResponse(responseCode = "403", description = "X-Callback-Key 불일치"),
            @ApiResponse(responseCode = "404", description = "해당 workMember의 서명 이미지 없음",
                    content = @Content(schema = @Schema(implementation = ResMessage.class))),
            @ApiResponse(responseCode = "500", description = "서버 오류",
                    content = @Content(schema = @Schema(implementation = ResMessage.class)))
    })
    @GetMapping("/sign-image/member/{workMemberId}")
    public ResponseEntity<Resource> streamSignImageByMember(
            @Parameter(description = "실무자 member id") @PathVariable Long workMemberId,
            @RequestHeader(value = "X-Callback-Key", defaultValue = "") String callbackKey
    ) {
        if (!excelWorkService.isValidCallbackKey(callbackKey)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        return excelWorkService.streamSignImageByWorkMemberId(workMemberId);
    }

    /**
     * done 콜백 — 미들웨어가 전체 작업 완료 후 호출.
     *
     * 배치 최종 상태(SUCCESS/FAIL) 를 확정하고 endDatetime 을 기록.
     * item-done 콜백에서 이미 finalize 됐을 수 있으나 명시적 완료 알림으로 재처리.
     * 인증: X-Callback-Key 헤더.
     */
    @Operation(
            summary = "전체 완료 콜백 (미들웨어→서버)",
            description = "미들웨어가 모든 item 처리 완료 후 호출. " +
                    "배치 최종 상태를 SUCCESS/FAIL 로 확정. 세션 인증 불필요, X-Callback-Key 인증"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "완료 처리 성공"),
            @ApiResponse(responseCode = "403", description = "X-Callback-Key 불일치",
                    content = @Content(schema = @Schema(implementation = ResMessage.class))),
            @ApiResponse(responseCode = "404", description = "유효하지 않은 token",
                    content = @Content(schema = @Schema(implementation = ResMessage.class))),
            @ApiResponse(responseCode = "500", description = "서버 오류",
                    content = @Content(schema = @Schema(implementation = ResMessage.class)))
    })
    @PostMapping("/callback/done")
    public ResponseEntity<ResMessage<Void>> callbackDone(
            @Valid @RequestBody ExcelWorkDTO.DoneCallbackReq req,
            @RequestHeader(value = "X-Callback-Key", defaultValue = "") String callbackKey
    ) {
        if (!excelWorkService.isValidCallbackKey(callbackKey)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(new ResMessage<>(-1, "유효하지 않은 콜백 키입니다.", null));
        }
        excelWorkService.callbackDone(req.token());
        return ResponseEntity.ok(new ResMessage<>(1, "전체 완료 처리 성공", null));
    }
}