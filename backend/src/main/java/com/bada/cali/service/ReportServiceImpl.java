package com.bada.cali.service;

import com.bada.cali.common.ResMessage;
import com.bada.cali.common.enums.*;
import com.bada.cali.dto.AgentCaliHistoryDTO;
import com.bada.cali.dto.EquipmentDTO;
import com.bada.cali.dto.ItemDTO;
import com.bada.cali.dto.ReportDTO;
import com.bada.cali.dto.ReportPrintDTO;
import com.bada.cali.dto.TuiGridDTO;
import com.bada.cali.entity.*;
import com.bada.cali.mapper.ReportMapper;
import com.bada.cali.mapper.StandardEquipmentRefMapper;
import com.bada.cali.repository.AgentRepository;
import com.bada.cali.repository.CaliOrderRepository;
import com.bada.cali.repository.EquipmentRefRepository;
import com.bada.cali.repository.FileInfoRepository;
import com.bada.cali.repository.LogRepository;
import com.bada.cali.repository.MemberRepository;
import com.bada.cali.repository.ReportRepository;
import com.bada.cali.repository.projection.AgentCaliHistoryListRow;
import com.bada.cali.repository.projection.OrderDetailsList;
import com.bada.cali.repository.projection.ReportPrintListRow;
import com.bada.cali.repository.projection.WorkApprovalListRow;
import com.bada.cali.security.CustomUserDetails;
import jakarta.persistence.EntityNotFoundException;
import lombok.AllArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Log4j2
@AllArgsConstructor
public class ReportServiceImpl {
	
	private final ReportRepository reportRepository;
	private final CaliOrderRepository caliOrderRepository;
	private final LogRepository logRepository;
	private final ReportMapper reportMapper;
	private final ItemServiceImpl itemService;
	private final EquipmentRefRepository equipmentRefRepository;
	private final StandardEquipmentRefMapper standardEquipmentRefMapper;
	private final NumberSequenceService numberSequenceService;
	private final FileInfoRepository fileInfoRepository;
	private final MemberRepository memberRepository;
	private final AgentRepository agentRepository;
	
	/**
	 * 성적서 등록
	 *
	 * @param reports
	 * @param caliOrderId
	 * @param user
	 * @return
	 */
	@Transactional
	public ResMessage<Object> addReport(List<ReportDTO.addReportReq> reports, Long caliOrderId, CustomUserDetails user) {
		
		int resCode = 0;
		String resMessage;
		
		LocalDateTime now = LocalDateTime.now();
		String workerName = user.getName();
		Long workerId = user.getId();
		
		// 접수정보
		CaliOrder orderInfo = caliOrderRepository.findById(caliOrderId).orElseThrow(() -> new EntityNotFoundException("접수정보를 알 수 없습니다."));
		ReportLang orderReportLang = orderInfo.getReportLang();    // 접수의 발행타입(KR, EN, BOTH)
		String orderNum = orderInfo.getOrderNum();        // 접수번호
		int orderYear = orderInfo.getOrderDate().getYear();    // 접수일 연도 (관리번호 조회용)
		PriorityType priorityType = orderInfo.getPriorityType();    // 우선순위
		CaliType caliType = orderInfo.getCaliType();                // 교정유형
		CaliTakeType caliTakeType = orderInfo.getCaliTakeType();    // 교정상세유형
		
		// 접수구분별 부모 성적서 건수 집계
		Map<OrderType, Integer> countByType = new EnumMap<>(OrderType.class);
		for (ReportDTO.addReportReq dto : reports) {
			countByType.merge(dto.getOrderType(), 1, Integer::sum);
		}

		// 접수구분별 시퀀스 범위 일괄 예약 (SELECT FOR UPDATE)
		// reserve(seqKey, count) → 반환값 ~ 반환값+count-1 범위가 이 트랜잭션에 독점 할당됨
		Map<OrderType, Integer> reportStartNums = new EnumMap<>(OrderType.class);
		Map<OrderType, Integer> manageStartNums = new EnumMap<>(OrderType.class);
		for (Map.Entry<OrderType, Integer> entry : countByType.entrySet()) {
			OrderType type = entry.getKey();
			int count = entry.getValue();
			reportStartNums.put(type, numberSequenceService.reserve(
					"report_" + caliOrderId + "_" + type.name(), count));
			manageStartNums.put(type, numberSequenceService.reserve(
					"manage_" + type.name() + "_" + orderYear, count));
		}

		// 접수구분별 배분 오프셋 (루프 내에서 0부터 순서대로 증가)
		Map<OrderType, Integer> reportOffsets = new EnumMap<>(OrderType.class);
		Map<OrderType, Integer> manageOffsets = new EnumMap<>(OrderType.class);
		
		// 자식성적서들의 경우, list에 담았다가 부모가 저장되고 나면 일괄적으로 저장한다.
		List<Report> childrenToSave = new ArrayList<>();
		
		// 성적서번호 prefix
		Map<OrderType, String> reportNumPrefix = Map.of(OrderType.ACCREDDIT, "", OrderType.UNACCREDDIT, "B", OrderType.TESTING, "T");
		
		String manageNoPrefix = String.valueOf(orderYear).substring(2);
		
		// 반복문 진행
		for (ReportDTO.addReportReq dto : reports) {
			OrderType orderType = dto.getOrderType();        //
			
			String prefix = reportNumPrefix.get(orderType);    // 접수구분별 prefix "", "B", "T"

			// 예약된 범위에서 순서대로 배분 (startNum + offset)
			int reportOffset = reportOffsets.getOrDefault(orderType, 0);
			reportOffsets.put(orderType, reportOffset + 1);
			int reportNumSeq = reportStartNums.get(orderType) + reportOffset;

			int manageOffset = manageOffsets.getOrDefault(orderType, 0);
			manageOffsets.put(orderType, manageOffset + 1);
			int manageNoSeq = manageStartNums.get(orderType) + manageOffset;
			
			String reportNum = orderNum + "-" + prefix + String.format("%04d", reportNumSeq);    // 성적서번호
			String manageNo = "BD" + manageNoPrefix + "-" + prefix + String.format("%05d", manageNoSeq);    // 관리번호
			
			// 교정주기가 없는 경우 기본 12 주기
			Integer itemCaliCycle = dto.getItemCaliCycle();
			if (itemCaliCycle == null || itemCaliCycle == 0) {
				itemCaliCycle = 12;
			}
			
			// dto -> entity 변환하기 전 itemId를 확인한다. id가 null이거나 0인 경우, 품목조회 후 자동삽입
			Long parentItemId = dto.getItemId();
			if (parentItemId == null || parentItemId == 0) {
				
				ItemDTO.ItemCheckData itemCheckData = new ItemDTO.ItemCheckData(dto.getItemName(), dto.getItemMakeAgent(), dto.getItemFormat());
				ItemDTO.FindOrInsertItemParams findOrInsertItemParams = new ItemDTO.FindOrInsertItemParams(itemCheckData, dto.getCaliFee(), dto.getMiddleItemCodeId(), dto.getSmallItemCodeId(), dto.getItemCaliCycle());
				
				Item getItemEntity = itemService.findOrInsertItem(findOrInsertItemParams, user);
				parentItemId = getItemEntity.getId();
			}
			
			
			// entity 변환 (mapstruct)
			Report reportEntity = reportMapper.toEntity(dto);
			reportEntity.setItemId(parentItemId);
			reportEntity.setCreateDatetime(now);
			reportEntity.setCreateMemberId(workerId);
			reportEntity.setReportNum(reportNum);
			reportEntity.setManageNo(manageNo);
			reportEntity.setCaliOrderId(caliOrderId);
			reportEntity.setReportLang(orderReportLang);    // 발행타입은 기본적으로 접수를 따라간다
			reportEntity.setItemCaliCycle(itemCaliCycle);        // 교정주기
			reportEntity.setIsVisible(YnType.y);
			reportEntity.setPriorityType(priorityType);        // 긴급여부(접수정보)
			reportEntity.setCaliType(caliType);                // 접수유형(접수정보)
			reportEntity.setCaliTakeType(caliTakeType);        // 접수상세유형(접수정보)
			
			// 저장
			Report savedReport = reportRepository.save(reportEntity);
			Long parentId = savedReport.getId();        // 생성된 부모 id
			
			// 이력을 남긴다.
			Log saveLog = Log.builder()
					.createDatetime(now)
					.createMemberId(workerId)
					.workerName(workerName)
					.refTable("report")
					.refTableId(parentId)
					.logType("i")
					.logContent(String.format("[성적서 등록] 성적서 번호: %s - 고유번호: %d", reportNum, parentId))
					.build();
			logRepository.save(saveLog);
			
			// 자식 데이터가 존재하는지 확인
			if (dto.getChild() != null && !dto.getChild().isEmpty()) {
				// 자식 데이터도 반복
				for (ReportDTO.addReportReq c : dto.getChild()) {
					
					Integer childCaliCycle = c.getItemCaliCycle();
					if (childCaliCycle == null || childCaliCycle == 0) {
						childCaliCycle = 12;
					}
					
					// 자식도 마찬가지로 품목의 중복여부를 판단한다.
					Long childItemId = c.getItemId();
					if (childItemId == null || childItemId == 0) {
						ItemDTO.ItemCheckData childItemCheckData = new ItemDTO.ItemCheckData(c.getItemName(), c.getItemMakeAgent(), c.getItemFormat());
						ItemDTO.FindOrInsertItemParams childFindOrInsertItemParams = new ItemDTO.FindOrInsertItemParams(childItemCheckData, c.getCaliFee(), c.getMiddleItemCodeId(), c.getSmallItemCodeId(), c.getItemCaliCycle());
						
						Item getItemEntity = itemService.findOrInsertItem(childFindOrInsertItemParams, user);
						childItemId = getItemEntity.getId();
					}
					
					Report childEntity = reportMapper.toEntity(c);
					// 자식성적서의 경우, 성적서번호와 관리번호는 존재하지 않는다. (NULL 허용)
					childEntity.setCaliOrderId(caliOrderId);
					childEntity.setItemId(childItemId);
					childEntity.setReportLang(orderReportLang);    // 자식성적서도 접수 건의 발행타입으로 초기화
					childEntity.setCreateDatetime(now);
					childEntity.setCreateMemberId(workerId);
					childEntity.setIsVisible(YnType.y);
					childEntity.setParentScaleId(parentId);
					childEntity.setItemCaliCycle(childCaliCycle);
					
					// 리스트에 담는다. 일괄 저장 위해
					childrenToSave.add(childEntity);
				}
			}
			
			// 자식이 존재할 경우, 일괄적으로 저장 후 이력 기록
			if (!childrenToSave.isEmpty()) {
				List<Report> savedChildren = reportRepository.saveAll(childrenToSave);

				// 자식성적서는 성적서번호/관리번호가 없으므로 부모 성적서번호와 자식 ID를 묶어서 기록
				for (Report child : savedChildren) {
					Log childLog = Log.builder()
							.createDatetime(now)
							.createMemberId(workerId)
							.workerName(workerName)
							.refTable("report")
							.refTableId(child.getId())
							.logType("i")
							.logContent(String.format("[자식성적서 등록] 부모 성적서번호: %s - 고유번호: %d", child.getParentScaleId(), child.getId()))
							.build();
					logRepository.save(childLog);
				}
			}
		}
		
		resCode = 1;
		resMessage = "";
		return new ResMessage<>(resCode, resMessage, null);
	}
	
	
	// 접수상세내역에 표시할 데이터를 가져온다.
	@Transactional(readOnly = true)
	public TuiGridDTO.ResData<OrderDetailsList> getOrderDetailsList(ReportDTO.GetOrderDetailsReq request) {
		// 페이징 옵션
		int pageIndex = request.getPage() - 1;
		int perPage = request.getPerPage();
		
		// 페이징 객체
		Pageable pageable = PageRequest.of(pageIndex, perPage);
		
		// 1. 접수구분, 2. 진행상태, 3. 검색타입, 4. 검색키워드 세팅
		// 1. 접수구분 (전체선택일 경우 null로 바인딩 됨
		OrderType orderType = request.getOrderType();    // 전체선택인 경우 null로 받게됨
		
		// 2. 진행상태 (Enum 타입으로 구분하지 않은 건, 성적서 관련 페이지별로 진행상태 option종류와 각 option별 해당하는 조건이 다르기 때문)
		String statusType = request.getStatusType();
		statusType = (statusType == null || statusType.isBlank()) ? null : statusType;
		
		Long middleItemCodeId = request.getMiddleItemCodeId();
		if (middleItemCodeId != null && middleItemCodeId == 0L) {
			middleItemCodeId = null;
		}
		Long smallItemCodeId = request.getSmallItemCodeId();
		if (smallItemCodeId != null && smallItemCodeId == 0L) {
			smallItemCodeId = null;
		}
		
		// 3. 검색타입
		String searchType = request.getSearchType();    // 전체선택은 all
		if (searchType == null || searchType.isBlank()) {
			searchType = "all";
		}

		String keyword = request.getKeyword();
		// 키워드가 혹시 null로 넘어온 경우 빈값으로 취급하여 where절을 타지 않도록 한다.
		keyword = (keyword == null) ? "" : keyword.trim();
		// 접수id
		Long caliOrderId = request.getCaliOrderId();

		// 4. 성적서타입 필터 (SELF | AGCY | null=전체)
		String reportTypeStr = request.getReportType();
		ReportType reportType = (reportTypeStr == null || reportTypeStr.isBlank()) ? null : ReportType.valueOf(reportTypeStr);

		// 프로젝션(인터페이스)를 통해서 바로 dto로 넘겨줄 데이터를 받기 때문에, Page<> 타입으로 받지 않음

		List<OrderDetailsList> pageResult = reportRepository.searchOrderDetails(reportType, orderType, statusType, searchType, keyword, caliOrderId, middleItemCodeId, smallItemCodeId, pageable);

		// NOTE 프로젝션 타입으로 바로 받기 때문에 entity -> dto 변환 과정은 생략

		// 전체 건수 조회 (서버 페이징 totalCount 제공용 — 동일 WHERE 조건으로 별도 count 쿼리 실행)
		long totalCount = reportRepository.countOrderDetails(reportType, orderType, statusType, searchType, keyword, caliOrderId, middleItemCodeId, smallItemCodeId);

		// 페이지네이션 데이터 세팅
		TuiGridDTO.Pagination pagination = TuiGridDTO.Pagination.builder()
				.page(request.getPage())
				.totalCount((int) totalCount)
				.build();
		
		return TuiGridDTO.ResData.<OrderDetailsList>builder()
				.contents(pageResult)
				.pagination(pagination)
				.build();
	}
	
