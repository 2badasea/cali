-- ============================================================
-- v_260510 : menu 테이블 id AUTO_INCREMENT 추가
-- ============================================================
-- JPA 엔티티(Menu.java)는 이미 @GeneratedValue(strategy = IDENTITY)로 선언되어 있으나
-- DB 스키마에는 AUTO_INCREMENT가 누락된 상태였음.
-- 자기참조 FK(fk_menu_parent) 때문에 직접 MODIFY 시 오류 발생(1833).
-- FK를 일시 해제 → MODIFY → FK 재설정 순서로 진행.
-- 기존 데이터의 id 값은 그대로 유지되며, MySQL이 MAX(id)+1 부터 자동 채번함.

-- 1) 자기참조 FK 제거
ALTER TABLE `menu` DROP FOREIGN KEY `fk_menu_parent`;

-- 2) id 컬럼에 AUTO_INCREMENT 추가 (기존 데이터 변경 없음)
ALTER TABLE `menu` MODIFY COLUMN `id` BIGINT NOT NULL AUTO_INCREMENT;

-- 3) FK 재설정
ALTER TABLE `menu`
  ADD CONSTRAINT `fk_menu_parent`
  FOREIGN KEY (`parent_id`) REFERENCES `menu` (`id`);
