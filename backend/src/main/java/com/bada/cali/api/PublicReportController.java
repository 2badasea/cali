package com.bada.cali.api;

import com.bada.cali.config.NcpStorageProperties;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.web.bind.annotation.*;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.io.IOException;
import java.io.InputStream;

/**
 * 공개 성적서 PDF 뷰어 API (인증 불필요 — SecurityConfig "/r/**" permitAll).
 *
 * 기술책임자결재 QR코드 URL({serverUrl}/r/{reportId})을 스캔했을 때
 * 해당 성적서의 signed.pdf를 인라인으로 렌더링한다.
 */
@Tag(name = "공개 성적서 PDF 뷰어", description = "QR코드 스캔용 공개 signed PDF 뷰어 (인증 불필요)")
@RestController("ApiPublicReportController")
@RequestMapping("/r")
@Log4j2
@RequiredArgsConstructor
public class PublicReportController {

    private final S3Client ncloudS3Client;
    private final NcpStorageProperties storageProps;

    /**
     * 성적서 signed.pdf 인라인 스트리밍.
     *
     * 기술책임자결재 QR코드가 이 URL을 가리키며, 모바일/PC 브라우저에서 PDF를 직접 표시한다.
     * 파일이 없으면 404 응답을 반환한다.
     *
     * @param reportId 성적서 id
     */
    @Operation(
            summary = "성적서 signed PDF 공개 뷰어",
            description = "기술책임자결재 QR코드가 가리키는 공개 PDF 뷰어. " +
                    "해당 성적서의 signed.pdf를 인라인(Content-Disposition: inline)으로 스트리밍함. " +
                    "인증 불필요 — SecurityConfig permitAll 적용 경로"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "PDF 스트리밍 성공"),
            @ApiResponse(responseCode = "404", description = "signed.pdf 파일 없음"),
            @ApiResponse(responseCode = "500", description = "서버 오류 또는 스토리지 오류"),
    })
    @GetMapping("/{reportId}")
    public void streamSignedPdf(
            @Parameter(description = "성적서 id") @PathVariable Long reportId,
            HttpServletResponse response
    ) throws IOException {

        String objectKey = storageProps.getRootDir() + "/report/" + reportId + "/signed.pdf";

        try {
            ResponseInputStream<GetObjectResponse> s3is = ncloudS3Client.getObject(
                    GetObjectRequest.builder()
                            .bucket(storageProps.getBucketName())
                            .key(objectKey)
                            .build());

            response.setContentType("application/pdf");
            // inline: 브라우저에서 직접 PDF 뷰어로 렌더링
            response.setHeader("Content-Disposition", "inline; filename=\"signed.pdf\"");

            GetObjectResponse meta = s3is.response();
            if (meta.contentLength() != null) {
                response.setContentLengthLong(meta.contentLength());
            }

            streamCopy(s3is, response);
            log.info("signed.pdf 스트리밍 완료 — reportId: {}", reportId);

        } catch (S3Exception e) {
            if (e.statusCode() == 404) {
                log.warn("signed.pdf 없음 — reportId: {}, key: {}", reportId, objectKey);
                response.sendError(HttpServletResponse.SC_NOT_FOUND, "성적서 PDF 파일을 찾을 수 없습니다.");
            } else {
                log.error("signed.pdf 스트리밍 오류 — reportId: {}, key: {}: {}", reportId, objectKey, e.getMessage(), e);
                response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "파일 조회 중 오류가 발생했습니다.");
            }
        }
    }

    private void streamCopy(InputStream in, HttpServletResponse response) throws IOException {
        byte[] buf = new byte[4096];
        int read;
        var os = response.getOutputStream();
        while ((read = in.read(buf)) != -1) os.write(buf, 0, read);
        in.close();
    }
}
