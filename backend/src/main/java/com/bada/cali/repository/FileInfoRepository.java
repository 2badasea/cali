package com.bada.cali.repository;

import com.bada.cali.common.enums.YnType;
import com.bada.cali.dto.FileInfoDTO;
import com.bada.cali.entity.FileInfo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

@Repository
public interface FileInfoRepository extends JpaRepository<FileInfo, Long> {
	
	// 여러 ref_table_id에 해당하는 파일 목록 일괄 조회 (서명이미지 존재여부 확인 등에 사용)
	List<FileInfo> findByRefTableNameAndRefTableIdInAndIsVisible(
			String refTableName,
			Collection<Long> refTableIds,
			YnType isVisible
	);

	// 특정 조건에 맞는 파일 목록 리턴
	List<FileInfo> findByRefTableNameAndRefTableIdAndIsVisible(
			String refTableName,
			Long refTableId,
			YnType isVisible
	);

	// 특정 조건에 맞는 파일 개수 리턴 (COUNT 쿼리)
	long countByRefTableNameAndRefTableIdAndIsVisible(
			String refTableName,
			Long refTableId,
			YnType isVisible
	);
	
	// 특정 조건에 맞는 파일 개수 리턴
	@Query("""
			select f
			from FileInfo as f
			where f.isVisible = :isVisible
			and f.refTableName = :refTableName
			and f.refTableId = :refTableId
			and (:dir IS NOT NULL AND f.dir = :dir)
	""")
	List<FileInfo> getFileInfoList(
			@Param("refTableName") String refTableName,
			@Param("refTableId") Long refTableId,
			@Param("dir") String dir,
			@Param("isVisible") YnType isVisible
	);
	
	// 단일 파일 정보를 가져오는 함수
	@Query("""
					select f
					from FileInfo as f
					where 1 = 1
					AND (:isVisible IS NULL OR f.isVisible = :isVisible)
					AND (:refTableName IS NULL OR f.refTableName = :refTableName)
					AND (:isVisible IS NULL OR f.refTableId = :refTableId)
					AND (:dir IS NULL OR f.dir = :dir)
			""")
	FileInfo getFileInfo(
			@Param("refTableName") String refTableName,
			@Param("refTableId") Long refTableId,
			@Param("dir") String dir,
			@Param("isVisible") YnType isVisible
	);
	
	
	
	// FileInfoDTO 클래스 내 inner class FileListRes에서 순서대로 매핑이 됨
	@Query("select new com.bada.cali.dto.FileInfoDTO$FileListRes(" +
			"  f.id, " +
			"  f.originName, " +
			"  m.name, " +          // Member 엔티티의 필드명에 맞게 수정 (예: m.memberName)
			"  f.createDatetime" +
			") " +
			"from FileInfo f " +
			"join f.createMember m " +
			"where f.refTableName = :refTableName " +
			"and f.refTableId = :refTableId " +
			"and f.isVisible = :isVisible")
	List<FileInfoDTO.FileListRes> getFileInfosWithJoin(
			@Param("refTableName") String refTableName,
			@Param("refTableId") Long refTableId,
			@Param("isVisible") YnType isVisible
	);
	
	// 파일 삭제 처리
	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query("""
			       update FileInfo f
			          set f.isVisible = :isVisible,
			              f.deleteDatetime = :deleteDatetime,
			              f.deleteMemberId = :deleteMemberId
			        where f.id = :fileId
			""")
	int deleteFile(@Param("fileId") Long fileId,
					  @Param("isVisible") YnType isVisible,
					  @Param("deleteDatetime") LocalDateTime deleteDatetime,
					  @Param("deleteMemberId") Long deleteMemberId
	);
	
	// 여러 refTableId에 해당하는 파일 일괄 소프트삭제
	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query("""
        update FileInfo f
           set f.isVisible = :isVisible,
               f.deleteDatetime = :deleteDatetime,
               f.deleteMemberId = :deleteMemberId
         where f.refTableName = :refTableName
           and f.refTableId in :refTableIds
    """)
	int softDeleteByRefTableIds(
			@Param("refTableName") String refTableName,
			@Param("refTableIds") Collection<Long> refTableIds,
			@Param("isVisible") YnType isVisible,
			@Param("deleteDatetime") LocalDateTime deleteDatetime,
			@Param("deleteMemberId") Long deleteMemberId
	);

