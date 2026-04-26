package com.bada.cali.repository;

import com.bada.cali.common.enums.OrderType;
import com.bada.cali.common.enums.YnType;
import com.bada.cali.dto.ReportDTO;
import com.bada.cali.entity.Report;
import com.bada.cali.repository.projection.LastManageNoByType;
import com.bada.cali.repository.projection.LastReportNumByOrderType;
import com.bada.cali.repository.projection.OrderDetailsList;
import com.bada.cali.repository.projection.ReportCountRow;
import com.bada.cali.repository.projection.ManagerApprovalListRow;
import com.bada.cali.repository.projection.WorkApprovalListRow;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ReportRepository extends JpaRepository<Report, Long> {
	
	// 접수구분별 가장 마지막 성적서번호 호출
	@Query(value = """
			SELECT x.order_type AS orderType, x.report_num AS reportNum
			FROM (
			  SELECT
			    r.order_type,
			    r.report_num,
			    ROW_NUMBER() OVER (PARTITION BY r.order_type ORDER BY r.id DESC) AS rn
			  FROM report r
			  WHERE r.cali_order_id = :caliOrderId
			    AND r.is_visible = 'y'
			    AND r.parent_id IS NULL
			    AND r.parent_scale_id IS NULL
			) x
			WHERE x.rn = 1
			""", nativeQuery = true)
	List<LastReportNumByOrderType> findLastReportNumsByOrderType(@Param("caliOrderId") Long caliOrderId);
	
	// 접수구분별 가장 마지막 관리번호를 가져온다.
	@Query(value = """
					SELECT x.order_type AS orderType, x.manage_no AS manageNo
						FROM (
							    SELECT r.order_type,
									r.manage_no,
										ROW_NUMBER() over (PARTITION BY r.order_type ORDER BY r.id DESC) as rn
								FROM report AS r
									LEFT JOIN cali_order o ON o.id = r.cali_order_id
								WHERE r.is_visible = 'y'
									AND YEAR(o.order_date) = :year
									AND r.parent_id IS NULL
										AND r.parent_scale_id IS NULL
			
							) x
							where x.rn = 1;
			""", nativeQuery = true)
	List<LastManageNoByType> findLastManageNoByOrderType(@Param("year") int year);
	
	
	// 접수상세내역 리스트 (인터페이스 프로젝션으로 가져옴)
	@Query("""
			    select
						r.id as id,
						r.reportType as reportType,
						r.orderType as orderType,
			
						r.middleItemCodeId as middleItemCodeId,
						r.smallItemCodeId as smallItemCodeId,
			
						r.reportNum as reportNum,
						r.itemName as itemName,
						r.itemFormat as itemFormat,
						r.itemNum as itemNum,
			
						r.itemCaliCycle as itemCaliCycle,
						r.itemMakeAgent as itemMakeAgent,
						r.manageNo as manageNo,
			
						r.reportStatus as reportStatus,
						r.remark as remark,
						r.caliFee as caliFee,
						r.statusRemark as statusRemark,
			
						r.approvalDatetime as approvalDatetime,
						r.workDatetime as workDatetime,
			
						r.workStatus as workStatus,
						r.approvalStatus as approvalStatus,
						mic.codeNum as middleCodeNum,
						sic.codeNum as smallCodeNum
			    	from Report r
					LEFT JOIN ItemCode mic ON mic.id = r.middleItemCodeId
					LEFT JOIN ItemCode sic ON sic.id = r.smallItemCodeId
			    	where r.isVisible = 'y'
			    	and r.parentId IS NULL
			    	and r.parentScaleId IS NULL
					and r.caliOrderId = :caliOrderId
					and (:middleItemCodeId IS NULL OR r.middleItemCodeId = :middleItemCodeId)
					and (:smallItemCodeId IS NULL OR r.smallItemCodeId = :smallItemCodeId)
			    	and (:orderType IS NULL OR r.orderType = :orderType)
			    	and (
			    		:keyword = '' OR
			    		(
			    			(:searchType = 'reportNum' AND r.reportNum LIKE concat('%', :keyword, '%'))
			    			OR (:searchType = 'manageNo' AND r.manageNo LIKE concat('%', :keyword, '%'))
			    			OR (:searchType = 'itemName' AND r.itemName LIKE concat('%', :keyword, '%'))
			    			OR (:searchType = 'itemMakeAgent' AND r.itemMakeAgent LIKE concat('%', :keyword, '%'))
			    			OR (:searchType = 'itemFormat' AND r.itemFormat LIKE concat('%', :keyword, '%'))
			    			OR (:searchType = 'itemNum' AND r.itemNum LIKE concat('%', :keyword, '%'))
			    			OR (:searchType = 'all' AND (
														    			r.reportNum LIKE concat('%', :keyword, '%')
														    			OR r.manageNo LIKE concat('%', :keyword, '%')
														    			OR r.itemName LIKE concat('%', :keyword, '%')
														    			OR r.itemMakeAgent LIKE concat('%', :keyword, '%')
														    			OR r.itemFormat LIKE concat('%', :keyword, '%')
														    			OR r.itemNum LIKE concat('%', :keyword, '%')
			    									)
			    			)
			    		)
			    	)
			    	AND (:statusType IS NULL OR (
			    			(:statusType = 'impossible' AND r.reportStatus = 'IMPOSSIBLE')
			    			OR  (:statusType = 'return' AND r.reportStatus = 'REJECTED')
			    			OR  (:statusType = 'wait' AND r.workDatetime IS NULL AND r.approvalDatetime IS NULL)
			    			OR (:statusType = 'progress' AND r.workDatetime IS NOT NULL AND r.approvalDatetime IS NULL)
			    			OR (:statusType = 'success' AND r.workDatetime IS NOT NULL AND r.approvalDatetime IS NOT NULL)
			    		)
			    	)
					ORDER BY
						case
							 when r.orderType = com.bada.cali.common.enums.OrderType.ACCREDDIT then 0
							 when r.orderType = com.bada.cali.common.enums.OrderType.UNACCREDDIT then 1
							 when r.orderType = com.bada.cali.common.enums.OrderType.TESTING then 2
			            	else 9
			         	end asc,
			         	r.id asc
			""")
	List<OrderDetailsList> searchOrderDetails(
			@Param("orderType") OrderType orderType,
			@Param("statusType") String statusType,	// 진행전체, 대기, 취소, 불가, 반려, 진행중, 완료
			@Param("searchType") String searchType,
			@Param("keyword") String keyword,
			@Param("caliOrderId") Long caliOrderId,
			@Param("middleItemCodeId") Long middleItemCodeId,
			@Param("smallItemCodeId") Long smallItemCodeId,
			Pageable pageable
			);

	/**
	 * searchOrderDetails 와 동일한 WHERE 조건으로 전체 건수를 집계한다.
	 * - Toast Grid 서버 페이징(useClient: false)의 totalCount 제공용
	 * - 자식성적서(parentId/parentScaleId IS NULL) 제외, 삭제 제외(isVisible = 'y') 조건 포함
	 */
	@Query("""
			SELECT COUNT(r)
			FROM Report r
			WHERE r.isVisible = 'y'
			AND r.parentId IS NULL
			AND r.parentScaleId IS NULL
			AND r.caliOrderId = :caliOrderId
			AND (:middleItemCodeId IS NULL OR r.middleItemCodeId = :middleItemCodeId)
			AND (:smallItemCodeId IS NULL OR r.smallItemCodeId = :smallItemCodeId)
			AND (:orderType IS NULL OR r.orderType = :orderType)
			AND (
				:keyword = '' OR
				(
					(:searchType = 'reportNum' AND r.reportNum LIKE concat('%', :keyword, '%'))
					OR (:searchType = 'manageNo' AND r.manageNo LIKE concat('%', :keyword, '%'))
					OR (:searchType = 'itemName' AND r.itemName LIKE concat('%', :keyword, '%'))
					OR (:searchType = 'itemMakeAgent' AND r.itemMakeAgent LIKE concat('%', :keyword, '%'))
					OR (:searchType = 'itemFormat' AND r.itemFormat LIKE concat('%', :keyword, '%'))
					OR (:searchType = 'itemNum' AND r.itemNum LIKE concat('%', :keyword, '%'))
					OR (:searchType = 'all' AND (
						r.reportNum LIKE concat('%', :keyword, '%')
						OR r.manageNo LIKE concat('%', :keyword, '%')
						OR r.itemName LIKE concat('%', :keyword, '%')
						OR r.itemMakeAgent LIKE concat('%', :keyword, '%')
						OR r.itemFormat LIKE concat('%', :keyword, '%')
						OR r.itemNum LIKE concat('%', :keyword, '%')
					))
				)
			)
			AND (:statusType IS NULL OR (
				(:statusType = 'impossible' AND r.reportStatus = 'IMPOSSIBLE')
				OR (:statusType = 'return' AND r.reportStatus = 'REJECTED')
				OR (:statusType = 'wait' AND r.workDatetime IS NULL AND r.approvalDatetime IS NULL)
				OR (:statusType = 'progress' AND r.workDatetime IS NOT NULL AND r.approvalDatetime IS NULL)
				OR (:statusType = 'success' AND r.workDatetime IS NOT NULL AND r.approvalDatetime IS NOT NULL)
			))
			""")
	long countOrderDetails(
			@Param("orderType") OrderType orderType,
			@Param("statusType") String statusType,
			@Param("searchType") String searchType,
			@Param("keyword") String keyword,
			@Param("caliOrderId") Long caliOrderId,
			@Param("middleItemCodeId") Long middleItemCodeId,
			@Param("smallItemCodeId") Long smallItemCodeId
	);
	
	
	/**
	 * @param orderType
	 * @param isAgcy
	 * @param reportIds
	 * @param pageable
	 * @return
	 */
	@Query("""
			SELECT r
			FROM Report r
			WHERE r.isVisible = 'y'
			AND r.parentId IS NULL
			AND r.parentScaleId IS NULL
			AND (
				(:isAgcy = true AND r.id IN :reportIds) OR (:isAgcy = false AND r.orderType = :orderType)
			)
			ORDER BY r.id DESC
	""")
	List<Report> getDeleteCheckReport(
			@Param("orderType") OrderType orderType,
			@Param("isAgcy") boolean isAgcy,
			@Param("reportIds") List<Long> reportIds,
			Pageable pageable
	);
	
	// 삭제대상 id를 가진 성적서와 이 성적서를 바라보는 재발행, 자식성적서를 모두 가져온다.
	List<Report> findByIdInOrParentIdInOrParentScaleIdIn(List<Long> deleteIds, List<Long> deleteIds1, List<Long> deleteIds2);
	
	/**
	 * 성적서 수정 모달 내 데이터 조회 (접수의 데이터도 조인하여 가져온다)
	 * @param id
	 * @return
	 */
	@Query("""
				select new com.bada.cali.dto.ReportDTO$ReportInfo(
					 r.id,
					 r.orderType,
					 r.caliDate,
					 r.reportLang,
					 r.priorityType,
					 r.caliType,
					 r.caliTakeType,
					 r.manageNo,
					
					 r.middleItemCodeId,
					 r.smallItemCodeId,
					 r.workMemberId,
					 r.approvalMemberId,
					
					 r.itemId,
					 r.itemName,
					 r.itemNameEn,
					 r.itemFormat,
					 r.itemNum,
					 r.itemCaliCycle,
					 r.itemMakeAgent,
					 r.itemMakeAgentEn,
					 r.remark,
					 r.caliFee,
					 r.additionalFee,
					 r.additionalFeeCause,
					 r.request,
					 r.environmentInfo,
					 r.tracestatementInfo,
					
					 o.custAgent,
					 o.custAgentAddr,
					 o.custManager,
					 o.custManagerTel,
					 o.reportAgent,
					 o.reportAgentEn,
					 o.reportAgentAddr,
					 o.reportManager,
					 o.reportManagerTel
				)
				from Report r
				JOIN CaliOrder o ON o.id = r.caliOrderId
				where r.id = :id
							and r.isVisible = 'y'
			""")
	ReportDTO.ReportInfo getReportInfo(@Param("id") Long id);
	
	@Query("""
			select new com.bada.cali.dto.ReportDTO$ChildReportInfo(
				    r.id,
					r.middleItemCodeId,
					r.smallItemCodeId,
					r.itemId,
					r.itemName,
					r.itemNameEn,
					r.itemFormat,
					r.itemNum,
					r.itemCaliCycle,
					r.itemMakeAgent,
					r.itemMakeAgentEn,
					r.remark,
					r.caliFee,
					r.additionalFee,
					r.additionalFeeCause
			)
			from Report  r
			where r.parentScaleId = :parentScaleId
				and r.isVisible = 'y'
			ORDER BY r.id ASC
	""")
	List<ReportDTO.ChildReportInfo> getChildReport(@Param("parentScaleId") Long parentScaleId);
	
	
	List<Report> findByMiddleItemCodeIdInAndIsVisible(List<Long> attr0, YnType isVisible);

	/** 성적서번호 목록으로 조회 (업로드 파일명 기반 성적서 매칭 용도) */
	List<Report> findByReportNumInAndIsVisible(List<String> reportNums, YnType isVisible);

	/**
	 * 실무자결재 목록 조회 (native query)
	 * - SELF 타입 자체성적서만, 삭제 제외, 자식성적서 제외, 재발행 성적서 포함
	 * - cali_order, item_code(소분류), member(작성자/실무자/기술책임자) LEFT JOIN
	 * - 정렬: 접수 id DESC → 공인/비공인/시험 순 → 성적서번호 마지막 순번 오름차순
	 *
	 * @param reportStatus  진행상태 필터 (null이면 전체)
	 * @param workStatus    실무자 결재상태 필터 (null이면 전체)
	 * @param orderType     접수구분 필터 (null이면 전체)
	 * @param middleItemCodeId 중분류코드 id (null이면 전체)
	 * @param smallItemCodeId  소분류코드 id (null이면 전체)
	 * @param searchType    키워드 검색 대상 컬럼
	 * @param keyword       검색 키워드 (빈값이면 전체)
	 * @param pageable      페이징 (LIMIT/OFFSET 적용)
	 * @return 실무자결재 목록 Projection 리스트
	 */
	@Query(value = """
			SELECT
			    r.id                                                  AS id,
			    r.manage_no                                           AS manageNo,
			    r.small_item_code_id                                  AS smallItemCodeId,
			    sic.code_num                                          AS smallCodeNum,
			    o.order_date                                          AS orderDate,
			    r.expect_complete_date                                AS expectCompleteDate,
			    r.report_num                                          AS reportNum,
			    o.cust_agent                                          AS custAgent,
			    o.report_agent                                        AS reportAgent,
			    r.item_name                                           AS itemName,
			    r.item_num                                            AS itemNum,
			    r.item_make_agent                                     AS itemMakeAgent,
			    r.item_format                                         AS itemFormat,
			    r.report_status                                       AS reportStatus,
			    r.work_status                                         AS workStatus,
			    r.write_status                                        AS writeStatus,
			    r.order_type                                          AS orderType,
			    wr.name                                               AS writeMemberName,
			    r.work_member_id                                     AS workMemberId,
			    wm.name                                               AS workMemberName,
			    am.name                                               AS approvalMemberName,
			    (SELECT fi.id FROM file_info fi
			         WHERE fi.ref_table_name = 'report'
			           AND fi.ref_table_id   = r.id
			           AND fi.name           = 'origin'
			           AND fi.is_visible     = 'y'
			         LIMIT 1)                                         AS originFileId,
			    (SELECT fi.id FROM file_info fi
			         WHERE fi.ref_table_name = 'report'
			           AND fi.ref_table_id   = r.id
			           AND fi.name           = 'signed_xlsx'
			           AND fi.is_visible     = 'y'
			         LIMIT 1)                                         AS excelFileId,
			    (SELECT fi.id FROM file_info fi
			         WHERE fi.ref_table_name = 'report'
			           AND fi.ref_table_id   = r.id
			           AND fi.name           = 'signed_pdf'
			           AND fi.is_visible     = 'y'
			         LIMIT 1)                                         AS pdfFileId
			FROM report r
			LEFT JOIN cali_order o  ON o.id  = r.cali_order_id
			LEFT JOIN item_code sic ON sic.id = r.small_item_code_id
			LEFT JOIN member wr     ON wr.id  = r.write_member_id
			LEFT JOIN member wm     ON wm.id  = r.work_member_id
			LEFT JOIN member am     ON am.id  = r.approval_member_id
			WHERE r.is_visible        = 'y'
			  AND r.report_type       = 'SELF'
			  AND r.parent_scale_id   IS NULL
			  AND (:reportStatus IS NULL OR r.report_status  = :reportStatus)
			  AND (
			      (:workStatus IS NOT NULL AND r.work_status = :workStatus)
			      OR (:workStatus IS NULL AND r.work_status NOT IN ('FAIL', 'SUCCESS'))
			  )
			  AND (:writeStatus  IS NULL OR r.write_status   = :writeStatus)
			  AND (:orderType    IS NULL OR r.order_type     = :orderType)
			  AND (:middleItemCodeId IS NULL OR r.middle_item_code_id = :middleItemCodeId)
			  AND (:smallItemCodeId  IS NULL OR r.small_item_code_id  = :smallItemCodeId)
			  AND (
			      :keyword = '' OR (
			          (:searchType = 'reportNum'      AND r.report_num     LIKE CONCAT('%', :keyword, '%'))
			          OR (:searchType = 'custAgent'   AND o.cust_agent     LIKE CONCAT('%', :keyword, '%'))
			          OR (:searchType = 'reportAgent' AND o.report_agent   LIKE CONCAT('%', :keyword, '%'))
			          OR (:searchType = 'workMember'  AND wm.name          LIKE CONCAT('%', :keyword, '%'))
			          OR (:searchType = 'approvalMember' AND am.name       LIKE CONCAT('%', :keyword, '%'))
			          OR (:searchType = 'itemName'    AND r.item_name      LIKE CONCAT('%', :keyword, '%'))
			          OR (:searchType = 'itemFormat'  AND r.item_format    LIKE CONCAT('%', :keyword, '%'))
			          OR (:searchType = 'itemMakeAgent' AND r.item_make_agent LIKE CONCAT('%', :keyword, '%'))
			          OR (:searchType = 'itemNum'     AND r.item_num       LIKE CONCAT('%', :keyword, '%'))
			          OR (:searchType = 'manageNo'    AND r.manage_no      LIKE CONCAT('%', :keyword, '%'))
			          OR (:searchType = 'all' AND (
			                r.report_num         LIKE CONCAT('%', :keyword, '%')
			                OR o.cust_agent      LIKE CONCAT('%', :keyword, '%')
			                OR o.report_agent    LIKE CONCAT('%', :keyword, '%')
			                OR wm.name           LIKE CONCAT('%', :keyword, '%')
			                OR am.name           LIKE CONCAT('%', :keyword, '%')
			                OR r.item_name       LIKE CONCAT('%', :keyword, '%')
			                OR r.item_format     LIKE CONCAT('%', :keyword, '%')
			                OR r.item_make_agent LIKE CONCAT('%', :keyword, '%')
			                OR r.item_num        LIKE CONCAT('%', :keyword, '%')
			                OR r.manage_no       LIKE CONCAT('%', :keyword, '%')
			          ))
			      )
			  )
			ORDER BY
			    r.cali_order_id DESC,
			    CASE
			        WHEN r.order_type = 'ACCREDDIT'   THEN 0
			        WHEN r.order_type = 'UNACCREDDIT' THEN 1
			        WHEN r.order_type = 'TESTING'     THEN 2
			        ELSE 9
			    END ASC,
			    CAST(REGEXP_REPLACE(SUBSTRING_INDEX(r.report_num, '-', -1), '[^0-9]', '') AS UNSIGNED) ASC
			""", nativeQuery = true)
	List<WorkApprovalListRow> searchWorkApprovalList(
			@Param("reportStatus") String reportStatus,
			@Param("workStatus") String workStatus,
			@Param("writeStatus") String writeStatus,
			@Param("orderType") String orderType,
			@Param("middleItemCodeId") Long middleItemCodeId,
			@Param("smallItemCodeId") Long smallItemCodeId,
			@Param("searchType") String searchType,
			@Param("keyword") String keyword,
			Pageable pageable
	);

	/**
	 * 실무자결재 목록 전체 건수 조회 (searchWorkApprovalList 와 동일 WHERE 조건)
	 * - Toast Grid 서버 페이징 totalCount 제공용
	 */
	@Query(value = """
			SELECT COUNT(*)
			FROM report r
			LEFT JOIN cali_order o  ON o.id  = r.cali_order_id
			LEFT JOIN member wm     ON wm.id  = r.work_member_id
			LEFT JOIN member am     ON am.id  = r.approval_member_id
			WHERE r.is_visible        = 'y'
			  AND r.report_type       = 'SELF'
			  AND r.parent_scale_id   IS NULL
			  AND (:reportStatus IS NULL OR r.report_status  = :reportStatus)
			  AND (
			      (:workStatus IS NOT NULL AND r.work_status = :workStatus)
			      OR (:workStatus IS NULL AND r.work_status NOT IN ('FAIL', 'SUCCESS'))
			  )
			  AND (:writeStatus  IS NULL OR r.write_status   = :writeStatus)
			  AND (:orderType    IS NULL OR r.order_type     = :orderType)
			  AND (:middleItemCodeId IS NULL OR r.middle_item_code_id = :middleItemCodeId)
			  AND (:smallItemCodeId  IS NULL OR r.small_item_code_id  = :smallItemCodeId)
			  AND (
			      :keyword = '' OR (
			          (:searchType = 'reportNum'      AND r.report_num     LIKE CONCAT('%', :keyword, '%'))
			          OR (:searchType = 'custAgent'   AND o.cust_agent     LIKE CONCAT('%', :keyword, '%'))
			          OR (:searchType = 'reportAgent' AND o.report_agent   LIKE CONCAT('%', :keyword, '%'))
			          OR (:searchType = 'workMember'  AND wm.name          LIKE CONCAT('%', :keyword, '%'))
			          OR (:searchType = 'approvalMember' AND am.name       LIKE CONCAT('%', :keyword, '%'))
			          OR (:searchType = 'itemName'    AND r.item_name      LIKE CONCAT('%', :keyword, '%'))
			          OR (:searchType = 'itemFormat'  AND r.item_format    LIKE CONCAT('%', :keyword, '%'))
			          OR (:searchType = 'itemMakeAgent' AND r.item_make_agent LIKE CONCAT('%', :keyword, '%'))
			          OR (:searchType = 'itemNum'     AND r.item_num       LIKE CONCAT('%', :keyword, '%'))
			          OR (:searchType = 'manageNo'    AND r.manage_no      LIKE CONCAT('%', :keyword, '%'))
			          OR (:searchType = 'all' AND (
			                r.report_num         LIKE CONCAT('%', :keyword, '%')
			                OR o.cust_agent      LIKE CONCAT('%', :keyword, '%')
			                OR o.report_agent    LIKE CONCAT('%', :keyword, '%')
			                OR wm.name           LIKE CONCAT('%', :keyword, '%')
			                OR am.name           LIKE CONCAT('%', :keyword, '%')
			                OR r.item_name       LIKE CONCAT('%', :keyword, '%')
			                OR r.item_format     LIKE CONCAT('%', :keyword, '%')
			                OR r.item_make_agent LIKE CONCAT('%', :keyword, '%')
			                OR r.item_num        LIKE CONCAT('%', :keyword, '%')
			                OR r.manage_no       LIKE CONCAT('%', :keyword, '%')
			          ))
			      )
			  )
			""", nativeQuery = true)
	long countWorkApprovalList(
			@Param("reportStatus") String reportStatus,
			@Param("workStatus") String workStatus,
			@Param("writeStatus") String writeStatus,
			@Param("orderType") String orderType,
			@Param("middleItemCodeId") Long middleItemCodeId,
			@Param("smallItemCodeId") Long smallItemCodeId,
			@Param("searchType") String searchType,
			@Param("keyword") String keyword
	);

	/**
	 * 기술책임자결재 목록 조회 (native query)
	 * - work_status = 'SUCCESS' (실무자결재 완료) 성적서만 대상
	 * - approval_status 필터: null이면 SUCCESS 제외 전체, 값이 있으면 해당 상태만
	 * - dateType: cali(교정일), receipt(접수일), approval(결재일)
	 * - 정렬: 교정일 DESC 기본
	 */
	@Query(value = """
			SELECT
			    r.id                                                  AS id,
			    r.report_num                                          AS reportNum,
			    sic.code_num                                          AS codeSmallName,
			    o.order_date                                          AS receiptDate,
			    r.expect_complete_date                                AS expectCompleteDate,
			    o.cust_agent                                          AS agentName,
			    o.report_agent                                        AS publishName,
			    r.item_name                                           AS itemName,
			    r.item_num                                            AS itemNum,
			    r.item_make_agent                                     AS manufacturer,
			    r.item_format                                         AS modelType,
			    r.cali_date                                           AS caliDate,
			    r.report_lang                                         AS reportLang,
			    wm.name                                               AS workMemberName,
			    am.name                                               AS approvalMemberName,
			    r.approval_status                                     AS approvalStatus,
			    r.approval_datetime                                   AS approvalDatetime,
			    (SELECT fi.id FROM file_info fi
			         WHERE fi.ref_table_name = 'report'
			           AND fi.ref_table_id   = r.id
			           AND fi.name           = 'origin'
			           AND fi.is_visible     = 'y'
			         LIMIT 1)                                         AS originFileId,
			    (SELECT fi.id FROM file_info fi
			         WHERE fi.ref_table_name = 'report'
			           AND fi.ref_table_id   = r.id
			           AND fi.name           = 'signed_xlsx'
			           AND fi.is_visible     = 'y'
			         LIMIT 1)                                         AS signedXlsxFileId,
			    (SELECT fi.id FROM file_info fi
			         WHERE fi.ref_table_name = 'report'
			           AND fi.ref_table_id   = r.id
			           AND fi.name           = 'signed_pdf'
			           AND fi.is_visible     = 'y'
			         LIMIT 1)                                         AS signedPdfFileId
			FROM report r
			LEFT JOIN cali_order o  ON o.id  = r.cali_order_id
			LEFT JOIN item_code sic ON sic.id = r.small_item_code_id
			LEFT JOIN member wm     ON wm.id  = r.work_member_id
			LEFT JOIN member am     ON am.id  = r.approval_member_id
			WHERE r.is_visible        = 'y'
			  AND r.report_type       = 'SELF'
			  AND r.parent_scale_id   IS NULL
			  AND r.work_status       = 'SUCCESS'
			  AND (
			      (:approvalStatus IS NOT NULL AND r.approval_status = :approvalStatus)
			      OR (:approvalStatus IS NULL AND r.approval_status != 'SUCCESS')
			  )
			  AND (:middleItemCodeId IS NULL OR r.middle_item_code_id = :middleItemCodeId)
			  AND (:smallItemCodeId  IS NULL OR r.small_item_code_id  = :smallItemCodeId)
			  AND (
			      :startDate IS NULL OR :endDate IS NULL OR (
			          (:dateType = 'cali'     AND r.cali_date          BETWEEN :startDate AND :endDate)
			          OR (:dateType = 'receipt'  AND o.order_date      BETWEEN :startDate AND :endDate)
			          OR (:dateType = 'approval' AND r.approval_datetime BETWEEN CONCAT(:startDate, ' 00:00:00') AND CONCAT(:endDate, ' 23:59:59'))
			      )
			  )
			  AND (
			      :keyword = '' OR (
			          (:searchType = 'reportNum'      AND r.report_num       LIKE CONCAT('%', :keyword, '%'))
			          OR (:searchType = 'agentName'   AND o.cust_agent       LIKE CONCAT('%', :keyword, '%'))
			          OR (:searchType = 'publishName' AND o.report_agent     LIKE CONCAT('%', :keyword, '%'))
			          OR (:searchType = 'itemName'    AND r.item_name        LIKE CONCAT('%', :keyword, '%'))
			          OR (:searchType = 'itemNum'     AND r.item_num         LIKE CONCAT('%', :keyword, '%'))
			          OR (:searchType = 'manufacturer' AND r.item_make_agent LIKE CONCAT('%', :keyword, '%'))
			          OR (:searchType = 'modelType'   AND r.item_format      LIKE CONCAT('%', :keyword, '%'))
			          OR (:searchType = 'workMember'  AND wm.name            LIKE CONCAT('%', :keyword, '%'))
			          OR (:searchType = 'approvalMember' AND am.name         LIKE CONCAT('%', :keyword, '%'))
			          OR (:searchType = 'all' AND (
			                r.report_num         LIKE CONCAT('%', :keyword, '%')
			                OR o.cust_agent      LIKE CONCAT('%', :keyword, '%')
			                OR o.report_agent    LIKE CONCAT('%', :keyword, '%')
			                OR r.item_name       LIKE CONCAT('%', :keyword, '%')
			                OR r.item_num        LIKE CONCAT('%', :keyword, '%')
			                OR r.item_make_agent LIKE CONCAT('%', :keyword, '%')
			                OR r.item_format     LIKE CONCAT('%', :keyword, '%')
			                OR wm.name           LIKE CONCAT('%', :keyword, '%')
			                OR am.name           LIKE CONCAT('%', :keyword, '%')
			          ))
			      )
			  )
			ORDER BY r.cali_date DESC, r.id DESC
			""", nativeQuery = true)
	List<ManagerApprovalListRow> searchManagerApprovalList(
			@Param("approvalStatus") String approvalStatus,
			@Param("middleItemCodeId") Long middleItemCodeId,
			@Param("smallItemCodeId") Long smallItemCodeId,
			@Param("dateType") String dateType,
			@Param("startDate") String startDate,
			@Param("endDate") String endDate,
			@Param("searchType") String searchType,
			@Param("keyword") String keyword,
			Pageable pageable
	);

	/**
	 * 기술책임자결재 목록 전체 건수 (searchManagerApprovalList 동일 WHERE 조건)
	 */
	@Query(value = """
			SELECT COUNT(*)
			FROM report r
			LEFT JOIN cali_order o  ON o.id  = r.cali_order_id
			LEFT JOIN member wm     ON wm.id  = r.work_member_id
			LEFT JOIN member am     ON am.id  = r.approval_member_id
			WHERE r.is_visible        = 'y'
			  AND r.report_type       = 'SELF'
			  AND r.parent_scale_id   IS NULL
			  AND r.work_status       = 'SUCCESS'
			  AND (
			      (:approvalStatus IS NOT NULL AND r.approval_status = :approvalStatus)
			      OR (:approvalStatus IS NULL AND r.approval_status != 'SUCCESS')
			  )
			  AND (:middleItemCodeId IS NULL OR r.middle_item_code_id = :middleItemCodeId)
			  AND (:smallItemCodeId  IS NULL OR r.small_item_code_id  = :smallItemCodeId)
			  AND (
			      :startDate IS NULL OR :endDate IS NULL OR (
			          (:dateType = 'cali'     AND r.cali_date          BETWEEN :startDate AND :endDate)
			          OR (:dateType = 'receipt'  AND o.order_date      BETWEEN :startDate AND :endDate)
			          OR (:dateType = 'approval' AND r.approval_datetime BETWEEN CONCAT(:startDate, ' 00:00:00') AND CONCAT(:endDate, ' 23:59:59'))
			      )
			  )
			  AND (
			      :keyword = '' OR (
			          (:searchType = 'reportNum'      AND r.report_num       LIKE CONCAT('%', :keyword, '%'))
			          OR (:searchType = 'agentName'   AND o.cust_agent       LIKE CONCAT('%', :keyword, '%'))
			          OR (:searchType = 'publishName' AND o.report_agent     LIKE CONCAT('%', :keyword, '%'))
			          OR (:searchType = 'itemName'    AND r.item_name        LIKE CONCAT('%', :keyword, '%'))
			          OR (:searchType = 'itemNum'     AND r.item_num         LIKE CONCAT('%', :keyword, '%'))
			          OR (:searchType = 'manufacturer' AND r.item_make_agent LIKE CONCAT('%', :keyword, '%'))
			          OR (:searchType = 'modelType'   AND r.item_format      LIKE CONCAT('%', :keyword, '%'))
			          OR (:searchType = 'workMember'  AND wm.name            LIKE CONCAT('%', :keyword, '%'))
			          OR (:searchType = 'approvalMember' AND am.name         LIKE CONCAT('%', :keyword, '%'))
			          OR (:searchType = 'all' AND (
			                r.report_num         LIKE CONCAT('%', :keyword, '%')
			                OR o.cust_agent      LIKE CONCAT('%', :keyword, '%')
			                OR o.report_agent    LIKE CONCAT('%', :keyword, '%')
			                OR r.item_name       LIKE CONCAT('%', :keyword, '%')
			                OR r.item_num        LIKE CONCAT('%', :keyword, '%')
			                OR r.item_make_agent LIKE CONCAT('%', :keyword, '%')
			                OR r.item_format     LIKE CONCAT('%', :keyword, '%')
			                OR wm.name           LIKE CONCAT('%', :keyword, '%')
			                OR am.name           LIKE CONCAT('%', :keyword, '%')
			          ))
			      )
			  )
			""", nativeQuery = true)
	long countManagerApprovalList(
			@Param("approvalStatus") String approvalStatus,
			@Param("middleItemCodeId") Long middleItemCodeId,
			@Param("smallItemCodeId") Long smallItemCodeId,
			@Param("dateType") String dateType,
			@Param("startDate") String startDate,
			@Param("endDate") String endDate,
			@Param("searchType") String searchType,
			@Param("keyword") String keyword
	);

	/**
	 * 교정신청서 다운로드용 성적서 목록 조회
	 * - 재발행 제외(parentId, parentScaleId IS NULL)
	 * - 삭제 제외(isVisible = 'y')
	 * - reportNum 오름차순 정렬
	 *
	 * @param caliOrderId 접수 ID
	 * @return 성적서 목록
	 */
	@Query("""
			SELECT r FROM Report r
			WHERE r.caliOrderId = :caliOrderId
			  AND r.isVisible = 'y'
			  AND r.parentId IS NULL
			  AND r.parentScaleId IS NULL
			ORDER BY r.reportNum ASC
			""")
	List<Report> findReportsForOrderDownload(@Param("caliOrderId") Long caliOrderId);

	/**
	 * 접수 ID 목록을 받아 각 접수별 성적서 개수를 집계한다.
	 * - 삭제된 성적서(is_visible != 'y') 제외
	 * - 자식성적서(parent_id, parent_scale_id가 있는 것) 제외 → 최상위 성적서만 카운트
	 *
	 * @param caliOrderIds 집계할 접수 ID 목록
	 * @return 접수 ID / 개수 쌍의 프로젝션 목록
	 */
	@Query("""
			SELECT r.caliOrderId AS caliOrderId, COUNT(r.id) AS reportCnt
			FROM Report r
			WHERE r.caliOrderId IN :caliOrderIds
			  AND r.isVisible = 'y'
			  AND r.parentScaleId IS NULL
			GROUP BY r.caliOrderId
			""")
	List<ReportCountRow> countByCaliOrderIds(@Param("caliOrderIds") List<Long> caliOrderIds);
}
