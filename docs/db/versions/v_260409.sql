-- ============================================================
-- v_260409: ExcelWork 미들웨어 파일 UUID 추가
-- ============================================================
-- report_job_item 에 file_uuid 컬럼 추가.
-- ExcelWork 미들웨어가 item 별로 파일을 on-demand 다운로드할 때
-- UUID 를 식별자로 사용하기 위해 createJob 시 부여.
-- cali-worker 방식 배치(token=NULL)는 file_uuid 도 NULL 로 유지.
-- ============================================================

ALTER TABLE `report_job_item`
  ADD COLUMN `file_uuid` varchar(36) DEFAULT NULL
  COMMENT 'ExcelWork 미들웨어 파일 다운로드 식별 UUID. WRITE 배치 생성 시 item별 부여. cali-worker 방식은 NULL'
  AFTER `report_id`;

ALTER TABLE `report_job_item`
  ADD UNIQUE KEY `uq_file_uuid` (`file_uuid`);