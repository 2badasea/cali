package com.bada.cali.dto;

import com.bada.cali.common.enums.*;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

// 성적서 관련 DTO 클래스 집합
public class ReportDTO {
	
	// 'ReportDTO'는 네임스페이스. 임의 생성자 호출 방지
	private ReportDTO() {
	}
	
	;
	
	// 성적서 등록 시 넘어오는 파라미터
	@Getter
	@Setter
	@NoArgsConstructor
	@ToString
	public static class addReportReq {
		
		private String hierarchyType;    // 계층구조(부모, 자식), entity에 없음
		private Long caliFee;        // 교정수수료
		private DocType docType;        // 문서타입
		private Integer itemCaliCycle;        // 교정주기
		private String itemName;        // 품목명
		private String itemFormat;        // 형식
		private String itemNum;            // 기기번호
		private String itemMakeAgent;            // 제작회사
		private Long itemId;            // 품몸 고유 id
		private Long middleItemCodeId;    // 중분류 코드 id
		private Long smallItemCodeId;    // 소분류 코드 id
		private OrderType orderType;    // 접수구분 (공인, 비공인, 시험)
		private String remark;            // 비고
		private ReportType reportType;        // 성적서타입 (자체-SELF, 대행-AGCY)
		
		// 자식성적서
		private List<addReportReq> child;
	}
	
	// 접수상세내역 리스트에서 넘어온 검색필터 및 키워드
	@Getter
	@Setter
	public static class GetOrderDetailsReq extends TuiGridDTO.Request {
		// TODO 추후, 중분류/소분류 추가되면 필드 추가 선언할 것
		private String searchType;        // 검색타입
		private String keyword;            // 검색키워드
		private String statusType;        // 진행상태
		private OrderType orderType;        // 접수구분
		private Long caliOrderId;            // 접수id
		private Long middleItemCodeId;        // 중분류코드id
		private Long smallItemCodeId;        // 소분류코드id
	}
	
	// 실무자결재 목록 조회 요청 파라미터
	@Getter
	@Setter
	public static class GetWorkApprovalListReq extends TuiGridDTO.Request {
		private String searchType;       // 검색타입 (reportNum, custAgent, reportAgent, workMember, approvalMember, itemName, itemFormat, itemMakeAgent, itemNum, manageNo)
		private String keyword;          // 검색키워드
		private String reportStatus;     // 진행상태 (NORMAL, REPAIR, IMPOSSIBLE, REJECTED, RESUBMITTED) — 빈값이면 전체
		private String workStatus;       // 실무자 결재상태 (IDLE, READY, PROGRESS, FAIL, SUCCESS) — 기본 IDLE
		private String writeStatus;      // 성적서작성(ExcelWork) 상태 (IDLE, READY, PROGRESS, SUCCESS, FAIL) — 빈값이면 전체
		private OrderType orderType;     // 접수구분 (null이면 전체)
		private Long middleItemCodeId;   // 중분류코드 id (0 또는 null이면 전체)
		private Long smallItemCodeId;    // 소분류코드 id (0 또는 null이면 전체)
	}

	// ── 성적서작성 필수항목 검증 ─────────────────────────────────────────────────

	/**
	 * 성적서작성 필수항목 검증 요청 DTO
	 *
	 * POST /api/report/validateWrite
	 * reportWrite 모달에서 샘플 행 클릭 후 배치 생성 전에 대상 성적서들의
	 * 필수항목(교정일자, 환경정보, 중/소분류, 실무자·기술책임자 및 서명이미지) 존재를 일괄 검증한다.
	 */
	@Getter
	@NoArgsConstructor
	@AllArgsConstructor
	@Schema(description = "성적서작성 필수항목 검증 요청")
	public static class ValidateWriteReq {

		@NotEmpty(message = "대상 성적서를 1건 이상 선택해야 합니다.")
		@Schema(description = "검증 대상 성적서 id 목록", example = "[1, 2, 3]")
		private List<Long> reportIds;
	}

