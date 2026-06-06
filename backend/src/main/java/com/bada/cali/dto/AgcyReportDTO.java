package com.bada.cali.dto;

import lombok.Getter;
import lombok.Setter;

public class AgcyReportDTO {

    private AgcyReportDTO() {}

    // 대행교정 목록 조회 요청 DTO
    @Getter
    @Setter
    public static class ListReq extends TuiGridDTO.Request {
        private String status;      // 진행상태 필터 (NORMAL/SUCCESS/CANCEL, null이면 전체)
        private String startDate;   // 교정일자 시작 (yyyy-MM-dd)
        private String endDate;     // 교정일자 종료 (yyyy-MM-dd)
        private String searchType;  // 검색 유형
        private String keyword;     // 검색 키워드
    }
}
