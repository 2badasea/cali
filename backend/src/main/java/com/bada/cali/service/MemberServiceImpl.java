package com.bada.cali.service;

import com.bada.cali.common.ResMessage;
import com.bada.cali.common.enums.AuthType;
import com.bada.cali.common.enums.YnType;
import com.bada.cali.dto.AgentAccountDTO;
import com.bada.cali.dto.MemberDTO;
import com.bada.cali.dto.TuiGridDTO;
import com.bada.cali.entity.*;
import com.bada.cali.mapper.AgentManagerMapper;
import com.bada.cali.mapper.AgentMapper;
import com.bada.cali.mapper.MemberCodeAuthMapper;
import com.bada.cali.mapper.MemberMapper;
import com.bada.cali.repository.*;
import com.bada.cali.repository.projection.AgentAccountListRow;
import com.bada.cali.repository.projection.GetMemberInfoPr;
import com.bada.cali.repository.projection.MemberCodeAuthPr;
import com.bada.cali.repository.projection.MemberListPr;
import com.bada.cali.security.CustomUserDetails;
import jakarta.persistence.EntityNotFoundException;
import lombok.AllArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@Log4j2
@AllArgsConstructor
public class MemberServiceImpl {
	private final MemberRepository memberRepository;
	private final AgentRepository agentRepository;
	private final AgentManagerRepository agentManagerRepository;
	private final LogRepository logRepository;
	private final FileServiceImpl fileServiceImpl;
	private final MemberCodeAuthRepository memberCodeAuthRepository;
	private final MemberCodeAuthMapper memberCodeAuthMapper;
	
	private final AgentManagerMapper agentManagerMapper;
	private final AgentMapper agentMapper;
	
	private final PasswordEncoder passwordEncoder;
	private final MemberMapper memberMapper;
	private final MenuRepository menuRepository;
	private final MemberPermissionReadRepository memberPermissionReadRepository;
	private final MenuQueryService menuQueryService;
	
	/**
	 * 로그인 성공 처리 (로그인 카운트 초기화, 마지막 로그인 일시 갱신, 비밀번호 만료 경고, 이력 저장)
	 * loadUserByUsername에서 이미 조회·검증된 CustomUserDetails를 직접 사용 — DB 재조회 없음
	 *
	 * @param userDetails 로그인 성공한 사용자 (SecurityContext의 Principal)
	 * @param clientIp    클라이언트 IP
	 * @return 응답 코드(1: 일반, 2: 관리자)와 환영 메시지
	 */
	@Transactional
	public ResMessage<Object> processLoginSuccess(CustomUserDetails userDetails, String clientIp) {
		Long userId = userDetails.getId();
		String userName = userDetails.getName();

		LocalDateTime now = LocalDateTime.now();
		String resMsg = userName + "님 로그인을 환영합니다.";
		int resCode = 1;

		// 로그인 성공 시 실패 카운트 초기화 (이미 0이어도 안전한 업데이트)
		memberRepository.updateMemberLoginCount(userId, 0);

		// 마지막 로그인 일시 업데이트
		memberRepository.updateLastLogin(userId);

		// 마지막 비밀번호 변경일시 체크(3개월)
		LocalDateTime lastPwdUpdate = userDetails.getLastPwdUpdated();
		if (lastPwdUpdate != null && now.isAfter(lastPwdUpdate.plusMonths(3))) {
			resMsg += "<br>비밀번호를 마지막으로 변경한 지<br>3개월이 지났습니다.<br>변경을 권장드립니다.";
		}

		// 관리자 계정 구분
		boolean isAdmin = userDetails.getAuthorities().stream()
				.anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
		if (isAdmin) {
			resCode = 2;
		}

		// 로그인 성공 이력 저장
		Log successLog = Log.builder()
				.logIp(clientIp)
				.workerName(userName)
				.logContent("[로그인 성공] - " + userName + " 님이 로그인 하셨습니다.")
				.logType("l")
				.refTable("member")
				.refTableId(userId)
				.createDatetime(now)
				.createMemberId(userId)
				.build();
		logRepository.save(successLog);

		return new ResMessage<>(resCode, resMsg, null);
	}