	/**
	 * 성적서작성 필수항목 검증 결과 DTO
	 *
	 * hasInProgress=true → 이미 작업이 진행중인 성적서가 1건 이상 존재.
	 *                       inProgressReportNums에 해당 성적서번호 목록 포함.
	 *                       프론트는 이 경우 진행을 완전히 차단해야 한다.
	 * hasInProgress=false + allPassed=true  → 모든 성적서가 검증 통과 (failures는 빈 리스트)
	 * hasInProgress=false + allPassed=false → 1건 이상 누락 필드 존재. passedIds/failedIds/failures에 상세 포함.
	 */
	@Getter
	@AllArgsConstructor
	@Schema(description = "성적서작성 필수항목 검증 결과")
	public static class ValidateWriteRes {

		@Schema(description = "이미 작업이 진행중인 성적서가 1건이라도 있는지 여부. true이면 진행 완전 차단")
		private boolean hasInProgress;

		@Schema(description = "작업 진행중인 성적서번호 목록 (hasInProgress=true일 때만 내용 있음)")
		private List<String> inProgressReportNums;

		@Schema(description = "모든 성적서가 검증을 통과했는지 여부")
		private boolean allPassed;

		@Schema(description = "검증을 통과한 성적서 id 목록")
		private List<Long> passedIds;

		@Schema(description = "1건 이상 필수항목이 누락된 성적서 id 목록")
		private List<Long> failedIds;

		@Schema(description = "필드별 누락 성적서번호 목록 (allPassed=false 일 때만 내용 있음)")
		private List<FieldFailure> failures;

		/**
		 * 누락 항목 1건 — 필드 한글명과 해당 성적서번호 목록을 묶음
		 */
		@Getter
		@AllArgsConstructor
		@Schema(description = "누락 필드 및 해당 성적서번호 목록")
		public static class FieldFailure {

			@Schema(description = "누락 필드 한글명", example = "교정일자")
			private String field;

			@Schema(description = "해당 필드가 누락된 성적서번호 목록", example = "[\"2026-001-01\", \"2026-001-02\"]")
			private List<String> reportNums;
		}
	}

	// 클라이언트에 반환할 데이터
	@Setter
	@Getter
	public static class ReportRowData {
		// 중분류코드, 소분류코드 또한 추후에 가져와야 함
		private Long id;    // 성적서 고유 id
		private ReportType reportType;    // 구분(자체, 대행)
		private OrderType orderType;    // 접수타입
		private Long middleItemCodeNum;    // 중분류코드 (NOTE 추후에 join으로 가져와야 함)
		private Long smallItemCodeNum;    // 소분류코드 (NOTE 추후에 join으로 가져와야 함)
		private String reportNum;        // 성적서번호
		private String itemName;        // 기기명
		private String itemFormat;        // 기기 형식
		private String itemNum;            // 기기번호
		private Integer itemCaliCycle;        // 품목교정주기
		private String itemMakeAgent;    // 제작회사
		private String manageNo;        // 관리번호
		private ReportStatus reportStatus;    // 성적서 진행상태 (NOTE cf)요청 파라미터는 statusType이었음)
		private String remark;                    // 비고
		private Long caliFee;                // 교정수수료
		private String statusRemark;            // 취소, 불가, 반려 사유
		
		private LocalDateTime approvalDatetime;    // 기술책임자 결재일시
		private LocalDateTime workDatetime;        // 실무자 결재일시
		
	}
	
	// 접수구분별 삭제요청 DTO (접수상세에서 넘어옴)
	public record ValidateDeleteReq(
			Long caliOrderId,
			// key는 'ACCREDDIT', 'UNACCREDDIT', 'TESTING', 'AGCY' 형태로 넘어온다.
			Map<String, List<Long>> validateInfo
	) {
	}
	
