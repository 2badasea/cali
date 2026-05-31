package com.bada.cali.common.enums;

/**
 * 성적서 작업 배치(report_job_batch)의 작업 유형
 * - WRITE            : 성적서작성 (샘플 엑셀 기반 원본 파일 생성)
 * - WORK_APPROVAL    : 실무자결재 (실무자 서명 삽입 + PDF 생성)
 * - MANAGER_APPROVAL : 기술책임자결재 (서명 + QR코드 + 발행일자 삽입 + PDF 생성)
 * - PRINT            : 성적서출력 (signed.pdf → 프린터 전송)
 */
public enum JobType {
    WRITE,
    WORK_APPROVAL,
    MANAGER_APPROVAL,
    PRINT
}
