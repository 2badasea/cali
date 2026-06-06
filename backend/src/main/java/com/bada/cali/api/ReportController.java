package com.bada.cali.api;

import com.bada.cali.common.ResMessage;
import com.bada.cali.dto.ReportDTO;
import com.bada.cali.dto.ReportPrintDTO;
import com.bada.cali.dto.TuiGridDTO;
import com.bada.cali.repository.projection.OrderDetailsList;
import com.bada.cali.repository.projection.ReportPrintListRow;
import com.bada.cali.repository.projection.WorkApprovalListRow;
import com.bada.cali.security.CustomUserDetails;
import com.bada.cali.entity.FileInfo;
import com.bada.cali.service.FileServiceImpl;
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
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController("ApiReportController")
@Log4j2
@RequiredArgsConstructor
@RequestMapping("/api/report")
@Tag(name = "Report", description = "성적서 관련 API")
public class ReportController {

	private final ReportServiceImpl reportService;
	private final FileServiceImpl fileService;

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

	@Operation(summary = "성적서대기변경",
			description = "실무자 결재 완료(workStatus=SUCCESS) 상태인 성적서를 대기(IDLE) 상태로 초기화하여 재결재 가능 상태로 전환함. " +
					"기술책임자 결재가 진행 중(PROGRESS)이거나 완료(SUCCESS)된 경우 변경 불가. " +
					"하나라도 부적합한 성적서가 포함되면 전체 처리 중단하고 부적합 목록 반환.")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "대기변경 성공 또는 검증 실패 목록 반환 (code=-1)"),
			@ApiResponse(responseCode = "400", description = "요청 파라미터 오류"),
			@ApiResponse(responseCode = "500", description = "서버 오류"),
	})
	@PatchMapping("/resetWorkStatus")
	public ResponseEntity<ResMessage<?>> resetWorkStatus(
			@Valid @RequestBody ReportDTO.ResetWorkStatusReq req,
			@AuthenticationPrincipal CustomUserDetails user
	) {
		return ResponseEntity.ok(reportService.resetWorkStatus(req.reportIds(), user));
	}

	// 성적서출력 목록 조회
	@Operation(summary = "성적서출력 목록 조회",
			description = "기술책임자 결재 완료(approval_status=SUCCESS) + 미출력(is_print='n') + 자체성적서(SELF) 목록 조회. " +
					"반려/불가 상태 제외. 기본 날짜 기준은 성적서발행일(approval_datetime), 기본 페이지당 50건.")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "조회 성공"),
			@ApiResponse(responseCode = "500", description = "서버 오류"),
	})
	@GetMapping("/reportPrintList")
	public ResponseEntity<TuiGridDTO.Res<TuiGridDTO.ResData<ReportPrintListRow>>> getReportPrintList(
			ReportPrintDTO.ListReq req) {

		TuiGridDTO.ResData<ReportPrintListRow> data = reportService.getReportPrintList(req);
		return ResponseEntity.ok(new TuiGridDTO.Res<>(true, data));
	}

	// ── 대행성적서(AGCY) ──────────────────────────────────────────────────────

	@Operation(summary = "대행성적서 등록",
			description = "접수 건에 대해 대행성적서를 N건 일괄 등록함. " +
					"자체대행성적서번호({접수번호}-D{4digits})와 관리번호(BD{yy}-D{5digits})를 자동 채번. " +
					"외부 성적서번호(report_num)는 외부 교정기관에서 받은 후 수정 시 입력.")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "등록 성공"),
			@ApiResponse(responseCode = "400", description = "요청 형식 오류"),
			@ApiResponse(responseCode = "404", description = "접수 정보 없음"),
			@ApiResponse(responseCode = "500", description = "서버 오류"),
	})
	@PostMapping("/addAgcyReport")
	public ResponseEntity<ResMessage<?>> addAgcyReport(
			@Valid @RequestBody ReportDTO.AddAgcyReportReq request,
			@AuthenticationPrincipal CustomUserDetails user
	) {
		ResMessage<Object> res = reportService.addAgcyReport(request, user);
		return ResponseEntity.ok(res);
	}

	@Operation(summary = "대행성적서 단건 조회",
			description = "수정 모달에서 사용할 대행성적서 상세 정보를 조회함. 접수 업체/발행처 정보 포함.")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "조회 성공"),
			@ApiResponse(responseCode = "404", description = "대행성적서 없음"),
			@ApiResponse(responseCode = "500", description = "서버 오류"),
	})
	@GetMapping("/getAgcyReportDetail")
	public ResponseEntity<ResMessage<ReportDTO.AgcyReportDetailRes>> getAgcyReportDetail(
			@Parameter(description = "성적서 ID") @RequestParam Long id
	) {
		ReportDTO.AgcyReportDetailRes res = reportService.getAgcyReportDetail(id);
		return ResponseEntity.ok(new ResMessage<>(1, null, res));
	}

	@Operation(summary = "대행성적서 수정",
			description = "대행성적서 기본정보(대행의뢰처, 기기정보, 교정일자, 외부 성적서번호 등)를 수정함. " +
					"reportNum이 null이면 기존 값 유지(외부 성적서번호를 아직 받지 못한 경우).")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "수정 성공"),
			@ApiResponse(responseCode = "400", description = "요청 형식 오류 또는 AGCY 타입 아님"),
			@ApiResponse(responseCode = "404", description = "성적서 없음"),
			@ApiResponse(responseCode = "500", description = "서버 오류"),
	})
	@PatchMapping("/updateAgcyReport")
	public ResponseEntity<ResMessage<?>> updateAgcyReport(
			@Valid @RequestBody ReportDTO.UpdateAgcyReportReq req,
			@AuthenticationPrincipal CustomUserDetails user
	) {
		ResMessage<Object> res = reportService.updateAgcyReport(req, user);
		return ResponseEntity.ok(res);
	}

	@Operation(summary = "대행성적서 통합수정",
			description = "선택된 복수의 대행성적서에 대행의뢰처·교정일자·진행상태·외부 성적서번호를 일괄 수정함. " +
					"null인 항목은 변경하지 않음. reportStatus는 SUCCESS(완료) 또는 CANCEL(취소)만 허용.")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "수정 성공"),
			@ApiResponse(responseCode = "400", description = "요청 형식 오류 또는 상태값 오류"),
			@ApiResponse(responseCode = "500", description = "서버 오류"),
	})
	@PatchMapping("/agcyReportMultiUpdate")
	public ResponseEntity<ResMessage<?>> agcyReportMultiUpdate(
			@Valid @RequestBody ReportDTO.AgcyReportMultiUpdateReq req,
			@AuthenticationPrincipal CustomUserDetails user
	) {
		ResMessage<Object> res = reportService.agcyReportMultiUpdate(req, user);
		return ResponseEntity.ok(res);
	}

	@Operation(summary = "대행성적서 상태 변경 (취소/초기화)",
			description = "대행성적서의 진행상태를 CANCEL(취소) 또는 NORMAL(대기)로 변경함. SUCCESS(완료) 설정은 불가.")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "변경 성공"),
			@ApiResponse(responseCode = "400", description = "유효하지 않은 상태값 또는 대행성적서가 아닌 경우"),
			@ApiResponse(responseCode = "404", description = "성적서 없음"),
			@ApiResponse(responseCode = "500", description = "서버 오류"),
	})
	@PreAuthorize("hasRole('ADMIN')")
	@PatchMapping("/agcyUpdateStatus")
	public ResponseEntity<ResMessage<Object>> agcyUpdateStatus(
			@RequestBody ReportDTO.UpdateAgcyStatusReq req,
			@AuthenticationPrincipal CustomUserDetails user
	) {
		return ResponseEntity.ok(reportService.updateAgcyStatus(req, user));
	}

	// 대행성적서 파일 업로드 (xlsx 또는 pdf, 각 타입별 1개 제한)
	@Operation(summary = "대행성적서 파일 업로드",
			description = "AGCY 성적서에 xlsx 또는 pdf 파일을 업로드. 동일 타입 파일이 이미 존재하면 400 반환 (삭제 후 재업로드 필요)")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "업로드 성공"),
			@ApiResponse(responseCode = "400", description = "허용되지 않은 확장자 또는 파일 중복"),
			@ApiResponse(responseCode = "500", description = "서버 오류"),
	})
	@PostMapping(value = "/agcyUploadFile", consumes = "multipart/form-data")
	public ResponseEntity<ResMessage<Long>> agcyUploadFile(
			@RequestParam Long reportId,
			@RequestPart("file") MultipartFile file,
			@AuthenticationPrincipal CustomUserDetails user
	) {
		FileInfo saved = fileService.uploadAgcyReportFile(reportId, file, user.getId());
		return ResponseEntity.ok(new ResMessage<>(1, "파일 업로드 성공", saved.getId()));
	}

}