	// 대상 성적서들이 삭제대상으로 적합한지 확인
	@Transactional(readOnly = true)
	public ResMessage<?> isValidDelete(ReportDTO.ValidateDeleteReq request) {
		
		int code = 0;
		String message;
		
		// TODO 완료통보서, 완료통보서 품목 테이블 생성 후에 참조하고 있는 성적서id가 존재하는지 체크
		// 접수 id가 존재하는 경우, 생성된 완료통보서가 존재하는 경우 리턴
		Long caliOrderId = request.caliOrderId();
		if (caliOrderId != null && caliOrderId > 0) {
			CaliOrder orderInfo = caliOrderRepository.findById(caliOrderId).orElseThrow(() -> new EntityNotFoundException("해당 접수 건이 존재하지 않습니다."));
			// Long completionId = orderInfo.getCompletionId();
			// if (completionId != null && completionId > 0) {
			// 	code = -1;
			// }
		}
		
		// 타입별 반복문을 돌리면서 확인
		Map<String, List<Long>> validateInfo = request.validateInfo();    // record 타입이기 때문에 getXX() 아님
		if (validateInfo != null && !validateInfo.isEmpty()) {
			// map의 key를 기준으로 순회 ('ACCREDDIT' || 'UNACCREDDIT' || 'TESTING' || 'AGCY')
			for (String orderTypeStr : validateInfo.keySet()) {
				List<Long> reportIds = validateInfo.get(orderTypeStr);    // 접수타입별 id 가져오기
				
				OrderType orderType;
				ReportType reportType;
				boolean isAgcy;
				// 대행인 경우 구분
				if ("AGCY".equals(orderTypeStr)) {
					reportType = ReportType.AGCY;
					isAgcy = true;
					orderType = null;        // 대행은 접수타입을 구분하지 않는다.
				} else {
					isAgcy = false;
					reportType = ReportType.SELF;
					orderType = OrderType.valueOf(orderTypeStr);
				}
				
				// 대행의 경우엔 limit을 두지 않음. (완료유무만 판단하고, 빈 성적서번호를 허용하기 때문 - 자체성적서번호이기 때문)
				Pageable pageable = (reportType == ReportType.AGCY) ? Pageable.unpaged() : PageRequest.of(0, reportIds.size());
				
				// 대행을 제외하곤 접수타입별 id개수만큼 최신순의 성적서를 가져와서 비교한다.
				List<Report> reportList = reportRepository.getDeleteCheckReport(orderType, isAgcy, reportIds, pageable);
				
				// 자체/대행 분리 검증
				if (reportType == ReportType.AGCY) {
					// 대행의 경우, 기술책임자 결재 완료 여부만 확인한다.
					for (Report report : reportList) {
						if (report.getApprovalStatus() == AppStatus.SUCCESS) {
							code = -1;
							message = String.format("결재가 완료된 대행성적서는 삭제가 불가능합니다. (성적서번호: %s)", report.getReportNum());
							return new ResMessage<>(code, message, null);
						}
					}
					
				} else {
					// 자체성적서의 경우, 우선 id가 일치하는지 확인 후, 일치하지 않으면 return 일치하면 결재상태 점검
					List<Long> listIds = new ArrayList<>();
					for (Report report : reportList) {
						listIds.add(report.getId());    // 넘어온 id와 비교하기 위해 담는다.
						// 결재가 진행중인 게 한 건이라도 존재하면 return
						if (report.getWorkDatetime() != null || report.getApprovalDatetime() != null || report.getWorkStatus() != AppStatus.IDLE || report.getApprovalStatus() != AppStatus.IDLE) {
							code = -1;
							message = String.format("결재가 진행중인 성적서가 존재합니다. (성적서번호: %s)", report.getReportNum());
							return new ResMessage<>(code, message, null);
						}
					}
					
					Set<Long> getIds = new HashSet<>(listIds);
					Set<Long> paramIds = new HashSet<>(reportIds);
					// 일치여부 확인
					boolean isSame = getIds.size() == paramIds.size() && getIds.containsAll(paramIds);
					// 일치하지 않으면,
					if (!isSame) {
						code = -1;
						message = "각 접수구분의 마지막 성적서만 삭제가 가능합니다.";
						return new ResMessage<>(code, message, null);
					}
				}
			}
			
		} else {
			code = -1;
			message = "삭제할 정보가 올바르지 않습니다.";
			return new ResMessage<>(code, message, null);
		}
		
		// 여기까지 왔다면, 삭제하는 데 문제가 없다는 의미
		code = 1;
		message = "선택한 성적서들을 삭제하시겠습니까?";
		return new ResMessage<>(code, message, null);
	}
	
