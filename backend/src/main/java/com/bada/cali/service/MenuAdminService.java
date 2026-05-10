package com.bada.cali.service;

import com.bada.cali.dto.MenuDTO;
import com.bada.cali.entity.Menu;
import com.bada.cali.repository.MemberPermissionReadRepository;
import com.bada.cali.repository.MenuRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MenuAdminService {

    private final MenuRepository menuRepository;
    private final MemberPermissionReadRepository permissionReadRepository;
    private final MenuQueryService menuQueryService;

    // ── 전체 메뉴 트리 조회 (관리용 — is_visible 무관, 전체 반환) ──────────────

    @Transactional(readOnly = true)
    public List<MenuDTO.TreeRes> getMenuTree() {
        // depth ASC, sort_order ASC 정렬된 전체 목록
        List<Menu> all = menuRepository.findAllByOrderByDepthAscSortOrderAsc();
        return buildTree(all);
    }

    // ── menu_code 중복 체크 ────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public boolean isMenuCodeDuplicate(String menuCode, Long excludeId) {
        return menuRepository.existsByMenuCodeAndIdNot(menuCode, excludeId == null ? -1L : excludeId);
    }

    // ── 메뉴 등록 ─────────────────────────────────────────────────────────────

    @Transactional
    public MenuDTO.TreeRes createMenu(MenuDTO.CreateReq req) {
        // menu_alias 중복 체크
        if (menuRepository.existsByMenuAlias(req.getMenuAlias())) {
            throw new IllegalArgumentException("이미 사용 중인 메뉴명입니다: " + req.getMenuAlias());
        }

        // menu_code 중복 체크
        if (menuRepository.existsByMenuCode(req.getMenuCode())) {
            throw new IllegalArgumentException("이미 사용 중인 메뉴 코드입니다: " + req.getMenuCode());
        }

        // url 중복 체크 (값이 있을 때만)
        if (StringUtils.hasText(req.getUrl()) && menuRepository.existsByUrl(req.getUrl())) {
            throw new IllegalArgumentException("이미 사용 중인 링크 URL입니다: " + req.getUrl());
        }

        Menu parent = null;
        int depth = 1;

        if (req.getParentId() != null) {
            parent = menuRepository.findById(req.getParentId())
                    .orElseThrow(() -> new EntityNotFoundException("상위 메뉴를 찾을 수 없습니다."));
            depth = parent.getDepth() + 1;

            // 사이드바가 depth 2단계까지만 렌더링하므로, depth 3 이상 생성 차단
            if (depth > 2) {
                throw new IllegalArgumentException("메뉴는 최대 2단계까지만 생성할 수 있습니다.");
            }
        }

        // 동일 부모 내 마지막 sort_order + 1
        int nextSortOrder = menuRepository.findMaxSortOrderByParentId(req.getParentId())
                .map(max -> max + 1)
                .orElse(1);

        Menu menu = Menu.builder()
                .menuAlias(req.getMenuAlias())
                .menuCode(req.getMenuCode())
                .url(req.getUrl())
                .target(req.getTarget())
                .depth(depth)
                .sortOrder(nextSortOrder)
                .isVisible(req.getIsVisible())
                .parent(parent)
                .build();

        Menu saved = menuRepository.save(menu);
        menuQueryService.evictMenuCache();
        return MenuDTO.TreeRes.from(saved);
    }

    // ── 메뉴 수정 ─────────────────────────────────────────────────────────────

    @Transactional
    public MenuDTO.TreeRes updateMenu(Long id, MenuDTO.UpdateReq req) {
        Menu menu = menuRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("메뉴를 찾을 수 없습니다."));

        // menu_alias 중복 체크 (자기 자신 제외)
        if (menuRepository.existsByMenuAliasAndIdNot(req.getMenuAlias(), id)) {
            throw new IllegalArgumentException("이미 사용 중인 메뉴명입니다: " + req.getMenuAlias());
        }

        // menu_code 중복 체크 (자기 자신 제외)
        if (menuRepository.existsByMenuCodeAndIdNot(req.getMenuCode(), id)) {
            throw new IllegalArgumentException("이미 사용 중인 메뉴 코드입니다: " + req.getMenuCode());
        }

        // url 중복 체크 (값이 있고 변경된 경우, 자기 자신 제외)
        if (StringUtils.hasText(req.getUrl())
                && !req.getUrl().equals(menu.getUrl())
                && menuRepository.existsByUrlAndIdNot(req.getUrl(), id)) {
            throw new IllegalArgumentException("이미 사용 중인 링크 URL입니다: " + req.getUrl());
        }

        menu.setMenuAlias(req.getMenuAlias());
        menu.setMenuCode(req.getMenuCode());
        menu.setUrl(req.getUrl());
        menu.setTarget(req.getTarget());
        menu.setIsVisible(req.getIsVisible());

        menuQueryService.evictMenuCache();
        return MenuDTO.TreeRes.from(menu);
    }

    // ── 메뉴 삭제 ─────────────────────────────────────────────────────────────

    @Transactional
    public void deleteMenu(Long id) {
        Menu menu = menuRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("메뉴를 찾을 수 없습니다."));

        // 자식 메뉴가 있으면 삭제 차단
        if (!menu.getChildren().isEmpty()) {
            throw new IllegalArgumentException("하위 메뉴가 존재하여 삭제할 수 없습니다. 하위 메뉴를 먼저 삭제해주세요.");
        }

        // 해당 메뉴의 읽기 권한 데이터 먼저 삭제 (고아 데이터 방지)
        permissionReadRepository.deleteAllByMenuId(id);

        menuRepository.delete(menu);
        menuQueryService.evictMenuCache();
    }

    // ── 형제 메뉴 순서 일괄 변경 ──────────────────────────────────────────────

    @Transactional
    public void reorderMenus(List<MenuDTO.ReorderItem> items) {
        if (items == null || items.isEmpty()) return;

        Map<Long, Integer> orderMap = items.stream()
                .filter(i -> i.getId() != null && i.getSortOrder() != null)
                .collect(Collectors.toMap(MenuDTO.ReorderItem::getId, MenuDTO.ReorderItem::getSortOrder));

        List<Menu> menus = menuRepository.findAllById(orderMap.keySet());

        for (Menu menu : menus) {
            menu.setSortOrder(orderMap.get(menu.getId()));
        }

        menuQueryService.evictMenuCache();
    }

    // ── 내부 유틸: flat 목록 → 트리 변환 ─────────────────────────────────────

    private List<MenuDTO.TreeRes> buildTree(List<Menu> menus) {
        Map<Long, MenuDTO.TreeRes> nodeMap = new LinkedHashMap<>();
        List<MenuDTO.TreeRes> roots = new ArrayList<>();

        for (Menu menu : menus) {
            MenuDTO.TreeRes node = MenuDTO.TreeRes.from(menu);
            nodeMap.put(menu.getId(), node);

            if (menu.getParent() == null) {
                roots.add(node);
            } else {
                MenuDTO.TreeRes parent = nodeMap.get(menu.getParent().getId());
                if (parent != null) {
                    parent.getChildren().add(node);
                }
            }
        }

        return roots;
    }
}