	/**
	 * 로그인 실패 처리 (실패 카운트 증가, 이력 저장)
	 * LockedException 등 계정 상태 예외는 Handler에서 처리하며, 이 메서드는 자격증명 실패 시만 호출.
	 *
	 * @param loginId  로그인 아이디
	 * @param clientIp 클라이언트 IP
	 */
	@Transactional
	public void processLoginFailure(String loginId, String clientIp) {
		Optional<Member> optLoginMember = memberRepository.findByLoginId(loginId, YnType.y);
		if (optLoginMember.isEmpty()) {
			log.debug("로그인 실패 - 존재하지 않는 아이디: {}", loginId);
			return;
		}

		Member tryLoginMember = optLoginMember.get();
		// count 0~4: +1 증가, count 5: 잠금 해제 후 재실패이므로 1로 리셋하여 새 카운트 시작
		if (tryLoginMember.getLoginCount() <= 5) {
			int updateCountValue = (tryLoginMember.getLoginCount() <= 4) ? (tryLoginMember.getLoginCount() + 1) : 1;
			memberRepository.updateMemberLoginCount(tryLoginMember.getId(), updateCountValue);
		}

		// 로그인 실패 이력 저장
		Log failLog = Log.builder()
				.logIp(clientIp)
				.workerName("")
				.logContent("[로그인 실패]")
				.logType("l")
				.refTable("member")
				.refTableId(tryLoginMember.getId())
				.createDatetime(LocalDateTime.now())
				.createMemberId(tryLoginMember.getId())
				.build();
		logRepository.save(failLog);
	}

	/**
	 * 사용자 계정(loginId) 중복 체크
	 *
	 * @param loginId
	 * @param refPage
	 * @return
	 */
	@Transactional(readOnly = true)    // 조회만 함.
	public MemberDTO.DuplicateLoginIdRes chkDuplicateLoginId(String loginId, String refPage) {
		
		Optional<Member> optMember = memberRepository.findByLoginId(loginId, YnType.y);
		// 없는 경우 바로 리턴
		if (optMember.isEmpty()) {
			return new MemberDTO.DuplicateLoginIdRes(false, null, null);
		}
		// 기본값: 중복은 맞지만 업체 정보는 안 실어보냄(업체정보가 아예 없는 의미라면 빈 문자열 보다는 null이 의미에 가까움)
		String name = null;
		String addr = null;
		
		// 중복 존재
		// refPage의 경우 null일 경우 NPE 예외 발생 가능성 -> 아래처럼 문자열 상수를 왼쪽에 두면 null 이어도 안전
		if ("memberJoin".equalsIgnoreCase(refPage)) {
			// 회원가입에서 조회한 경우, 업체 정보까지 조회 후 리턴
			Long agentId = optMember.get().getAgentId();
			if (agentId > 0) {
				Optional<Agent> optAgent = agentRepository.findById(agentId);
				if (optAgent.isPresent()) {
					name = optAgent.get().getName();
					addr = optAgent.get().getAddr();
				}
			}
		}
		return new MemberDTO.DuplicateLoginIdRes(true, name, addr);
	}
	
