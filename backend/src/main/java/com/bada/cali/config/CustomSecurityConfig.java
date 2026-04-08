package com.bada.cali.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.boot.autoconfigure.security.servlet.PathRequest;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityCustomizer;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.authentication.rememberme.JdbcTokenRepositoryImpl;
import org.springframework.security.web.authentication.rememberme.PersistentTokenRepository;

import javax.sql.DataSource;
import jakarta.servlet.http.HttpServletResponse;

@Log4j2
@Configuration
@RequiredArgsConstructor
@EnableMethodSecurity(prePostEnabled = true)    // 기본값(true). 애너테이션을 이용한 권한체크(NOTATION 1번)
@EnableConfigurationProperties(RememberMeProperties.class)
public class CustomSecurityConfig {

	private final AuthenticationSuccessHandler LoginSuccessHandler;        // 로그인 성공 훅
	private final AuthenticationFailureHandler LoginFailureHandler;        // 로그인 실패 훅
	private final DataSource dataSource;        // 자동로그인 기능을 위한 의존성 주입
	private final RememberMeProperties rememberMeProperties;              // Remember-Me 설정

	// filterChain 메서드 호출 시, 전역에서 로그인 검증을 하지 않고, 원하는 URL의 자원을 반환
	// NOTE 시큐리티 필터체인(filterChain) 내부에서 던지는 예외들에 대해선 로그인 성공/실패 훅 내부로 전달됨(훅 내부에서 체크 가능)
	@Bean
	public SecurityFilterChain filterChain(HttpSecurity http, UserDetailsService userDetailsService) throws Exception {
		log.debug("================= configure ===============");

		http
				.csrf(AbstractHttpConfigurer::disable)                    // csrf 토큰확인 비활성화 처리 (NOTATION 3번)

				// 접근 허용 경로 설정
				.authorizeHttpRequests(auth -> auth
						// DELETE는 무조건 ADMIN만 허용
						.requestMatchers(org.springframework.http.HttpMethod.DELETE, "/api/**").hasRole("ADMIN")
						// /api/admin/** 경로의 쓰기 요청(POST·PATCH·PUT)은 ADMIN만 허용
						// GET은 제외 — 조회는 인증된 사용자 전체에 허용
						.requestMatchers(org.springframework.http.HttpMethod.POST, "/api/admin/**").hasRole("ADMIN")
						.requestMatchers(org.springframework.http.HttpMethod.PATCH, "/api/admin/**").hasRole("ADMIN")
						.requestMatchers(org.springframework.http.HttpMethod.PUT, "/api/admin/**").hasRole("ADMIN")
						// 정적 리소스는 아래 WebSecurityCustomizer에서 처리
						.requestMatchers(
								"/error"
								, "/member/login"            // 쿼리스트링(?error, ?logout 등)은 경로 매칭에 포함되지 않으므로 정확한 경로만 명시
								, "/member/memberJoin"
								, "/guide/**"
								, "/api/member/**"
								, "/swagger-ui/**"           // Swagger UI 리소스
								, "/v3/api-docs/**"          // OpenAPI 스펙 JSON
								, "/actuator/health"         // CI/CD 헬스체크 전용 (인증 없이 접근 허용)
								// 성적서 작업서버 ↔ CALI 내부 통신 경로 (세션 없는 서버→서버 호출)
								// 인증은 X-Worker-Api-Key 헤더로 컨트롤러 레벨에서 검증함
								, "/api/worker/**"
								// 대시보드 → CALI webhook 콜백 경로 (세션 없는 서버→서버 호출)
								// 인증은 X-Api-Key 헤더로 컨트롤러 레벨에서 검증함
								, "/api/callback/**"
								// ExcelWork 미들웨어 연동 경로 (세션 없는 클라이언트 앱 호출)
								// 잡 조회/파일 중계: token 으로 식별
								// 콜백: X-Callback-Key 헤더로 컨트롤러 레벨에서 검증함
								, "/api/excelwork/job/**"
								, "/api/excelwork/file/**"
								, "/api/excelwork/callback/**"
						).permitAll()
						.anyRequest().authenticated()                // 그외 요청에 대해선 인증된 사용자만 허용
				)

				// 로그인폼 처리
				.formLogin(form -> form
						.loginPage("/member/login").permitAll()    // 커스텀 로그인 페이지 경로 지정
						.loginProcessingUrl("/api/member/login")    // 로그인 처리 URL (Security Filter가 가로채서 직접 처리)
						.usernameParameter("username")
						.passwordParameter("password")
						.successHandler(LoginSuccessHandler)
						.failureHandler(LoginFailureHandler)
				)

				// 자동로그인 설정
				.rememberMe(rememberMe -> rememberMe
						.key(rememberMeProperties.key())                // application.properties에서 주입받은 서명용 키
						.tokenRepository(persistentTokenRepository())    // 자동로그인 토큰 저장 위치 (DB)
						.userDetailsService(userDetailsService)            // 쿠키/토큰으로 실제 유저를 로딩하는 서비스
						.tokenValiditySeconds(60 * 60 * 24 * 30)        // 쿠키 유효기간 (30일)
				)

				// 로그아웃 처리
				.logout(logout -> logout
						.logoutUrl("/logout")
						.logoutSuccessHandler((request, response, authentication) -> {
							response.setContentType("application/json;charset=UTF-8");
							response.getWriter().write("{\"ok\":true,\"redirect\":\"/member/login?logout\"}");
						})
						.invalidateHttpSession(true)                                // 세션 무효화
						.clearAuthentication(true)                                    // SecurityContext 비우기
						.deleteCookies("JSESSIONID", "remember-me")    // 쿠키 삭제
				)

				// 예외처리
				// - 미인증(401): AuthenticationEntryPoint → 로그인 페이지로 리다이렉트
				// - 권한 부족(403): AccessDeniedHandler → ResMessage JSON 응답 ("권한이 없습니다.")
				.exceptionHandling(exception -> exception
						.authenticationEntryPoint(unauthenticatedEntryPoint())
						.accessDeniedHandler(accessDeniedHandler())
				);

		return http.build();
	}

