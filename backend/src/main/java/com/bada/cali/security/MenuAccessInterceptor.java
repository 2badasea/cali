package com.bada.cali.security;

import com.bada.cali.entity.Menu;
import com.bada.cali.exceptions.MenuAccessDeniedException;
import com.bada.cali.service.MenuQueryService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.List;
import java.util.Set;

/**
 * 메뉴 테이블에 URL이 등록된 경로에 대해 사용자의 readableMenuIds 기반으로 접근 제어
 * 정책: 메뉴 URL에 매핑된 경로만 체크 — 서브페이지/폼 페이지 등 미등록 경로는 통과
 */
@Component
@RequiredArgsConstructor
@Log4j2
public class MenuAccessInterceptor implements HandlerInterceptor {

	private final MenuQueryService menuQueryService;

	// 업체계정(agentId > 0)에게 허용된 SSR URL 목록
	private static final Set<String> AGENT_ALLOWED_URLS = Set.of(
			"/cali/customerCaliHistory"
	);

	@Override
	public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {

		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

		// 미인증 또는 익명 접근 → SecurityConfig의 anyRequest().authenticated() 가 처리
		if (authentication == null || !authentication.isAuthenticated()
				|| authentication instanceof AnonymousAuthenticationToken) {
			return true;
		}

		// ADMIN은 전체 메뉴 접근 허용
		boolean isAdmin = authentication.getAuthorities().stream()
				.anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
		if (isAdmin) {
			return true;
		}

		if (!(authentication.getPrincipal() instanceof CustomUserDetails userDetails)) {
			return true;
		}

		// 요청 URI 정규화 (contextPath 제거)
		String uri = request.getRequestURI();
		String contextPath = request.getContextPath();
		if (contextPath != null && !contextPath.isEmpty() && uri.startsWith(contextPath)) {
			uri = uri.substring(contextPath.length());
		}

		// 업체계정(agentId > 0)은 허용된 URL만 접근 가능 (화이트리스트 방식)
		Long agentId = userDetails.getAgentId();
		if (agentId != null && agentId > 0) {
			if (!AGENT_ALLOWED_URLS.contains(uri)) {
				log.warn("업체계정 접근 거부 — user={}, uri={}", userDetails.getUsername(), uri);
				throw new MenuAccessDeniedException("접근 권한이 없습니다.");
			}
			return true;
		}

		Set<Long> readableMenuIds = userDetails.getReadableMenuIds();

		// 캐시된 전체 visible 메뉴에서 요청 URI와 일치하는 메뉴 탐색
		List<Menu> allVisibleMenus = menuQueryService.getAllVisibleMenus();
		final String finalUri = uri;
		Menu matchedMenu = allVisibleMenus.stream()
				.filter(m -> finalUri.equals(m.getUrl()))
				.findFirst()
				.orElse(null);

		// 메뉴 테이블에 등록되지 않은 URL (서브페이지, 폼 등) → 통과
		if (matchedMenu == null) {
			return true;
		}

		// 메뉴 URL인데 권한이 없는 경우 → 접근 거부
		if (readableMenuIds == null || !readableMenuIds.contains(matchedMenu.getId())) {
			log.warn("메뉴 접근 거부 — user={}, uri={}, menuId={}", userDetails.getUsername(), finalUri, matchedMenu.getId());
			throw new MenuAccessDeniedException("접근 권한이 없는 메뉴입니다.");
		}

		return true;
	}

}