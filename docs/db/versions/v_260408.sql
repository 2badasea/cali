-- v_260408.sql — ExcelWork 미들웨어 연동을 위한 token 컬럼 추가
-- 적용 대상: report_job_batch 테이블

ALTER TABLE `report_job_batch`
    ADD COLUMN `token` varchar(64) DEFAULT NULL
        COMMENT 'ExcelWork 미들웨어 인증 토큰 (UUID hex). cali-worker 방식 배치는 NULL'
        AFTER `status`,
    ADD UNIQUE KEY `uq_token` (`token`);