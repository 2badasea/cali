package com.bada.cali.service;

import com.bada.cali.common.enums.ReportType;
import com.bada.cali.config.NcpStorageProperties;
import com.bada.cali.entity.Agent;
import com.bada.cali.entity.CaliOrder;
import com.bada.cali.entity.Env;
import com.bada.cali.entity.Log;
import com.bada.cali.entity.Report;
import com.bada.cali.repository.AgentRepository;
import com.bada.cali.repository.CaliOrderRepository;
import com.bada.cali.repository.EnvRepository;
import com.bada.cali.repository.LogRepository;
import com.bada.cali.repository.ReportRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellReference;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * 엑셀 파일 생성 담당 서비스
 * - 교정신청서 템플릿 다운로드 → 데이터 삽입 → 파일 반환
 * - 임시 디렉토리(UUID) 관리 및 정리
 * - 향후 양식 추가 시(출장보고서, 견적서 등) 메서드 단위로 추가할 것
 *   각 메서드 내부에 해당 양식 전용 상수/포맷터를 지역 변수로 선언하여 관리
 */
@Service
@Log4j2
@RequiredArgsConstructor
public class ExcelServiceImpl {

	private final S3Client ncloudS3Client;
	private final NcpStorageProperties storageProps;
	private final CaliOrderRepository caliOrderRepository;
	private final AgentRepository agentRepository;
	private final ReportRepository reportRepository;
	private final EnvRepository envRepository;
	/** 다운로드 감사 로그 저장용 */
	private final LogRepository logRepository;

	@Value("${app.temp.dir}")
	private String tempBaseDir;

	/**
	 * 교정신청서 엑셀 파일 생성 및 다운로드 로그 기록
	 * - 스토리지에서 템플릿 다운로드 → 데이터 삽입 → 임시 파일 반환
	 * - 엑셀 생성 성공 후 log 테이블에 다운로드 이력 저장
	 * - 호출부에서 사용 후 반드시 cleanupTempDir(path.getParent()) 호출 필요
	 *
	 * @param orderId    교정접수 ID
	 * @param workerName 로그에 기록할 작업자 이름 (CustomUserDetails.getName())
	 * @param memberId   로그에 기록할 작업자 ID (CustomUserDetails.getId())
	 * @param logIp      로그에 기록할 요청 IP (HttpServletRequest.getRemoteAddr())
	 * @return 생성된 엑셀 파일 경로 (temp/{UUID}/order.xlsx)
	 */
	public Path buildOrderExcel(Long orderId, String workerName, Long memberId, String logIp) throws IOException {

		// ── 교정신청서 템플릿 레이아웃 상수 ─────────────────────────────────────────
		// 다른 양식 추가 시 해당 메서드 안에 별도로 선언할 것 (클래스 레벨 공유 금지)
		final int page1Start   = 7;   // page1 시작 행 index (0-based) → 시트 행 8
		final int page1Count   = 10;  // page1 최대 수용 건수
		final int page2Start   = 24;  // page2 시작 행 index (0-based) → 시트 행 25
		final int page2Count   = 22;  // page2 최대 수용 건수
		final int maxBaseRows  = page1Count + page2Count;        // 기본 최대 수용 건수 = 32
		final int page2LastIdx = page2Start + page2Count - 1;    // page2 마지막 행 index = 45
		final DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("yyyy. MM. dd"); // 신청일 포맷
		// ────────────────────────────────────────────────────────────────────────────

		// 1. 접수 조회
		CaliOrder order = caliOrderRepository.findById(orderId)
				.orElseThrow(() -> new EntityNotFoundException("교정접수 데이터를 찾을 수 없습니다. id=" + orderId));

		// 2. 업체 조회 (custAgentId 존재 시 agent 테이블에서 가져옴)
		Agent agent = null;
		if (order.getCustAgentId() != null && order.getCustAgentId() > 0) {
			agent = agentRepository.findById(order.getCustAgentId()).orElse(null);
		}

		// 3. 성적서 목록 조회 (재발행 제외, reportNum 오름차순)
		List<Report> reports = reportRepository.findReportsForOrderDownload(orderId);

		// 4. UUID 임시 디렉토리 생성
		Path baseDir = Paths.get(tempBaseDir).toAbsolutePath();
		Path uuidDir = baseDir.resolve(UUID.randomUUID().toString());
		Files.createDirectories(uuidDir);

		try {
			// 5. 템플릿 다운로드 (스토리지 → temp/UUID/order.xlsx)
			String templateKey = storageProps.getRootDir() + "/env/order.xlsx";
			Path templatePath = downloadFromStorage(templateKey, uuidDir, "order.xlsx");
			log.debug("[교정신청서] 템플릿 다운로드 완료: {}", templatePath);

			// 6. 회사 로고 다운로드 (optional)
			Path logoPath = null;
			Env env = envRepository.findById((byte) 1).orElse(null);
			if (env != null && StringUtils.hasText(env.getCompany())) {
				String ext = extractExtension(env.getCompany());
				// rootDir를 붙여 실제 스토리지 경로로 변환 (env.company 값은 "env/company.ico" 형태로 저장됨)
				String logoKey = storageProps.getRootDir() + "/" + env.getCompany();
				try {
					logoPath = downloadFromStorage(logoKey, uuidDir, "logo" + ext);
					log.debug("[교정신청서] 로고 다운로드 완료: {}", logoPath);
				} catch (Exception e) {
					log.warn("[교정신청서] 회사 로고 다운로드 실패 - 로고 삽입 생략: {}", e.getMessage());
				}
			}

			// 7. 엑셀 파일 열기 및 데이터 삽입
			try (FileInputStream fis = new FileInputStream(templatePath.toFile());
				 XSSFWorkbook workbook = new XSSFWorkbook(fis)) {

				Sheet sheet = workbook.getSheetAt(0);

				// 8. 성적서 기본 최대 건수(32건) 초과 시 행 추가 (데이터 삽입 전 처리)
				if (reports.size() > maxBaseRows) {
					int extraCount = reports.size() - maxBaseRows;
					log.debug("[교정신청서] 성적서 {}건 초과 → 행 {}개 추가", maxBaseRows, extraCount);
					expandListRows(sheet, extraCount, page2LastIdx);
				}

				// 9. 상단 정보 삽입
				insertHeader(sheet, order, agent, dateFormatter);

				// 10. 성적서 리스트 삽입
				insertReportList(sheet, reports, page1Start, page1Count, page2Start);

				// 11. 회사 로고 삽입
				if (logoPath != null) {
					insertLogo(workbook, sheet, logoPath);
				}

				// 12. 완성된 워크북 저장
				try (FileOutputStream fos = new FileOutputStream(templatePath.toFile())) {
					workbook.write(fos);
				}
			}

			// 13. 다운로드 로그 기록 (엑셀 생성 성공 후에만 저장)
			logRepository.save(Log.builder()
					.logIp(logIp)
					.workerName(workerName)
					.logContent("교정신청서 다운로드 - 접수 id: " + orderId)
					.logType("download")
					.refTable("cali_order")
					.refTableId(orderId)
					.createDatetime(LocalDateTime.now())
					.createMemberId(memberId)
					.build());

			return templatePath;

		} catch (Exception e) {
			// 오류 발생 시 temp 디렉토리 정리 후 재던짐
			cleanupTempDir(uuidDir);
			throw e;
		}
	}