	/**
	 * 회원가입 로직 수행
	 * 1. agent 테이블에 업체정보 우선 삽입 (create_type 확인)
	 * 2. 생성된 agent_id를 바탕으로 member에 사업자번호(agentNum)을 loginId로 하는 데이터 생성
	 * 3. 생성된 agent_id를 바탕으로 업체 담당자(Agent_manager) 테이블에 데이터 삽입
	 * 4. 생성되는 데이터에 대해선 모두 로그에 남길 것
	 * TODO 추후 slack api를 활용해서 관리자가 신규가입 업체를 확인할 수 있도록 한다.
	 *
	 * @param memberJoinReq
	 * @return
	 */
	@Transactional
	public MemberDTO.MemberJoinRes memberJoin(MemberDTO.MemberJoinReq memberJoinReq) {
		log.info("arrive at memberJoin service layer");
		
		// 1) insert agent
		Agent insertAgentEntity = agentMapper.toAgentFromMemberJoinReq(memberJoinReq);
		insertAgentEntity.setCreateType("join");    // 기본값 basic이 아닌 join으로 설정(mapper에서 @mapping 방법도 존재)
		insertAgentEntity.setAgentFlag(1);        // 기본가입 시, 신청업체로 간주
		insertAgentEntity.setCreateDatetime(LocalDateTime.now());    // 넣어주지 않으면 NULL를 insert하는 과정에서 DB의 NOT NULL제약에 걸림
		// save()는 성공하면 예외없이 엔티티를 그대로 리턴. 실패 시, 예외를 던짐 (null이나 false를 리턴하지 않음)
		// 이때 예외는 컨트롤러까지 전파 => 전역예외에서 캐치하여 에러페이지 or JSON 응답으로 가공해서 반환
		Agent savedAgent = agentRepository.save(insertAgentEntity);
		// 생성된 업체 id
		Long agentId = savedAgent.getId();
		
		// 2) inset agent_manager (업체 담당자 삽입)
		// agentId 값 할당
		AgentManager insertManagerEntity = agentManagerMapper.toEntityFromMemberJoinReq(memberJoinReq);
		insertManagerEntity.setAgentId(agentId);    // 업체id 세팅
		insertManagerEntity.setMainYn(YnType.y);    // 처음 등록된 담당자가 대표담당자가 되도록 값 변경
		insertManagerEntity.setCreateDatetime(LocalDateTime.now());
		AgentManager savedAgentManager = agentManagerRepository.save(insertManagerEntity);
		
		// 3) insert member
		Member insertMemberEntity = memberMapper.toMemberFromMemberJoin(memberJoinReq);
		insertMemberEntity.setJoinDate(LocalDate.now());                        // 가입일
		insertMemberEntity.setPwd(passwordEncoder.encode(memberJoinReq.getPwd()));        // 인코딩된 비밀번호
		insertMemberEntity.setAgentId(agentId);        // 업체 고유id
		insertMemberEntity.setCreateDatetime(LocalDateTime.now());
		// formatter 클래스를 이용한 방식
//		LocalDate today = LocalDate.now();
//		DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
//		String todayStr = today.format(formatter);   // 예) "2025-11-21"
		Member savedMember = memberRepository.save(insertMemberEntity);
		
		// 로그를 남긴다.
		String logContent = "[회원가입] 사용자 ID: " + savedMember.getLoginId() + ", 업체명: " + savedAgent.getName();
		Log insertSuccessLog = Log.builder()
				.logType("i")
				.refTable("member")
				.refTableId(savedMember.getId())
				.createDatetime(LocalDateTime.now())
				.createMemberId(0L)
				.logContent(logContent)
				.build();
		logRepository.save(insertSuccessLog);
		
		return new MemberDTO.MemberJoinRes(1, "가입신청이 완료되었습니다.");
	}
	
	@Transactional(readOnly = true)
	public TuiGridDTO.ResData<MemberListPr> getMemberList(MemberDTO.GetMemberListReq req) {
		log.info("데이터 확인");
		
		int pageIndex = req.getPage() - 1;
		int perPage = req.getPerPage();
		
		PageRequest pageRequest = PageRequest.of(pageIndex, perPage);
		
		// 검색타입 정리
		Integer workType = req.getWorkType();        // 재직여부
		String searchType = req.getSearchType();    // 검색타입
		String keyword = req.getKeyword();            // 키워드
		
		if (searchType == null || searchType.isBlank()) {
			searchType = "all";
		}
		keyword = (keyword == null) ? "" : keyword.trim();
		YnType isVisible = YnType.y;
		
		Page<MemberListPr> pageResult = memberRepository.getMemberList(workType, searchType, keyword, isVisible, pageRequest);
		
		TuiGridDTO.Pagination pagination = TuiGridDTO.Pagination.builder()
				.page(req.getPage())
				.totalCount((int) pageResult.getTotalElements())
				.build();
		
		return TuiGridDTO.ResData.<MemberListPr>builder()
				.pagination(pagination)
				.contents(pageResult.getContent())
				.build();
	}
	
