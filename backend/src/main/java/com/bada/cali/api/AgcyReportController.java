package com.bada.cali.api;

import com.bada.cali.common.enums.YnType;
import com.bada.cali.config.NcpStorageProperties;
import com.bada.cali.dto.AgcyReportDTO;
import com.bada.cali.dto.TuiGridDTO;
import com.bada.cali.entity.FileInfo;
import com.bada.cali.entity.Report;
import com.bada.cali.repository.FileInfoRepository;
import com.bada.cali.repository.ReportRepository;
import com.bada.cali.repository.projection.AgcyReportListRow;
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

@Tag(name = "대행교정", description = "대행교정 목록 조회 및 파일 일괄 다운로드 API")
@RestController("ApiAgcyReportController")
@RequestMapping("/api/report")
@RequiredArgsConstructor
@Log4j2
public class AgcyReportController {

    private final ReportServiceImpl reportService;
    private final ReportRepository reportRepository;
    private final FileInfoRepository fileInfoRepository;
    private final S3Client ncloudS3Client;
    private final NcpStorageProperties storageProps;

    /**
     * 대행교정 목록 조회
     * AGCY 성적서를 진행상태/교정일자/키워드 필터로 조회
     */
    @Operation(summary = "대행교정 목록 조회",
            description = "AGCY 성적서 목록을 조회함. 진행상태/교정일자/키워드 필터 적용 가능함.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "500", description = "서버 오류"),
    })
    @GetMapping("/agcyReportList")
    public ResponseEntity<TuiGridDTO.Res<TuiGridDTO.ResData<AgcyReportListRow>>> getAgcyReportList(
            @ModelAttribute AgcyReportDTO.ListReq req) {

        TuiGridDTO.ResData<AgcyReportListRow> data = reportService.getAgcyReportList(req);
        return ResponseEntity.ok(new TuiGridDTO.Res<>(true, data));
    }

    /**
     * 대행교정 파일 일괄 다운로드 (EXCEL 또는 PDF).
     *
     * AGCY 파일: {rootDir}/report/{reportId}/{fileInfoId}.{extension}
     * 파일명: agcy_self_report_num 기준 (없으면 "report_{id}")
     * 1건: 파일 직접 스트리밍
     * N건: ZipOutputStream으로 묶어 단일 ZIP 스트리밍
     *
     * @param ids      쉼표 구분 성적서 id 목록 (예: 1,2,3)
     * @param fileType EXCEL 또는 PDF
     */
    @Operation(summary = "대행교정 파일 일괄 다운로드",
            description = "선택한 대행성적서의 EXCEL 또는 PDF 파일을 다운로드함. " +
                    "1건: 단순 스트리밍. N건: ZIP으로 묶어 스트리밍.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "파일 다운로드 스트림"),
            @ApiResponse(responseCode = "500", description = "서버 오류 또는 스토리지 다운로드 실패"),
    })
    @GetMapping("/agcyReport/download")
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

        // 파일 유형별 메타 정보
        final String contentType;
        final String ext;
        final String agcyFileInfoName; // file_info.name 값

        if ("PDF".equalsIgnoreCase(fileType)) {
            contentType      = "application/pdf";
            ext              = "pdf";
            agcyFileInfoName = "agcy_pdf";
        } else {
            contentType      = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
            ext              = "xlsx";
            agcyFileInfoName = "agcy_excel";
        }

        // reportId → FileInfo 맵 구성 (file_info.name 기준 필터)
        List<FileInfo> fileInfoList = fileInfoRepository.findByRefTableNameAndRefTableIdInAndIsVisible(
                "report", ids, YnType.y);
        Map<Long, FileInfo> agcyFileMap = fileInfoList.stream()
                .filter(f -> agcyFileInfoName.equals(f.getName()))
                .collect(Collectors.toMap(FileInfo::getRefTableId, f -> f,
                        (a, b) -> a)); // 동일 reportId에 여러 건이면 첫 번째 사용

        // id → 파일명 맵 (자체성적서번호 기준, 없으면 "report_{id}")
        Map<Long, String> reportNameMap = reports.stream()
                .collect(Collectors.toMap(Report::getId,
                        r -> r.getAgcySelfReportNum() != null ? r.getAgcySelfReportNum() : "report_" + r.getId()));

        if (ids.size() == 1) {
            // 단건: 직접 스트리밍
            Long reportId = ids.get(0);
            FileInfo fi = agcyFileMap.get(reportId);

            if (fi == null) {
                response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "파일 정보를 찾을 수 없습니다.");
                return;
            }

            String objectKey = rootDir + "/report/" + reportId + "/" + fi.getId() + "." + fi.getExtension();
            String fileName  = reportNameMap.getOrDefault(reportId, "report_" + reportId) + "." + ext;
            String encodedName = URLEncoder.encode(fileName, StandardCharsets.UTF_8).replaceAll("\\+", "%20");

            response.setContentType(contentType);
            response.setHeader("Content-Disposition",
                    "attachment; filename=\"" + encodedName + "\"; filename*=UTF-8''" + encodedName);

            try {
                ResponseInputStream<GetObjectResponse> s3is = ncloudS3Client.getObject(
                        GetObjectRequest.builder().bucket(bucket).key(objectKey).build());
                streamCopy(s3is, response);
            } catch (S3Exception e) {
                log.error("[agcyReport] 파일 다운로드 실패: {}", objectKey, e);
                response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "파일 다운로드 중 오류가 발생했습니다.");
            }
        } else {
            // 다건: ZIP 스트리밍
            String zipName = "대행교정_" + fileType.toLowerCase() + "_" + ids.size() + "건.zip";
            String encodedZip = URLEncoder.encode(zipName, StandardCharsets.UTF_8).replaceAll("\\+", "%20");

            response.setContentType("application/zip");
            response.setHeader("Content-Disposition",
                    "attachment; filename=\"" + encodedZip + "\"; filename*=UTF-8''" + encodedZip);

            try (ZipOutputStream zos = new ZipOutputStream(response.getOutputStream())) {
                for (Long reportId : ids) {
                    FileInfo fi = agcyFileMap.get(reportId);
                    if (fi == null) {
                        log.warn("[agcyReport] ZIP 항목 건너뜀 (파일 정보 없음) — reportId={}", reportId);
                        continue;
                    }
                    String objectKey = rootDir + "/report/" + reportId + "/" + fi.getId() + "." + fi.getExtension();
                    String entryName = reportNameMap.getOrDefault(reportId, "report_" + reportId) + "." + ext;
                    try {
                        ResponseInputStream<GetObjectResponse> s3is = ncloudS3Client.getObject(
                                GetObjectRequest.builder().bucket(bucket).key(objectKey).build());
                        zos.putNextEntry(new ZipEntry(entryName));
                        streamCopy(s3is, zos);
                        zos.closeEntry();
                    } catch (S3Exception e) {
                        log.warn("[agcyReport] ZIP 항목 건너뜀 — reportId={}, key={}: {}",
                                reportId, objectKey, e.getMessage());
                    }
                }
            } catch (IOException e) {
                log.error("[agcyReport] ZIP 스트리밍 오류", e);
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
