package com.bada.cali.repository;

import com.bada.cali.entity.MemberPermissionRead;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MemberPermissionReadRepository extends JpaRepository<MemberPermissionRead, Long> {
	
	// 이 멤버가 읽기 권한 가진 메뉴 id 목록만 가져오기
	@Query("select mpr.menu.id " +
			"from MemberPermissionRead mpr " +
			"where mpr.member.id = :memberId")
	List<Long> findMenuIdsByMemberId(@Param("memberId") Long memberId);

	// 특정 멤버의 모든 메뉴 권한 삭제 (저장 시 replace 방식에 사용)
	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query("DELETE FROM MemberPermissionRead mpr WHERE mpr.member.id = :memberId")
	void deleteAllByMemberId(@Param("memberId") Long memberId);

	// 특정 메뉴에 대한 모든 읽기 권한 삭제 (메뉴 삭제 시 고아 권한 데이터 정리용)
	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query("DELETE FROM MemberPermissionRead mpr WHERE mpr.menu.id = :menuId")
	void deleteAllByMenuId(@Param("menuId") Long menuId);

}