	// 직원 등록
	@Transactional
	public ResMessage<Long> memberCreate(MemberDTO.CreateMemberReq req, CustomUserDetails user) {
		LocalDateTime now = LocalDateTime.now();
		Long userId = user.getId();
		String workerName = user.getName();

		// 로그인 아이디 중복 체크
		Optional<Member> optMember = memberRepository.findByLoginId(req.loginId(), YnType.y);
		if (optMember.isPresent()) {
			return new ResMessage<>(-1, "중복되는 아이디가 존재합니다.", null);
		}

		Member createMember = memberMapper.toMemberByCreateReq(req);
		createMember.setCreateDatetime(now);
		createMember.setCreateMemberId(userId);
		createMember.setLastPwdUpdated(now);
		createMember.setAgentId(0L);    // 자사 직원은 모두 0

		Member savedMember = memberRepository.save(createMember);
		Long id = savedMember.getId();

		// 비밀번호 암호화 후 업데이트
		String reqPwd = req.pwd();
		if (reqPwd != null && !reqPwd.isBlank()) {
			savedMember.updatePwd(passwordEncoder.encode(reqPwd));
		}

		// 이미지 저장
		MultipartFile memberImage = req.memberImage();
		if (memberImage != null && !memberImage.isEmpty()) {
			fileServiceImpl.softDeleteAndInsert("member", id, memberImage, userId);
		}

		// 중분류 권한 저장
		List<MemberDTO.MemberAuthData> itemAuthData = req.itemAuthData();
		if (itemAuthData != null && !itemAuthData.isEmpty()) {
			memberCodeAuthRepository.deleteMemberCodeAuth(id);
			List<MemberCodeAuth> saveCodeAuthList = new ArrayList<>();
			for (MemberDTO.MemberAuthData data : itemAuthData) {
				saveCodeAuthList.add(memberCodeAuthMapper.toEntity(data, id));
			}
			if (!saveCodeAuthList.isEmpty()) {
				memberCodeAuthRepository.saveAll(saveCodeAuthList);
			}
		}

		// 등록 로그
		logRepository.save(Log.builder()
				.logType("i")
				.refTable("member")
				.refTableId(id)
				.logContent("[직원 등록] 고유번호 - [" + id + "]")
				.workerName(workerName)
				.createDatetime(now)
				.createMemberId(userId)
				.build());

		return new ResMessage<>(1, "저장 성공", id);
	}

	// 직원 수정
	@Transactional
	public ResMessage<Long> memberUpdate(Long id, MemberDTO.UpdateMemberReq req, CustomUserDetails user) {
		LocalDateTime now = LocalDateTime.now();
		Long userId = user.getId();
		String workerName = user.getName();

		Member originMember = memberRepository.findById(id)
				.orElseThrow(() -> new EntityNotFoundException("수정할 회원정보를 찾지 못 했습니다."));

		memberMapper.updateMemberByReq(req, originMember);
		originMember.setUpdateMemberId(userId);

		// 비밀번호가 넘어온 경우 업데이트
		String reqPwd = req.pwd();
		if (reqPwd != null && !reqPwd.isBlank()) {
			originMember.updatePwd(passwordEncoder.encode(reqPwd));
		}

		// 이미지 저장
		MultipartFile memberImage = req.memberImage();
		if (memberImage != null && !memberImage.isEmpty()) {
			fileServiceImpl.softDeleteAndInsert("member", id, memberImage, userId);
		}

		// 중분류 권한 저장
		List<MemberDTO.MemberAuthData> itemAuthData = req.itemAuthData();
		if (itemAuthData != null && !itemAuthData.isEmpty()) {
			memberCodeAuthRepository.deleteMemberCodeAuth(id);
			List<MemberCodeAuth> saveCodeAuthList = new ArrayList<>();
			for (MemberDTO.MemberAuthData data : itemAuthData) {
				saveCodeAuthList.add(memberCodeAuthMapper.toEntity(data, id));
			}
			if (!saveCodeAuthList.isEmpty()) {
				memberCodeAuthRepository.saveAll(saveCodeAuthList);
			}
		}

		// 수정 로그
		logRepository.save(Log.builder()
				.logType("u")
				.refTable("member")
				.refTableId(id)
				.logContent("[직원 수정] 고유번호 - [" + id + "]")
				.workerName(workerName)
				.createDatetime(now)
				.createMemberId(userId)
				.build());

		return new ResMessage<>(1, "저장 성공", id);
	}
	
