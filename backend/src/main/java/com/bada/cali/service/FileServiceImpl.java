package com.bada.cali.service;

import com.bada.cali.common.enums.YnType;
import com.bada.cali.config.NcpStorageProperties;
import com.bada.cali.dto.FileInfoDTO;
import com.bada.cali.entity.FileInfo;
import com.bada.cali.repository.FileInfoRepository;
import com.bada.cali.security.CustomUserDetails;
import lombok.AllArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.InvalidMediaTypeException;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

@Service
@Log4j2
@AllArgsConstructor
public class FileServiceImpl {
	
	private final FileInfoRepository fileInfoRepository;
	private final S3Client ncloudS3Client;
	private final NcpStorageProperties storageProps; // endpoint, bucketName 등
	
	@Transactional
	public void saveFiles(String refTableName,
						  Long refTableId,
						  String dir,        // '폴더명/' or '폴더명/id/' 형태로 넘어올 수 있음
						  List<MultipartFile> files,
						  Long userId
	) {
		
		LocalDateTime now = LocalDateTime.now();
		String bucket = storageProps.getBucketName();
		String rootDir = storageProps.getRootDir();
		
		List<String> uploadedKeys = new ArrayList<>();
		
		try {
			for (MultipartFile file : files) {
				// 비어 있는 항목은 skip
				if (file == null || file.isEmpty()) {
					continue;
				}
				
				String originName = file.getOriginalFilename();    // 풀네임
				if (originName == null) {
					originName = "";
				}
				// 일부 브라우저에서 경로까지 오는 경우 처리
				else {
					// 역슬래시 기반 경로로 한번 잘라보고, 슬래시 경로로 한번 더 잘라서 -> windows/unix 둘 다 커버
					originName = originName
							.substring(originName.lastIndexOf("\\") + 1)
							.substring(originName.lastIndexOf("/") + 1);
					// 아래처럼 표현도 가능
					// originName = originName.replace("\\", "/");
					// originName = originName.substring(originName.lastIndexOf("/") + 1);
				}
				
				String extension = getFileExtension(originName);        // 파일 확장자 구하기
				String name = getFileNameWithoutExtension(originName);    // 파일명 얻기
				
				FileInfo fileInfo = FileInfo.builder()
						.refTableId(refTableId)
						.refTableName(refTableName)
						.originName(originName)
						.name(name)
						.extension(extension)
						.fileSize(file.getSize())
						.contentType(file.getContentType())
						.dir(dir)
						.isVisible(YnType.y)
						.createDatetime(now)
						.createMemberId(userId)
						.build();
				// 파일 저장
				
				FileInfo savedFile = fileInfoRepository.save(fileInfo);
				// 오브젝트 키 만들기 ('dir' + 'file_info.id' + '.확장자')
				String objectKey = rootDir + "/";
				if (dir.endsWith("/")) {
					objectKey += dir + savedFile.getId();
				} else {
					objectKey += dir + "/" + savedFile.getId();
				}
				
				if (extension != null && !extension.isEmpty()) {
					objectKey = objectKey + "." + extension;
				}
				
				// 스토리지 업로드
				// ★ v2: ObjectMetadata 대신 PutObjectRequest 사용
				PutObjectRequest putReq = PutObjectRequest.builder()
						.bucket(bucket)
						.key(objectKey)
						.acl(ObjectCannedACL.PUBLIC_READ)
						.contentType(file.getContentType())
						.build();
				
				
				try (InputStream is = file.getInputStream()) {
					
					try {
						ncloudS3Client.putObject(putReq, RequestBody.fromInputStream(is, file.getSize()));
					} catch (S3Exception e) {
						log.error("S3 API 에러: status={}, awsMessage={}, requestId={}",
								e.statusCode(),
								(e.awsErrorDetails() != null ? e.awsErrorDetails().errorMessage() : null),
								e.requestId(),
								e);
						throw e;
					} catch (SdkClientException e) {
						// 네트워크/클라이언트 단 오류
						log.error("S3 클라이언트 에러: {}", e.getMessage(), e);
						throw e;
					}
				}
				uploadedKeys.add(objectKey);
				
			}    // End for
			
		} catch (Exception e) {
			// 필요 시 보상 트랜잭션: 이미 업로드된 파일 삭제
			for (String key : uploadedKeys) {
				try {
					ncloudS3Client.deleteObject(
							DeleteObjectRequest.builder().bucket(bucket).key(key).build());
				} catch (Exception ex) {
					log.error("스토리지 롤백 실패 - key: {}", key, ex);
				}
			}
			throw new RuntimeException("파일 업로드 중 오류 발생", e);
		}
	}
	