	/**
	 * 성적서 삭제 요청
	 *
	 * @param request ('deleteIds' 라는 key로 List<Long> 타입의 데이터를 받는다.
	 * @param user
	 * @return
	 */
	@Transactional
	public ResMessage<?> deleteReport(ReportDTO.DeleteReportReq request, CustomUserDetails user) {
		LocalDateTime now = LocalDateTime.now();
		String workerName = user.getName();
		Long userId = user.getId();
		List<Long> deleteIds = request.deleteIds();
		
		int code = 0;
		String msg = "";
		
		
		if (deleteIds != null && !deleteIds.isEmpty()) {
			// 대행, 자식성적서, 접수구분 상관없이 모두 가져옴
			List<Report> reportList = reportRepository.findByIdInOrParentIdInOrParentScaleIdIn(deleteIds, deleteIds, deleteIds);
			
			// 삭제된 성적서 번호
			List<String> deleteReportNums = new ArrayList<>();
			
			for (Report report : reportList) {
				
				// 자식성적서는 성적서번호, 관리번호 업데이트가 없음
				if ((report.getParentId() != null && report.getParentId() > 0) || (report.getParentScaleId() != null && report.getParentScaleId() > 0)) {
					report.setIsVisible(YnType.n);
					report.setDeleteDatetime(now);
					report.setDeleteMemberId(userId);
				}
				// 그외 부모성적서, 대행성적서의 경우엔 '성적서번호 + 'deleted' + uuid 형태로 업데이트
				else {
					String uuid = UUID.randomUUID().toString();    //
					String originReportNum = report.getReportNum();
					String originManageNo = report.getManageNo();
					String suffix = String.format("[deleted-%s", uuid);
					String newReportNum = originReportNum + suffix;
					String newManageNo = originManageNo + suffix;
					
					// dirty checking 방식으로 영속성컨텍스트로 들어온 성적서들에 대해 모두 set을 통해 update
					report.setReportNum(newReportNum);
					report.setManageNo(newManageNo);
					report.setIsVisible(YnType.n);
					report.setDeleteDatetime(now);
					report.setDeleteMemberId(userId);
					
					deleteReportNums.add(String.format("[성적서 삭제] 성적서번호: %s, - 고유 ID: %d)", originReportNum, report.getId()));
				}
			}
			
			// 로그를 남긴다.
			if (!deleteReportNums.isEmpty()) {
				String logContent = String.join(", ", deleteReportNums);
				Log deleteLog = Log.builder()
						.logType("d")
						.logContent(logContent)
						.workerName(workerName)
						.createDatetime(now)
						.createMemberId(userId)
						.refTable("report")
						.refTableId(deleteIds.get(0))	// 다건 삭제이므로 첫 번째 ID를 대표값으로 사용 (상세 목록은 logContent에 포함)
						.build();
				// 이력을 저장한다.
				logRepository.save(deleteLog);
				
				code = 1;
				msg = String.format("성적서 %d건이 삭제되었습니다.", deleteReportNums.size());
				
			} else {
				code = -1;
				msg = "성적서 삭제 중 문제가 발생했씁니다.";
			}
			
		} else {
			code = -1;
			msg = "삭제대상 성적서를 찾을 수 없습니다.";
		}
		return new ResMessage<>(code, msg, null);
	}
	
	/**
	 * 개별 성적서 조회
	 *
	 * @param id
	 * @return
	 */
	@Transactional(readOnly = true)
	public ReportDTO.ReportInfoRes getReportInfo(Long id) {
		
		ReportDTO.ReportInfo reportInfo = reportRepository.getReportInfo(id);
		// 자식 성적서를 조회한다.
		List<ReportDTO.ChildReportInfo> childReportInfos = reportRepository.getChildReport(id);
		return new ReportDTO.ReportInfoRes(
				reportInfo,
				childReportInfos
		);
		
	}
	
	/**
	 * 성적서 삭제 요청 (is_visible = 'n' 처리)
	 *
	 * @param id
	 * @param user
	 * @return
	 */
	@Transactional
	public ResMessage<Object> deleteById(Long id, CustomUserDetails user) {
		int resCode = 0;
		String resMsg;
		
		
		Report deleteTargetRepost = reportRepository.findById(id).orElseThrow(() -> new EntityNotFoundException("해당 성적서가 존재하지 않습니다."));
		
		String reportNum = (deleteTargetRepost.getReportNum() == null) ? "[성적서번호 없음]" : deleteTargetRepost.getReportNum();
		
		LocalDateTime now = LocalDateTime.now();
		Long userId = user.getId();
		String workerName = user.getName();    // log에 넣을 데이터
		
		// dirty checking으로 영속성 컨텍스트 선에서 삭제처리
		deleteTargetRepost.setIsVisible(YnType.n);
		deleteTargetRepost.setDeleteDatetime(now);
		deleteTargetRepost.setDeleteMemberId(userId);
		
		String logContent = String.format("[성적서 삭제] 성적서번호: %s, 고유번호 - %d", reportNum, deleteTargetRepost.getId());
		Log deleteLog = Log.builder()
				.logType("d")
				.createMemberId(userId)
				.createDatetime(now)
				.workerName(workerName)
				.refTable("report")
				.refTableId(id)
				.logContent(logContent)
				.build();
		logRepository.save(deleteLog);
		
		resCode = 1;
		resMsg = "성적서가 삭제되었습니다.";
		
		return new ResMessage<>(resCode, resMsg, null);
	}
	
