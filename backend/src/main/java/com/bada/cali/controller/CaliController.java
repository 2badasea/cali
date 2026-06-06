package com.bada.cali.controller;

import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.temporal.TemporalAdjuster;
import java.time.temporal.TemporalAdjusters;

@Controller
@RequestMapping("/cali")
@Log4j2
public class CaliController {
	
	// 교정접수 페이지 이동
	@GetMapping(value = "/caliOrder")
	public String caliOrder(Model model) {
		model.addAttribute("title", "교정접수");
		
		// 검색시작일, 검색종료일 반환
		// NOTE LocalDate 타입으로 model에 담아서 타임리프선에서 파싱하는 방법과 String으로 보내는 방법 존재
		// 1. LocalDate 타입으로 생성 (NOTATION 41번으로 정리. 날짜 구하는 방식)
		LocalDate today = LocalDate.now();
		LocalDate orderStartDate = today.minusMonths(3).withDayOfMonth(1);
		LocalDate orderEndDate = today.with(TemporalAdjusters.lastDayOfMonth());
		
		// String으로 보낸다면 아래처럼 형변환 후 전달
		// String startDate = orderStartDate.toString();
		// String endDate = orderEndDate.toString();
		
		// model에 담아서 리턴 -> 타임리프를 통해 값을 format하여 표시한다.
		model.addAttribute("orderStartDate", orderStartDate);
		model.addAttribute("orderEndDate", orderEndDate);
		
		return "cali/caliOrder";
	}
	
	// 교정접수 등록/수정 페이지 [모달]
	@PostMapping(value = "/caliOrderModify")
	public String caliOrderModify(Model model, @RequestParam(required = false) Long id) {
		boolean isModify = (id != null && id > 0);
		model.addAttribute("isModify", isModify);
		return "cali/caliOrderModify";
	}
	
	// 접수상세내역
	@GetMapping(value = "/orderDetails")
	public String orderDetails(Model model, @RequestParam long caliOrderId) {
		model.addAttribute("title", "교정접수내역");
		model.addAttribute("caliOrderId", caliOrderId);
		return "cali/orderDetails";
	}
	
	// 성적서 등록 [모달]
	@PostMapping(value = "/registerMultiReport")
	public String registerMultiReport()  {
		return "cali/registerMultiReport";
	}
	
	// 성적서 수정 [모달] (SELF 전용)
	@PostMapping(value = "/reportModify")
	public String reportModify() {
		log.info("성적서 수정 모달 호출");
		return "cali/reportModify";
	}

	// 대행성적서 등록 모달 (orderDetails 페이지에서 gModal로 호출)
	@PostMapping(value = "/agcyRegisterReport")
	public String agcyRegisterReport() {
		log.info("대행성적서 등록 모달 호출");
		return "cali/agcyRegisterReport";
	}

	// 대행성적서 수정 모달 (orderDetails 페이지에서 gModal로 호출)
	@PostMapping(value = "/agcyReportModify")
	public String agcyReportModify() {
		log.info("대행성적서 수정 모달 호출");
		return "cali/agcyReportModify";
	}

	// 대행성적서 통합수정 모달 (orderDetails 페이지에서 gModal로 호출)
	@PostMapping(value = "/agcyReportMultiUpdate")
	public String agcyReportMultiUpdate() {
		log.info("대행성적서 통합수정 모달 호출");
		return "cali/agcyReportMultiUpdate";
	}

	// 실무자결재 페이지 이동
	@GetMapping(value = "/workApproval")
	public String workApproval(Model model) {
		model.addAttribute("title", "실무자결재");
		return "cali/workApproval";
	}

	// 성적서작성 모달 (workApproval 리스트 또는 reportModify 모달에서 호출)
	// gModal은 jQuery .load(url, data)를 사용하므로 데이터가 있을 때 POST로 전송됨
	@PostMapping(value = "/reportWrite")
	public String reportWrite() {
		return "cali/reportWrite";
	}

	// 통합수정 모달 (workApproval, orderDetails 페이지에서 호출)
	@PostMapping(value = "/selfReportMultiUpdate")
	public String selfReportMultiUpdate() {
		return "cali/selfReportMultiUpdate";
	}

	// 성적서업로드 모달 (workApproval 페이지에서 호출)
	@PostMapping(value = "/reportUpload")
	public String reportUpload() {
		return "cali/reportUpload";
	}

	// 기술책임자결재 페이지 이동
	@GetMapping(value = "/managerApproval")
	public String managerApproval(Model model) {
		model.addAttribute("title", "기술책임자결재");
		return "cali/managerApproval";
	}

	// 반려사유 입력 모달 (managerApproval 페이지에서 gModal로 호출)
	@PostMapping(value = "/reportReject")
	public String reportReject() {
		return "cali/reportReject";
	}

	// 성적서출력 페이지 이동
	@GetMapping(value = "/reportPrint")
	public String reportPrint(Model model) {
		model.addAttribute("title", "성적서출력");
		return "cali/reportPrint";
	}

	// 대행교정 페이지 이동
	@GetMapping(value = "/agcyReport")
	public String agcyReport(Model model) {
		model.addAttribute("title", "대행교정");
		return "cali/agcyReport";
	}

	// 업체별교정이력조회 페이지 이동
	@GetMapping(value = "/customerCaliHistory")
	public String customerCaliHistory(Model model) {
		model.addAttribute("title", "업체별교정이력조회");
		return "cali/customerCaliHistory";
	}

	// 출장일정 페이지 이동
	@GetMapping(value = "/businessTrip")
	public String businessTrip(Model model) {
		model.addAttribute("title", "출장일정");
		return "cali/businessTrip";
	}

	// 출장일정 등록/수정 모달 (gModal 호출)
	@PostMapping(value = "/businessTripModify")
	public String businessTripModify(Model model, @RequestParam(required = false) Long id) {
		// id가 있으면 수정 모달, 없으면 등록 모달
		boolean isModify = (id != null && id > 0);
		model.addAttribute("isModify", isModify);
		model.addAttribute("btripId", id);
		return "cali/businessTripModify";
	}

	// 출장일정 리스트 페이지 이동 (캘린더 '리스트보기' 버튼 또는 직접 URL 진입)
	// 메뉴테이블 미등록 — 캘린더 페이지(/cali/businessTrip)에서만 진입 유도
	@GetMapping(value = "/businessTripList")
	public String businessTripList(Model model) {
		model.addAttribute("title", "출장일정 리스트");
		return "cali/businessTripList";
	}
}
