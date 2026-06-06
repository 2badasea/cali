package com.bada.cali.repository.projection;

import java.time.LocalDate;

/**
 * 대행교정 목록 Projection
 * - report.type = AGCY, is_visible != 'n' 조건
 * - excelFileId/pdfFileId: file_info.name = 'agcy_excel'/'agcy_pdf'
 */
public interface AgcyReportListRow {
    Long getId();
    String getOrderType();           // 접수구분 (ACCREDDIT/UNACCREDDIT/TESTING)
    String getOrderNum();            // 접수번호
    String getAgcySelfReportNum();   // 자체성적서번호
    String getReportNum();           // 외부성적서번호
    String getCustAgent();           // 신청업체
    String getReportAgent();         // 성적서발행처
    String getAgcyAgent();           // 대행의뢰처
    String getItemName();            // 기기명
    String getItemMakeAgent();       // 제작회사
    String getItemFormat();          // 형식
    String getItemNum();             // 기기번호
    Long getCaliFee();               // 교정수수료
    LocalDate getCaliDate();         // 교정일자
    String getRemark();              // 비고
    String getReportStatus();        // 진행상태 (NORMAL/SUCCESS/CANCEL)
    Long getExcelFileId();           // EXCEL 파일 id (null이면 없음)
    Long getPdfFileId();             // PDF 파일 id (null이면 없음)
}
