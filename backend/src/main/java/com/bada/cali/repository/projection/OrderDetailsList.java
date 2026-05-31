package com.bada.cali.repository.projection;

import com.bada.cali.common.enums.AppStatus;
import com.bada.cali.common.enums.OrderType;
import com.bada.cali.common.enums.ReportStatus;
import com.bada.cali.common.enums.ReportType;

import java.time.LocalDateTime;

// 접수상세내역 리스트에 표시할 데이터용 DTO 프로젝션
public interface OrderDetailsList {
	
	Long getId();                    // 성적서 고유 id
	
	ReportType getReportType();      // 구분(자체, 대행)
	
	OrderType getOrderType();        // 접수타입
	
	Long getMiddleItemCodeId();     // 중분류코드
	
	Long getSmallItemCodeId();      // 소분류코드
	
	String getReportNum();           // 성적서번호
	
	String getItemName();            // 기기명
	
	String getItemFormat();          // 기기 형식
	
	String getItemNum();             // 기기번호
	
	Integer getItemCaliCycle();      // 품목교정주기
	
	String getItemMakeAgent();       // 제작회사
	
	String getManageNo();            // 관리번호
	
	ReportStatus getReportStatus();  // 성적서 진행상태
	
	String getRemark();              // 비고
	
	Long getCaliFee();               // 교정수수료
	
	String getStatusRemark();        // 취소, 불가, 반려 사유
	
	LocalDateTime getApprovalDatetime(); // 기술책임자 결재일시
	
	LocalDateTime getWorkDatetime();     // 실무자 결재일시
	
	AppStatus getWorkStatus();		// 실무자 결재상태
	
	AppStatus getApprovalStatus();	// 기술책임자 결재상태
	
	String getMiddleCodeNum();	// 중분류코드

	String getSmallCodeNum();	// 소분류코드

	String getAgcyAgent();           // 대행의뢰처 (AGCY 전용, SELF 는 null)

	String getAgcySelfReportNum();   // 자체대행성적서번호 (AGCY 전용, SELF 는 null)

	// 파일 다운로드용 file_info.id (없으면 null)
	// SELF: signed_xlsx / AGCY: agcy_excel
	Long getXlsxFileId();

	// SELF: signed_pdf / AGCY: agcy_pdf
	Long getPdfFileId();

}