	// 자동로그인 기능을 위한 등록
	@Bean
	public PersistentTokenRepository persistentTokenRepository() {
		JdbcTokenRepositoryImpl repository = new JdbcTokenRepositoryImpl();
		repository.setDataSource(dataSource);
		return repository;
	}

	// 정적 리소스(css, js, images 등)는 보안 필터 자체를 스킵하여 불필요한 필터 처리 방지 (성능 이점)
	// web.ignoring(): 필터 체인 자체를 태우지 않음 (SecurityContext 없음)
	// permitAll(): 필터 체인은 통과하되 인증 없이 접근 허용 (SecurityContext 존재)
	// → 정적 리소스는 ignoring, URL 경로 접근 제어는 permitAll로 역할을 분리
	@Bean
	public WebSecurityCustomizer webSecurityCustomizer() {
		return (web) -> web.ignoring()
				.requestMatchers(PathRequest.toStaticResources().atCommonLocations())
				.requestMatchers("/vendor/**");
	}

	// 미인증 접근 시
	// - API 요청(/api/**): 401 JSON 응답 (React SPA가 감지하여 로그인 페이지로 이동)
	// - 일반 페이지 요청: 로그인 페이지로 리다이렉트
	@Bean
	public AuthenticationEntryPoint unauthenticatedEntryPoint() {
		return (request, response, authException) -> {
			String uri = request.getRequestURI();
			if (uri.startsWith("/api/")) {
				// API 요청에 리다이렉트를 보내면 React가 HTML을 받아 처리할 수 없으므로 JSON 401 반환
				response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
				response.setContentType("application/json;charset=UTF-8");
				response.getWriter().write("{\"code\":-1,\"msg\":\"인증이 필요합니다.\",\"data\":null}");
			} else {
				// 루트("/") 접근 시 단순 로그인 페이지 이동
				// 그 외: 원래 요청 URI를 redirect 파라미터로 전달 → 로그인 성공 후 복귀에 사용
				if ("/".equals(uri)) {
					response.sendRedirect("/member/login");
				} else {
					String encodedUri = java.net.URLEncoder.encode(uri, java.nio.charset.StandardCharsets.UTF_8);
					response.sendRedirect("/member/login?redirect=" + encodedUri + "&required=1");
				}
			}
		};
	}

	// 인증은 됐지만 권한 부족(403) 시 → ResMessage JSON 응답
	// @RestControllerAdvice는 Security 필터 단계에서 발생하는 AccessDeniedException을 잡지 못하므로 별도 핸들러 필요
	@Bean
	public AccessDeniedHandler accessDeniedHandler() {
		return (request, response, accessDeniedException) -> {
			response.setStatus(HttpServletResponse.SC_FORBIDDEN);
			response.setContentType("application/json;charset=UTF-8");
			// ResMessage 형식: { code, msg, data }
			response.getWriter().write("{\"code\":-1,\"msg\":\"권한이 없습니다.\",\"data\":null}");
		};
	}


}
