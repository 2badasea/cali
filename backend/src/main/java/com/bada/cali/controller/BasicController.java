package com.bada.cali.controller;

import com.bada.cali.dto.FileInfoDTO;
import com.bada.cali.dto.ItemDTO;
import com.bada.cali.entity.FileInfo;
import com.bada.cali.service.FileServiceImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.data.domain.Page;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Controller
@RequestMapping("/basic")
@Log4j2
@RequiredArgsConstructor
public class BasicController {
	private final FileServiceImpl fileService;
	
	// 기본 페이지 리턴
	// TODO 추후 관리자와 일반 사용자의 로그인 성공 시, 경로 구분할 것
	@GetMapping("/home")
	public String home(Model model) {
		model.addAttribute("title", "홈");
		// NOTE 기본 설정(스프링부트)의 탐색경로: classpath:/templates/ + viewname + .html => /basic/home 을 명시할 경우, //basic/home이 된다.
		return "basic/home";
	}
	
	// 업체관리 페이지
	@GetMapping(value = "/agentList")
	public String agentList(Model model, @AuthenticationPrincipal UserDetails user) {
		model.addAttribute("title", "업체관리");
		return "basic/agentList";
	}
	
	// 업체 등록/수정 페이지 [모달]
	@PostMapping(value = "/agentModify")
	public String agentModify(Model model) {
		return "basic/agentModify";
	}
	
	// 그룹관리 페이지 [모달]
	@PostMapping(value = "/agentGroupModify")
	public String agentGroupModify(Model model) {
		return "basic/agentGroupModify";
	}
	
	// 첨부파일 리스트 [모달]
	@PostMapping(value = "/fileList")
	public String fileList(Model model, @RequestParam String refTableName, @RequestParam Long refTableId) {
		
		// 작업자까지 join을 통해서 가져온다.
		List<FileInfoDTO.FileListRes> fileList = fileService.getFileInfosWithJoin(refTableName, refTableId);
		
		model.addAttribute("fileList", fileList);
		return "basic/fileList";
	}
	
	//
	// 로드뷰 모달 호출
	@PostMapping(value = "/viewRoadMap")
	public String viewRoadMap() {
		return "basic/viewRoadMap";
	}
	
	// 분류코드관리 페이지 호출
	@GetMapping(value = "/itemCode")
	public String itemCode(Model model) {
		model.addAttribute("title", "분류코드관리");
		return "basic/itemCode";
	}
	
	// 대분류코드 관리 [모달]
	@PostMapping(value = "/bigItemCodeModify")
	public String bigItemCodeModify() {
		return "basic/bigItemCodeModify";
	}
	
	// 품목관리
	@GetMapping(value = "/item")
	public String item(Model model) {
		model.addAttribute("title", "품목관리");
		return "basic/item";
	}
	
	// 품목관리 등록/수정 모달
	@PostMapping(value = "/itemModify")
	public String itemModify() {
		return "basic/itemModify";
	}
	
	// 품목 검색
	@PostMapping(value = "/searchItemList")
	public String searchItemList() {
		return "basic/searchItemList";
	}
	
	// 부서 및 직급관리
	@GetMapping(value = "/manageDepartAndLevel")
	public String manageDepartAndLevel(Model model) {
		model.addAttribute("title", "부서 및 직급관리");
		return "basic/manageDepartAndLevel";
	}
	
	// 비고, 요청사항 등의 내용 보여주기 [모달]
	@PostMapping(value = "/showContent")
	public String showContent() {
		return "basic/showContent";
	}
	
	// 샘플관리 페이지 이동
	@GetMapping(value = "sampleManage")
	public String sampleManage(Model model) {
		model.addAttribute("title", "샘플관리");
		return "basic/sampleManage";
	}

	// 성적서시트 설정 페이지 이동
	@GetMapping(value = "/dataSheetSetting")
	public String dataSheetSetting(Model model) {
		model.addAttribute("title", "성적서시트");
		return "basic/dataSheetSetting";
	}

	// 업체계정관리 페이지
	@GetMapping(value = "/agentAccountManage")
	public String agentAccountManage(Model model) {
		model.addAttribute("title", "업체계정관리");
		return "basic/agentAccountManage";
	}

	// 업체계정 등록/수정 모달 (param.id 없으면 등록, 있으면 수정)
	@PostMapping(value = "/agentAccountModify")
	public String agentAccountModify(Model model) {
		return "basic/agentAccountModify";
	}
}
