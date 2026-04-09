package com.bada.cali.repository.projection;

import java.time.LocalDate;

/**
 * 실무자결재 목록 조회용 Projection (native query 기반)
 * - report 테이블 기준, SELF 타입 자체성적서만
 * - cali_order, item_code, member(작성자/실무자/기술책임자) JOIN
 */
public interface WorkApprovalListRow {

	Long getId();                    // 성적서 고유 id

	String getManageNo();            // 관리번호

	Long getSmallItemCodeId();       // 소분류코드 id (성적서작성 모달 호출 시 전달용)

	String getSmallCodeNum();        // 소분류코드

	LocalDate getOrderDate();        // 접수일 (cali_order.order_date)

	LocalDate getExpectCompleteDate(); // 완료예정일

	String getReportNum();           // 성적서번호

	String getCustAgent();           // 신청업체 (cali_order.cust_agent)

	String getReportAgent();         // 성적서발행처 (cali_order.report_agent)

	String getItemName();            // 기기명

	String getItemNum();             // 기기번호

	String getItemMakeAgent();       // 제작회사

	String getItemFormat();          // 형식

	String getReportStatus();        // 성적서 진행상태 (String — native query)

	String getWorkStatus();          // 실무자 결재상태 (String — native query)

	String getOrderType();           // 접수구분 (String — native query)

	String getWriteMemberName();     // 작성자 이름 (member.name JOIN)

	Long getWorkMemberId();          // 실무자 member.id (다중결재 시 그룹화 기준)

	String getWorkMemberName();      // 실무자 이름 (member.name JOIN)

	String getApprovalMemberName();  // 기술책임자 이름 (member.name JOIN)

	// ── 파일 다운로드 컬럼 (file_info 서브쿼리 — 없으면 null) ─────────────────
	/** 성적서작성(ExcelWork) 상태 (IDLE/READY/PROGRESS/SUCCESS/FAIL) */
	String getWriteStatus();

	/** 원본 성적서 엑셀 file_info.id (name='report_origin') */
	Long getOriginFileId();

	/** 결재 완료 후 생성되는 Excel 출력 file_info.id (name='report_excel') */
	Long getExcelFileId();

	/** 결재 완료 후 생성되는 PDF 출력 file_info.id (name='report_pdf') */
	Long getPdfFileId();

}