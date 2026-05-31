package com.bada.cali.repository.projection;

import java.time.LocalDate;

/**
 * 성적서출력 목록 조회용 Projection (native query 기반)
 * - approval_datetime IS NOT NULL (기술책임자 결재 완료)
 * - approval_status = 'SUCCESS'
 * - is_print = 'n' (미출력)
 * - report_type = 'SELF' (자체성적서)
 */
public interface ReportPrintListRow {

    Long getId();                       // 성적서 고유 id

    String getReportNum();              // 성적서번호

    String getCodeSmallName();          // 소분류코드명

    LocalDate getReceiptDate();         // 접수일 (cali_order.order_date)

    String getAgentName();              // 신청업체 (cali_order.cust_agent)

    String getPublishName();            // 성적서발행처 (cali_order.report_agent)

    String getItemName();               // 기기명

    String getItemNum();                // 기기번호

    String getManufacturer();           // 제작회사 (item_make_agent)

    String getModelType();              // 형식 (item_format)

    LocalDate getCaliDate();            // 교정일자

    Long getSignedXlsxFileId();         // signed_xlsx file_info.id (nullable)

    Long getSignedPdfFileId();          // signed_pdf file_info.id (nullable)
}