	// 직원 삭제 (소프트삭제)
	@Transactional
	public ResMessage<Void> memberDelete(List<Long> ids, CustomUserDetails user) {
		if (ids == null || ids.isEmpty()) {
			return new ResMessage<>(-1, "삭제할 직원을 선택해주세요.", null);
		}

		Long userId = user.getId();
		LocalDateTime now = LocalDateTime.now();

		// admin 계정(id=1) 또는 auth='admin' 계정 삭제 시도 방어
		boolean hasAdminTarget = memberRepository.findAllByIdInAndIsVisible(ids, YnType.y)
				.stream()
				.anyMatch(m -> m.getId() == 1L || "admin".equalsIgnoreCase(m.getAuth().name()));
		if (hasAdminTarget) {
			return new ResMessage<>(-1, "관리자 계정은 삭제할 수 없습니다.", null);
		}

		int deletedCount = memberRepository.softDeleteByIds(ids, YnType.n, now, userId);

		// 삭제 로그
		logRepository.save(Log.builder()
				.logType("d")
				.refTable("member")
				.refTableId(ids.get(0))
				.logContent("[직원 삭제] 고유번호 - " + ids)
				.workerName(user.getName())
				.createDatetime(now)
				.createMemberId(userId)
				.build());

		return new ResMessage<>(1, deletedCount + "명 삭제되었습니다.", null);
	}

	// 회원정보를 가져온다 (직원등록/수정 페이지)
	@Transactional(readOnly = true)
	public ResMessage<MemberDTO.GetMemberInfoSet> getMemberInfo(Long memberId) {
		YnType isVisible = YnType.y;
		List<GetMemberInfoPr> memberInfoList = memberRepository.getMemberInfo(memberId, isVisible);
		if (memberInfoList.isEmpty()) {
			throw new EntityNotFoundException("직원 정보를 찾을 수 없습니다.");
		}
		GetMemberInfoPr getMemberInfo = memberInfoList.get(0);

		Long imgFileInfoId = getMemberInfo.getImgFileId();
		String memberImgPath = "";
		if (imgFileInfoId != null && imgFileInfoId > 0) {
			String dir = getMemberInfo.getDir();
			String extension = getMemberInfo.getExtension();
			memberImgPath = fileServiceImpl.getFilePath(dir, imgFileInfoId, extension);
		}

		// 중분류 권한 가져오기
		List<MemberCodeAuthPr> itemAuthData = memberCodeAuthRepository.getMemberCodeAuth(memberId);

		MemberDTO.GetMemberInfoSet data = new MemberDTO.GetMemberInfoSet(getMemberInfo, memberImgPath, itemAuthData);
		return new ResMessage<>(1, "", data);
	}

	/**
	 * 직원의 메뉴 읽기 권한 목록 조회
	 * 전체 visible 메뉴 + 해당 직원의 권한 여부 플래그 포함
	 */
	@Transactional(readOnly = true)
	public MemberDTO.MenuPermissionListRes getMenuPermissions(Long memberId) {
		Member member = memberRepository.findByIdAndIsVisible(memberId, YnType.y);
		if (member == null) throw new EntityNotFoundException("직원 정보를 찾을 수 없습니다.");

		boolean isAdminMember = AuthType.admin == member.getAuth();

		List<Menu> allMenus = menuQueryService.getAllVisibleMenus();
		Set<Long> permittedMenuIds = new HashSet<>(memberPermissionReadRepository.findMenuIdsByMemberId(memberId));

		// 부모-자식 계층 순서로 재정렬: 1depth → 하위 children 순으로 이어서 나열
		Map<Long, List<Menu>> childrenByParentId = allMenus.stream()
				.filter(m -> m.getParent() != null)
				.collect(Collectors.groupingBy(m -> m.getParent().getId()));

		List<MemberDTO.MenuPermissionItem> items = new ArrayList<>();
		allMenus.stream()
				.filter(m -> m.getParent() == null)
				.sorted(Comparator.comparing(Menu::getSortOrder))
				.forEach(root -> {
					items.add(toMenuPermissionItem(root, permittedMenuIds));
					childrenByParentId.getOrDefault(root.getId(), List.of())
							.stream()
							.sorted(Comparator.comparing(Menu::getSortOrder))
							.forEach(child -> items.add(toMenuPermissionItem(child, permittedMenuIds)));
				});

		return new MemberDTO.MenuPermissionListRes(isAdminMember, items);
	}

