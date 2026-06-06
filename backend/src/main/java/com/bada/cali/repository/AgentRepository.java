package com.bada.cali.repository;

import com.bada.cali.common.enums.YnType;
import com.bada.cali.dto.AgentDTO;
import com.bada.cali.entity.Agent;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface AgentRepository extends JpaRepository<Agent, Long> {
	
	// FIX 업체별 첨부파일 갯수를 확인하는 과정에서 목록 행 수만큼 (스칼라)서브쿼리가 도는 형태 -> 데이터가 커지면 성능 이슈 가능
	@Query("""
			SELECT new com.bada.cali.dto.AgentDTO$AgentRowData(
			      a.id,
			      a.createType,
			      a.groupName,
			      a.name,
			      a.ceo,
			      a.agentNum,
			      a.addr,
			      a.agentTel,
			      a.email,
			      a.manager,
			      a.managerTel,
			      a.remark,
			
			      a.nameEn,
			      a.agentFlag,
			      a.agentZipCode,
			      a.businessType,
			      a.businessKind,
			      a.addrEn,
			      a.phone,
			      a.managerEmail,
			      a.fax,
			      a.accountNumber,
			      a.calibrationCycle,
			      a.selfDiscount,
			      a.outDiscount,
			      a.isClose,
			
			      am.id,
			      am.name,
			      am.tel,
			      am.email,
			
			     (SELECT COUNT(f.id)
			      FROM FileInfo f
			      WHERE f.refTableName = 'agent'
			        AND f.refTableId = a.id
			        AND f.isVisible = :isVisible)
			)
			FROM Agent a
			LEFT JOIN AgentManager am
				  ON am.agentId = a.id
				  AND am.isVisible = :isVisible
				  AND am.mainYn = :mainYn
			WHERE a.isVisible = :isVisible
			  AND (:isClose IS NULL OR a.isClose = :isClose)
			  AND (
					  :agentFlag = 0
					  OR mod( cast( a.agentFlag / :agentFlag as Integer ), 2 ) = 1
			  )
			  AND (
			        :keyword = '' OR
			        (
			            (:searchType = 'name'     AND a.name     LIKE %:keyword%)
			         OR (:searchType = 'agentNum' AND a.agentNum LIKE %:keyword%)
			         OR (:searchType = 'addr'     AND a.addr     LIKE %:keyword%)
			         OR (:searchType = 'all'      AND (
			                a.name     LIKE %:keyword%
			             OR a.agentNum LIKE %:keyword%
			             OR a.addr     LIKE %:keyword%
			         ))
			        )
			  )
			ORDER BY
			    CASE WHEN (a.agentNum IS NULL OR a.agentNum = '') THEN 0 ELSE 1 END ASC,
			    a.name ASC
			""")
	Page<AgentDTO.AgentRowData> searchAgents(
			@Param("isVisible") YnType isVisible,
			@Param("isClose") YnType isClose,          // null이면 필터 미적용(전체선택)
			@Param("searchType") String searchType,    // all/name/agentNum/addr
			@Param("keyword") String keyword,          // ""이면 검색조건 미적용
			@Param("agentFlag") Integer agentFlag,		// 업체형태
			@Param("mainYn") YnType mainYn,			// 대표
			Pageable pageable
	);
	
	// 단건 조회
	Optional<Agent> findByIsVisibleAndId(YnType isVisible, Long id);
	
	// 삭제대상 업체정보
	List<Agent> findAllByIdInAndIsVisible(Collection<Long> ids, YnType isVisible);
	
	// 기본: 삭제된 것을 제외하고 전체 가져오기
	Page<Agent> findByIsVisible(YnType isVisible, Pageable pageable);
	
	// 업체 삭제 처리
	// NOTE @parma에 명시된 문자열과 쿼리문 내 ':필드명' 부분과 일치해야 함
	// => 앞에 a.xxx 부분은 entity 객체의 필드명 실제 쿼리문이 수행될 때는 @Column에 명시된 필드명으로 매핑되어서 처리된다.
	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query("""
			       update Agent a
			          set a.isVisible = :isVisible,
			              a.deleteDatetime = :deleteDatetime,
			              a.deleteMemberId = :deleteMemberId
			        where a.id in :agentIds
			""")
	int delAgentByIds(@Param("agentIds") Collection<Long> agentIds,
					  @Param("isVisible") YnType isVisible,
					  @Param("deleteDatetime") LocalDateTime deleteDatetime,
					  @Param("deleteMemberId") Long deleteMemberId
	);
	
	// 존재하는 업체 그룹명들 반환 (null과 빈문자열 모두 배제하기 위해 JPQL 작성 && 중복그룹명 제외 by distinct)
	@Query("""
				select distinct a.groupName
				from Agent a
				where 1 =1
					and a.isVisible = :isVisible
					and (a.groupName <> '' and a.groupName IS NOT NULL)
			
			""")
	List<String> findAllByIsVisibleAndGroupNameIsNotBlank(YnType isVisible);
	
	// 업체 그룹명 업데이트
	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query("""
						UPDATE Agent a
						SET a.groupName = :groupName
						WHERE a.id in :ids
			""")
	int updateAgentGroupName(
			@Param("ids") List<Long> ids,
			@Param("groupName") String groupName
	);
	
	// 업체명 기준으로 업체정보 조회하기
	@Query("""
			select
				  a.id as id,
			      a.name as name,
			      a.addr as addr,
			      a.agentNum as agentNum
			from Agent a
			where a.isVisible = 'y'
			and replace(a.name, ' ', '') like concat('%', :agentName, '%')
			""")
	List<AgentDTO.AgentRowView> chkAgentInfos(@Param("agentName") String agentName);

	/**
	 * 업체별교정이력조회 — 그룹명이 동일한 업체 id 목록 조회.
	 * 업체 사용자가 동일 그룹관리의 다른 업체 성적서도 조회할 수 있도록 허용하기 위한 용도.
	 */
	@Query("SELECT a.id FROM Agent a WHERE a.isVisible = 'y' AND a.groupName = :groupName")
	List<Long> findIdsByGroupName(@Param("groupName") String groupName);

	/**
	 * 업체계정 삭제 전 사전 조건 확인용.
	 * 주어진 agent id 목록 중 이미 소프트삭제(is_visible='n')된 agent id만 반환.
	 * 반환 개수 == 입력 개수이면 삭제 가능.
	 */
	@Query("SELECT a.id FROM Agent a WHERE a.id IN :ids AND a.isVisible = 'n'")
	List<Long> findDeletedAgentIds(@Param("ids") List<Long> ids);

}
