package com.bada.cali.dto;

import lombok.Getter;
import lombok.Setter;

public class ReportPrintDTO {

    @Getter
    @Setter
    public static class ListReq extends TuiGridDTO.Request {
        private String dateType;          // 날짜 기준 (cali: 교정일자, approval: 성적서발행일) — 기본 approval
        private String startDate;         // 시작일 (yyyy-MM-dd)
        private String endDate;           // 종료일 (yyyy-MM-dd)
        private Long   middleItemCodeId;  // 중분류코드 id (0 = 전체)
        private Long   smallItemCodeId;   // 소분류코드 id (0 = 전체)
        private String searchType;        // 검색 대상 컬럼 ('' = 전체)
        private String keyword;           // 검색 키워드
    }
}