	/**
	 * 직원의 메뉴 읽기 권한 저장 (replace: 기존 전체 삭제 후 선택된 항목만 재저장)
	 * ADMIN 계정은 변경 불가
	 */
	@Transactional
	public ResMessage<Void> saveMenuPermissions(Long memberId, List<Long> menuIds, CustomUserDetails user) {
		Member member = memberRepository.findByIdAndIsVisible(memberId, YnType.y);
		if (member == null) throw new EntityNotFoundException("직원 정보를 찾을 수 없습니다.");

		if (AuthType.admin == member.getAuth()) {
			return new ResMessage<>(-1, "관리자 계정의 권한은 변경할 수 없습니다.", null);
		}

		memberPermissionReadRepository.deleteAllByMemberId(memberId);

		if (menuIds != null && !menuIds.isEmpty()) {
			List<Menu> menus = menuRepository.findAllById(menuIds);
			List<MemberPermissionRead> newPerms = menus.stream()
					.map(m -> MemberPermissionRead.of(member, m))
					.toList();
			memberPermissionReadRepository.saveAll(newPerms);
		}

		logRepository.save(Log.builder()
				.logType("u")
				.refTable("member_permission_read")
				.refTableId(memberId)
				.createDatetime(LocalDateTime.now())
				.createMemberId(user.getId())
				.logContent("메뉴 권한 변경 - 대상 직원 id: " + memberId + ", 부여된 메뉴 id 목록: " + (menuIds != null ? menuIds : "[]"))
				.build());

		return new ResMessage<>(1, "메뉴 권한이 저장되었습니다.", null);
	}

	private MemberDTO.MenuPermissionItem toMenuPermissionItem(Menu m, Set<Long> permittedMenuIds) {
		return new MemberDTO.MenuPermissionItem(
				m.getId(),
				m.getMenuAlias(),
				m.getDepth(),
				m.getParent() != null ? m.getParent().getId() : null,
				permittedMenuIds.contains(m.getId())
		);
	}

	// ──────────────────────────────────────────────────────────────
	// 업체계정관리 CRUD
	// ──────────────────────────────────────────────────────────────

	/**
	 * 업체계정 목록 조회 (TUI Grid 서버사이드 페이지네이션)
	 */
	@Transactional(readOnly = true)
	public TuiGridDTO.Res<TuiGridDTO.ResData<AgentAccountListRow>> getAgentAccountList(AgentAccountDTO.ListReq req) {
		PageRequest pageRequest = PageRequest.of(req.getPage() - 1, req.getPerPage());
		Page<AgentAccountListRow> page = memberRepository.getAgentAccountList(
				req.getIsActive(),
				req.getSearchType(),
				req.getKeyword(),
				pageRequest
		);

		TuiGridDTO.ResData<AgentAccountListRow> data = TuiGridDTO.ResData.<AgentAccountListRow>builder()
				.contents(page.getContent())
				.pagination(TuiGridDTO.Pagination.builder()
						.page(req.getPage())
						.totalCount((int) page.getTotalElements())
						.build())
				.build();
		return new TuiGridDTO.Res<>(true, data);
	}

	/**
	 * 업체계정 단건 조회.
	 * 수정 모달 초기화 시 DB에서 최신 값을 가져오기 위해 사용.
	 */
	@Transactional(readOnly = true)
	public AgentAccountDTO.DetailRes getAgentAccountDetail(Long memberId) {
		Member member = memberRepository.findByIdAndIsVisible(memberId, YnType.y);
		if (member == null) {
			throw new EntityNotFoundException("해당 계정을 찾을 수 없습니다.");
		}
		// agent.name 조회
		String agentName = agentRepository.findByIsVisibleAndId(YnType.y, member.getAgentId())
				.map(a -> a.getName())
				.orElse("");

		return new AgentAccountDTO.DetailRes(
				member.getId(),
				member.getAgentId(),
				agentName,
				member.getLoginId(),
				member.getIsActive().name()
		);
	}

