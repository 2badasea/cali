package com.bada.cali.repository.projection;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 기술책임자결재 목록 조회용 Projection (native query 기반)
 * - work_status = 'SUCCESS' (실무자결재 완료) 성적서만 대상
 * - cali_order, item_code, member(실무자/기술책임자) LEFT JOIN
 */
public interface ManagerApprovalListRow {

    Long getId();                       // 성적서 고유 id

    String getReportNum();              // 성적서번호

    String getCodeSmallName();          // 소분류코드명

    LocalDate getReceiptDate();         // 접수일 (cali_order.order_date)

    LocalDate getExpectCompleteDate();  // 완료예정일

    String getAgentName();              // 신청업체 (cali_order.cust_agent)

    String getPublishName();            // 성적서발행처 (cali_order.report_agent)

    String getItemName();               // 기기명

    String getItemNum();                // 기기번호

    String getManufacturer();           // 제작회사

    String getModelType();              // 형식

    LocalDate getCaliDate();            // 교정일자

    String getReportLang();             // 발행타입 (KR/EN/BOTH)

    String getWorkMemberName();         // 실무자 이름

    String getApprovalMemberName();     // 기술책임자 이름

    String getApprovalStatus();         // 결재상태 (AppStatus enum → String)

    LocalDateTime getApprovalDatetime(); // 결재일시

    Long getOriginFileId();             // type='origin' file_info.id (nullable)

    Long getSignedXlsxFileId();         // type='signed_xlsx' file_info.id (nullable)

    Long getSignedPdfFileId();          // type='signed_pdf' file_info.id (nullable)
}