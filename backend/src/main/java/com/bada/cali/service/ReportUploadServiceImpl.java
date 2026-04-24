package com.bada.cali.service;

import com.bada.cali.common.enums.AppStatus;
import com.bada.cali.common.enums.ReportType;
import com.bada.cali.common.enums.YnType;
import com.bada.cali.config.NcpStorageProperties;
import com.bada.cali.dto.ReportJobBatchDTO;
import com.bada.cali.entity.FileInfo;
import com.bada.cali.entity.Report;
import com.bada.cali.repository.FileInfoRepository;
import com.bada.cali.repository.ReportRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.ObjectCannedACL;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@Log4j2
@RequiredArgsConstructor
public class ReportUploadServiceImpl {

    private static final String CT_XLSX =
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    private final ReportRepository      reportRepository;
    private final FileInfoRepository    fileInfoRepository;
    private final S3Client              ncloudS3Client;
    private final NcpStorageProperties  storageProps;

    /**
     * 업로드 사전 검증.
     *
     * 파일명(확장자 제거) = 성적서번호(report_num) 완전 일치로 성적서를 조회한다.
     * mode = "upload"   : 업로드 가능 여부만 확인
     * mode = "approval" : 업로드 후 실무자결재 가능 여부까지 확인
     */
    @Transactional(readOnly = true)
    public ReportJobBatchDTO.ValidateRes validateUpload(List<String> reportNums, String mode) {
        // 파일명 기반 성적서 일괄 조회 (isVisible=y)
        List<Report> reports = reportRepository.findByReportNumInAndIsVisible(reportNums, YnType.y);
        Map<String, Report> reportMap = reports.stream()
                .collect(Collectors.toMap(Report::getReportNum, r -> r));

        // approval 모드: 실무자 서명 이미지 일괄 조회 (N+1 방지)
        Set<Long> memberIdsWithSign = Set.of();
        if ("approval".equals(mode)) {
            Set<Long> workMemberIds = reports.stream()
                    .filter(r -> r.getWorkMemberId() != null)
                    .map(Report::getWorkMemberId)
                    .collect(Collectors.toSet());
            if (!workMemberIds.isEmpty()) {
                memberIdsWithSign = fileInfoRepository
                        .findByRefTableNameAndRefTableIdInAndIsVisible("member", workMemberIds, YnType.y)
                        .stream()
                        .map(FileInfo::getRefTableId)
                        .collect(Collectors.toSet());
            }
        }

        List<ReportJobBatchDTO.ValidateItem>  valid   = new ArrayList<>();
        List<ReportJobBatchDTO.InvalidItem>   invalid = new ArrayList<>();

        final Set<Long> finalMemberIdsWithSign = memberIdsWithSign;

        for (String reportNum : reportNums) {
            Report report = reportMap.get(reportNum);

            if (report == null) {
                invalid.add(new ReportJobBatchDTO.InvalidItem(null, reportNum,
                        "성적서번호와 일치하는 성적서를 찾을 수 없습니다."));
                continue;
            }

            if (report.getReportType() != ReportType.SELF) {
                invalid.add(new ReportJobBatchDTO.InvalidItem(report.getId(), reportNum,
                        "자체성적서(SELF)만 업로드 가능합니다."));
                continue;
            }

            // 업로드 불가: 성적서작성이 READY/PROGRESS 진행 중
            if (report.getWriteStatus() == AppStatus.READY
                    || report.getWriteStatus() == AppStatus.PROGRESS) {
                invalid.add(new ReportJobBatchDTO.InvalidItem(report.getId(), reportNum,
                        "성적서작성이 진행 중인 성적서입니다."));
                continue;
            }

            // 업로드 불가: 실무자결재가 READY/PROGRESS/SUCCESS
            if (report.getWorkStatus() == AppStatus.READY
                    || report.getWorkStatus() == AppStatus.PROGRESS
                    || report.getWorkStatus() == AppStatus.SUCCESS) {
                invalid.add(new ReportJobBatchDTO.InvalidItem(report.getId(), reportNum,
                        "실무자결재가 진행 중이거나 완료된 성적서입니다."));
                continue;
            }

            // 업로드 불가: 기술책임자결재가 READY/PROGRESS/SUCCESS
            if (report.getApprovalStatus() == AppStatus.READY
                    || report.getApprovalStatus() == AppStatus.PROGRESS
                    || report.getApprovalStatus() == AppStatus.SUCCESS) {
                invalid.add(new ReportJobBatchDTO.InvalidItem(report.getId(), reportNum,
                        "기술책임자결재가 진행 중이거나 완료된 성적서입니다."));
                continue;
            }

            // approval 모드 추가 검증
            if ("approval".equals(mode)) {
                if (report.getWorkMemberId() == null) {
                    invalid.add(new ReportJobBatchDTO.InvalidItem(report.getId(), reportNum,
                            "실무자가 지정되지 않은 성적서입니다."));
                    continue;
                }
                if (!finalMemberIdsWithSign.contains(report.getWorkMemberId())) {
                    invalid.add(new ReportJobBatchDTO.InvalidItem(report.getId(), reportNum,
                            "실무자 서명 이미지가 등록되어 있지 않습니다."));
                    continue;
                }
            }

            valid.add(new ReportJobBatchDTO.ValidateItem(report.getId(), reportNum));
        }

        return new ReportJobBatchDTO.ValidateRes(valid, invalid);
    }