	/**
	 * 실무자결재 목록 조회
	 * - 전체 SELF 타입 성적서 대상 (접수 id 무관)
	 * - 검색필터: 진행상태, 결재상태, 접수구분, 중/소분류, 키워드
	 *
	 * @param request 검색 파라미터 (GetWorkApprovalListReq)
	 * @return Toast Grid 응답 포맷
	 */
	@Transactional(readOnly = true)
	public TuiGridDTO.ResData<WorkApprovalListRow> getWorkApprovalList(ReportDTO.GetWorkApprovalListReq request) {
		int pageIndex = request.getPage() - 1;
		int perPage = request.getPerPage();
		Pageable pageable = PageRequest.of(pageIndex, perPage);

		// 진행상태: 빈값 → null (전체 조회)
		String reportStatus = request.getReportStatus();
		reportStatus = (reportStatus == null || reportStatus.isBlank()) ? null : reportStatus;

		// 결재상태: 빈값 → null (전체 조회)
		String workStatus = request.getWorkStatus();
		workStatus = (workStatus == null || workStatus.isBlank()) ? null : workStatus;

		// 성적서작성(ExcelWork) 상태: 빈값 → null (전체 조회)
		String writeStatus = request.getWriteStatus();
		writeStatus = (writeStatus == null || writeStatus.isBlank()) ? null : writeStatus;

		// 접수구분: null이면 전체
		OrderType orderTypeEnum = request.getOrderType();
		String orderType = (orderTypeEnum == null) ? null : orderTypeEnum.name();

		// 중/소분류: 0 또는 null → null (전체)
		Long middleItemCodeId = request.getMiddleItemCodeId();
		if (middleItemCodeId != null && middleItemCodeId == 0L) middleItemCodeId = null;
		Long smallItemCodeId = request.getSmallItemCodeId();
		if (smallItemCodeId != null && smallItemCodeId == 0L) smallItemCodeId = null;

		// 검색타입: 빈값 → 'all'
		String searchType = request.getSearchType();
		if (searchType == null || searchType.isBlank()) searchType = "all";

		// 키워드: null → 빈값 처리 (WHERE keyword = '' 로 전체 조회)
		String keyword = request.getKeyword();
		keyword = (keyword == null) ? "" : keyword.trim();

		List<WorkApprovalListRow> pageResult = reportRepository.searchWorkApprovalList(
				reportStatus, workStatus, writeStatus, orderType,
				middleItemCodeId, smallItemCodeId,
				searchType, keyword, pageable
		);

		long totalCount = reportRepository.countWorkApprovalList(
				reportStatus, workStatus, writeStatus, orderType,
				middleItemCodeId, smallItemCodeId,
				searchType, keyword
		);

		TuiGridDTO.Pagination pagination = TuiGridDTO.Pagination.builder()
				.page(request.getPage())
				.totalCount((int) totalCount)
				.build();

		return TuiGridDTO.ResData.<WorkApprovalListRow>builder()
				.contents(pageResult)
				.pagination(pagination)
				.build();
	}

	// 성적서 수정 요청
	@Transactional
	public ResMessage<Object> updateReport(ReportDTO.ReportUpdateReq req, CustomUserDetails user) {
		int resCode = 0;
		String resMessage;
		
		Long userId = user.getId();
		LocalDateTime now = LocalDateTime.now();
		String workerName = user.getName();
		// 부모데이터를 가져온다.
		Long id = req.id();
		Report updateReport = reportRepository.findById(id).orElseThrow(() -> new EntityNotFoundException("수정할 성적서 정보를 찾는 데 실패했습니다."));
		
		// mapstruct를 통해 넘어온 값들을 그대로 영속성 컨텍스트 내 조회된 entity에 덮어씌운다.
		reportMapper.updateEntityFromDto(req, updateReport);
		updateReport.setUpdateMemberId(userId);
		
		Log updateLog = Log.builder()
				.logType("u")
				.createMemberId(userId)
				.createDatetime(now)
				.workerName(workerName)
				.refTableId(id)
				.refTable("report")
				.logContent(String.format("[성적서 수정] 성적서번호: %s - 고유번호: %d", updateReport.getReportNum(), id))
				.build();
		logRepository.save(updateLog);
		
		List<EquipmentDTO.UsedEquipment> equipmentDatas = req.equipmentDatas();
		if (equipmentDatas != null && !equipmentDatas.isEmpty()) {
			// 기존에 참조하던 데이터가 있을 경우 삭제한다.
			String refTable = "report";
			equipmentRefRepository.deleteUsedEquipData(refTable, id);
			
			List<StandardEquipmentRef> saveEquipList = new ArrayList<>();
			for (EquipmentDTO.UsedEquipment equipment : equipmentDatas) {
				saveEquipList.add(standardEquipmentRefMapper.toEntityFromRecord(equipment));
			}
			equipmentRefRepository.saveAll(saveEquipList);
		}
		
		// 자식성적서도 존재하는지 확인
		List<ReportDTO.ChildReportInfo> childReportInfos = req.childReportInfos();
		// 자식성적서가 존재하는 경우, update 또는 insert
		if (childReportInfos != null && !childReportInfos.isEmpty()) {
			Long caliOrderId = updateReport.getCaliOrderId();
			ReportLang reportLang = updateReport.getReportLang();
			
			
			// 자식성적서 데이터 반복
			for (ReportDTO.ChildReportInfo childReport : childReportInfos) {
				Long childReportId = childReport.id();
				
				// id가 존재하면 update, 없으면 insert
				if (childReportId != null && childReportId > 0) {
					Report updateChildReport = reportRepository.findById(childReportId).orElseThrow(() -> new EntityNotFoundException("자식성적서가 존재하지 않습니다."));
					reportMapper.updateChildEntityFromDto(childReport, updateChildReport);
					updateChildReport.setUpdateMemberId(userId);
					
				}
				// 신규등록인 경우
				else {
					Report newChildReport = reportMapper.insertChildEntityFromDto(childReport);
					// 부모 id를 넣어준다.
					newChildReport.setCreateDatetime(now);
					newChildReport.setCreateMemberId(userId);
					newChildReport.setParentScaleId(id);
					newChildReport.setCaliOrderId(caliOrderId);
					newChildReport.setReportLang(reportLang);
					newChildReport.setIsVisible(YnType.y);
					
					// 신규 등록
					reportRepository.save(newChildReport);
				}
				
			}
		}
		
		resCode = 1;
		resMessage = "성적서 수정에 성공했습니다.";

		return new ResMessage<>(resCode, resMessage, null);
	}


	// ── 성적서 통합수정 ────────────────────────────────────────────────────────

	/**
	 * 선택된 복수의 성적서에 완료예정일·교정일자·환경정보·중/소분류코드·실무자·기술책임자·표준장비를 일괄 수정.
	 *
	 * - null(또는 0)인 항목은 변경하지 않음 (partial update 방식)
	 * - 실무자/기술책임자 변경: updateMemberInfo=true 이고 체크박스가 선택되어 있어야 서버로 값이 전달됨.
	 *   서버에서는 각 성적서의 업데이트 전(원래) middleItemCodeId 기준으로 유효성 검사 후 반영.
	 *   해당 중분류에 속하지 않는 직원을 선택한 경우 그 성적서의 기존 담당자가 유지됨.
	 * - 표준장비: equipmentIds 목록이 비어있지 않을 때만 대상 성적서 전체에 동일 장비 목록을 일괄 적용
	 */
	@Transactional
	public ResMessage<Object> selfReportMultiUpdate(ReportDTO.SelfReportMultiUpdateReq req, CustomUserDetails user) {
		List<Long> reportIds = req.getReportIds();
		List<Report> reports = reportRepository.findAllById(reportIds);

		if (reports.size() != reportIds.size()) {
			return new ResMessage<>(-1, "일부 성적서를 찾을 수 없습니다.", null);
		}

		Long userId    = user.getId();
		String userName = user.getName();
		LocalDateTime now = LocalDateTime.now();

		List<Long> updatedIds = new ArrayList<>();

		for (Report report : reports) {
			// 실무자/기술책임자 유효성 검사 기준: 업데이트 전(원래) 중분류코드
			Long originalMiddleItemCodeId = report.getMiddleItemCodeId();

			// 완료예정일
			if (req.getExpectCompleteDate() != null) {
				report.setExpectCompleteDate(req.getExpectCompleteDate());
			}
			// 교정일자
			if (req.getCaliDate() != null) {
				report.setCaliDate(req.getCaliDate());
			}
			// 환경정보 (JSON 문자열): 입력된 항목만 기존 JSON에 merge (미입력 항목은 기존 값 유지)
			if (req.getEnvironmentInfo() != null && !req.getEnvironmentInfo().isBlank()) {
				report.setEnvironmentInfo(mergeEnvironmentInfo(report.getEnvironmentInfo(), req.getEnvironmentInfo()));
			}
			// 중분류코드
			if (req.getMiddleItemCodeId() != null && req.getMiddleItemCodeId() > 0) {
				report.setMiddleItemCodeId(req.getMiddleItemCodeId());
			}
			// 소분류코드
			if (req.getSmallItemCodeId() != null && req.getSmallItemCodeId() > 0) {
				report.setSmallItemCodeId(req.getSmallItemCodeId());
			}

			// 실무자: originalMiddleItemCodeId 기준으로 유효한 실무자인지 확인 후 반영
			if (req.isUpdateMemberInfo() && req.getWorkMemberId() != null && req.getWorkMemberId() > 0) {
				if (isMemberValidForCode(originalMiddleItemCodeId, req.getWorkMemberId(), 1)) {
					report.setWorkMemberId(req.getWorkMemberId());
				}
			}
			// 기술책임자: originalMiddleItemCodeId 기준으로 유효한 기술책임자인지 확인 후 반영
			if (req.isUpdateMemberInfo() && req.getApprovalMemberId() != null && req.getApprovalMemberId() > 0) {
				if (isMemberValidForCode(originalMiddleItemCodeId, req.getApprovalMemberId(), 6)) {
					report.setApprovalMemberId(req.getApprovalMemberId());
				}
			}

			report.setUpdateMemberId(userId);
			updatedIds.add(report.getId());
		}

		// 표준장비 일괄 적용 (값이 있을 때만 기존 데이터 교체)
		List<Long> equipmentIds = req.getEquipmentIds();
		if (equipmentIds != null && !equipmentIds.isEmpty()) {
			for (Long reportId : updatedIds) {
				// 기존 표준장비 참조 데이터 삭제 후 신규 삽입
				equipmentRefRepository.deleteUsedEquipData("report", reportId);
				List<StandardEquipmentRef> equipList = new ArrayList<>();
				for (int i = 0; i < equipmentIds.size(); i++) {
					EquipmentDTO.UsedEquipment usage = new EquipmentDTO.UsedEquipment("report", reportId, equipmentIds.get(i), i);
					equipList.add(standardEquipmentRefMapper.toEntityFromRecord(usage));
				}
				equipmentRefRepository.saveAll(equipList);
			}
		}

		// 로그 (고유번호 목록 포함)
		Log updateLog = Log.builder()
				.logType("u")
				.createMemberId(userId)
				.createDatetime(now)
				.workerName(userName)
				.refTableId(updatedIds.get(0))
				.refTable("report")
				.logContent(String.format("[성적서 통합수정] 고유번호 - %s", updatedIds))
				.build();
		logRepository.save(updateLog);

		return new ResMessage<>(1, String.format("%d건 수정되었습니다.", reports.size()), null);
	}

