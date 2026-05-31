package com.bada.cali.api;

import com.bada.cali.common.ResMessage;
import com.bada.cali.dto.ReportJobBatchDTO;
import com.bada.cali.service.ReportJobBatchServiceImpl;

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
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "성적서 작업 배치", description = "성적서작성/결재 작업 배치 조회·검증 API")
@RestController("ApiReportJobBatchController")
@RequestMapping("/api/report/jobs")
@Log4j2
@RequiredArgsConstructor
public class ReportJobBatchController {

    private final ReportJobBatchServiceImpl batchService;

    /**
     * 실무자결재 사전 검증 (사이드이펙트 없음)
     *
     * 배치 생성 전 대상 성적서들의 결재 가능 여부를 순수하게 조회한다.
     * 프론트엔드는 이 결과를 받아 불가 건을 사용자에게 안내 후 유효 건만 배치 생성한다.
     */
    @Operation(
            summary = "실무자결재 사전 검증",
            description = "배치 생성 전 대상 성적서들의 결재 가능 여부를 조회. 사이드이펙트 없음. " +
                    "valid(결재 가능), invalid(불가 및 사유) 목록을 반환"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "검증 완료 (valid/invalid 목록 반환)"),
            @ApiResponse(responseCode = "400", description = "요청 파라미터 오류",
                    content = @Content(schema = @Schema(implementation = ResMessage.class))),
            @ApiResponse(responseCode = "500", description = "서버 오류",
                    content = @Content(schema = @Schema(implementation = ResMessage.class)))
    })
    @PostMapping("/validateWorkApproval")
    public ResponseEntity<ResMessage<ReportJobBatchDTO.ValidateRes>> validateWorkApproval(
            @Valid @RequestBody ReportJobBatchDTO.ValidateWorkApprovalReq req
    ) {
        ReportJobBatchDTO.ValidateRes res = batchService.validateWorkApproval(req.getReportIds());
        return ResponseEntity.ok(new ResMessage<>(1, "검증 완료", res));
    }

    /**
     * 배치 진행상황 조회 (브라우저 Polling용)
     *
     * 성적서작성 모달 또는 작업 현황 UI에서 일정 주기로 호출한다.
     * 배치 전체 상태 + 소속 item 목록(status, step, message)을 반환한다.
     *
     * 인증된 사용자만 접근 가능 (Security 기본 설정).
     */
    @Operation(
            summary = "배치 진행상황 조회 (Polling)",
            description = "브라우저에서 일정 주기로 호출하여 배치와 개별 item의 진행상황을 조회. " +
                    "인증된 사용자만 접근 가능"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "404", description = "존재하지 않는 배치 id",
                    content = @Content(schema = @Schema(implementation = ResMessage.class))),
            @ApiResponse(responseCode = "500", description = "서버 오류",
                    content = @Content(schema = @Schema(implementation = ResMessage.class)))
    })
    @GetMapping("/batches/{batchId}")
    public ResponseEntity<ResMessage<ReportJobBatchDTO.BatchStatusRes>> getBatchStatus(
            @Parameter(description = "배치 id", example = "123") @PathVariable Long batchId
    ) {
        ReportJobBatchDTO.BatchStatusRes res = batchService.getBatchStatus(batchId);
        return ResponseEntity.ok(new ResMessage<>(1, "배치 상태 조회 성공", res));
    }
}