    /**
     * 원본 파일(origin.xlsx) 교체 업로드.
     *
     * 대상 성적서의 기존 origin file_info를 소프트삭제하고
     * 스토리지에 origin.xlsx를 덮어쓴 뒤 신규 file_info를 등록한다.
     * 업로드 성공 시 report.writeStatus = SUCCESS 로 설정한다.
     *
     * @param files      업로드할 MultipartFile 목록 (파일명 = 성적서번호.xlsx)
     * @param reportIds  업로드 대상 성적서 id 목록 (validate 결과 기반)
     * @param userId     요청자 member id
     */
    @Transactional
    public void replaceOriginFiles(List<MultipartFile> files, List<Long> reportIds, Long userId) {
        List<Report> reports = reportRepository.findAllById(reportIds);
        if (reports.size() != reportIds.size()) {
            throw new EntityNotFoundException("일부 성적서를 찾을 수 없습니다.");
        }

        // reportId → Report 매핑
        Map<Long, Report> reportMap = reports.stream()
                .collect(Collectors.toMap(Report::getId, r -> r));

        // 파일명(확장자 제거) → reportId 역방향 매핑
        Map<String, Long> numToId = reports.stream()
                .collect(Collectors.toMap(Report::getReportNum, Report::getId));

        String bucket   = storageProps.getBucketName();
        String rootDir  = storageProps.getRootDir();
        LocalDateTime now = LocalDateTime.now();

        List<String> uploadedKeys = new ArrayList<>();

        try {
            for (MultipartFile file : files) {
                if (file == null || file.isEmpty()) continue;

                String originalFilename = file.getOriginalFilename();
                if (originalFilename == null) continue;
                // 역슬래시/슬래시 경로 제거
                originalFilename = originalFilename
                        .substring(originalFilename.lastIndexOf("\\") + 1)
                        .substring(originalFilename.lastIndexOf("/") + 1);

                // 파일명에서 성적서번호 추출 (확장자 제거)
                int dotIdx = originalFilename.lastIndexOf('.');
                String reportNum = dotIdx > 0 ? originalFilename.substring(0, dotIdx) : originalFilename;

                Long reportId = numToId.get(reportNum);
                if (reportId == null) {
                    log.warn("파일명 '{}' 에 해당하는 성적서를 찾을 수 없어 건너뜁니다.", reportNum);
                    continue;
                }

                // 기존 origin file_info 소프트삭제
                fileInfoRepository.softDeleteByRefAndNames(
                        "report", reportId,
                        List.of("origin"),
                        YnType.n,
                        now,
                        userId
                );

                // 스토리지 고정 경로: {rootDir}/report/{reportId}/origin.xlsx
                String objectKey = rootDir + "/report/" + reportId + "/origin.xlsx";

                PutObjectRequest putReq = PutObjectRequest.builder()
                        .bucket(bucket)
                        .key(objectKey)
                        .acl(ObjectCannedACL.PUBLIC_READ)
                        .contentType(CT_XLSX)
                        .build();

                try (InputStream is = file.getInputStream()) {
                    ncloudS3Client.putObject(putReq, RequestBody.fromInputStream(is, file.getSize()));
                }
                uploadedKeys.add(objectKey);
                log.info("origin 업로드 완료 — reportId: {}, key: {}", reportId, objectKey);

                // 신규 file_info 등록
                fileInfoRepository.save(FileInfo.builder()
                        .refTableName("report")
                        .refTableId(reportId)
                        .originName("origin.xlsx")
                        .name("origin")
                        .extension("xlsx")
                        .fileSize(file.getSize())
                        .contentType(CT_XLSX)
                        .dir("report/" + reportId + "/")
                        .isVisible(YnType.y)
                        .createDatetime(now)
                        .createMemberId(userId)
                        .build());

                // writeStatus = SUCCESS (업로드 완료)
                Report report = reportMap.get(reportId);
                report.setWriteStatus(AppStatus.SUCCESS);
                report.setWriteDatetime(now);
            }
        } catch (Exception e) {
            // 이미 업로드된 파일은 롤백하지 않음 (DB 트랜잭션 롤백으로 file_info 원복)
            // 스토리지 파일은 재업로드 시 덮어쓰기로 처리됨
            log.error("origin 파일 업로드 중 오류 발생", e);
            throw new RuntimeException("파일 업로드 중 오류가 발생했습니다: " + e.getMessage(), e);
        }
    }
}