	/**
	 * 선택된 멤버(memberId)가 지정 중분류코드(middleItemCodeId)에 대해 bitmask 권한을 보유했는지 확인.
	 * - middleItemCodeId가 null 또는 0이면 중분류 제한이 없으므로 항상 유효로 처리.
	 */
	private boolean isMemberValidForCode(Long middleItemCodeId, Long memberId, int bitmask) {
		if (middleItemCodeId == null || middleItemCodeId == 0) {
			return true;
		}
		return memberRepository.findMembersByMiddleCodeAndBitmask(middleItemCodeId, bitmask)
				.stream()
				.anyMatch(m -> m.getId().equals(memberId));
	}

	/**
	 * 기존 environmentInfo JSON에 신규 값을 merge.
	 * 신규 JSON에 존재하는 키만 덮어쓰고, 나머지 키는 기존 값을 그대로 유지.
	 *
	 * <p>예시: 기존 {"tempMin":"10","tempMax":"20","humMin":"40","humMax":"60"} 에
	 * 신규 {"tempMax":"25"} 를 merge하면 → {"tempMin":"10","tempMax":"25","humMin":"40","humMax":"60"}
	 *
	 * <p>파싱 실패 시(기존값 또는 신규값이 유효한 JSON이 아닌 경우) 신규 JSON을 그대로 반환.
	 *
	 * @param existingJson 성적서에 저장된 기존 environmentInfo (null 가능)
	 * @param newJson      클라이언트에서 전달된 신규 environmentInfo (비어있지 않은 값이 보장됨)
	 * @return merge된 JSON 문자열
	 */
	private String mergeEnvironmentInfo(String existingJson, String newJson) {
		ObjectMapper objectMapper = new ObjectMapper();
		try {
			// 기존 JSON이 없거나 비어 있으면 신규 JSON을 그대로 사용
			if (existingJson == null || existingJson.isBlank()) {
				return newJson;
			}
			Map<String, Object> existingMap = objectMapper.readValue(existingJson, new TypeReference<>() {});
			Map<String, Object> newMap      = objectMapper.readValue(newJson,      new TypeReference<>() {});
			// 신규 JSON의 항목만 기존 map에 덮어쓰기 (미입력 항목은 기존 값 유지)
			existingMap.putAll(newMap);
			return objectMapper.writeValueAsString(existingMap);
		} catch (Exception e) {
			// JSON 파싱 실패 시 신규 JSON 그대로 반환
			log.warn("mergeEnvironmentInfo 파싱 실패 — 신규 JSON으로 덮어씁니다. existingJson={}", existingJson, e);
			return newJson;
		}
	}

	// ── 성적서작성 필수항목 검증 ──────────────────────────────────────────────

