-- ============================================================
-- v_260426: report_status 상태값 정리 및 기본값 변경
-- ============================================================
-- 기존 상태값(WAIT, WORK_RETURN, APPROV_RETURN, REUPLOAD, COMPLETE, CANCEL)을
-- 신규 상태값 체계로 통합 정리.
-- 새 상태값: NORMAL(기본), REPAIR(수리), IMPOSSIBLE(불가), REJECTED(반려), RESUBMITTED(재업로드)
-- ============================================================

-- 1) 데이터 마이그레이션 (ALTER 전에 실행)
UPDATE `report` SET `report_status` = 'NORMAL'      WHERE `report_status` IN ('WAIT', 'COMPLETE', 'CANCEL');
UPDATE `report` SET `report_status` = 'REJECTED'    WHERE `report_status` IN ('WORK_RETURN', 'APPROV_RETURN');
UPDATE `report` SET `report_status` = 'RESUBMITTED' WHERE `report_status` = 'REUPLOAD';

-- 2) 컬럼 기본값 및 코멘트 변경
ALTER TABLE `report`
  MODIFY COLUMN `report_status` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT 'NORMAL'
  COMMENT '진행상태 (NORMAL: 기본, REPAIR: 수리, IMPOSSIBLE: 불가, REJECTED: 반려, RESUBMITTED: 재업로드)';
