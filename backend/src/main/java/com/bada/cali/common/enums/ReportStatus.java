package com.bada.cali.common.enums;

public enum ReportStatus {
	NORMAL,      // 기본
	REPAIR,      // 수리
	IMPOSSIBLE,  // 불가
	REJECTED,    // 반려
	RESUBMITTED, // 재업로드(재상신)
	SUCCESS,     // 완료 (AGCY 전용 — 성적서번호 부여 후 저장 시)
	CANCEL       // 취소 (AGCY 전용)
}
