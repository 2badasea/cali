package com.bada.cali.api;

import com.bada.cali.common.ResMessage;
import com.bada.cali.dto.ReportDTO;
import com.bada.cali.dto.TuiGridDTO;
import com.bada.cali.repository.projection.OrderDetailsList;
import com.bada.cali.repository.projection.WorkApprovalListRow;
import com.bada.cali.security.CustomUserDetails;
import com.bada.cali.service.ReportServiceImpl;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController("ApiReportController")
@Log4j2
@RequiredArgsConstructor
@RequestMapping("/api/report")
@Tag(name = "Report", description = "성적서 관련 API")
public class ReportController {

	private final ReportServiceImpl reportService;

	// 성적서 등록
	@Operation(summary = "성적서 등록", description = "접수 건에 속하는 성적서(자체/대행, 부모/자식)를 일괄 등록함.")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "등록 성공"),
			@ApiResponse(responseCode = "400", description = "요청 형식 오류"),
			@ApiResponse(responseCode = "404", description = "접수 정보 없음"),
			@ApiResponse(responseCode = "500", description = "서버 오류"),
	})
	@PostMapping(value = "/addReport")
	public ResponseEntity<ResMessage<?>> addReport(
			@Valid @RequestBody List<ReportDTO.addReportReq> reports,
			@Parameter(description = "접수 ID (쿼리스트링)") @RequestParam Long caliOrderId,
			@AuthenticationPrincipal CustomUserDetails user) {

		ResMessage<Object> resMessage = reportService.addReport(reports, caliOrderId, user);

		return ResponseEntity.ok(resMessage);
	}

	// 접수상세내역 내 성적서 리스트
	@Operation(summary = "접수상세내역 성적서 목록 조회", description = "특정 접수에 속한 성적서 목록을 페이징/필터/검색 조건으로 조회함.")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "조회 성공"),
			@ApiResponse(responseCode = "500", description = "서버 오류"),
	})
	@GetMapping(value = "/getOrderDetailsList")
	public ResponseEntity<TuiGridDTO.Res<TuiGridDTO.ResData<OrderDetailsList>>> getOrderDetailsList(@ModelAttribute ReportDTO.GetOrderDetailsReq req) {
		// 리스트 데이터 가져오기 (인터페이스 프로젝션 형태로 가져옴 )
		TuiGridDTO.ResData<OrderDetailsList> reportGridData = reportService.getOrderDetailsList(req);
		// 가져온 데이터를 바탕으로 최종 그리드 API 형식으로 세팅

		TuiGridDTO.Res<TuiGridDTO.ResData<OrderDetailsList>> body = new TuiGridDTO.Res<>(true, reportGridData);

		return ResponseEntity.ok(body);
	}

	// 삭제 대상 성적서들이 삭제에 문제가 없는지 판단
	@Operation(summary = "성적서 삭제 가능 여부 검증", description = "선택한 성적서들이 삭제 조건(결재 미진행, 마지막 순서 등)을 만족하는지 사전 검증함.")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "검증 완료 (code 값으로 가능/불가 구분)"),
			@ApiResponse(responseCode = "400", description = "요청 형식 오류"),
			@ApiResponse(responseCode = "404", description = "접수 정보 없음"),
			@ApiResponse(responseCode = "500", description = "서버 오류"),
	})
	@PostMapping(value = "/isValidDelete")
	public ResponseEntity<ResMessage<?>> isValidDelete(
			@Valid @RequestBody ReportDTO.ValidateDeleteReq validateDeleteReq
	) {
		log.info("삭제 검증 api 도착");
		ResMessage<?> resMessage = reportService.isValidDelete(validateDeleteReq);

		return ResponseEntity.ok(resMessage);
	}

	// 성적서 삭제 시키기
	@Operation(summary = "성적서 삭제", description = "선택한 성적서 ID 목록을 논리 삭제(is_visible = 'n') 처리함. 부모 삭제 시 연관된 자식성적서도 함께 삭제됨.")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "삭제 성공"),
			@ApiResponse(responseCode = "400", description = "요청 형식 오류"),
			@ApiResponse(responseCode = "500", description = "서버 오류"),
	})
	@DeleteMapping(value = "/deleteReport")
	public ResponseEntity<ResMessage<?>> deleteReport(
			@Valid @RequestBody ReportDTO.DeleteReportReq deleteReportReq,
			@AuthenticationPrincipal CustomUserDetails user) {

		log.info("성적서 삭제요청 api 호출");
		ResMessage<?> resMessage = reportService.deleteReport(deleteReportReq, user);

		return ResponseEntity.ok(resMessage);
	}

	// 성적서 수정 모달 데이터 조회
	@Operation(summary = "성적서 단건 조회", description = "수정 모달에서 사용할 성적서 상세 정보(부모+자식)를 조회함.")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "조회 성공"),
			@ApiResponse(responseCode = "404", description = "성적서 없음"),
			@ApiResponse(responseCode = "500", description = "서버 오류"),
	})
	@GetMapping(value = "/getReportInfo")
	public ResponseEntity<ResMessage<ReportDTO.ReportInfoRes>> getReportInfo(
			@Parameter(description = "성적서 ID") @RequestParam Long id
	) {
		log.info("개별 성적서 데이터 조회");
		log.info("쿼리스트링 성적서 id: {}", id);
		ReportDTO.ReportInfoRes resData = reportService.getReportInfo(id);
		return ResponseEntity.ok(new ResMessage<>(1, null, resData));
	}

	// 자식 성적서 삭제 요청
	@Operation(summary = "자식성적서 단건 삭제", description = "수정 모달 내에서 자식성적서를 즉시 논리 삭제(is_visible = 'n') 처리함.")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "삭제 성공"),
			@ApiResponse(responseCode = "404", description = "성적서 없음"),
			@ApiResponse(responseCode = "500", description = "서버 오류"),
	})
	@DeleteMapping(value = "/delete/{id}")
	public ResponseEntity<ResMessage<?>> delete(
			@Parameter(description = "자식성적서 ID") @PathVariable Long id,
			@AuthenticationPrincipal CustomUserDetails user
	) {
		log.info("delete id : {}", id);
		ResMessage<Object> resMessage = reportService.deleteById(id, user);

		return ResponseEntity.ok(resMessage);
	}

	// 실무자결재 목록 조회
	@Operation(summary = "실무자결재 목록 조회", description = "전체 자체성적서(SELF)를 대상으로 진행상태·결재상태·접수구분·중/소분류·키워드 필터 조건으로 목록을 조회함.")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "조회 성공"),
			@ApiResponse(responseCode = "500", description = "서버 오류"),
	})
	@GetMapping(value = "/workApprovalList")
	public ResponseEntity<TuiGridDTO.Res<TuiGridDTO.ResData<WorkApprovalListRow>>> getWorkApprovalList(
			@ModelAttribute ReportDTO.GetWorkApprovalListReq req) {

		TuiGridDTO.ResData<WorkApprovalListRow> data = reportService.getWorkApprovalList(req);
		return ResponseEntity.ok(new TuiGridDTO.Res<>(true, data));
	}

	// 성적서 수정 요청
	@Operation(summary = "성적서 수정", description = "성적서 기본정보, 자식성적서, 표준장비 데이터를 일괄 수정함.")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "수정 성공"),
			@ApiResponse(responseCode = "400", description = "요청 형식 오류"),
			@ApiResponse(responseCode = "404", description = "성적서 없음"),
			@ApiResponse(responseCode = "500", description = "서버 오류"),
	})
	@PatchMapping(value = "updateReport")
	public ResponseEntity<ResMessage<?>> updateReport(
			@Valid @RequestBody ReportDTO.ReportUpdateReq req,
			@AuthenticationPrincipal CustomUserDetails user
	) {
		ResMessage<Object> resMessage = reportService.updateReport(req, user);

		return ResponseEntity.ok(resMessage);
	}

	// 성적서 통합수정
	@Operation(
			summary = "성적서 통합수정",
			description = "선택된 복수의 성적서에 완료예정일·교정일자·환경정보·중/소분류코드·실무자·기술책임자·표준장비를 일괄 수정함. " +
					"null(또는 0)인 항목은 수정하지 않음. " +
					"실무자/기술책임자는 updateMemberInfo=true 이고 각 성적서의 원래 중분류코드에 속한 직원일 때만 반영됨."
	)
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "수정 성공"),
			@ApiResponse(responseCode = "400", description = "요청 형식 오류"),
			@ApiResponse(responseCode = "500", description = "서버 오류"),
	})
	@PatchMapping("/selfReportMultiUpdate")
	public ResponseEntity<ResMessage<?>> selfReportMultiUpdate(
			@Valid @RequestBody ReportDTO.SelfReportMultiUpdateReq req,
			@AuthenticationPrincipal CustomUserDetails user
	) {
		ResMessage<?> res = reportService.selfReportMultiUpdate(req, user);
		return ResponseEntity.ok(res);
	}

	/**
	 * 성적서작성 전 필수항목 일괄 검증 API
	 *
	 * reportWrite 모달에서 샘플을 선택한 뒤, 배치 생성(성적서작성) 전에 호출한다.
	 * 대상 성적서들의 교정일자/환경정보/중소분류/실무자·기술책임자 및 서명이미지 존재를 검증하여
	 * 누락 항목이 있는 성적서를 필드별로 묶어 반환한다.
	 */
	@Operation(
			summary = "성적서작성 필수항목 검증",
			description = "배치 생성 전 대상 성적서들의 필수항목(교정일자, 환경정보, 중/소분류, " +
					"실무자·기술책임자 및 서명이미지) 존재를 일괄 검증. " +
					"allPassed=true이면 모두 통과, false이면 failures에 필드별 누락 성적서번호 목록 포함"
	)
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "검증 완료 (allPassed 값으로 결과 판단)"),
			@ApiResponse(responseCode = "400", description = "요청 파라미터 오류"),
			@ApiResponse(responseCode = "500", description = "서버 오류"),
	})
	@PostMapping("/validateWrite")
	public ResponseEntity<ResMessage<ReportDTO.ValidateWriteRes>> validateWrite(
			@Valid @RequestBody ReportDTO.ValidateWriteReq req
	) {
		ReportDTO.ValidateWriteRes res = reportService.validateWriteReports(req.getReportIds());
		return ResponseEntity.ok(new ResMessage<>(1, "검증 완료", res));
	}

	@Operation(summary = "성적서 진행상태 변경",
			description = "수리(REPAIR) / 불가(IMPOSSIBLE) / 초기화(NORMAL) 처리. " +
					"수리·불가 처리 시 성적서작성 및 결재 관련 데이터와 첨부파일을 모두 초기화함. " +
					"실무자 결재가 완료된 성적서(work_status=SUCCESS)는 수리·불가 처리 불가.")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "상태 변경 성공"),
			@ApiResponse(responseCode = "400", description = "요청 파라미터 오류 또는 검증 실패"),
			@ApiResponse(responseCode = "404", description = "성적서를 찾을 수 없음"),
			@ApiResponse(responseCode = "500", description = "서버 오류"),
	})
	@PatchMapping("/updateStatus/{id}")
	public ResponseEntity<ResMessage<Void>> updateReportStatus(
			@PathVariable Long id,
			@Valid @RequestBody ReportDTO.UpdateStatusReq req,
			@AuthenticationPrincipal CustomUserDetails user
	) {
		return ResponseEntity.ok(reportService.updateReportStatus(id, req.getNewStatus(), user));
	}


}
