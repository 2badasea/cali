package com.bada.cali.repository;

import com.bada.cali.dto.MemberDTO;
import com.bada.cali.entity.Member;
import com.bada.cali.common.enums.AuthType;
import com.bada.cali.common.enums.YnType;
import com.bada.cali.repository.projection.AgentAccountListRow;
import com.bada.cali.repository.projection.GetMemberInfoPr;
import com.bada.cali.repository.projection.MemberListPr;
import com.bada.cali.repository.projection.MemberSelectRow;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

// Sprig Data JPA에서 JpaRepository를 상속한 인터페이스는 별도 애너테이션 없이도 빈으로 등록됨(명시적으로 @Repository 애너테이션 명시해도 됨)
public interface MemberRepository extends JpaRepository<Member, Long> {
	
	// auth 기준 멤버 전체 조회 (메뉴 권한 동기화용)
	List<Member> findAllByAuth(AuthType auth);

	// loginId(username)으로 유저 정보 조회
	@Query("SELECT m FROM Member m WHERE m.loginId = :username AND m.isVisible = :isVisible ")
	Optional<Member> findByLoginId(String username, YnType isVisible);
	
	// 로그인 시도 횟수 초기화 - @Transactional은 Service 계층(processLoginSuccess/processLoginFailure)에서 관리
	@Modifying
	@Query("UPDATE Member m SET m.loginCount = :setCount, m.lastLoginFailDatetime = CASE WHEN :setCount >= 1 AND :setCount <= 5 THEN CURRENT_TIMESTAMP ELSE m.lastLoginFailDatetime END  WHERE m.id = :id")
	int updateMemberLoginCount(@Param("id") Long id, @Param("setCount") Integer setCount);

	// 마지막 로그인일시 업데이트 - @Transactional은 Service 계층(processLoginSuccess)에서 관리
	@Modifying
	@Query("UPDATE Member m SET m.lastLoginDatetime = CURRENT_TIMESTAMP  WHERE m.id = :id")
	int updateLastLogin(@Param("id") Long id);
	
	// 개별 id로 회원 조회
	Member findByIdAndIsVisible(Long id, YnType isVisible);
	
	// 리스트로 넘어온 id로 n개 조회
	List<Member> findAllByIdInAndIsVisible(List<Long> ids, YnType isVisible);
	
	// 업체 id를 가진 member 데이터 조회
	List<Member> findAllByAgentIdInAndIsVisible(List<Long> ids, YnType isVisible);
	
	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query("""
			       update Member m
			          set m.isVisible = :isVisible,
			              m.deleteDatetime = :deleteDatetime,
			              m.deleteMemberId = :deleteMemberId
			        where m.agentId in :agentIds
			""")
	void delMemberByAgentIds(@Param("agentIds") Collection<Long> agentIds,
								  @Param("isVisible") YnType isVisible,
								  @Param("deleteDatetime") LocalDateTime deleteDatetime,
								  @Param("deleteMemberId") Long deleteMemberId);
	
	
	// 직원관리 회원 리스트
	// NOTE ADMIN 계정은 나오지 않도록 변경
	@Query("""
					SELECT
						m.id AS id,
						m.companyNo as companyNo,
						m.loginId as loginId,
						m.email as email,
						m.name as name,
						m.hp as hp,
						m.tel as tel,
						m.workType as workType,
						d.name as departmentName,
						ml.name as levelName
					FROM Member AS m
					LEFT JOIN Department as d ON d.id = m.departmentId AND d.isVisible = :isVisible
					LEFT JOIN MemberLevel as ml ON ml.id = m.levelId AND ml.isVisible = :isVisible
					where m.isVisible = :isVisible
					and m.agentId = 0
					and m.isActive = 'y'
					and m.auth != 'ADMIN'
					and (:workType IS NULL OR m.workType = :workType)
					AND (
									:keyword = '' OR
									(
										(:searchType = 'loginId' AND m.loginId LIKE concat('%', :keyword, '%'))
										OR (:searchType = 'name' AND m.name LIKE concat('%', :keyword, '%'))
										OR (:searchType = 'email' AND m.email LIKE concat('%', :keyword, '%'))
										OR (:searchType = 'department' AND d.name LIKE concat('%', :keyword, '%'))
										OR (:searchType = 'level' AND ml.name LIKE concat('%', :keyword, '%'))
										OR (:searchType = 'all' AND (
																	m.loginId LIKE concat('%', :keyword, '%')
																	OR 	m.name LIKE concat('%', :keyword, '%')
																	OR 	m.email LIKE concat('%', :keyword, '%')
																	OR 	d.name LIKE concat('%', :keyword, '%')
																	OR 	ml.name LIKE concat('%', :keyword, '%')
																)
										)
									)
								)
			
			""")
	Page<MemberListPr> getMemberList(
			@Param("workType") Integer workType,
			@Param("searchType") String searchType,
			@Param("keyword") String keyword,
			@Param("isVisible") YnType isVisible,
			PageRequest pageRequest
	);
	
