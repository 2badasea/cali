package com.bada.cali.repository;

import com.bada.cali.common.enums.YnType;
import com.bada.cali.entity.Menu;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface MenuRepository extends JpaRepository<Menu, Long> {

	// isVisible = 'y' 이면서, 로그인한 유저가 접근 가능한 메뉴만 depth, sort_order 순으로 조회
	List<Menu> findByIsVisibleAndIdInOrderByDepthAscSortOrderAsc(YnType isVisible,
																 Collection<Long> ids);

	// isVisible = 'y' 인 전체 메뉴를 depth, sort_order 순으로 조회 (캐시용 — MenuQueryService에서 사용)
	List<Menu> findByIsVisibleOrderByDepthAscSortOrderAsc(YnType isVisible);

	// 관리용: is_visible 무관 전체 메뉴를 depth, sort_order 순으로 조회
	List<Menu> findAllByOrderByDepthAscSortOrderAsc();

	// menu_code 존재 여부 확인 (신규 등록 중복 체크)
	boolean existsByMenuCode(String menuCode);

	// menu_code 존재 여부 확인 — 특정 id 제외 (수정 시 자기 자신 제외)
	boolean existsByMenuCodeAndIdNot(String menuCode, Long id);

	// menu_alias 존재 여부 확인 (신규 등록 중복 체크)
	boolean existsByMenuAlias(String menuAlias);

	// menu_alias 존재 여부 확인 — 특정 id 제외 (수정 시 자기 자신 제외)
	boolean existsByMenuAliasAndIdNot(String menuAlias, Long id);

	// url 존재 여부 확인 (신규 등록 중복 체크 — 빈 문자열/null은 서비스에서 사전 필터링)
	boolean existsByUrl(String url);

	// url 존재 여부 확인 — 특정 id 제외 (수정 시 자기 자신 제외)
	boolean existsByUrlAndIdNot(String url, Long id);

	// 동일 부모 내 최대 sort_order 조회 (신규 메뉴 정렬 순서 계산용)
	@Query("SELECT MAX(m.sortOrder) FROM Menu m WHERE " +
		   "(:parentId IS NULL AND m.parent IS NULL) OR " +
		   "(:parentId IS NOT NULL AND m.parent.id = :parentId)")
	Optional<Integer> findMaxSortOrderByParentId(@Param("parentId") Long parentId);
}