	@Transactional(readOnly = true)
	public ResponseEntity<Resource> downloadFile(Long fileId) {
		
		// DB에서 file조회
		FileInfo fileInfo = fileInfoRepository.findById(fileId).orElseThrow(() -> new IllegalArgumentException("파일 정보를 찾을 수 없습니다. id= " + fileId));
		
		String bucket = storageProps.getBucketName();
		String rootDir = storageProps.getRootDir();
		String dir = fileInfo.getDir();
		String extension = fileInfo.getExtension();
		
		// 업로드 시 사용했던 규칙 그대로 objectKey 생성
		String objectKey = rootDir + "/";
		if (dir.endsWith("/")) {
			objectKey += dir + fileInfo.getId();
		} else {
			objectKey += dir + "/" + fileInfo.getId();
		}
		if (extension != null && !extension.isEmpty()) {
			objectKey = objectKey + "." + extension;
		}
		
		try {
			// NCP Object Storage에서 파일 가져오기 (스트리밍 방식)
			GetObjectRequest getReq = GetObjectRequest.builder()
					.bucket(bucket)
					.key(objectKey)
					.build();
			
			// 주의: 여기서 try-with-resources로 닫아버리면 응답 전에 스트림이 닫힘
			ResponseInputStream<GetObjectResponse> s3is = ncloudS3Client.getObject(getReq);
			GetObjectResponse resp = s3is.response();
			long contentLength = resp.contentLength();
			
			Resource resource = new InputStreamResource(s3is);
			
			// 헤더용 파일명, 컨텐츠 타입 세팅
			String originName = fileInfo.getOriginName();
			if (originName == null || originName.isBlank()) {
				originName = "file";
			}
			String encodedName = URLEncoder.encode(originName, StandardCharsets.UTF_8).replaceAll("\\+", "%20");    // 공백깨짐 방지
			
			String contentType = fileInfo.getContentType();
			MediaType mediaType;
			try {
				if (contentType != null && !contentType.isBlank()) {
					mediaType = MediaType.parseMediaType(contentType);
				} else {
					mediaType = MediaType.APPLICATION_OCTET_STREAM;
				}
			} catch (InvalidMediaTypeException e) {
				mediaType = MediaType.APPLICATION_OCTET_STREAM;
			}
			
			// 다운로드 응답 생성
			return ResponseEntity.ok().contentType(mediaType).header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + encodedName + "\"").contentLength(contentLength).body(resource);
			
		} catch (S3Exception e) {
			if (e.statusCode() == 404) {
				throw new IllegalArgumentException("스토리지에서 파일을 찾을 수 없습니다. key=" + objectKey, e);
			}
			throw new IllegalStateException("스토리지 파일 다운로드 중 오류 발생", e);
		}
	}
	
	/**
	 * 성적서 파일 전용 다운로드
	 *
	 * 성적서 파일은 file_info.id 가 아닌 report.id 기반의 고정 경로로 S3 에 저장된다.
	 *   - 원본: {rootDir}/report/{reportId}/origin.xlsx
	 *   - 결재 EXCEL: {rootDir}/report/{reportId}/signed.xlsx
	 *   - 결재 PDF:  {rootDir}/report/{reportId}/signed.pdf
	 *
	 * fileType 허용값: "origin" | "signed_xlsx" | "signed_pdf"
	 * reportNum(선택): 전달 시 다운로드 파일명을 "{reportNum}.xlsx" 등으로 변경.
	 *                  null/빈값이면 기존 고정명으로 fallback.
	 *
	 * @param reportId  성적서 id
	 * @param fileType  파일 종류 ("origin", "signed_xlsx", "signed_pdf")
	 * @param reportNum 다운로드 파일명에 사용할 성적서 번호 (선택)
	 * @return 파일 다운로드 응답
	 * @throws IllegalArgumentException 파일을 스토리지에서 찾을 수 없을 때
	 */
	@Transactional(readOnly = true)
	public ResponseEntity<Resource> downloadReportFile(Long reportId, String fileType, String reportNum) {

		// fileType → 스토리지 파일명 + contentType + 확장자 매핑
		final String storedFileName; // 스토리지 상의 실제 파일명
		final String contentType;
		final String extension;
		switch (fileType) {
			case "origin" -> {
				storedFileName = "origin.xlsx";
				contentType    = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
				extension      = "xlsx";
			}
			case "signed_xlsx" -> {
				storedFileName = "signed.xlsx";
				contentType    = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
				extension      = "xlsx";
			}
			case "signed_pdf" -> {
				storedFileName = "signed.pdf";
				contentType    = "application/pdf";
				extension      = "pdf";
			}
			default -> throw new IllegalArgumentException("유효하지 않은 fileType: " + fileType
					+ " (허용값: origin | signed_xlsx | signed_pdf)");
		}

		// 다운로드 파일명 결정:
		//   reportNum 이 전달되면 "{reportNum}.{ext}", 없으면 storedFileName 그대로
		final String downloadFileName = (reportNum != null && !reportNum.isBlank())
				? reportNum + "." + extension
				: storedFileName;

		// 성적서 파일 고정 objectKey: {rootDir}/report/{reportId}/{storedFileName}
		final String objectKey = storageProps.getRootDir() + "/report/" + reportId + "/" + storedFileName;

		try {
			ResponseInputStream<GetObjectResponse> s3is = ncloudS3Client.getObject(
					GetObjectRequest.builder()
							.bucket(storageProps.getBucketName())
							.key(objectKey)
							.build()
			);
			long contentLength = s3is.response().contentLength();

			String encodedName = URLEncoder.encode(downloadFileName, StandardCharsets.UTF_8)
					.replaceAll("\\+", "%20");

			return ResponseEntity.ok()
					.contentType(MediaType.parseMediaType(contentType))
					.header(HttpHeaders.CONTENT_DISPOSITION,
							"attachment; filename=\"" + encodedName + "\"; filename*=UTF-8''" + encodedName)
					.contentLength(contentLength)
					.body(new InputStreamResource(s3is));

		} catch (S3Exception e) {
			if (e.statusCode() == 404) {
				throw new IllegalArgumentException(
						"스토리지에서 성적서 파일을 찾을 수 없습니다. reportId=" + reportId + ", fileType=" + fileType, e);
			}
			throw new IllegalStateException("성적서 파일 다운로드 중 오류 발생", e);
		}
	}

	/**
	 * 대행성적서(AGCY) 파일 업로드.
	 *
	 * xlsx → name='agcy_excel' / pdf → name='agcy_pdf' 로 file_info에 등록한다.
	 * 같은 타입(name)의 기존 파일이 존재하면 업로드를 거부한다 (먼저 삭제 후 업로드 필요).
	 * S3 경로: {rootDir}/report/{reportId}/{fileInfoId}.{extension}
	 *
	 * @param reportId  성적서 id (AGCY 타입이어야 함)
	 * @param file      업로드할 파일 (xlsx 또는 pdf)
	 * @param userId    업로드 요청자 id
	 * @return 저장된 FileInfo
	 * @throws IllegalArgumentException 확장자 불일치 또는 동일 타입 파일 이미 존재 시
	 */
	@Transactional
	public FileInfo uploadAgcyReportFile(Long reportId, MultipartFile file, Long userId) {
		String originName = file.getOriginalFilename();
		if (originName == null) {
			originName = "";
		}
		// 브라우저에 따라 경로가 포함될 수 있으므로 파일명만 추출
		originName = originName.substring(originName.lastIndexOf("\\") + 1)
				.substring(originName.lastIndexOf("/") + 1);

		String extension = getFileExtension(originName).toLowerCase();

		// 확장자 검증: xlsx, pdf만 허용
		if (!extension.equals("xlsx") && !extension.equals("pdf")) {
			throw new IllegalArgumentException("xlsx 또는 pdf 파일만 업로드 가능합니다.");
		}

		// 파일 역할 구분: xlsx → agcy_excel, pdf → agcy_pdf
		String agcyType = extension.equals("xlsx") ? "agcy_excel" : "agcy_pdf";

		// 동일 타입 파일 존재 시 거부 (사용자가 먼저 삭제해야 함)
		boolean alreadyExists = fileInfoRepository.existsByRefTableNameAndRefTableIdAndNameAndIsVisible(
				"report", reportId, agcyType, YnType.y);
		if (alreadyExists) {
			throw new IllegalArgumentException(
					(extension.equals("xlsx") ? "Excel" : "PDF") + " 파일이 이미 존재합니다. 기존 파일을 삭제한 후 업로드해 주세요.");
		}

		String dir = "report/" + reportId + "/";
		String bucket = storageProps.getBucketName();
		String rootDir = storageProps.getRootDir();
		LocalDateTime now = LocalDateTime.now();

		// 콘텐츠타입 결정
		String contentType = extension.equals("xlsx")
				? "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
				: "application/pdf";

		// file_info 먼저 저장 → id 확보 후 S3 키 구성
		FileInfo fileInfo = FileInfo.builder()
				.refTableName("report")
				.refTableId(reportId)
				.originName(originName)
				.name(agcyType)     // agcy_excel 또는 agcy_pdf 고정
				.extension(extension)
				.fileSize(file.getSize())
				.contentType(contentType)
				.dir(dir)
				.isVisible(YnType.y)
				.createDatetime(now)
				.createMemberId(userId)
				.build();
		FileInfo saved = fileInfoRepository.save(fileInfo);

		// S3 objectKey: {rootDir}/report/{reportId}/{fileInfoId}.{extension}
		String objectKey = rootDir + "/" + dir + saved.getId() + "." + extension;

		PutObjectRequest putReq = PutObjectRequest.builder()
				.bucket(bucket)
				.key(objectKey)
				.acl(ObjectCannedACL.PUBLIC_READ)
				.contentType(contentType)
				.build();

		try (InputStream is = file.getInputStream()) {
			ncloudS3Client.putObject(putReq, RequestBody.fromInputStream(is, file.getSize()));
		} catch (Exception e) {
			// 업로드 실패 시 file_info soft-delete (트랜잭션 롤백 대신 명시적 정리)
			saved.setIsVisible(YnType.n);
			fileInfoRepository.save(saved);
			log.error("AGCY 파일 S3 업로드 실패 — reportId: {}, type: {}", reportId, agcyType, e);
			throw new RuntimeException("파일 업로드 중 오류가 발생했습니다.", e);
		}

		log.info("AGCY 파일 업로드 완료 — reportId: {}, type: {}, fileInfoId: {}, key: {}",
				reportId, agcyType, saved.getId(), objectKey);
		return saved;
	}

	// 파일 삭제(스토리지에선 그대로)
	@Transactional
	public int deleteFile(Long fileId, CustomUserDetails user) {
		return fileInfoRepository.deleteFile(fileId, YnType.n, LocalDateTime.now(), user.getId());
	}

	// 특정 테이블의 여러 id에 해당하는 파일 일괄 소프트삭제 (업체/성적서 등 삭제 연계용)
	@Transactional
	public void softDeleteFilesByRefTableIds(String refTableName, Collection<Long> refTableIds, Long userId) {
		fileInfoRepository.softDeleteByRefTableIds(refTableName, refTableIds, YnType.n, LocalDateTime.now(), userId);
	}
	
	
	// "파일명.확장자" 에서 확장자를 추출 (없으면 빈 문자열)
	@Transactional
	public String getFileExtension(String originName) {
		if (originName == null || originName.isEmpty()) {
			return "";
		}
		int lastDot = originName.lastIndexOf('.');
		if (lastDot == -1 || lastDot == originName.length() - 1) {
			return "";
		}
		return originName.substring(lastDot + 1);  // pdf, xlsx, png ...
	}
	
	// "파일명.확장자" 에서 확장자를 제외한 이름만 추출
	@Transactional
	public String getFileNameWithoutExtension(String originName) {
		if (originName == null || originName.isEmpty()) {
			return "";
		}
		int lastDot = originName.lastIndexOf('.');
		if (lastDot == -1) {
			return originName;
		}
		return originName.substring(0, lastDot);
	}
	
	// join을 고려하지 않은 단순 file_info 정보만 반환
	public List<FileInfo> getFileInfos(String refTableName, Long refTableId) {
		return fileInfoRepository.findByRefTableNameAndRefTableIdAndIsVisible(refTableName, refTableId, YnType.y);
	}

	// 파일 개수만 필요한 경우 COUNT 쿼리 사용
	@Transactional(readOnly = true)
	public long countFiles(String refTableName, Long refTableId) {
		return fileInfoRepository.countByRefTableNameAndRefTableIdAndIsVisible(refTableName, refTableId, YnType.y);
	}
	
	public List<FileInfoDTO.FileListRes> getFileInfosWithJoin(String refTableName, Long refTableId) {
		return fileInfoRepository.getFileInfosWithJoin(refTableName, refTableId, YnType.y);
	}
	
	// 기존에 있던 파일을 삭제처리(is_visible = 'n') 처리 후 새로운 파일을 업로드 한다.
	@Transactional
	public void softDeleteAndInsert(
			String refTableName,
			Long id,
			MultipartFile file,
			Long userId
	) {
		
		String dir = String.format("%s/%d/", refTableName, id);
		YnType isVisible = YnType.n;
		LocalDateTime now = LocalDateTime.now();
		
		// 기존이미지 삭제 (soft-delete)
		int resDeleteCnt = fileInfoRepository.softDeleteVisibleByRefAndDir(
				refTableName,
				id,
				dir,
				isVisible,
				now,
				userId
		);
		
		// 새로운 파일을 추가한다.
		saveFiles(refTableName, id, dir, List.of(file), userId);
	}
	
	// 이미지 파일의 경로를 반환한다.
	public String getFilePath(String dir, Long fileInfoId, String extension) {
		
		String endPoint = storageProps.getEndpoint();
		String bucket = storageProps.getBucketName();
		String rootDir = storageProps.getRootDir();
		String filePath = String.format("%s/%s/%s/%s%d.%s", endPoint, bucket,rootDir, dir, fileInfoId, extension);

		return filePath;
	}
	
	
}
