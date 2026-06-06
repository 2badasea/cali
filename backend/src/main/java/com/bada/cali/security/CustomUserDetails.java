package com.bada.cali.security;

import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Set;

// UserDetails를 커스텀(principal)
// CustomUserDetailService class에서 loadUserByUsername에서 리턴하는 객체의 형태를 커스텀하기 위함
// Serializable: HTTP 세션에 저장되는 객체는 직렬화 필요 (세션 persist, 서버 재시작, 클러스터링 대비)
@Getter
public class CustomUserDetails implements UserDetails, Serializable {

	@Serial
	private static final long serialVersionUID = 1L;

	private final Long id;
	private final String username;	// 로그인id
	private final String password;
	private final String name;
	private final String hp;                        // 휴대폰번호 (개발팀 문의 작성 시 연락처 자동 세팅용)
	private final Long agentId;                     // 업체계정 여부 판별용 (0: 사내직원, > 0: 업체계정)
	private final Collection<? extends GrantedAuthority> authorities;
	private final Set<Long> readableMenuIds;        // 사용자별 접근이 가능한 메뉴id
	private final LocalDateTime lastPwdUpdated;     // 마지막 비밀번호 변경일시 (로그인 성공 처리에서 만료 경고용)

	public CustomUserDetails(Long id, String username, String password, String name, String hp,
							 Long agentId,
							 Collection<? extends GrantedAuthority> authorities,
							 Set<Long> readableMenuIds, LocalDateTime lastPwdUpdated) {
		this.id = id;
		this.username = username;
		this.password = password;
		this.name = name;
		this.hp = hp;
		this.agentId = agentId;
		this.authorities = authorities;
		this.readableMenuIds = readableMenuIds;
		this.lastPwdUpdated = lastPwdUpdated;
	}

	@Override
	public Collection<? extends GrantedAuthority> getAuthorities() {
		return authorities;
	}

	@Override
	public String getPassword() {
		return password;
	}

	@Override
	public String getUsername() {
		return username;
	}
	// 아래는 굳이 오버라이드 하지 않아도 됨. 지우거나 return 문에 UserDetails.super.xxx()로 리턴문 변경해야 함
//	@Override public boolean isAccountNonExpired() { return true; }
//	@Override public boolean isAccountNonLocked() { return true; }
//	@Override public boolean isCredentialsNonExpired() { return true; }
//	@Override public boolean isEnabled() { return true; }


}