	/**
	 * 성적서작성 전 필수항목 일괄 검증.
	 *
	 * 검증 항목 (순서 유지):
	 *   교정일자, 최고온도, 최저온도, 최저습도, 최고습도, 최저기압, 최고기압,
	 *   중분류, 소분류, 실무자, 기술책임자, 실무자 서명이미지, 기술책임자 서명이미지
	 *
	 * - environmentInfo는 JSON 문자열 컬럼. tempMin/tempMax/humMin/humMax/preMin/preMax 키를 파싱해서 확인.
	 * - 서명이미지는 file_info 테이블에서 ref_table_name='member', ref_table_id=멤버id, is_visible='y' 인 행 존재 여부로 판단.
	 * - 성적서번호(report_num)가 null인 경우 "성적서ID:{id}" 형태로 대체 표기.
	 *
	 * @param reportIds 검증 대상 성적서 id 목록
	 * @return ValidateWriteRes (allPassed, passedIds, failedIds, failures)
	 */
	@Transactional(readOnly = true)
	public ReportDTO.ValidateWriteRes validateWriteReports(List<Long> reportIds) {
		// 1. 대상 성적서 전체 조회
		List<Report> reports = reportRepository.findAllById(reportIds);

		// ── 사전 차단: 작업이 이미 진행중인 성적서가 1건이라도 있으면 즉시 반환 ──
		// write/work/approvalStatus 중 하나라도 READY·PROGRESS 상태면 진행 불가
		// 이 경우 필드 검증을 수행하지 않고 즉시 hasInProgress=true 응답을 반환한다.
		List<String> inProgressNums = new ArrayList<>();
		for (Report r : reports) {
			boolean inProgress =
					r.getWriteStatus()    == AppStatus.PROGRESS ||
					r.getWorkStatus()     == AppStatus.READY    || r.getWorkStatus()     == AppStatus.PROGRESS ||
					r.getApprovalStatus() == AppStatus.READY    || r.getApprovalStatus() == AppStatus.PROGRESS;
			if (inProgress) {
				inProgressNums.add(r.getReportNum());
			}
		}
		if (!inProgressNums.isEmpty()) {
			// 진행중 성적서가 존재 → 프론트에서 진행 완전 차단
			return new ReportDTO.ValidateWriteRes(true, inProgressNums, false, List.of(), reportIds, List.of());
		}

		// 2. 실무자·기술책임자 멤버 ID 수집 → 서명이미지 존재 여부를 한 번에 조회
		Set<Long> memberIds = new HashSet<>();
		reports.forEach(r -> {
			if (r.getWorkMemberId() != null)     memberIds.add(r.getWorkMemberId());
			if (r.getApprovalMemberId() != null) memberIds.add(r.getApprovalMemberId());
		});
		// 서명이미지가 등록된 멤버 ID Set
		Set<Long> membersWithSign = new HashSet<>();
		if (!memberIds.isEmpty()) {
			fileInfoRepository.findByRefTableNameAndRefTableIdInAndIsVisible("member", memberIds, YnType.y)
					.forEach(fi -> membersWithSign.add(fi.getRefTableId()));
		}

		// 3. 검증 항목 순서 정의 (LinkedHashMap으로 순서 유지)
		// key: 표시용 한글명, value: 누락 성적서번호 리스트
		Map<String, List<String>> failureMap = new LinkedHashMap<>();
		String[] fieldLabels = {
				"교정일자", "최고온도", "최저온도", "최저습도", "최고습도", "최저기압", "최고기압",
				"중분류", "소분류", "실무자", "기술책임자", "실무자 서명이미지", "기술책임자 서명이미지"
		};
		for (String label : fieldLabels) {
			failureMap.put(label, new ArrayList<>());
		}

		ObjectMapper objectMapper = new ObjectMapper();
		List<Long> passedIds = new ArrayList<>();
		List<Long> failedIds = new ArrayList<>();

		// 4. 성적서별 필드 검증
		for (Report r : reports) {
			boolean hasFail = false;
			// 성적서번호가 null인 경우 대체 표기
			String displayNum = (r.getReportNum() != null && !r.getReportNum().isBlank())
					? r.getReportNum()
					: "성적서ID:" + r.getId();

			// environmentInfo JSON 파싱 (파싱 실패 시 빈 맵으로 처리)
			Map<String, String> envMap = new HashMap<>();
			if (r.getEnvironmentInfo() != null && !r.getEnvironmentInfo().isBlank()) {
				try {
					envMap = objectMapper.readValue(r.getEnvironmentInfo(), new TypeReference<>() {});
				} catch (Exception e) {
					log.warn("environmentInfo JSON 파싱 실패 — reportId={}, value={}", r.getId(), r.getEnvironmentInfo());
				}
			}

			// ① 교정일자
			if (r.getCaliDate() == null) {
				failureMap.get("교정일자").add(displayNum); hasFail = true;
			}
			// ② 최고온도
			if (!hasValue(envMap.get("tempMax"))) {
				failureMap.get("최고온도").add(displayNum); hasFail = true;
			}
			// ③ 최저온도
			if (!hasValue(envMap.get("tempMin"))) {
				failureMap.get("최저온도").add(displayNum); hasFail = true;
			}
			// ④ 최저습도
			if (!hasValue(envMap.get("humMin"))) {
				failureMap.get("최저습도").add(displayNum); hasFail = true;
			}
			// ⑤ 최고습도
			if (!hasValue(envMap.get("humMax"))) {
				failureMap.get("최고습도").add(displayNum); hasFail = true;
			}
			// ⑥ 최저기압
			if (!hasValue(envMap.get("preMin"))) {
				failureMap.get("최저기압").add(displayNum); hasFail = true;
			}
			// ⑦ 최고기압
			if (!hasValue(envMap.get("preMax"))) {
				failureMap.get("최고기압").add(displayNum); hasFail = true;
			}
			// ⑧ 중분류
			if (r.getMiddleItemCodeId() == null) {
				failureMap.get("중분류").add(displayNum); hasFail = true;
			}
			// ⑨ 소분류
			if (r.getSmallItemCodeId() == null) {
				failureMap.get("소분류").add(displayNum); hasFail = true;
			}
			// ⑩ 실무자
			if (r.getWorkMemberId() == null) {
				failureMap.get("실무자").add(displayNum); hasFail = true;
			}
			// ⑪ 기술책임자
			if (r.getApprovalMemberId() == null) {
				failureMap.get("기술책임자").add(displayNum); hasFail = true;
			}
			// ⑫ 실무자 서명이미지 (실무자가 지정되어 있어도 서명이미지가 없으면 누락)
			if (r.getWorkMemberId() == null || !membersWithSign.contains(r.getWorkMemberId())) {
				failureMap.get("실무자 서명이미지").add(displayNum); hasFail = true;
			}
			// ⑬ 기술책임자 서명이미지
			if (r.getApprovalMemberId() == null || !membersWithSign.contains(r.getApprovalMemberId())) {
				failureMap.get("기술책임자 서명이미지").add(displayNum); hasFail = true;
			}

			if (hasFail) failedIds.add(r.getId());
			else         passedIds.add(r.getId());
		}

		// 5. 누락이 없는 항목(빈 리스트)은 제거하여 failures 구성
		List<ReportDTO.ValidateWriteRes.FieldFailure> failures = failureMap.entrySet().stream()
				.filter(e -> !e.getValue().isEmpty())
				.map(e -> new ReportDTO.ValidateWriteRes.FieldFailure(e.getKey(), e.getValue()))
				.toList();

		return new ReportDTO.ValidateWriteRes(false, List.of(), failedIds.isEmpty(), passedIds, failedIds, failures);
	}

	/** null이거나 공백인 경우 false 반환 */
	private boolean hasValue(String val) {
		return val != null && !val.isBlank();
	}

	/**
	 * 성적서대기변경: 실무자 결재 완료(workStatus=SUCCESS) 상태를 대기(IDLE)로 초기화
	 *
	 * 조건: workStatus=SUCCESS + approvalStatus=IDLE 인 성적서만 허용
	 * 기술책임자 결재가 진행 중(PROGRESS)이거나 완료(SUCCESS)된 경우 전체 중단
	 * workMemberId는 유지, workStatus·workDatetime만 초기화
	 */
	@Transactional
	public ResMessage<ReportDTO.ResetWorkStatusRes> resetWorkStatus(List<Long> reportIds, CustomUserDetails user) {
		List<Report> reports = reportRepository.findAllById(reportIds);

		// 요청 ID 중 실제로 존재하지 않는 건 검출
		Set<Long> foundIds = reports.stream().map(Report::getId).collect(Collectors.toSet());
		List<ReportDTO.InvalidReportItem> invalids = new ArrayList<>();
		for (Long reqId : reportIds) {
			if (!foundIds.contains(reqId)) {
				invalids.add(new ReportDTO.InvalidReportItem(reqId, null, "성적서를 찾을 수 없습니다."));
			}
		}

		// 상태 검증: workStatus=SUCCESS + approvalStatus=IDLE 만 허용
		for (Report report : reports) {
			if (report.getWorkStatus() != AppStatus.SUCCESS) {
				invalids.add(new ReportDTO.InvalidReportItem(
						report.getId(), report.getReportNum(),
						"실무자 결재 완료 상태가 아닙니다. (현재: " + report.getWorkStatus() + ")"
				));
			} else if (report.getApprovalStatus() == AppStatus.PROGRESS) {
				invalids.add(new ReportDTO.InvalidReportItem(
						report.getId(), report.getReportNum(),
						"기술책임자 결재가 진행 중입니다."
				));
			} else if (report.getApprovalStatus() == AppStatus.SUCCESS) {
				invalids.add(new ReportDTO.InvalidReportItem(
						report.getId(), report.getReportNum(),
						"기술책임자 결재가 이미 완료된 성적서입니다."
				));
			}
		}

		// 한 건이라도 검증 실패 시 전체 중단
		if (!invalids.isEmpty()) {
			return new ResMessage<>(-1, "대기변경 불가 항목이 있습니다.", new ReportDTO.ResetWorkStatusRes(invalids));
		}

		LocalDateTime now = LocalDateTime.now();
		Long userId = user.getId();

		// 일괄 초기화: workStatus → IDLE, workDatetime → null (workMemberId 유지)
		for (Report report : reports) {
			report.setWorkStatus(AppStatus.IDLE);
			report.setWorkDatetime(null);
			report.setUpdateMemberId(userId);
		}

		List<Long> changedIds = reports.stream().map(Report::getId).toList();
		Log statusLog = Log.builder()
				.logType("u")
				.createMemberId(userId)
				.createDatetime(now)
				.workerName(user.getName())
				.refTableId(changedIds.get(0))
				.refTable("report")
				.logContent(String.format("[성적서대기변경] 고유번호 - %s", changedIds))
				.build();
		logRepository.save(statusLog);

		return new ResMessage<>(1, "성적서 " + reports.size() + "건이 대기상태로 변경되었습니다.", null);
	}