	/**
	 * 업체계정 등록.
	 * - login_id 중복 검사 (is_visible='y' 기준)
	 * - agent_id 유효성 검사 (agent가 존재하고 is_visible='y'인지)
	 * - 비밀번호 BCrypt 암호화
	 */
	@Transactional
	public void createAgentAccount(AgentAccountDTO.CreateReq req, CustomUserDetails user) {
		// login_id 중복 확인
		if (memberRepository.existsByLoginIdAndIsVisible(req.getLoginId(), YnType.y)) {
			throw new IllegalArgumentException("이미 사용 중인 아이디입니다. 다른 아이디를 입력해주세요.");
		}

		// agent 유효성 확인
		agentRepository.findByIsVisibleAndId(YnType.y, req.getAgentId())
				.orElseThrow(() -> new IllegalArgumentException("선택한 업체 정보를 찾을 수 없습니다."));

		String encodedPwd = passwordEncoder.encode(req.getPwd());
		LocalDateTime now = LocalDateTime.now();

		Member newMember = Member.builder()
				.loginId(req.getLoginId())
				.pwd(encodedPwd)
				.agentId(req.getAgentId())
				.name(req.getName())
				.nameEng("")
				.isActive(YnType.valueOf(req.getIsActive()))
				.isVisible(YnType.y)
				.auth(AuthType.user)
				.createDatetime(now)
				.createMemberId(user.getId())
				.build();
		Member saved = memberRepository.save(newMember);

		logRepository.save(Log.builder()
				.workerName(user.getName())
				.logType("i")
				.refTable("member")
				.refTableId(saved.getId())
				.logContent("업체계정 등록 - 고유번호 - [" + saved.getId() + "], loginId: " + saved.getLoginId())
				.createDatetime(now)
				.createMemberId(user.getId())
				.build());
	}

	/**
	 * 업체계정 수정.
	 * - 업체명(name), 로그인허용유무(isActive), agentId 변경
	 * - 비밀번호는 입력한 경우에만 변경
	 */
	@Transactional
	public void updateAgentAccount(Long memberId, AgentAccountDTO.UpdateReq req, CustomUserDetails user) {
		Member member = memberRepository.findByIdAndIsVisible(memberId, YnType.y);
		if (member == null) {
			throw new jakarta.persistence.EntityNotFoundException("해당 계정을 찾을 수 없습니다.");
		}

		// agent 유효성 확인 (업체 변경 시)
		agentRepository.findByIsVisibleAndId(YnType.y, req.getAgentId())
				.orElseThrow(() -> new IllegalArgumentException("선택한 업체 정보를 찾을 수 없습니다."));

		member.setAgentId(req.getAgentId());
		member.setName(req.getName());
		member.setIsActive(YnType.valueOf(req.getIsActive()));

		// 비밀번호는 입력이 있을 때만 변경
		if (req.getPwd() != null && !req.getPwd().isBlank()) {
			member.updatePwd(passwordEncoder.encode(req.getPwd()));
		}

		logRepository.save(Log.builder()
				.workerName(user.getName())
				.logType("u")
				.refTable("member")
				.refTableId(memberId)
				.logContent("업체계정 수정 - 고유번호 - [" + memberId + "]")
				.createDatetime(LocalDateTime.now())
				.createMemberId(user.getId())
				.build());
	}

	/**
	 * 업체계정 소프트삭제.
	 * - 삭제 조건: 해당 member의 agentId가 가리키는 agent가 is_visible='n'이어야 함
	 * - 하나라도 조건 미충족 시 전체 삭제 불가
	 */
	@Transactional
	public void deleteAgentAccounts(AgentAccountDTO.DeleteReq req, CustomUserDetails user) {
		List<Long> memberIds = req.getIds();

		// 대상 member들의 agentId 수집
		List<Long> agentIds = memberRepository.findAgentIdsByMemberIds(memberIds);

		// 삭제 조건: 해당 agent가 모두 is_visible='n'이어야 함
		List<Long> deletedAgentIds = agentRepository.findDeletedAgentIds(agentIds);
		if (deletedAgentIds.size() != agentIds.size()) {
			throw new IllegalArgumentException(
					"삭제할 수 없는 계정이 포함되어 있습니다. 업체관리에서 해당 업체를 먼저 삭제한 후 다시 시도해주세요."
			);
		}

		LocalDateTime now = LocalDateTime.now();
		int deleted = memberRepository.softDeleteByIds(memberIds, YnType.n, now, user.getId());

		logRepository.save(Log.builder()
				.workerName(user.getName())
				.logType("d")
				.refTable("member")
				.refTableId(0L)
				.logContent("업체계정 삭제 - 고유번호 - " + memberIds)
				.createDatetime(now)
				.createMemberId(user.getId())
				.build());
	}

}
