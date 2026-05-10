package com.bada.cali.api;

import com.bada.cali.common.ResMessage;
import com.bada.cali.dto.MenuDTO;
import com.bada.cali.service.MenuAdminService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController("ApiMenuAdminController")
@RequestMapping("/api/admin/menus")
@RequiredArgsConstructor
@Tag(name = "메뉴 관리 API", description = "관리자 전용 메뉴 CRUD 및 순서 변경 API")
public class MenuAdminController {

    private final MenuAdminService menuAdminService;

    // ── 전체 메뉴 트리 조회 ────────────────────────────────────────────────────

    @GetMapping("/tree")
    @Operation(summary = "전체 메뉴 트리 조회", description = "is_visible 무관 전체 메뉴를 계층 구조로 반환")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "500", description = "서버 오류")
    })
    public ResponseEntity<ResMessage<List<MenuDTO.TreeRes>>> getMenuTree() {
        List<MenuDTO.TreeRes> tree = menuAdminService.getMenuTree();
        return ResponseEntity.ok(new ResMessage<>(1, "조회 성공", tree));
    }

    // ── menu_code 중복 체크 ────────────────────────────────────────────────────

    @GetMapping("/check-code")
    @Operation(summary = "메뉴 코드 중복 확인",
               description = "menuCode가 이미 사용 중인지 확인. 수정 시 excludeId로 자기 자신 제외")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "확인 완료 (data: true=중복, false=사용가능)"),
            @ApiResponse(responseCode = "500", description = "서버 오류")
    })
    public ResponseEntity<ResMessage<Boolean>> checkMenuCode(
            @Parameter(description = "확인할 메뉴 코드") @RequestParam String menuCode,
            @Parameter(description = "수정 시 제외할 메뉴 ID (신규 등록 시 생략)") @RequestParam(required = false) Long excludeId) {
        boolean isDuplicate = menuAdminService.isMenuCodeDuplicate(menuCode, excludeId);
        return ResponseEntity.ok(new ResMessage<>(1, isDuplicate ? "중복된 코드입니다." : "사용 가능한 코드입니다.", isDuplicate));
    }

    // ── 메뉴 등록 ─────────────────────────────────────────────────────────────

    @PostMapping
    @Operation(summary = "메뉴 등록", description = "신규 메뉴 등록. parentId가 없으면 대메뉴(depth=1)로 생성")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "등록 성공"),
            @ApiResponse(responseCode = "400", description = "유효성 오류 또는 menu_code 중복"),
            @ApiResponse(responseCode = "404", description = "상위 메뉴를 찾을 수 없음"),
            @ApiResponse(responseCode = "500", description = "서버 오류")
    })
    public ResponseEntity<ResMessage<MenuDTO.TreeRes>> createMenu(@Valid @RequestBody MenuDTO.CreateReq req) {
        MenuDTO.TreeRes created = menuAdminService.createMenu(req);
        return ResponseEntity.ok(new ResMessage<>(1, "메뉴가 등록되었습니다.", created));
    }

    // ── 메뉴 수정 ─────────────────────────────────────────────────────────────

    @PatchMapping("/{id}")
    @Operation(summary = "메뉴 수정", description = "메뉴명, 코드, URL, 링크방식, 노출 여부 수정")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "수정 성공"),
            @ApiResponse(responseCode = "400", description = "유효성 오류 또는 menu_code 중복"),
            @ApiResponse(responseCode = "404", description = "메뉴를 찾을 수 없음"),
            @ApiResponse(responseCode = "500", description = "서버 오류")
    })
    public ResponseEntity<ResMessage<MenuDTO.TreeRes>> updateMenu(
            @Parameter(description = "메뉴 ID") @PathVariable Long id,
            @Valid @RequestBody MenuDTO.UpdateReq req) {
        MenuDTO.TreeRes updated = menuAdminService.updateMenu(id, req);
        return ResponseEntity.ok(new ResMessage<>(1, "메뉴가 수정되었습니다.", updated));
    }

    // ── 메뉴 삭제 ─────────────────────────────────────────────────────────────

    @DeleteMapping("/{id}")
    @Operation(summary = "메뉴 삭제",
               description = "메뉴 삭제. 하위 메뉴가 있으면 차단. 연결된 읽기 권한(member_permission_read)도 함께 삭제")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "삭제 성공"),
            @ApiResponse(responseCode = "400", description = "하위 메뉴가 존재하여 삭제 불가"),
            @ApiResponse(responseCode = "404", description = "메뉴를 찾을 수 없음"),
            @ApiResponse(responseCode = "500", description = "서버 오류")
    })
    public ResponseEntity<ResMessage<Void>> deleteMenu(
            @Parameter(description = "메뉴 ID") @PathVariable Long id) {
        menuAdminService.deleteMenu(id);
        return ResponseEntity.ok(new ResMessage<>(1, "메뉴가 삭제되었습니다.", null));
    }

    // ── 형제 메뉴 순서 일괄 변경 ──────────────────────────────────────────────

    @PatchMapping("/reorder")
    @Operation(summary = "메뉴 순서 변경", description = "동일 부모 내 형제 메뉴의 sort_order를 일괄 업데이트")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "순서 변경 성공"),
            @ApiResponse(responseCode = "400", description = "유효성 오류"),
            @ApiResponse(responseCode = "500", description = "서버 오류")
    })
    public ResponseEntity<ResMessage<Void>> reorderMenus(@RequestBody List<MenuDTO.ReorderItem> items) {
        menuAdminService.reorderMenus(items);
        return ResponseEntity.ok(new ResMessage<>(1, "순서가 변경되었습니다.", null));
    }
}
