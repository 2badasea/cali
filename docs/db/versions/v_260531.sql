-- =============================================================================
-- v_260531 : 대행성적서(AGCY) 기능 추가
-- 적용 대상: report 테이블
-- =============================================================================

-- 1. 대행의뢰처 컬럼 추가 (AGCY 전용)
ALTER TABLE `report`
    ADD COLUMN `agcy_agent` varchar(200) DEFAULT NULL COMMENT '대행의뢰처 (AGCY 전용)'
        AFTER `report_type`;

-- 2. 자체대행성적서번호 컬럼 추가 (AGCY 전용, 예: BD26-0006-D0001)
ALTER TABLE `report`
    ADD COLUMN `agcy_self_report_num` varchar(100) DEFAULT NULL COMMENT '자체대행성적서번호 (AGCY 전용, 예: BD26-0006-D0001)'
        AFTER `report_num`,
    ADD UNIQUE KEY `agcy_self_report_num` (`agcy_self_report_num`);

-- 3. report_status 주석 업데이트 (varchar 타입 유지, SUCCESS/CANCEL 값 추가 안내)
--    Java ReportStatus enum에 SUCCESS, CANCEL 추가됨
--    MySQL 컬럼 자체는 varchar(50)이므로 DDL 변경 없음
ALTER TABLE `report`
    MODIFY COLUMN `report_status` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT 'NORMAL'
        COMMENT '진행상태 (NORMAL: 기본, REPAIR: 수리, IMPOSSIBLE: 불가, REJECTED: 반려, RESUBMITTED: 재업로드, SUCCESS: 완료(AGCY 전용), CANCEL: 취소(AGCY 전용))';