	/**
	 * 성적서 진행상태 변경 (수리/불가/초기화)
	 *
	 * REPAIR·IMPOSSIBLE 처리 시: write/work/approval 상태·일시 초기화 + 파일 전체 소프트삭제
	 * NORMAL 처리 시: 상태만 NORMAL로 변경 (초기화)
	 * 실무자 결재 완료(workStatus=SUCCESS) 상태이면 REPAIR·IMPOSSIBLE 불가
	 */
	@Transactional
	public ResMessage<Void> updateReportStatus(Long reportId, String newStatus, CustomUserDetails user) {
		Report report = reportRepository.findById(reportId)
				.orElseThrow(() -> new EntityNotFoundException("성적서를 찾을 수 없습니다."));

		// 유효성 검증
		ReportStatus targetStatus;
		try {
			targetStatus = ReportStatus.valueOf(newStatus);
		} catch (IllegalArgumentException e) {
			throw new IllegalArgumentException("유효하지 않은 상태값입니다: " + newStatus);
		}

		// 실무자 결재 완료 상태이면 수리/불가 불가
		if ((targetStatus == ReportStatus.REPAIR || targetStatus == ReportStatus.IMPOSSIBLE)
				&& report.getWorkStatus() == AppStatus.SUCCESS) {
			return new ResMessage<>(-1, "실무자 결재가 완료된 성적서는 수리/불가 처리가 불가능합니다.", null);
		}

		LocalDateTime now = LocalDateTime.now();
		Long userId = user.getId();

		// 수리·불가 처리 시 관련 상태 및 파일 초기화
		if (targetStatus == ReportStatus.REPAIR || targetStatus == ReportStatus.IMPOSSIBLE) {
			report.setWriteStatus(AppStatus.IDLE);
			report.setWriteDatetime(null);
			report.setWorkStatus(AppStatus.IDLE);
			report.setWorkDatetime(null);
			report.setApprovalStatus(AppStatus.IDLE);
			report.setApprovalDatetime(null);
			fileInfoRepository.softDeleteAllByRef("report", reportId, YnType.n, now, userId);
		}

		report.setReportStatus(targetStatus);
		report.setUpdateMemberId(userId);

		Log statusLog = Log.builder()
				.logType("u")
				.createMemberId(userId)
				.createDatetime(now)
				.workerName(user.getName())
				.refTableId(reportId)
				.refTable("report")
				.logContent(String.format("[성적서상태변경] 고유번호 - [%d] → %s", reportId, newStatus))
				.build();
		logRepository.save(statusLog);

		return new ResMessage<>(1, "상태가 변경되었습니다.", null);
	}

	/**
	 * 성적서출력 목록 조회
	 * - 기술책임자 결재 완료(approval_status=SUCCESS) + 미출력(is_print='n') + 자체성적서(SELF)
	 * - 반려/불가 상태 제외
	 */
	@Transactional(readOnly = true)
	public TuiGridDTO.ResData<ReportPrintListRow> getReportPrintList(ReportPrintDTO.ListReq req) {
		int page    = Math.max(req.getPage() - 1, 0);
		int perPage = req.getPerPage() > 0 ? req.getPerPage() : 50;
		Pageable pageable = PageRequest.of(page, perPage);

		// 날짜 타입: 기본 approval(성적서발행일)
		String dateType  = (req.getDateType()  == null || req.getDateType().isBlank())  ? "approval" : req.getDateType();
		String startDate = (req.getStartDate() == null || req.getStartDate().isBlank()) ? null : req.getStartDate();
		String endDate   = (req.getEndDate()   == null || req.getEndDate().isBlank())   ? null : req.getEndDate();

		// 중/소분류: 0 → null
		Long middleItemCodeId = req.getMiddleItemCodeId();
		if (middleItemCodeId != null && middleItemCodeId == 0L) middleItemCodeId = null;
		Long smallItemCodeId = req.getSmallItemCodeId();
		if (smallItemCodeId != null && smallItemCodeId == 0L) smallItemCodeId = null;

		// 검색타입: 빈값 → 'all'
		String searchType = req.getSearchType();
		if (searchType == null || searchType.isBlank()) searchType = "all";

		String keyword = (req.getKeyword() == null) ? "" : req.getKeyword().trim();

		List<ReportPrintListRow> items = reportRepository.searchReportPrintList(
				middleItemCodeId, smallItemCodeId,
				dateType, startDate, endDate,
				searchType, keyword, pageable
		);

		long totalCount = reportRepository.countReportPrintList(
				middleItemCodeId, smallItemCodeId,
				dateType, startDate, endDate,
				searchType, keyword
		);

		TuiGridDTO.Pagination pagination = TuiGridDTO.Pagination.builder()
				.page(req.getPage())
				.totalCount((int) totalCount)
				.build();

		return TuiGridDTO.ResData.<ReportPrintListRow>builder()
				.contents(items)
				.pagination(pagination)
				.build();
	}

	// ── 대행성적서(AGCY) ──────────────────────────────────────────────────────

	/**
	 * 대행성적서 등록 (1회 요청에 N건 일괄 등록)
	 * - 자체대행성적서번호: {접수번호}-D{4digits} (예: BD26-0006-D0001)
	 * - 관리번호: BD{yy}-D{5digits} (예: BD26-D00001)
	 * - report_num은 외부 교정기관에서 받기 전까지 null로 유지
	 */
	@Transactional
	public ResMessage<Object> addAgcyReport(ReportDTO.AddAgcyReportReq request, CustomUserDetails user) {
		LocalDateTime now = LocalDateTime.now();
		Long userId = user.getId();
		String workerName = user.getName();

		Long caliOrderId = request.getCaliOrderId();
		CaliOrder orderInfo = caliOrderRepository.findById(caliOrderId)
				.orElseThrow(() -> new EntityNotFoundException("접수정보를 찾을 수 없습니다."));

		String orderNum = orderInfo.getOrderNum();
		int orderYear = orderInfo.getOrderDate().getYear();
		String yearSuffix = String.valueOf(orderYear).substring(2);  // "26"

		List<ReportDTO.AgcyItemReq> items = request.getItems();
		if (items == null || items.isEmpty()) {
			return new ResMessage<>(-1, "등록할 기기 목록이 없습니다.", null);
		}
		int count = items.size();

		// 시퀀스 일괄 예약 (SELECT FOR UPDATE — 동시성 안전)
		int agcyReportStart = numberSequenceService.reserve("report_agcy_" + caliOrderId, count);
		int agcyManageStart = numberSequenceService.reserve("manage_agcy_" + orderYear, count);

		List<Long> savedIds = new ArrayList<>();
		for (int i = 0; i < count; i++) {
			ReportDTO.AgcyItemReq item = items.get(i);

			String agcySelfReportNum = orderNum + "-D" + String.format("%04d", agcyReportStart + i);
			String manageNo = "BD" + yearSuffix + "-D" + String.format("%05d", agcyManageStart + i);

			Integer itemCaliCycle = item.itemCaliCycle();
			if (itemCaliCycle == null || itemCaliCycle == 0) {
				itemCaliCycle = 12;
			}

			Report report = Report.builder()
					.caliOrderId(caliOrderId)
					.reportType(ReportType.AGCY)
					.orderType(request.getOrderType())
					.agcyAgent(request.getAgcyAgent())
					.agcySelfReportNum(agcySelfReportNum)
					.manageNo(manageNo)
					.middleItemCodeId(item.middleItemCodeId())
					.smallItemCodeId(item.smallItemCodeId())
					.itemId(item.itemId())
					.itemName(item.itemName())
					.itemNameEn(item.itemNameEn())
					.itemMakeAgent(item.itemMakeAgent())
					.itemMakeAgentEn(item.itemMakeAgentEn())
					.itemFormat(item.itemFormat())
					.itemNum(item.itemNum())
					.itemCaliCycle(itemCaliCycle)
					.caliFee(item.caliFee() != null ? item.caliFee() : 0L)
					.remark(item.remark())
					.reportStatus(ReportStatus.NORMAL)
					.isVisible(YnType.y)
					.createMemberId(userId)
					.createDatetime(now)
					.build();

			Report saved = reportRepository.save(report);
			savedIds.add(saved.getId());

			Log saveLog = Log.builder()
					.createDatetime(now)
					.createMemberId(userId)
					.workerName(workerName)
					.refTable("report")
					.refTableId(saved.getId())
					.logType("i")
					.logContent(String.format("[대행성적서 등록] 자체대행성적서번호: %s - 고유번호: %d", agcySelfReportNum, saved.getId()))
					.build();
			logRepository.save(saveLog);
		}

		return new ResMessage<>(1, String.format("대행성적서 %d건이 등록되었습니다.", count), savedIds);
	}

	/**
	 * 대행성적서 수정 모달 데이터 조회
	 */
	@Transactional(readOnly = true)
	public ReportDTO.AgcyReportDetailRes getAgcyReportDetail(Long id) {
		ReportDTO.AgcyReportDetailRes detail = reportRepository.getAgcyReportDetail(id);
		if (detail == null) {
			throw new EntityNotFoundException("대행성적서를 찾을 수 없습니다. id=" + id);
		}
		return detail;
	}

