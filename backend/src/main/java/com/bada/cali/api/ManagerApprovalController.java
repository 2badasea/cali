package com.bada.cali.api;

import com.bada.cali.common.ResMessage;
import com.bada.cali.config.NcpStorageProperties;
import com.bada.cali.dto.ManagerApprovalDTO;
import com.bada.cali.dto.TuiGridDTO;
import com.bada.cali.repository.projection.ManagerApprovalListRow;
import com.bada.cali.repository.ReportRepository;
import com.bada.cali.entity.Report;
import com.bada.cali.security.CustomUserDetails;
import com.bada.cali.service.ManagerApprovalServiceImpl;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.io.IOException;
import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Tag(name = "기술책임자결재", description = "기술책임자결재 목록 조회·반려·취소·일괄다운로드 API")
@RestController("ApiManagerApprovalController")
@RequestMapping("/api/admin/managerApproval")
@Log4j2
@RequiredArgsConstructor
public class ManagerApprovalController {

    private final ManagerApprovalServiceImpl managerApprovalService;
    private final ReportRepository reportRepository;
    private final S3Client ncloudS3Client;
    private final NcpStorageProperties storageProps;

    @Operation(summary = "기술책임자결재 목록 조회",
            description = "work_status=SUCCESS 성적서 기준 페이징 조회. " +
                    "approvalStatus 미전달 시 SUCCESS 제외 전체(대기 목록)를 기본 조회함.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "500", description = "서버 오류"),
    })
    @GetMapping("/list")
    public ResponseEntity<TuiGridDTO.Res<TuiGridDTO.ResData<ManagerApprovalListRow>>> getList(
            @ModelAttribute ManagerApprovalDTO.ListReq req) {
        TuiGridDTO.ResData<ManagerApprovalListRow> data = managerApprovalService.getList(req);
        return ResponseEntity.ok(new TuiGridDTO.Res<>(true, data));
    }

    @Operation(summary = "성적서 반려 처리",
            description = "선택한 성적서를 반려 처리함. " +
                    "approval_status가 READY/PROGRESS/SUCCESS인 성적서는 반려 불가. " +
                    "반려 시 work_status·approval_status 초기화, signed 파일 소프트삭제, 활성 배치 CANCELED 처리됨.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "반려 성공 또는 실패 메시지"),
            @ApiResponse(responseCode = "400", description = "요청 파라미터 오류"),
            @ApiResponse(responseCode = "500", description = "서버 오류"),
    })
    @PatchMapping("/reject")
    public ResponseEntity<ResMessage<Void>> rejectReports(
            @Valid @RequestBody ManagerApprovalDTO.RejectReq req,
            @AuthenticationPrincipal CustomUserDetails user) {
        String errMsg = managerApprovalService.rejectReports(req, user);
        if (errMsg != null) {
            return ResponseEntity.ok(new ResMessage<>(0, errMsg, null));
        }
        return ResponseEntity.ok(new ResMessage<>(1,
                String.format("%d건 반려 처리가 완료되었습니다.", req.getReportIds().size()), null));
    }

    @Operation(summary = "결재 취소 처리",
            description = "approval_status=SUCCESS인 성적서의 결재를 취소함. " +
                    "approval_status → IDLE, approval_datetime → NULL으로 초기화. approval_member_id는 유지됨.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "취소 성공 또는 실패 메시지"),
            @ApiResponse(responseCode = "400", description = "요청 파라미터 오류"),
            @ApiResponse(responseCode = "500", description = "서버 오류"),
    })
    @PatchMapping("/cancel")
    public ResponseEntity<ResMessage<Void>> cancelApproval(
            @Valid @RequestBody ManagerApprovalDTO.CancelApprovalReq req,
            @AuthenticationPrincipal CustomUserDetails user) {
        String errMsg = managerApprovalService.cancelApproval(req, user);
        if (errMsg != null) {
            return ResponseEntity.ok(new ResMessage<>(0, errMsg, null));
        }
        return ResponseEntity.ok(new ResMessage<>(1,
                String.format("%d건 취소 처리가 완료되었습니다.", req.getReportIds().size()), null));
    }

    /**
     * signed 파일 일괄 다운로드 (EXCEL 또는 PDF).
     *
     * 1건: 파일 직접 스트리밍
     * N건: ZipOutputStream으로 묶어 단일 ZIP 스트리밍 (임시 디스크 파일 미생성)
     *
     * @param ids      쉼표 구분 성적서 id 목록 (예: 1,2,3)
     * @param fileType EXCEL → signed_xlsx, PDF → signed_pdf
     */
    @Operation(summary = "signed 파일 일괄 다운로드",
            description = "선택한 성적서의 signed xlsx 또는 pdf 파일을 다운로드함. " +
                    "1건: 단순 파일 스트리밍. N건: ZIP으로 묶어 스트리밍 (임시 파일 미생성).")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "파일 다운로드 스트림"),
            @ApiResponse(responseCode = "500", description = "서버 오류 또는 스토리지 다운로드 실패"),
    })
    @GetMapping("/download")
    public void download(
            @Parameter(description = "쉼표 구분 성적서 id 목록", example = "1,2,3")
            @RequestParam List<Long> ids,
            @Parameter(description = "파일 유형: EXCEL 또는 PDF", example = "EXCEL")
            @RequestParam String fileType,
            HttpServletResponse response) throws IOException {

        // fileType → 스토리지 파일명 / contentType / 확장자 결정
        final String storedFileName;
        final String contentType;
        final String ext;
        final String fileTypeName;
        if ("PDF".equalsIgnoreCase(fileType)) {
            storedFileName = "signed.pdf";
            contentType    = "application/pdf";
            ext            = "pdf";
            fileTypeName   = "signed_pdf";
        } else {
            storedFileName = "signed.xlsx";
            contentType    = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
            ext            = "xlsx";
            fileTypeName   = "signed_xlsx";
        }

        String rootDir = storageProps.getRootDir();
        String bucket  = storageProps.getBucketName();

        // 성적서 목록 조회 → reportNum 추출 (파일명 생성용)
        List<Report> reports = reportRepository.findAllById(ids);

        if (ids.size() == 1) {
            // 단건: 직접 스트리밍
            Report r = reports.isEmpty() ? null : reports.get(0);
            String reportNum = (r != null && r.getReportNum() != null) ? r.getReportNum() : "signed";
            String objectKey = rootDir + "/report/" + ids.get(0) + "/" + storedFileName;
            String encodedName = URLEncoder.encode(reportNum + "." + ext, StandardCharsets.UTF_8)
                    .replaceAll("\\+", "%20");

            response.setContentType(contentType);
            response.setHeader("Content-Disposition",
                    "attachment; filename=\"" + encodedName + "\"; filename*=UTF-8''" + encodedName);
            try {
                ResponseInputStream<GetObjectResponse> s3is = ncloudS3Client.getObject(
                        GetObjectRequest.builder().bucket(bucket).key(objectKey).build());
                streamCopy(s3is, response);
            } catch (S3Exception e) {
                log.error("[managerApproval] 파일 다운로드 실패: {}", objectKey, e);
                response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "파일 다운로드 중 오류가 발생했습니다.");
            }
        } else {
            // 다건: ZIP 스트리밍
            String zipName = "signed_" + fileType.toLowerCase() + "_" + ids.size() + "건.zip";
            String encodedZip = URLEncoder.encode(zipName, StandardCharsets.UTF_8).replaceAll("\\+", "%20");

            response.setContentType("application/zip");
            response.setHeader("Content-Disposition",
                    "attachment; filename=\"" + encodedZip + "\"; filename*=UTF-8''" + encodedZip);

            // id → reportNum 맵
            java.util.Map<Long, String> reportNumMap = reports.stream()
                    .collect(java.util.stream.Collectors.toMap(Report::getId,
                            r -> r.getReportNum() != null ? r.getReportNum() : "report_" + r.getId()));

            try (ZipOutputStream zos = new ZipOutputStream(response.getOutputStream())) {
                for (Long reportId : ids) {
                    String objectKey = rootDir + "/report/" + reportId + "/" + storedFileName;
                    String entryName = reportNumMap.getOrDefault(reportId, "report_" + reportId) + "." + ext;
                    try {
                        ResponseInputStream<GetObjectResponse> s3is = ncloudS3Client.getObject(
                                GetObjectRequest.builder().bucket(bucket).key(objectKey).build());
                        zos.putNextEntry(new ZipEntry(entryName));
                        streamCopy(s3is, zos);
                        zos.closeEntry();
                    } catch (S3Exception e) {
                        log.warn("[managerApproval] ZIP 항목 건너뜀 — reportId={}, key={}: {}",
                                reportId, objectKey, e.getMessage());
                    }
                }
            } catch (IOException e) {
                log.error("[managerApproval] ZIP 스트리밍 오류", e);
            }
        }
    }

    /** InputStream → OutputStream 스트림 복사 (4KB 버퍼) */
    private void streamCopy(InputStream in, Object out) throws IOException {
        byte[] buf = new byte[4096];
        int read;
        if (out instanceof HttpServletResponse res) {
            var os = res.getOutputStream();
            while ((read = in.read(buf)) != -1) os.write(buf, 0, read);
        } else if (out instanceof java.io.OutputStream os) {
            while ((read = in.read(buf)) != -1) os.write(buf, 0, read);
        }
        in.close();
    }
}