	// 파일 목록 조회 (createDatetime 내림차순)
	@Query("select new com.bada.cali.dto.FileInfoDTO$FileListRes(" +
			"  f.id, " +
			"  f.originName, " +
			"  m.name, " +
			"  f.createDatetime" +
			") " +
			"from FileInfo f " +
			"join f.createMember m " +
			"where f.refTableName = :refTableName " +
			"and f.refTableId = :refTableId " +
			"and f.isVisible = :isVisible " +
			"order by f.createDatetime desc")
	List<FileInfoDTO.FileListRes> getFileInfosWithJoinOrderByDateDesc(
			@Param("refTableName") String refTableName,
			@Param("refTableId") Long refTableId,
			@Param("isVisible") YnType isVisible
	);

	@Transactional
	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query("""
        update FileInfo f
           set f.isVisible = :isVisible,
               f.deleteDatetime = :now,
               f.deleteMemberId = :userId
         where f.refTableName = :refTableName
           and f.refTableId = :refTableId
           and f.dir = :dir
    """)
	int softDeleteVisibleByRefAndDir(
			@Param("refTableName") String refTableName,
			@Param("refTableId") Long refTableId,
			@Param("dir") String dir,
			@Param("isVisible") YnType isVisible,
			@Param("now") LocalDateTime now,
			@Param("userId") Long userId
	);

	/**
	 * 특정 ref에 해당하는 file_info 전체 소프트삭제.
	 * 수리/불가 처리 시 origin·signed 구분 없이 모든 파일을 논리 삭제할 때 사용한다.
	 */
	@Transactional
	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query("""
        update FileInfo f
           set f.isVisible = :isVisible,
               f.deleteDatetime = :now,
               f.deleteMemberId = :userId
         where f.refTableName = :refTableName
           and f.refTableId = :refTableId
           and f.isVisible = 'y'
    """)
	int softDeleteAllByRef(
			@Param("refTableName") String refTableName,
			@Param("refTableId") Long refTableId,
			@Param("isVisible") YnType isVisible,
			@Param("now") LocalDateTime now,
			@Param("userId") Long userId
	);

	/**
	 * 특정 ref + name 목록에 해당하는 file_info만 소프트삭제.
	 * WORK_APPROVAL 콜백 SUCCESS 시 signed_xlsx / signed_pdf 파일만 삭제하고
	 * origin 파일 file_info는 보존하기 위해 사용한다.
	 */
	@Transactional
	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query("""
        update FileInfo f
           set f.isVisible = :isVisible,
               f.deleteDatetime = :now,
               f.deleteMemberId = :userId
         where f.refTableName = :refTableName
           and f.refTableId = :refTableId
           and f.name in :names
           and f.isVisible = 'y'
    """)
	int softDeleteByRefAndNames(
			@Param("refTableName") String refTableName,
			@Param("refTableId") Long refTableId,
			@Param("names") java.util.List<String> names,
			@Param("isVisible") YnType isVisible,
			@Param("now") LocalDateTime now,
			@Param("userId") Long userId
	);

	/**
	 * 여러 refTableId + name 목록에 해당하는 file_info 일괄 소프트삭제.
	 * 기술책임자결재 반려 시 signed_xlsx / signed_pdf 파일만 다건 삭제할 때 사용한다.
	 */
	@Transactional
	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query("""
        update FileInfo f
           set f.isVisible = :isVisible,
               f.deleteDatetime = :now,
               f.deleteMemberId = :userId
         where f.refTableName = :refTableName
           and f.refTableId in :refTableIds
           and f.name in :names
           and f.isVisible = 'y'
    """)
	int softDeleteByRefTableIdsAndNames(
			@Param("refTableName") String refTableName,
			@Param("refTableIds") Collection<Long> refTableIds,
			@Param("names") java.util.List<String> names,
			@Param("isVisible") YnType isVisible,
			@Param("now") LocalDateTime now,
			@Param("userId") Long userId
	);


}
