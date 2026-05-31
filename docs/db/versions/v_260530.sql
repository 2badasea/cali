-- v_260530.sql: 성적서출력 기능 추가 — report 테이블 is_print 컬럼 추가
-- 주의: 컬럼을 대문자 ENUM('Y','N')으로 추가한 경우 아래 MODIFY로 소문자로 수정할 것
ALTER TABLE report
    MODIFY COLUMN `is_print` ENUM('y','n') NOT NULL DEFAULT 'n'
        COMMENT '출력여부 (n: 미출력, y: 출력완료)';