	// 성적서 삭제요청 (id 파라미터)
	public record DeleteReportReq(
			List<Long> deleteIds
	) {
	}
	
	// 개별 성적서 조회 데이터
	public record ReportInfo(
			Long id,
			OrderType orderType,    // 접수구분
			LocalDate caliDate,        // 교정일자
			ReportLang reportLang,    // 성적서발행타입
			PriorityType priorityType, // 긴급여부(NORMAL:일반, EMERGENCY: 긴급)
			CaliType caliType,        // 교정유형
			CaliTakeType caliTakeType,    // 교정상세유형
			String manageNo,        // 관리번호
			
			Long middleItemCodeId,        // 중분류id
			Long smallItemCodeId,        // 소뷴류id
			Long workMemberId,        // 실무자 id
			Long approvalMemberId,        // 기술책임자id
			
			// 품목정보
			Long itemId,            // 품목id
			String itemName,        // 기기명
			String itemNameEn,        // 기기명(영문)
			String itemFormat,       // 기기 형식
			String itemNum,            // 기기번호
			Integer itemCaliCycle,        // 품목교정주기
			String itemMakeAgent,    // 제작회사
			String itemMakeAgentEn,    // 제작회사(영문명)
			String remark,                    // 비고(품목비고)
			Long caliFee,                // 교정수수료
			Long additionalFee,                // 추가금액
			String additionalFeeCause,        // 추가금액사유
			String request,               // 요청사항
			String environmentInfo,            // 환경정보
			String tracestatementInfo,            // 소급성문구
			
			// 접수데이터
			String custAgent,        // 신청업체
			String custAgentAddr,        // 신청업체 주소
			String custManager,        // 신청업체 담당자
			String custManagerTel,        // 신청업체 담당자 연락처
			String reportAgent,            // 성적서발행처
			String reportAgentEn,        // 성적서발행처(영문)
			String reportAgentAddr,        // 성적서발행처 주소
			String reportManager,        // 성적서발행처 담당자
			String reportManagerTel   // 성적서발행처 담당자 연락처
	) {
	}
	
	// 자식성적서 조회 데이터 (성적서 수정 모달에서 데이터 수정 가능
	public record ChildReportInfo(
			Long id,        // 자싱성적서 id
			Long middleItemCodeId,        // 중분류id
			Long smallItemCodeId,        // 소분류id
			Long itemId,            // 품목id
			String itemName,        // 기기명
			String itemNameEn,        // 기기명(영문)
			String itemFormat,       // 기기 형식
			String itemNum,            // 기기번호
			Integer itemCaliCycle,        // 품목교정주기
			String itemMakeAgent,    // 제작회사
			String itemMakeAgentEn,    // 제작회사(영문명)
			String remark,                    // 비고(품목비고)
			Long caliFee,                // 교정수수료
			Long additionalFee,                // 추가금액
			String additionalFeeCause        // 추가금액사유
	) {
	}
	
	// 성적서 수정 모달 응답 데이터
	public record ReportInfoRes(
			ReportInfo reportInfo,
			List<ChildReportInfo> childReportInfos
	) {
	}
	
	// 통합수정 요청 DTO
	// - 각 필드가 null(또는 0)이면 해당 항목을 변경하지 않음 (partial update)
	// - updateMemberInfo=true 일 때만 실무자/기술책임자 변경을 시도하며,
	//   서버에서 각 성적서의 원래 middleItemCodeId 기준으로 유효성 검사 후 반영
	@Getter
	@Setter
	@NoArgsConstructor
	@AllArgsConstructor
	@Schema(description = "성적서 통합수정 요청")
	public static class SelfReportMultiUpdateReq {

		@NotEmpty(message = "대상 성적서를 1건 이상 선택해야 합니다.")
		@Schema(description = "대상 성적서 id 목록")
		private List<Long> reportIds;

