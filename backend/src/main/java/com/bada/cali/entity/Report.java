package com.bada.cali.entity;

import com.bada.cali.common.enums.*;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

// NOTE DDL 자동생성(ddl-auto) 기능은 꺼져있지만, 문서/메타데이터 역할을 위해 명시
@Entity
@Table(
		name = "report",
		uniqueConstraints = {        // 성적서번호 유니크 제약
				@UniqueConstraint(name = "report_num", columnNames = "report_num"),
				@UniqueConstraint(name = "manage_no", columnNames = "manage_no")
		},
		indexes = {        // where조건으로 자주 사용되는 필드에 대해서 인덱스 생성
				@Index(name = "is_visible", columnList = "is_visible"),
				@Index(name = "cali_order_id", columnList = "cali_order_id")
		}
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Report {
	
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;
	
	// 접수번호 id
	@Column(name = "cali_order_id", nullable = false)
	private Long caliOrderId;
	
	// 긴급여부 (일반 NORMAL 이 기본값)
	@Column(name = "priority_type", nullable = false, length = 20)
	@Enumerated(EnumType.STRING)
	@Builder.Default
	private PriorityType priorityType = PriorityType.NORMAL;
	
	// 성적서번호 (NULL을 허용한다. 자식성적서는 성적서번호가 부모를 따라감)
	@Column(name = "report_num", length = 200)
	private String reportNum;
	
	// 부모성적서 id
	@Column(name = "parent_id")
	private Long parentId;
	
	// 분동성적서 부모id
	@Column(name = "parent_scale_id")
	private Long parentScaleId;
	
	// 견적서 품목 id
	@Column(name = "es_sub_id")
	private Long esSubId;
	
	// 교정일자
	@Column(name = "cali_date")
	private LocalDate caliDate;

	// 완료예정일 (DB: expect_complete_date DATE NULL)
	@Column(name = "expect_complete_date")
	private LocalDate expectCompleteDate;
	
	// 교정주기
	@Column(name = "item_cali_cycle", nullable = false)
	private Integer itemCaliCycle;
	
	// 성적서 상태 (NORMAL: 기본, REPAIR: 수리, IMPOSSIBLE: 불가, REJECTED: 반려, RESUBMITTED: 재업로드)
	@Column(name = "report_status", nullable = false, length = 50)
	@Enumerated(EnumType.STRING)
	@Builder.Default
	private ReportStatus reportStatus = ReportStatus.NORMAL;    // 기본
	
	// 취소, 불가, 반려 사유
	@Column(name = "status_remark")
	private String statusRemark;        // 기본값은 NULL
	
	// 성적서 타입 (자체/ 대행)
	@Column(name = "report_type", nullable = false, length = 20)
	@Enumerated(EnumType.STRING)
	@Builder.Default
	private ReportType reportType = ReportType.SELF;
	
	// 접수타입 (ACCREDDIT: 공인, UNACCREDDIT: 비공식, TESTING: 시험)
	@Column(name = "order_type", nullable = false, length = 10)
	@Enumerated(EnumType.STRING)
	@Builder.Default
	private OrderType orderType = OrderType.ACCREDDIT;    // 기본적으로 공인 부여
	
	// 문서타입 기본적으로 ISO
	@Column(name = "doc_type", nullable = false, length = 10)
	@Builder.Default
	@Enumerated(EnumType.STRING)
	private DocType docType = DocType.ISO;
	
	// 발행타입 (기본값은 국문)
	@Column(name = "report_lang", nullable = false, length = 10)
	@Enumerated(EnumType.STRING)
	@Builder.Default
	private ReportLang reportLang = ReportLang.KR;         // KR / EN / BOTH
	
	@Column(name = "cali_type", nullable = false, length = 50)
	@Enumerated(EnumType.STRING)
	@Builder.Default
	private CaliType caliType = CaliType.STANDARD;    // 기본값으로 고정표준실
	
	@Column(name = "cali_take_type", nullable = false, length = 50)
	@Enumerated(EnumType.STRING)
	@Builder.Default
	private CaliTakeType caliTakeType = CaliTakeType.SELF;    // 방문(SELF)가 기본값
	
	// 품목 고유 id (null 가능)
	@Column(name = "item_id")
	private Long itemId;
	
	// 품목명
	@Column(name = "item_name", nullable = false, length = 200)
	private String itemName;
	
	// 품목명 (영문)
	@Column(name = "item_name_en", length = 200)
	private String itemNameEn;
	
	// 품목 형식
	@Column(name = "item_format", length = 200)
	private String itemFormat;
	
	// 기기번호
	@Column(name = "item_num", length = 200)
	private String itemNum;
	
	// 제작회사
	@Column(name = "item_make_agent", length = 200)
	private String itemMakeAgent;
	
	// 제작회사 (영문)
	@Column(name = "item_make_agent_en", length = 200)
	private String itemMakeAgentEn;
	
	// 교정수수료
	@Column(name = "cali_fee", nullable = false)
	@Builder.Default
	private Long caliFee = 0L;
	
	// 추가금액
	@Column(name = "additional_fee", nullable = false)
	@Builder.Default
	private Long additionalFee = 0L;
	
	// 추가금액사유
	@Column(name = "additional_fee_cause", length = 250)
	private String additionalFeeCause;
	
	
	// 관리번호
	@Column(name = "manage_no", length = 50)
	private String manageNo;
	
	// 중분류 코드 고유 id
	@Column(name = "middle_item_code_id")
	private Long middleItemCodeId;
	
	// 소분류코드 고유 id
	@Column(name = "small_item_code_id")
	private Long smallItemCodeId;
	
	// 환경정보 (json형태로 DB에 값을 보관한다)
	@Column(name = "environment_info", columnDefinition = "json")
	private String environmentInfo;
	
	// 소급성문구 (json 형태로 관리)
	@Column(name = "tracestatement_info", columnDefinition = "json")
	private String tracestatementInfo;
	
	// 비고/품목비고
	@Column(name = "remark", length = 250)
	private String remark;
	
	// 요청사항
	@Column(name = "request", columnDefinition = "LONGTEXT")
	@Lob
	private String request;
	
	// 작성자 고유 id
	@Column(name = "write_member_id")
	private Long writeMemberId;

	// 작성일시
	@Column(name = "write_datetime")
	private LocalDateTime writeDatetime;

	// 성적서작성 진행상태 (IDLE: 미작성, PROGRESS: 작성중, SUCCESS: 작성완료, FAIL: 작성실패)
	// work_status / approval_status 와 동일한 패턴으로 관리
	@Column(name = "write_status", nullable = false, length = 50)
	@Enumerated(EnumType.STRING)
	@Builder.Default
	private AppStatus writeStatus = AppStatus.IDLE;

	// 실무자 id
	@Column(name = "work_member_id")
	private Long workMemberId;
	
	// 실무자 결재일시
	@Column(name = "work_datetime")
	private LocalDateTime workDatetime;
	
	// 실무자 결재 상태
	@Column(name = "work_status", nullable = false)
	@Enumerated(EnumType.STRING)
	@Builder.Default
	private AppStatus workStatus = AppStatus.IDLE;    // 기본 대기 상태
	
	// 기술책임자 id
	@Column(name = "approval_member_id")
	private Long approvalMemberId;
	
	// 기술책임자 결재일시
	@Column(name = "approval_datetime")
	private LocalDateTime approvalDatetime;
	
	// 기술책임자 결재 상태
	@Column(name = "approval_status", nullable = false)
	@Enumerated(EnumType.STRING)
	@Builder.Default
	private AppStatus approvalStatus = AppStatus.IDLE;    // 기본 대기 상태
	
	
	// 삭제유무
	@Column(name = "is_visible", nullable = false)
	@Enumerated(EnumType.STRING)
	@Builder.Default
	private YnType isVisible = YnType.y;
	
	@Column(name = "create_member_id", nullable = false)
	private Long createMemberId;       // 생성자 id
	
	@Column(name = "update_member_id")
	private Long updateMemberId;       // 수정자 id (nullable)
	
	@Column(name = "delete_member_id")
	private Long deleteMemberId;       // 삭제자 id (nullable)
	
	@Column(name = "create_datetime", nullable = false)
	private LocalDateTime createDatetime;  // 생성일시
	
	// insertable = false, updatable = false 명시를 통해 읽기 전용으로 선언. insert/update 시 hibernate가 이 컬럼을 절대로 건드리지 않음
	@Column(name = "update_datetime", insertable = false, updatable = false)
	private LocalDateTime updateDatetime;  // 수정일시 (nullable)
	
	@Column(name = "delete_datetime")
	private LocalDateTime deleteDatetime;  // 삭제일시 (nullable)
	
	// 교정접수 테이블과의 연관관계 정의
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "cali_order_id", insertable = false, updatable = false)
	private CaliOrder caliOrder;
	
}