	/**
	 * 대행성적서 수정 (단건)
	 * - reportNum은 null이면 기존 값 유지 (외부 성적서번호를 아직 받지 못한 경우)
	 */
	@Transactional
	public ResMessage<Object> updateAgcyReport(ReportDTO.UpdateAgcyReportReq req, CustomUserDetails user) {
		Long id = req.id();
		Report report = reportRepository.findById(id)
				.orElseThrow(() -> new EntityNotFoundException("수정할 대행성적서를 찾을 수 없습니다. id=" + id));

		if (report.getReportType() != ReportType.AGCY) {
			throw new IllegalArgumentException("대행성적서만 수정할 수 있습니다.");
		}

		LocalDateTime now = LocalDateTime.now();
		Long userId = user.getId();

		report.setAgcyAgent(req.agcyAgent());
		report.setMiddleItemCodeId(req.middleItemCodeId());
		report.setSmallItemCodeId(req.smallItemCodeId());
		if (req.itemId() != null && req.itemId() > 0) {
			report.setItemId(req.itemId());
		}
		report.setItemName(req.itemName());
		report.setItemNameEn(req.itemNameEn());
		report.setItemMakeAgent(req.itemMakeAgent());
		report.setItemMakeAgentEn(req.itemMakeAgentEn());
		report.setItemFormat(req.itemFormat());
		report.setItemNum(req.itemNum());
		if (req.caliFee() != null) {
			report.setCaliFee(req.caliFee());
		}
		report.setRemark(req.remark());
		if (req.caliDate() != null) {
			report.setCaliDate(req.caliDate());
		}
		if (req.reportNum() != null) {
			report.setReportNum(req.reportNum());
		}
		report.setUpdateMemberId(userId);

		Log updateLog = Log.builder()
				.logType("u")
				.createMemberId(userId)
				.createDatetime(now)
				.workerName(user.getName())
				.refTableId(id)
				.refTable("report")
				.logContent(String.format("[대행성적서 수정] 자체대행성적서번호: %s - 고유번호: %d", report.getAgcySelfReportNum(), id))
				.build();
		logRepository.save(updateLog);

		return new ResMessage<>(1, "대행성적서가 수정되었습니다.", null);
	}

	/**
	 * 대행성적서 통합수정 (N건 partial update)
	 * - 각 필드가 null이면 변경하지 않음
	 * - reportStatus는 SUCCESS(완료) 또는 CANCEL(취소)만 허용
	 */
	@Transactional
	public ResMessage<Object> agcyReportMultiUpdate(ReportDTO.AgcyReportMultiUpdateReq req, CustomUserDetails user) {
		List<Long> reportIds = req.getReportIds();
		List<Report> reports = reportRepository.findAllById(reportIds);

		if (reports.size() != reportIds.size()) {
			return new ResMessage<>(-1, "일부 대행성적서를 찾을 수 없습니다.", null);
		}

		for (Report report : reports) {
			if (report.getReportType() != ReportType.AGCY) {
				return new ResMessage<>(-1, "대행성적서만 통합수정할 수 있습니다. id=" + report.getId(), null);
			}
		}

		// reportStatus 유효성 검증 (SUCCESS 또는 CANCEL만 허용)
		if (req.getReportStatus() != null && !req.getReportStatus().isBlank()) {
			try {
				ReportStatus targetStatus = ReportStatus.valueOf(req.getReportStatus());
				if (targetStatus != ReportStatus.SUCCESS && targetStatus != ReportStatus.CANCEL) {
					return new ResMessage<>(-1, "대행성적서 상태는 SUCCESS(완료) 또는 CANCEL(취소)만 허용됩니다.", null);
				}
			} catch (IllegalArgumentException e) {
				return new ResMessage<>(-1, "유효하지 않은 상태값입니다: " + req.getReportStatus(), null);
			}
		}

		LocalDateTime now = LocalDateTime.now();
		Long userId = user.getId();
		List<Long> updatedIds = new ArrayList<>();

		for (Report report : reports) {
			if (req.getAgcyAgent() != null) {
				report.setAgcyAgent(req.getAgcyAgent());
			}
			if (req.getCaliDate() != null) {
				report.setCaliDate(req.getCaliDate());
			}
			if (req.getReportStatus() != null && !req.getReportStatus().isBlank()) {
				report.setReportStatus(ReportStatus.valueOf(req.getReportStatus()));
			}
			if (req.getReportNum() != null && !req.getReportNum().isBlank()) {
				report.setReportNum(req.getReportNum());
			}
			report.setUpdateMemberId(userId);
			updatedIds.add(report.getId());
		}

		Log updateLog = Log.builder()
				.logType("u")
				.createMemberId(userId)
				.createDatetime(now)
				.workerName(user.getName())
				.refTableId(updatedIds.get(0))
				.refTable("report")
				.logContent(String.format("[대행성적서 통합수정] 고유번호 - %s", updatedIds))
				.build();
		logRepository.save(updateLog);

		return new ResMessage<>(1, String.format("대행성적서 %d건이 수정되었습니다.", reports.size()), null);
	}

	// ── 업체별교정이력조회 ─────────────────────────────────────────────────────

	/**
	 * 업체별교정이력 목록 조회
	 *
	 * 접근 제어:
	 *   - 내부직원(agentId=0): 전체 성적서 조회 가능 (isInternal=1 → SQL의 OR 절로 bypass)
	 *   - 업체계정(agentId>0): 동일 group_name 업체 id 목록에 속하는 성적서만 조회
	 *     - groupName이 없으면 자기 자신(agentId)만 허용
	 *
	 * 조회 대상:
	 *   - SELF: approval_status='SUCCESS'
	 *   - AGCY: report_status='SUCCESS'
	 *
	 * @param req  필터 요청 DTO (dateType, startDate, endDate, reportType, searchType, keyword)
	 * @param user 로그인 사용자
	 */
	@Transactional(readOnly = true)
	public TuiGridDTO.ResData<AgentCaliHistoryListRow> getAgentCaliHistoryList(
			AgentCaliHistoryDTO.ListReq req, CustomUserDetails user) {

		// 로그인 사용자의 agentId 조회 (CustomUserDetails에 agentId 없으므로 Member 조회)
		Member member = memberRepository.findByIdAndIsVisible(user.getId(), YnType.y);
		long agentId = member.getAgentId();
		boolean isInternal = (agentId == 0L);

		List<Long> allowedAgentIds;
		if (isInternal) {
			// 내부직원: placeholder -1L 사용 (isInternal=1 OR 절이 항상 true → 전체 조회)
			allowedAgentIds = List.of(-1L);
		} else {
			// 업체계정: 동일 group_name의 업체 id 목록 조회
			Agent agent = agentRepository.findByIsVisibleAndId(YnType.y, agentId).orElse(null);
			String groupName = (agent != null) ? agent.getGroupName() : null;
			if (groupName != null && !groupName.isBlank()) {
				allowedAgentIds = agentRepository.findIdsByGroupName(groupName);
			} else {
				// group_name이 없으면 자기 자신만
				allowedAgentIds = List.of(agentId);
			}
		}

		int page    = Math.max(req.getPage() - 1, 0);
		int perPage = req.getPerPage() > 0 ? req.getPerPage() : 100;
		Pageable pageable = PageRequest.of(page, perPage);

		// 날짜 타입: null·공백 → 기본 approval
		String dateType  = (req.getDateType()  == null || req.getDateType().isBlank())  ? "approval" : req.getDateType();
		String startDate = (req.getStartDate() == null || req.getStartDate().isBlank()) ? null : req.getStartDate();
		String endDate   = (req.getEndDate()   == null || req.getEndDate().isBlank())   ? null : req.getEndDate();
		// null·공백 → null (전체) 유지
		String reportType = (req.getReportType() == null || req.getReportType().isBlank()) ? null : req.getReportType();
		// 검색타입: null·공백 → "all"
		String searchType = (req.getSearchType() == null || req.getSearchType().isBlank()) ? "all" : req.getSearchType();
		String keyword    = (req.getKeyword() == null) ? "" : req.getKeyword().trim();

		List<AgentCaliHistoryListRow> items = reportRepository.searchAgentCaliHistoryList(
				dateType, startDate, endDate, reportType,
				searchType, keyword,
				isInternal ? 1 : 0, allowedAgentIds, pageable);

		long totalCount = reportRepository.countAgentCaliHistoryList(
				dateType, startDate, endDate, reportType,
				searchType, keyword,
				isInternal ? 1 : 0, allowedAgentIds);

		TuiGridDTO.Pagination pagination = TuiGridDTO.Pagination.builder()
				.page(req.getPage())
				.totalCount((int) totalCount)
				.build();

		return TuiGridDTO.ResData.<AgentCaliHistoryListRow>builder()
				.contents(items)
				.pagination(pagination)
				.build();
	}

}
