package com.bada.cali.dto;

import lombok.Getter;
import lombok.Setter;

public class AgentCaliHistoryDTO {

    /**
     * 업체별교정이력조회 목록 요청 DTO
     */
    @Getter
    @Setter
    public static class ListReq extends TuiGridDTO.Request {
        /** 날짜 기준: cali(교정일자) / approval(발행일자, 기본값). AGCY는 항상 교정일자 기준 */
        private String dateType;
        /** 시작일 (yyyy-MM-dd) */
        private String startDate;
        /** 종료일 (yyyy-MM-dd) */
        private String endDate;
        /** 성적서 유형: SELF / AGCY / null(전체) */
        private String reportType;
        /** 검색 대상: agentName / publishName / itemName / manufacturer / modelType / itemNum / reportNum / 빈값(전체) */
        private String searchType;
        /** 검색 키워드 */
        private String keyword;
    }
}
