package com.bada.cali.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

public class ManagerApprovalDTO {

    private ManagerApprovalDTO() {}

    /** 기술책임자결재 목록 조회 요청 파라미터 */
    @Getter
    @Setter
    public static class ListReq extends TuiGridDTO.Request {
        /** 날짜 기준 (cali=교정일, receipt=접수일, approval=결재일) */
        private String dateType = "cali";
        private String startDate;
        private String endDate;
        /** 검색 타입 (reportNum / agentName / publishName / itemName / itemNum / manufacturer / modelType / workMember / approvalMember) */
        private String searchType;
        private String keyword;
        /** 중분류코드 id (0 또는 null이면 전체) */
        private Long middleItemCodeId;
        /** 소분류코드 id (0 또는 null이면 전체) */
        private Long smallItemCodeId;
        /**
         * approval_status 필터 (빈값=SUCCESS 제외 전체, 특정값=해당 상태만)
         * 기본 동작: SUCCESS 제외한 전체 (대기 목록)
         */
        private String approvalStatus;
    }

    /** 성적서 반려 요청 */
    @Getter
    @Setter
    @Schema(description = "성적서 반려 요청")
    public static class RejectReq {

        @NotEmpty(message = "반려 대상 성적서를 선택해야 합니다.")
        @Schema(description = "반려할 성적서 id 목록")
        private List<Long> reportIds;

        @NotBlank(message = "반려 사유를 입력해야 합니다.")
        @Schema(description = "반려 사유")
        private String rejectReason;
    }

    /** 결재 취소 요청 */
    @Getter
    @Setter
    @Schema(description = "결재 취소 요청")
    public static class CancelApprovalReq {

        @NotEmpty(message = "취소 대상 성적서를 선택해야 합니다.")
        @Schema(description = "취소할 성적서 id 목록")
        private List<Long> reportIds;
    }
}