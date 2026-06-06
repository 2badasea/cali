package com.bada.cali.security;

import com.bada.cali.common.enums.AuthType;
import com.bada.cali.entity.Member;
import com.bada.cali.common.enums.YnType;
import com.bada.cali.repository.MemberPermissionReadRepository;
import com.bada.cali.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Log4j2
@Service
@RequiredArgsConstructor
public class CustomUserDetailService implements UserDetailsService {
	
	private final MemberRepository memberRepository;
	private final MemberPermissionReadRepository memberPermissionReadRepository;
	
	@Override
	public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
		log.info("loadUserByUsername username={}", username);
		
		// orElseThrow의 경우, 값이 있으면 Optional<T>에 명시한 T타입의 값(get)으로 반환하도록 동작
		// 시큐리티 필터 내부에서 UsernameNotFoundException를 던져도 실패 훅에선 BadCredentialException으로 받게 됨.(NOTATION 6번 확인)
		Member loginMember = memberRepository.findByLoginId(username, YnType.y).orElseThrow(() -> new UsernameNotFoundException("사용자를 찾을 수 없습니다."));
		
		// 로그인 시도 5회 이상 && 마지막 로그인 시도 일시가 10분도 안 된 상태라면 실패로 던지기
		// NOTE LockedException을 던졌을 때, 실패 훅에서 해당 예외로 직접받을 수 없음 (NOTATION 6번 확인)
		LocalDateTime now = LocalDateTime.now();
		if (loginMember.getLoginCount() >= 5 && loginMember.getLastLoginFailDatetime() != null && loginMember.getLastLoginFailDatetime().plusMinutes(10).isAfter(now)) {
			throw new LockedException("10분 뒤에 로그인이 가능합니다.");
		}
		// 계정의 가입 승인이 이루어지지 않은 경우 y: 가능(승인완료), n: 불가(미승인/차단)
		if (loginMember.getIsActive() != YnType.y) {
			throw new LockedException("관리자의 승인이 필요합니다.\n관리자에게 문의해주세요.");
		}
		
		// 인가를 받아 반환하는 UserDetails 객체에서 더 많은 정보를 얻기 위해 CustomUserDetails(= 커스텀 principal) 만들기
//		UserDetails userDetails = User.withUsername(loginMember.getLoginId())
//				.password(loginMember.getPwd())                        // DB의 해쉬값 그대로
//				.authorities("ROLE_USER")                        // 최소 권한이라도 넣어두기(필요시 실제 권한 매핑)
//				.build();
//		return userDetails;
		
		// 회원의 auth 값 기반으로 Spring Security 권한(ROLE_USER / ROLE_ADMIN) 생성
		// NOTE var는 Java 10+ 로컬 변수 타입 추론 (컴파일러가 우측 표현식으로 타입 결정)
		AuthType authType = (loginMember.getAuth() == null) ? AuthType.user : loginMember.getAuth();
		String role = "ROLE_" + authType.name().toUpperCase(); // 'ROLE_USER' | 'ROLE_ADMIN'
		var authorities = List.of(new SimpleGrantedAuthority(role));
		
		// 유저가 읽기 가능한 메뉴 id 리스트 조회
		List<Long> readableMenuIds = memberPermissionReadRepository.findMenuIdsByMemberId(loginMember.getId());
		// 메뉴 접근 권한 체크 시 contains() 성능을 위해 Set으로 변환
		// NOTE 변수 타입은 인터페이스(Set), 실제 객체는 구현체(HashSet) → 구현체 교체에 유연
		Set<Long> readableMenuIdSet = new HashSet<>(readableMenuIds);
		
		// 스프링시큐리티 환경에선 api 요청별로 사용자의 정보를 얻기 위해 httpsession을 이용하는 것은 권장되지 않음
		// 'SecurityContext/Principal' 로 가져오는 방식이 표준
		// id도 담겨져 있는 CustomUserDetails를 반환. 위 기존 리턴 객체도 확인
		return new CustomUserDetails(loginMember.getId()
				, loginMember.getLoginId()
				, loginMember.getPwd()
				, loginMember.getName()
				, loginMember.getHp()
				, loginMember.getAgentId()
				, authorities
				, readableMenuIdSet
				, loginMember.getLastPwdUpdated()
		);
		
	}
}
