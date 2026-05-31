package com.bada.cali.api;

import com.bada.cali.common.enums.ReportType;
import com.bada.cali.common.enums.YnType;
import com.bada.cali.config.NcpStorageProperties;
import com.bada.cali.dto.AgentCaliHistoryDTO;
import com.bada.cali.dto.TuiGridDTO;
import com.bada.cali.entity.FileInfo;
import com.bada.cali.entity.Report;
import com.bada.cali.repository.FileInfoRepository;
import com.bada.cali.repository.ReportRepository;
import com.bada.cali.repository.projection.AgentCaliHistoryListRow;
import com.bada.cali.security.CustomUserDetails;
import com.bada.cali.service.ReportServiceImpl;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
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
import java.util.Map;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Tag(name = "업체별교정이력조회", description = "업체별교정이력 목록 조회 및 일괄 다운로드 API")
@RestController("ApiCustomerCaliHistoryController")
@RequestMapping("/api/report")
@RequiredArgsConstructor
@Log4j2
public class CustomerCaliHistoryController {

    private final ReportServiceImpl reportService;
    private final ReportRepository reportRepository;
    private final FileInfoRepository fileInfoRepository;
    private final S3Client ncloudS3Client;
    private final NcpStorageProperties storageProps;

    /**
     * 업체별교정이력 목록 조회
     *
     * 접근 제어는 서비스 레이어에서 처리:
     *   - 내부직원(agentId=0): 전체 조회
     *   - 업체계정(agentId>0): 동일 그룹명 업체 성적서만 조회
     */
    @Operation(summary = "업체별교정이력 목록 조회",
            description = "SELF(approval_status=SUCCESS) + AGCY(report_status=SUCCESS) 성적서를 통합 조회함. " +
                    "업체계정은 동일 그룹명 업체의 성적서만 조회 가능함.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "500", description = "서버 오류"),
    })
    @GetMapping("/agentCaliHistoryList")
    public ResponseEntity<TuiGridDTO.Res<TuiGridDTO.ResData<AgentCaliHistoryListRow>>> getAgentCaliHistoryList(
            @ModelAttribute AgentCaliHistoryDTO.ListReq req,
            @AuthenticationPrincipal CustomUserDetails user) {

        TuiGridDTO.ResData<AgentCaliHistoryListRow> data = reportService.getAgentCaliHistoryList(req, user);
        return ResponseEntity.ok(new TuiGridDTO.Res<>(true, data));
    }

    /**
     * 업체별교정이력 파일 일괄 다운로드 (EXCEL 또는 PDF).
     *
     * SELF 파일: {rootDir}/report/{reportId}/signed.xlsx|pdf
     * AGCY 파일: {rootDir}/report/{reportId}/{fileInfoId}.{extension}
     *
     * 제약: SELF와 AGCY를 동시에 선택하면 400 반환 (프론트에서 1차 차단, 서버에서 2차 검증)
     * 1건: 파일 직접 스트리밍
     * N건: ZipOutputStream으로 묶어 단일 ZIP 스트리밍
     *
     * @param ids      쉼표 구분 성적서 id 목록 (예: 1,2,3)
     * @param fileType EXCEL 또는 PDF
     */
    @Operation(summary = "업체별교정이력 파일 일괄 다운로드",
            description = "선택한 성적서의 EXCEL 또는 PDF 파일을 다운로드함. " +
                    "SELF·AGCY 혼합 선택 불가. 1건: 단순 스트리밍. N건: ZIP으로 묶어 스트리밍.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "파일 다운로드 스트림"),
            @ApiResponse(responseCode = "400", description = "SELF·AGCY 혼합 선택 또는 유효하지 않은 파라미터"),
            @ApiResponse(responseCode = "500", description = "서버 오류 또는 스토리지 다운로드 실패"),
    })
    @GetMapping("/agentCaliHistory/download")
    public void download(
            @Parameter(description = "쉼표 구분 성적서 id 목록", example = "1,2,3")
            @RequestParam List<Long> ids,
            @Parameter(description = "파일 유형: EXCEL 또는 PDF", example = "EXCEL")
            @RequestParam String fileType,
            HttpServletResponse response) throws IOException {

        String rootDir = storageProps.getRootDir();
        String bucket  = storageProps.getBucketName();

        // 성적서 목록 조회
        List<Report> reports = reportRepository.findAllById(ids);

        // SELF / AGCY 혼합 선택 검증
        long selfCount = reports.stream().filter(r -> r.getReportType() == ReportType.SELF).count();
        long agcyCount = reports.stream().filter(r -> r.getReportType() == ReportType.AGCY).count();
        if (selfCount > 0 && agcyCount > 0) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST,
                    "자체성적서(SELF)와 대행성적서(AGCY)를 함께 선택하여 다운로드할 수 없습니다.");
            return;
        }

        boolean isSelf = selfCount > 0;

        // 파일 유형별 메타 정보
        final String contentType;
        final String ext;
        final String selfStoredFileName;
        final String agcyFileInfoName;   // file_info.name 값 (AGCY 전용)

        if ("PDF".equalsIgnoreCase(fileType)) {
            contentType       = "application/pdf";
            ext               = "pdf";
            selfStoredFileName = "signed.pdf";
            agcyFileInfoName  = "agcy_pdf";
        } else {
            contentType       = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
            ext               = "xlsx";
            selfStoredFileName = "signed.xlsx";
            agcyFileInfoName  = "agcy_excel";
        }

        // AGCY인 경우: reportId → fileInfo 맵 미리 구성
        Map<Long, FileInfo> agcyFileMap = null;
        if (!isSelf) {
            List<FileInfo> agcyFiles = fileInfoRepository.findByRefTableNameAndRefTableIdInAndIsVisible(
                    "report", ids, YnType.y);
            agcyFileMap = agcyFiles.stream()
                    .filter(f -> agcyFileInfoName.equals(f.getName()))
                    .collect(Collectors.toMap(FileInfo::getRefTableId, f -> f,
                            (a, b) -> a)); // 동일 reportId에 여러 건이면 첫 번째 사용
        }

        // id → reportNum 맵 (파일명 생성용)
        Map<Long, String> reportNumMap = reports.stream()
                .collect(Collectors.toMap(Report::getId,
                        r -> r.getReportNum() != null ? r.getReportNum() : "report_" + r.getId()));

        if (ids.size() == 1) {
            // 단건: 직접 스트리밍
            Long reportId = ids.get(0);
            String reportNum = reportNumMap.getOrDefault(reportId, "report_" + reportId);
            String objectKey = resolveObjectKey(isSelf, rootDir, reportId, selfStoredFileName, agcyFileMap);

            if (objectKey == null) {
                response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "파일 정보를 찾을 수 없습니다.");
                return;
            }

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
                log.error("[customerCaliHistory] 파일 다운로드 실패: {}", objectKey, e);
                response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "파일 다운로드 중 오류가 발생했습니다.");
            }
        } else {
            // 다건: ZIP 스트리밍
            String typeName = isSelf ? "self" : "agcy";
            String zipName = "교정이력_" + typeName + "_" + fileType.toLowerCase() + "_" + ids.size() + "건.zip";
            String encodedZip = URLEncoder.encode(zipName, StandardCharsets.UTF_8).replaceAll("\\+", "%20");

            response.setContentType("application/zip");
            response.setHeader("Content-Disposition",
                    "attachment; filename=\"" + encodedZip + "\"; filename*=UTF-8''" + encodedZip);

            try (ZipOutputStream zos = new ZipOutputStream(response.getOutputStream())) {
                for (Long reportId : ids) {
                    String objectKey = resolveObjectKey(isSelf, rootDir, reportId, selfStoredFileName, agcyFileMap);
                    if (objectKey == null) {
                        log.warn("[customerCaliHistory] ZIP 항목 건너뜀 (파일 정보 없음) — reportId={}", reportId);
                        continue;
                    }
                    String entryName = reportNumMap.getOrDefault(reportId, "report_" + reportId) + "." + ext;
                    try {
                        ResponseInputStream<GetObjectResponse> s3is = ncloudS3Client.getObject(
                                GetObjectRequest.builder().bucket(bucket).key(objectKey).build());
                        zos.putNextEntry(new ZipEntry(entryName));
                        streamCopy(s3is, zos);
                        zos.closeEntry();
                    } catch (S3Exception e) {
                        log.warn("[customerCaliHistory] ZIP 항목 건너뜀 — reportId={}, key={}: {}",
                                reportId, objectKey, e.getMessage());
                    }
                }
            } catch (IOException e) {
                log.error("[customerCaliHistory] ZIP 스트리밍 오류", e);
            }
        }
    }

    /**
     * S3 objectKey 결정.
     * SELF: {rootDir}/report/{reportId}/signed.xlsx|pdf
     * AGCY: {rootDir}/report/{reportId}/{fileInfoId}.{extension} (file_info 조회 필요)
     *
     * @return objectKey 문자열, AGCY인데 file_info가 없으면 null
     */
    private String resolveObjectKey(boolean isSelf, String rootDir, Long reportId,
                                    String selfStoredFileName, Map<Long, FileInfo> agcyFileMap) {
        if (isSelf) {
            return rootDir + "/report/" + reportId + "/" + selfStoredFileName;
        } else {
            FileInfo fi = agcyFileMap != null ? agcyFileMap.get(reportId) : null;
            if (fi == null) return null;
            return rootDir + "/report/" + reportId + "/" + fi.getId() + "." + fi.getExtension();
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