		@Schema(description = "완료예정일 (null = 변경 안 함)")
		private LocalDate expectCompleteDate;

		@Schema(description = "교정일자 (null = 변경 안 함)")
		private LocalDate caliDate;

		@Schema(description = "환경정보 JSON 문자열 (null 또는 빈 문자열 = 변경 안 함)")
		private String environmentInfo;

		@Schema(description = "중분류코드 id (null 또는 0 = 변경 안 함)")
		private Long middleItemCodeId;

		@Schema(description = "소분류코드 id (null 또는 0 = 변경 안 함)")
		private Long smallItemCodeId;

		@Schema(description = "실무자/기술책임자 정보 업데이트 여부")
		private boolean updateMemberInfo;

		@Schema(description = "실무자 id (updateMemberInfo=true 일 때만 유효, 각 성적서 원래 중분류코드 기준 유효성 검사)")
		private Long workMemberId;

		@Schema(description = "기술책임자 id (updateMemberInfo=true 일 때만 유효, 각 성적서 원래 중분류코드 기준 유효성 검사)")
		private Long approvalMemberId;

		@Schema(description = "적용할 표준장비 id 목록 (null 또는 빈 목록 = 변경 안 함, 값이 있으면 모든 대상 성적서에 일괄 적용)")
		private List<Long> equipmentIds;
	}

	// 성적서 수정 요청 데이터
	public record ReportUpdateReq(
			Long id,
			OrderType orderType,    // 접수구분
			LocalDate caliDate,        // 교정일자
			ReportLang reportLang,    // 성적서발행타입
			PriorityType priorityType, // 긴급여부(NORMAL:일반, EMERGENCY: 긴급)
			CaliType caliType,        // 교정유형
			CaliTakeType caliTakeType,    // 교정상세유형
			String manageNo,        // 관리번호
			
			Long middleItemCodeId,        // 중분류id
			Long smallItemCodeId,        // 소뷴류id
			Long workMemberId,        // 실무자 id
			Long approvalMemberId,        // 기술책임자id
			
			// 품목정보
			Long itemId,            // 품목id
			String itemName,        // 기기명
			String itemNameEn,        // 기기명(영문)
			String itemFormat,       // 기기 형식
			String itemNum,            // 기기번호
			Integer itemCaliCycle,        // 품목교정주기
			String itemMakeAgent,    // 제작회사
			String itemMakeAgentEn,    // 제작회사(영문명)
			String remark,                    // 비고(품목비고)
			Long caliFee,                // 교정수수료
			Long additionalFee,                // 추가금액
			String additionalFeeCause,        // 추가금액사유
			String request,               // 요청사항
			String environmentInfo,            // 환경정보
			String tracestatementInfo,           // 소급성문구
			
			// 자식성적서
			List<ChildReportInfo> childReportInfos,
			// 사용주인 표준장비
			List<EquipmentDTO.UsedEquipment> equipmentDatas
	) {
	}

	// 성적서 진행상태 변경 요청
	@Getter
	@Setter
	@NoArgsConstructor
	@AllArgsConstructor
	@Schema(description = "성적서 진행상태 변경 요청")
	public static class UpdateStatusReq {

		@NotEmpty(message = "변경할 상태값을 입력해야 합니다.")
		@Schema(description = "변경할 진행상태 (REPAIR | IMPOSSIBLE | NORMAL)", example = "REPAIR")
		private String newStatus;
	}

	/** 성적서대기변경 요청 */
	public record ResetWorkStatusReq(
		@Schema(description = "대기변경 대상 성적서 ID 목록")
		List<Long> reportIds
	) {}

	/** 성적서대기변경 검증 실패 항목 */
	public record InvalidReportItem(
		Long id,
		String reportNum,
		String reason
	) {}

	/** 성적서대기변경 응답 (검증 실패 시 data에 포함) */
	public record ResetWorkStatusRes(
		List<InvalidReportItem> invalid
	) {}

}
