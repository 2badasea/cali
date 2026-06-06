package com.bada.cali.api;

import com.bada.cali.common.ResMessage;
import com.bada.cali.dto.AgentAccountDTO;
import com.bada.cali.dto.TuiGridDTO;
import com.bada.cali.repository.projection.AgentAccountListRow;
import com.bada.cali.security.CustomUserDetails;
import com.bada.cali.service.MemberServiceImpl;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@Tag(name = "업체계정관리", description = "업체 서브계정 등록·수정·삭제·목록 조회 API")
@RestController("ApiAgentAccountController")
@RequestMapping("/api/admin/agentAccount")
@RequiredArgsConstructor
@Log4j2
public class AgentAccountController {

    private final MemberServiceImpl memberService;

    // ── 목록 조회 (GET /api/admin/agentAccount/list) ─────────────────────

    @Operation(summary = "업체계정 목록 조회",
            description = "agent_id > 0, is_visible='y' 조건의 멤버 목록을 페이지네이션으로 조회함. " +
                    "isActive 필터와 loginId/name 검색을 지원함.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "500", description = "서버 오류"),
    })
    @GetMapping("/list")
    public ResponseEntity<TuiGridDTO.Res<TuiGridDTO.ResData<AgentAccountListRow>>> getList(
            @ModelAttribute AgentAccountDTO.ListReq req) {

        TuiGridDTO.Res<TuiGridDTO.ResData<AgentAccountListRow>> res =
                memberService.getAgentAccountList(req);
        return ResponseEntity.ok(res);
    }

    // ── 단건 조회 (GET /api/admin/agentAccount/{id}) ────────────────────

    @Operation(summary = "업체계정 단건 조회",
            description = "수정 모달 초기화 시 DB 최신값을 반환함.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "404", description = "대상 계정을 찾을 수 없음"),
            @ApiResponse(responseCode = "500", description = "서버 오류"),
    })
    @GetMapping("/{id}")
    public ResponseEntity<ResMessage<AgentAccountDTO.DetailRes>> getDetail(@PathVariable Long id) {
        AgentAccountDTO.DetailRes detail = memberService.getAgentAccountDetail(id);
        return ResponseEntity.ok(new ResMessage<>(1, "조회 성공", detail));
    }

    // ── 등록 (POST /api/admin/agentAccount) ─────────────────────────────

    @Operation(summary = "업체계정 등록",
            description = "업체 서브계정을 신규 등록함. loginId 중복 시 400 반환.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "등록 성공"),
            @ApiResponse(responseCode = "400", description = "아이디 중복 또는 유효하지 않은 파라미터"),
            @ApiResponse(responseCode = "500", description = "서버 오류"),
    })
    @PostMapping
    public ResponseEntity<ResMessage<Void>> create(
            @Valid @RequestBody AgentAccountDTO.CreateReq req,
            @AuthenticationPrincipal CustomUserDetails user) {

        memberService.createAgentAccount(req, user);
        return ResponseEntity.ok(new ResMessage<>(1, "업체계정이 등록되었습니다.", null));
    }

    // ── 수정 (PATCH /api/admin/agentAccount/{id}) ────────────────────────

    @Operation(summary = "업체계정 수정",
            description = "업체명·로그인허용유무·agentId를 수정함. 비밀번호는 입력 시에만 변경됨.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "수정 성공"),
            @ApiResponse(responseCode = "400", description = "유효하지 않은 파라미터"),
            @ApiResponse(responseCode = "404", description = "대상 계정을 찾을 수 없음"),
            @ApiResponse(responseCode = "500", description = "서버 오류"),
    })
    @PatchMapping("/{id}")
    public ResponseEntity<ResMessage<Void>> update(
            @PathVariable Long id,
            @Valid @RequestBody AgentAccountDTO.UpdateReq req,
            @AuthenticationPrincipal CustomUserDetails user) {

        memberService.updateAgentAccount(id, req, user);
        return ResponseEntity.ok(new ResMessage<>(1, "업체계정이 수정되었습니다.", null));
    }

    // ── 삭제 (DELETE /api/admin/agentAccount) ────────────────────────────

    @Operation(summary = "업체계정 삭제",
            description = "선택한 계정들을 소프트삭제함. " +
                    "각 계정의 agentId 기준으로 agent.is_visible='n'인지 사전 검증함. " +
                    "하나라도 조건 미충족 시 전체 삭제 불가.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "삭제 성공"),
            @ApiResponse(responseCode = "400", description = "삭제 조건 미충족 (업체가 아직 삭제되지 않음)"),
            @ApiResponse(responseCode = "500", description = "서버 오류"),
    })
    @DeleteMapping
    public ResponseEntity<ResMessage<Void>> delete(
            @Valid @RequestBody AgentAccountDTO.DeleteReq req,
            @AuthenticationPrincipal CustomUserDetails user) {

        memberService.deleteAgentAccounts(req, user);
        return ResponseEntity.ok(new ResMessage<>(1, "업체계정이 삭제되었습니다.", null));
    }
}
