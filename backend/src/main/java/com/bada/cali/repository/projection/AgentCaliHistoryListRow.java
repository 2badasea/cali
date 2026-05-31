package com.bada.cali.repository.projection;

import java.time.LocalDate;

/**
 * 업체별교정이력조회 목록 Projection
 * - SELF: approval_status = SUCCESS 성적서
 * - AGCY: report_status = SUCCESS 성적서
 */
public interface AgentCaliHistoryListRow {

    Long getId();
    String getReportType();           // SELF / AGCY
    String getSmallCodeNum();         // 소분류번호 (item_code.code_num)
    String getReportNum();            // 성적서번호
    LocalDate getCaliDate();          // 교정일자
    String getCustAgent();            // 신청업체 (cali_order.cust_agent)
    String getReportAgent();          // 성적서발행처 (cali_order.report_agent)
    String getItemName();             // 기기명
    String getItemMakeAgent();        // 제작회사
    String getItemFormat();           // 형식
    String getItemNum();              // 기기번호
    String getWorkMemberName();       // 실무자
    String getApprovalMemberName();   // 기술책임자
    LocalDate getPublishDate();       // 발행일자 (approval_datetime 날짜만, AGCY는 null)
    Long getExcelFileId();            // EXCEL 파일 file_info.id (SELF: signed_xlsx, AGCY: agcy_excel)
    Long getPdfFileId();              // PDF 파일 file_info.id (SELF: signed_pdf, AGCY: agcy_pdf)
}
