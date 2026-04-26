package com.bada.cali.api;

import com.bada.cali.common.ResMessage;
import com.bada.cali.dto.ReportJobBatchDTO;
import com.bada.cali.security.CustomUserDetails;
import com.bada.cali.service.ReportUploadServiceImpl;

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
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Tag(name = "성적서 업로드", description = "성적서 원본 파일(origin) 업로드·검증 API")
@RestController("ApiReportUploadController")
@RequestMapping("/api/report/upload")
@Log4j2
@RequiredArgsConstructor
public class ReportUploadController {

    private final ReportUploadServiceImpl uploadService;

    /**
     * 성적서 업로드 사전 검증.
     *
     * 파일명(확장자 제거) 기반으로 성적서를 조회하여 업로드/결재 가능 여부를 반환한다.
     * 사이드이펙트 없음. 프론트엔드는 이 결과로 불가 건 안내 후 유효 건만 업로드 진행.
     */
    @Operation(
            summary = "성적서 업로드 사전 검증",
            description = "파일명(확장자 제거) = 성적서번호로 성적서를 조회하여 업로드/결재 가능 여부를 반환. " +
                    "mode=upload: 업로드 가능 여부만 확인. " +
                    "mode=approval: 업로드 후 실무자결재 가능 여부까지 확인. " +
                    "사이드이펙트 없음"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "검증 완료 (valid/invalid 목록 반환)"),
            @ApiResponse(responseCode = "400", description = "요청 파라미터 오류",
                    content = @Content(schema = @Schema(implementation = ResMessage.class))),
            @ApiResponse(responseCode = "500", description = "서버 오류",
                    content = @Content(schema = @Schema(implementation = ResMessage.class)))
    })
    @PostMapping("/validate")
    public ResponseEntity<ResMessage<ReportJobBatchDTO.ValidateRes>> validateUpload(
            @Valid @RequestBody ReportJobBatchDTO.ValidateUploadReq req
    ) {
        ReportJobBatchDTO.ValidateRes res = uploadService.validateUpload(req.getReportNums(), req.getMode());
        return ResponseEntity.ok(new ResMessage<>(1, "검증 완료", res));
    }

    /**
     * signed 파일(xlsx/pdf) 교체 업로드.
     *
     * 기술책임자결재 완료(approval_status=SUCCESS) 성적서에 대해 signed.xlsx 또는 signed.pdf를 교체한다.
     * 성적서 상태 변경 없음 (파일 교체만).
     */
    @Operation(
            summary = "signed 파일(xlsx/pdf) 교체 업로드",
            description = "기술책임자결재 완료(approval_status=SUCCESS) 성적서에 대해 " +
                    "signed.xlsx 또는 signed.pdf를 스토리지에 덮어쓰고 file_info를 교체. " +
                    "성적서 상태 변경 없음."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "업로드 성공"),
            @ApiResponse(responseCode = "400", description = "요청 파라미터 오류",
                    content = @Content(schema = @Schema(implementation = ResMessage.class))),
            @ApiResponse(responseCode = "404", description = "존재하지 않는 성적서 포함",
                    content = @Content(schema = @Schema(implementation = ResMessage.class))),
            @ApiResponse(responseCode = "500", description = "서버 오류 또는 스토리지 업로드 실패",
                    content = @Content(schema = @Schema(implementation = ResMessage.class)))
    })
    @PostMapping("/signed")
    public ResponseEntity<ResMessage<Void>> uploadSigned(
            @Parameter(description = "업로드할 xlsx/pdf 파일 목록 (파일명 = 성적서번호.xlsx 또는 성적서번호.pdf)")
            @RequestParam("files") List<MultipartFile> files,
            @Parameter(description = "validate API에서 반환된 valid 성적서 id 목록", example = "[1, 2, 3]")
            @RequestParam("reportIds") List<Long> reportIds,
            @AuthenticationPrincipal CustomUserDetails user
    ) {
        if (files == null || files.isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(new ResMessage<>(0, "업로드할 파일을 선택해야 합니다.", null));
        }
        uploadService.replaceSignedFiles(files, reportIds, user.getId());
        return ResponseEntity.ok(new ResMessage<>(1,
                String.format("파일 교체가 완료되었습니다. (%d건)", files.size()), null));
    }

    /**
     * 성적서 원본 파일(origin.xlsx) 교체 업로드.
     *
     * 파일명(확장자 제거) = 성적서번호로 대상 성적서를 특정하여 스토리지에 origin.xlsx를 덮어쓴다.
     * 기존 origin file_info를 소프트삭제하고 신규 file_info를 등록한다.
     * 업로드 성공 시 report.writeStatus = SUCCESS 로 변경된다.
     * reportIds 파라미터: validate 결과에서 유효(valid)한 성적서 id 목록 (검증 중복 방지 용도)
     */
    @Operation(
            summary = "성적서 원본 파일 교체 업로드",
            description = "multipart/form-data로 xlsx 파일 1건 이상을 받아 " +
                    "성적서 원본(origin.xlsx)을 스토리지에 덮어쓰고 file_info를 교체. " +
                    "업로드 성공 시 writeStatus = SUCCESS 로 변경. " +
                    "reportIds: validate API에서 반환된 valid 성적서 id 목록"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "업로드 성공"),
            @ApiResponse(responseCode = "400", description = "요청 파라미터 오류",
                    content = @Content(schema = @Schema(implementation = ResMessage.class))),
            @ApiResponse(responseCode = "404", description = "존재하지 않는 성적서 포함",
                    content = @Content(schema = @Schema(implementation = ResMessage.class))),
            @ApiResponse(responseCode = "500", description = "서버 오류 또는 스토리지 업로드 실패",
                    content = @Content(schema = @Schema(implementation = ResMessage.class)))
    })
    @PostMapping("/origin")
    public ResponseEntity<ResMessage<Void>> uploadOrigin(
            @Parameter(description = "업로드할 xlsx 파일 목록 (파일명 = 성적서번호.xlsx)")
            @RequestParam("files") List<MultipartFile> files,
            @Parameter(description = "validate API에서 반환된 valid 성적서 id 목록", example = "[1, 2, 3]")
            @RequestParam("reportIds") List<Long> reportIds,
            @AuthenticationPrincipal CustomUserDetails user
    ) {
        if (files == null || files.isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(new ResMessage<>(0, "업로드할 파일을 선택해야 합니다.", null));
        }
        uploadService.replaceOriginFiles(files, reportIds, user.getId());
        return ResponseEntity.ok(new ResMessage<>(1,
                String.format("원본 파일 업로드가 완료되었습니다. (%d건)", files.size()), null));
    }
}
