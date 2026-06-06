package com.bada.cali.repository.projection;

import com.bada.cali.common.enums.YnType;

import java.time.LocalDateTime;

/**
 * 업체계정관리 목록 그리드용 Projection.
 * member.agent_id > 0, is_visible != 'n' 조건으로 조회.
 */
public interface AgentAccountListRow {
    Long getId();
    Long getAgentId();
    /** 업체명 (member.name) */
    String getName();
    String getLoginId();
    Integer getLoginCount();
    LocalDateTime getLastLoginDatetime();
    LocalDateTime getCreateDatetime();
    /** 로그인허용유무: 'y' / 'n' */
    YnType getIsActive();
}