	/**
	 * temp/UUID 디렉토리 및 하위 파일 삭제 (안전 경로 검증 포함)
	 *
	 * @param dir 삭제할 UUID 디렉토리
	 */
	public void cleanupTempDir(Path dir) {
		if (dir == null) return;

		Path base = Paths.get(tempBaseDir).toAbsolutePath().normalize();
		Path target = dir.toAbsolutePath().normalize();

		// 안전 검증: 기준 temp 경로 하위이고, temp 루트 자체가 아닌 경우만 삭제
		if (!target.startsWith(base) || target.equals(base)) {
			log.warn("[temp 정리] 안전 경로 외 삭제 시도 차단: {}", target);
			return;
		}

		try (Stream<Path> files = Files.walk(dir)) {
			files.sorted(Comparator.reverseOrder())
				 .forEach(p -> {
					 try {
						 Files.delete(p);
					 } catch (IOException e) {
						 log.warn("[temp 정리] 파일 삭제 실패: {}", p, e);
					 }
				 });
			log.debug("[temp 정리] 디렉토리 정리 완료: {}", dir);
		} catch (IOException e) {
			log.warn("[temp 정리] 디렉토리 정리 실패: {}", dir, e);
		}
	}

	// ── Private helpers ───────────────────────────────────────────────────────

	/** 스토리지에서 파일 다운로드 → targetDir/fileName 으로 저장 */
	private Path downloadFromStorage(String objectKey, Path targetDir, String fileName) throws IOException {
		GetObjectRequest req = GetObjectRequest.builder()
				.bucket(storageProps.getBucketName())
				.key(objectKey)
				.build();

		Path dest = targetDir.resolve(fileName);
		try (ResponseInputStream<GetObjectResponse> s3is = ncloudS3Client.getObject(req)) {
			Files.copy(s3is, dest, StandardCopyOption.REPLACE_EXISTING);
		}
		return dest;
	}

