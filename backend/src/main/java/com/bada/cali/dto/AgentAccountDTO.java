package com.bada.cali.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

public class AgentAccountDTO {

    private AgentAccountDTO() {}

    // ── 목록 조회 요청 ──────────────────────────────────────────────────────

    /**
     * 업체계정관리 목록 조회 파라미터.
     * TuiGridDTO.Request를 상속해 page/perPage를 포함.
     */
    @Getter
    @Setter
    public static class ListReq extends TuiGridDTO.Request {
        @Schema(description = "로그인허용유무 필터 (비어있으면 전체 / y: 허용 / n: 미허용)")
        private String isActive = "";   // 기본: 전체

        @Schema(description = "검색타입 (loginId / name)")
        private String searchType = "";

        @Schema(description = "검색어")
        private String keyword = "";
    }

    // ── 등록 요청 ──────────────────────────────────────────────────────────

    @Getter
    @Setter
    @Schema(description = "업체계정 등록 요청")
    public static class CreateReq {
        @NotNull
        @Schema(description = "업체 id (agent.id)", example = "5")
        private Long agentId;

        @NotBlank
        @Schema(description = "로그인 아이디 (영문소문자 시작, 4~20자)")
        private String loginId;

        @NotBlank
        @Schema(description = "비밀번호 (소문자+대문자+숫자 각 1자 이상, 8~20자)")
        private String pwd;

        @NotBlank
        @Schema(description = "로그인허용유무 (y: 허용 / n: 미허용)")
        private String isActive;

        @NotBlank
        @Schema(description = "업체명 (member.name 저장용)")
        private String name;
    }

    // ── 수정 요청 ──────────────────────────────────────────────────────────

    @Getter
    @Setter
    @Schema(description = "업체계정 수정 요청")
    public static class UpdateReq {
        @NotNull
        @Schema(description = "업체 id (변경 시)")
        private Long agentId;

        @Schema(description = "비밀번호 (입력 시 변경, 미입력 시 유지)")
        private String pwd;

        @NotBlank
        @Schema(description = "로그인허용유무 (y / n)")
        private String isActive;

        @NotBlank
        @Schema(description = "업체명 (member.name 변경용)")
        private String name;
    }

    // ── 단건 조회 응답 ──────────────────────────────────────────────────────

    @Getter
    @Setter
    @Schema(description = "업체계정 단건 조회 응답")
    public static class DetailRes {
        private Long id;
        private Long agentId;
        private String agentName;   // agent.name (업체명)
        private String loginId;
        private String isActive;    // 'y' / 'n'

        public DetailRes(Long id, Long agentId, String agentName, String loginId, String isActive) {
            this.id        = id;
            this.agentId   = agentId;
            this.agentName = agentName;
            this.loginId   = loginId;
            this.isActive  = isActive;
        }
    }

    // ── 삭제 요청 ──────────────────────────────────────────────────────────

    @Getter
    @Setter
    @Schema(description = "업체계정 삭제 요청")
    public static class DeleteReq {
        @NotNull
        @Schema(description = "삭제할 member id 목록")
        private List<Long> ids;
    }
}