	// 직원 소프트삭제 (id 목록 기준, 이미 삭제된 건 제외)
	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query("""
			update Member m
			   set m.isVisible      = :isVisible,
			       m.deleteDatetime = :deleteDatetime,
			       m.deleteMemberId = :deleteMemberId
			 where m.id in :ids
			   and m.isVisible = 'y'
			""")
	int softDeleteByIds(@Param("ids") List<Long> ids,
						@Param("isVisible") YnType isVisible,
						@Param("deleteDatetime") LocalDateTime deleteDatetime,
						@Param("deleteMemberId") Long deleteMemberId);

	/**
	 * 접수자 select 구성용 사내 직원 목록 조회.
	 * - agentId = 0 (사내 직원)
	 * - isActive = 'y' (재직중)
	 * - leaveDate IS NULL OR leaveDate > CURRENT_DATE (퇴사하지 않은 직원)
	 * - isVisible = 'y' (삭제되지 않음)
	 * - loginId != 'bada' (bada 계정 제외)
	 * id 기준 오름차순 정렬
	 */
	@Query("""
			SELECT m.id AS id, m.name AS name
			FROM Member m
			WHERE m.agentId = 0
			  AND m.isActive = 'y'
			  AND m.isVisible = 'y'
			  AND (m.leaveDate IS NULL OR m.leaveDate > CURRENT_DATE)
			  AND m.loginId != 'bada'
			ORDER BY m.id ASC
			""")
	List<MemberSelectRow> findInternalMembers();

	/**
	 * 특정 중분류코드에 대해 지정한 권한 비트마스크를 보유한 사내 직원 조회.
	 * JPQL이 비트 연산을 지원하지 않으므로 nativeQuery 사용.
	 *
	 * @param middleItemCodeId 중분류코드 고유 id
	 * @param bitmask          권한 비트마스크 (1=실무자, 6=기술책임자 부/정 통합)
	 * @return 조건에 맞는 직원 id·name 목록
	 */
	@Query(value = """
			SELECT m.id AS id, m.name AS name
			FROM member m
			JOIN member_code_auth mca ON mca.member_id = m.id
			WHERE mca.middle_item_code_id = :middleItemCodeId
			  AND (mca.auth_bitmask & :bitmask) > 0
			  AND m.agent_id = 0
			  AND m.is_active = 'y'
			  AND m.is_visible = 'y'
			  AND (m.leave_date IS NULL OR m.leave_date > CURDATE())
			  AND m.login_id != 'bada'
			ORDER BY m.id ASC
			""", nativeQuery = true)
	List<MemberSelectRow> findMembersByMiddleCodeAndBitmask(
			@Param("middleItemCodeId") Long middleItemCodeId,
			@Param("bitmask") int bitmask);