	/**
	 * 성적서 최대 건수 초과 시 리스트 마지막 행 뒤에 행 추가
	 * - shiftRows로 하위 행 밀어내기 → 마지막 리스트 행의 스타일을 신규 행에 복사
	 *
	 * @param sheet        대상 시트
	 * @param extraCount   추가할 행 수
	 * @param page2LastIdx 리스트 마지막 행 index (0-based) — 양식마다 다름
	 */
	private void expandListRows(Sheet sheet, int extraCount, int page2LastIdx) {
		int lastListRowIdx = page2LastIdx;
		int lastSheetRowNum = sheet.getLastRowNum();

		// lastListRowIdx 다음 행부터 sheetEnd 까지 extraCount만큼 밀어내기
		if (lastListRowIdx + 1 <= lastSheetRowNum) {
			sheet.shiftRows(lastListRowIdx + 1, lastSheetRowNum, extraCount, true, false);
		}

		// 마지막 리스트 행의 스타일을 신규 행에 복사
		Row templateRow = sheet.getRow(lastListRowIdx);
		for (int i = 1; i <= extraCount; i++) {
			Row newRow = sheet.createRow(lastListRowIdx + i);
			if (templateRow != null) {
				newRow.setHeight(templateRow.getHeight());
				int lastCellNum = templateRow.getLastCellNum();
				for (int c = 0; c < lastCellNum; c++) {
					Cell srcCell = templateRow.getCell(c);
					Cell newCell = newRow.createCell(c);
					if (srcCell != null) {
						newCell.setCellStyle(srcCell.getCellStyle());
					}
				}
			}
		}
	}

	/**
	 * 상단 정보 삽입
	 * - agent != null : agent 테이블 데이터 사용
	 * - agent == null : CaliOrder 직접 저장 필드 사용
	 * - 알 수 없는 값은 빈 값으로 유지 (임의값 삽입 금지)
	 *
	 * @param sheet         대상 시트
	 * @param order         교정접수 엔티티
	 * @param agent         업체 엔티티 (없으면 null)
	 * @param dateFormatter 신청일 포맷터 — 양식마다 포맷이 다를 수 있으므로 파라미터로 수신
	 */
	private void insertHeader(Sheet sheet, CaliOrder order, Agent agent, DateTimeFormatter dateFormatter) {
		String companyName = agent != null ? agent.getName()        : order.getCustAgent();
		String addr        = agent != null ? agent.getAddr()         : order.getCustAgentAddr();
		String tel         = agent != null ? agent.getAgentTel()     : order.getCustAgentTel();
		String hp          = agent != null ? agent.getPhone()        : null;
		String ceo         = agent != null ? agent.getCeo()          : null;
		String applicant   = agent != null ? agent.getManager()      : order.getCustManager();
		String fax         = agent != null ? agent.getFax()          : order.getCustAgentFax();
		String email       = agent != null ? agent.getManagerEmail() : order.getCustManagerEmail();

		// 행 3 (0-indexed: 2) — 회사명 / 대표자 / 신청일
		setCellValue(sheet, 2, "W",  companyName);
		setCellValue(sheet, 2, "CD", ceo);
		setCellValue(sheet, 2, "DH", LocalDate.now().format(dateFormatter));

		// 행 4 (0-indexed: 3) — 주소 / 신청인
		setCellValue(sheet, 3, "W",  addr);
		setCellValue(sheet, 3, "CD", applicant);

		// 행 5 (0-indexed: 4) — 전화 / 휴대폰 / 팩스 / Email
		setCellValue(sheet, 4, "W",  tel);
		setCellValue(sheet, 4, "AZ", hp);
		setCellValue(sheet, 4, "CD", fax);
		setCellValue(sheet, 4, "DH", email);

		// 행 6 (0-indexed: 5) — 성적서발급처 / 성적서발급처주소
		setCellValue(sheet, 5, "W",  order.getReportAgent());
		setCellValue(sheet, 5, "BN", order.getReportAgentAddr());
	}

	/**
	 * 성적서 리스트 삽입 (NO, 품명, 형식, 기기번호, 제조회사, 비고, 자산번호)
	 *
	 * @param sheet      대상 시트
	 * @param reports    삽입할 성적서 목록
	 * @param page1Start page1 시작 행 index (0-based)
	 * @param page1Count page1 최대 수용 건수
	 * @param page2Start page2 시작 행 index (0-based)
	 */
	private void insertReportList(Sheet sheet, List<Report> reports, int page1Start, int page1Count, int page2Start) {
		for (int i = 0; i < reports.size(); i++) {
			Report r = reports.get(i);
			int rowIdx = getListRowIndex(i, page1Start, page1Count, page2Start);

			// AGCY 성적서는 자체대행성적서번호를 자산번호로 사용
			String assetNum = (r.getReportType() == ReportType.AGCY)
					? r.getAgcySelfReportNum()
					: r.getManageNo();

			setCellValue(sheet, rowIdx, "A",  String.valueOf(i + 1)); // NO
			setCellValue(sheet, rowIdx, "E",  r.getItemName());       // 품명
			setCellValue(sheet, rowIdx, "AI", r.getItemFormat());     // 형식
			setCellValue(sheet, rowIdx, "BH", r.getItemNum());        // 기기번호
			setCellValue(sheet, rowIdx, "CE", r.getItemMakeAgent());  // 제조회사
			setCellValue(sheet, rowIdx, "CY", r.getRemark());         // 비고
			setCellValue(sheet, rowIdx, "DW", assetNum);              // 자산번호 (AGCY: 자체대행성적서번호, SELF: 관리번호)
		}
	}

	/**
	 * 아이템 인덱스(0-based) → 시트 행 인덱스(0-based) 변환
	 * - page1 범위를 초과하면 page2 영역으로 매핑
	 *
	 * @param itemIndex  성적서 목록 내 순번 (0-based)
	 * @param page1Start page1 시작 행 index
	 * @param page1Count page1 최대 수용 건수
	 * @param page2Start page2 시작 행 index
	 */
	private int getListRowIndex(int itemIndex, int page1Start, int page1Count, int page2Start) {
		if (itemIndex < page1Count) {
			return page1Start + itemIndex; // page1 영역 (행 8~17)
		}
		return page2Start + (itemIndex - page1Count); // page2 영역 (행 25~)
	}

	/**
	 * 회사 로고 삽입 (좌측 상단)
	 * - PNG / JPEG 형식만 삽입, 그 외 형식은 경고 로그 후 생략
	 */
	private void insertLogo(XSSFWorkbook workbook, Sheet sheet, Path logoPath) {
		String lowerName = logoPath.getFileName().toString().toLowerCase();
		int pictureType;
		if (lowerName.endsWith(".png")) {
			pictureType = Workbook.PICTURE_TYPE_PNG;
		} else if (lowerName.endsWith(".jpg") || lowerName.endsWith(".jpeg")) {
			pictureType = Workbook.PICTURE_TYPE_JPEG;
		} else {
			log.warn("[교정신청서] 로고 형식 미지원(삽입 생략): {}", lowerName);
			return;
		}

		try {
			byte[] imageBytes = Files.readAllBytes(logoPath);
			int pictureIndex = workbook.addPicture(imageBytes, pictureType);

			Drawing<?> drawing = sheet.createDrawingPatriarch();
			CreationHelper helper = workbook.getCreationHelper();
			ClientAnchor anchor = helper.createClientAnchor();

			// A1 셀 좌측 상단 고정, 50x50px 크기 (1px = 9525 EMU, 96dpi 기준)
			anchor.setCol1(0);
			anchor.setRow1(0);
			anchor.setDx1(0);
			anchor.setDy1(0);
			anchor.setCol2(0);
			anchor.setRow2(0);
			anchor.setDx2(476250);  // 50px
			anchor.setDy2(476250);  // 50px
			anchor.setAnchorType(ClientAnchor.AnchorType.DONT_MOVE_AND_RESIZE);

			drawing.createPicture(anchor, pictureIndex);
			log.debug("[교정신청서] 로고 삽입 완료");
		} catch (IOException e) {
			log.warn("[교정신청서] 로고 삽입 실패 (생략): {}", e.getMessage());
		}
	}

	/** 셀에 값 설정 (null 또는 공백이면 설정하지 않음) */
	private void setCellValue(Sheet sheet, int rowIdx, String colLetter, String value) {
		if (!StringUtils.hasText(value)) return;
		int colIdx = CellReference.convertColStringToIndex(colLetter);
		Row row = sheet.getRow(rowIdx);
		if (row == null) row = sheet.createRow(rowIdx);
		Cell cell = row.getCell(colIdx);
		if (cell == null) cell = row.createCell(colIdx);
		cell.setCellValue(value);
	}

	/** 파일 경로에서 확장자 추출 (예: ".png", ".jpg") */
	private String extractExtension(String path) {
		int dot = path.lastIndexOf('.');
		return dot >= 0 ? path.substring(dot) : "";
	}
}