	/**
	 * 중분류코드 제한 없이 지정한 권한 비트마스크를 보유한 모든 사내 직원 조회.
	 * 통합수정 모달 최초 진입 시(중분류 미선택) 전체 실무자/기술책임자 목록을 반환할 때 사용.
	 * DISTINCT: 동일 직원이 여러 중분류에 등록된 경우 중복 제거.
	 *
	 * @param bitmask 권한 비트마스크 (1=실무자, 6=기술책임자 부/정 통합)
	 * @return 조건에 맞는 직원 id·name 목록 (중복 없음)
	 */
	@Query(value = """
			SELECT DISTINCT m.id AS id, m.name AS name
			FROM member m
			JOIN member_code_auth mca ON mca.member_id = m.id
			WHERE (mca.auth_bitmask & :bitmask) > 0
			  AND m.agent_id = 0
			  AND m.is_active = 'y'
			  AND m.is_visible = 'y'
			  AND (m.leave_date IS NULL OR m.leave_date > CURDATE())
			  AND m.login_id != 'bada'
			ORDER BY m.id ASC
			""", nativeQuery = true)
	List<MemberSelectRow> findMembersByBitmask(@Param("bitmask") int bitmask);

	// FileInfo가 복수 존재할 경우 NonUniqueResultException 방어 → List 반환 후 서비스에서 첫 번째 사용
	@Query("""
			   select
			     m.loginId AS loginId,
			     m.name AS name,
			     m.nameEng AS nameEng,
			     m.hp AS hp,
			     m.companyNo AS companyNo,
			     m.birth AS birth,
			     m.addr1 AS addr1,
			     m.addr2 AS addr2,
			     m.email AS email,
			     m.tel AS tel,
			     m.departmentId AS departmentId,
			     m.levelId AS levelId,
			     m.isActive AS isActive,
			     m.joinDate AS joinDate,
			     m.leaveDate AS leaveDate,
			     m.workType AS workType,
			     m.remark AS remark,
			     f.id AS imgFileId,
			     f.extension as extension,
			     f.dir as dir
			    FROM Member m
			    LEFT JOIN FileInfo f
			      ON f.refTableName = 'member'
			     AND f.refTableId = :id
			     AND f.isVisible = :isVisible
			    WHERE m.id = :id
			    ORDER BY f.id DESC
			""")
	List<GetMemberInfoPr> getMemberInfo(
			@Param("id") Long id,
			@Param("isVisible") YnType isVisible
	);

	// ──────────────────────────────────────────────────────────────
	// 업체계정관리 전용 쿼리
	// ──────────────────────────────────────────────────────────────

	/**
	 * 업체계정(agent_id > 0, is_visible = 'y') 목록 조회.
	 * - isActive: 'y'/'n' 필터. 빈 문자열이면 전체.
	 * - searchType: loginId / name. 빈 문자열이면 조건 미적용.
	 * - keyword: 빈 문자열이면 조건 미적용.
	 * 정렬: member.name ASC → id ASC
	 */
	@Query("""
			SELECT
			    m.id               AS id,
			    m.agentId          AS agentId,
			    m.name             AS name,
			    m.loginId          AS loginId,
			    m.loginCount       AS loginCount,
			    m.lastLoginDatetime AS lastLoginDatetime,
			    m.createDatetime   AS createDatetime,
			    m.isActive         AS isActive
			FROM Member m
			WHERE m.agentId > 0
			  AND m.isVisible = 'y'
			  AND (:isActive IS NULL OR m.isActive = :isActive)
			  AND (
			        :keyword = ''
			        OR (:searchType = 'loginId' AND m.loginId LIKE concat('%', :keyword, '%'))
			        OR (:searchType = 'name'    AND m.name    LIKE concat('%', :keyword, '%'))
			      )
			ORDER BY m.name ASC, m.id ASC
			""")
	Page<AgentAccountListRow> getAgentAccountList(
			@Param("isActive")    YnType isActive,  // null이면 전체 조회
			@Param("searchType")  String searchType,
			@Param("keyword")     String keyword,
			PageRequest pageRequest
	);

	/** 업체계정 loginId 중복 확인 (is_visible = 'y'인 것만) */
	boolean existsByLoginIdAndIsVisible(String loginId, YnType isVisible);

	/** 특정 member id의 agentId 목록 반환 (삭제 전 agent 상태 확인용) */
	@Query("SELECT m.agentId FROM Member m WHERE m.id IN :ids AND m.isVisible = 'y'")
	List<Long> findAgentIdsByMemberIds(@Param("ids") List<Long> ids);

}
