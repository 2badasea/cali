-- =============================================================================
-- CALI DB 전체 스키마 (Single Source of Truth)
-- 최신 버전: v_260328
-- 업데이트 규칙:
--   - 테이블 추가/변경 시 이 파일을 직접 최신 상태로 유지한다.
--   - 변경 내역은 docs/db/versions/ 에 델타(변경분만) 파일로 별도 기록한다.
--   - 새 환경 셋업 시 이 파일 하나만 실행하면 된다.
-- =============================================================================

CREATE TABLE IF NOT EXISTS `agent` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '업체 고유번호',
  `create_type` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT 'basic' COMMENT 'basic : 기본데이터(직접등록) , auto: 자동등록(접수), join(회원가입)',
  `group_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '업체그룹명',
  `name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '업체명',
  `name_en` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '업체명 영문',
  `agent_flag` int NOT NULL DEFAULT (0) COMMENT '업체형태(1:고객사 , 2:대리점 , 4:성적서업체 , 8:교정기관 , 16 : 판매기관)',
  `ceo` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '대표자',
  `agent_num` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '사업자등록번호',
  `business_type` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '업태(업태코드 따로 생성)',
  `business_kind` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '종목(종목코드 따로 생성)',
  `agent_type` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '형태(형태코드 따로 생성)',
  `agent_zip_code` char(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '우편번호',
  `addr` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '성적서 발행주소',
  `addr_en` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '성적서 발행주소 영문',
  `agent_tel` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '전화번호',
  `phone` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '휴대전화번호',
  `email` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '메일',
  `manager` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '업체 담당자명',
  `manager_email` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '업체 담당자 이메일',
  `manager_tel` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '업체 담당자 연락처',
  `fax` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '팩스',
  `account_number` varchar(250) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '계좌정보(ex. 은행명, 예금주, 계좌번호 등등)',
  `remark` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT '업체 특이사항',
  `calibration_cycle` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT 'SELF_CYCLE' COMMENT '''self_cycle'',''next_cycle'',''none''',
  `self_discount` decimal(4,1) DEFAULT NULL COMMENT '자체 할인율',
  `out_discount` decimal(4,1) DEFAULT NULL COMMENT '대행 할인율',
  `is_visible` enum('y','n') CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT 'y' COMMENT 'y:사용, n:미사용',
  `is_close` enum('y','n') CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT 'n' COMMENT '폐업 구분',
  `create_datetime` datetime NOT NULL DEFAULT (now()) COMMENT '등록일시',
  `create_member_id` bigint NOT NULL COMMENT '등록자',
  `update_datetime` datetime DEFAULT (now()) ON UPDATE CURRENT_TIMESTAMP COMMENT '수정일시',
  `update_member_id` bigint DEFAULT NULL COMMENT '수정자',
  `delete_datetime` datetime DEFAULT NULL COMMENT '삭제일시',
  `delete_member_id` bigint DEFAULT NULL COMMENT '삭제자',
  PRIMARY KEY (`id`) USING BTREE,
  KEY `create_member_id` (`create_member_id`) USING BTREE,
  KEY `update_member_id` (`update_member_id`) USING BTREE,
  KEY `delete_member_id` (`delete_member_id`) USING BTREE,
  KEY `is_visible` (`is_visible`)
) ENGINE=InnoDB AUTO_INCREMENT=15 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='업체(거래처)관리';

CREATE TABLE IF NOT EXISTS `agent_manager` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '담당자 고유번호 (기본키)',
  `main_yn` enum('y','n') CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT 'n' COMMENT '대표 담당자 여부',
  `agent_id` bigint DEFAULT NULL COMMENT 'agent 고유번호 (내부 직원은 무조건 NULL. 즉, 값이 없음)',
  `name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '담당자명',
  `email` varchar(125) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '담당자 메일주소',
  `tel` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '담당자 전화번호',
  `is_visible` enum('y','n') CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT 'y' COMMENT '삭제 유무 - y: 노출됨(삭제되지 않음),  n: 삭제처리(노출되지 않음)',
  `create_datetime` datetime NOT NULL DEFAULT (now()) COMMENT '등록일시',
  `create_member_id` bigint NOT NULL COMMENT '등록자',
  `update_datetime` datetime DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '수정일시',
  `update_member_id` bigint DEFAULT NULL COMMENT '수정자',
  `delete_datetime` datetime DEFAULT NULL COMMENT '삭제일시',
  `delete_member_id` bigint DEFAULT NULL COMMENT '삭제자',
  PRIMARY KEY (`id`),
  KEY `is_visible` (`is_visible`),
  KEY `agent_id` (`agent_id`)
) ENGINE=InnoDB AUTO_INCREMENT=21 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='업체 담당자 관리 테이블';

CREATE TABLE IF NOT EXISTS `cali_order` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '교정접수 고유 id',
  `completion_id` bigint DEFAULT NULL COMMENT '완료통보서 id.    1개의 완료통보서는 n개의 접수로 구성될 수 있음.',
  `estimate_id` bigint DEFAULT NULL COMMENT '견적서 id   1개의 견적서를 기반으로 여러 접수 건 생성가능',
  `btrip_id` bigint DEFAULT NULL COMMENT '출장일정id - 접수와 출장일정은 1:1의 관계(출장일정에서 접수 생성 시 값을 가진다)',
  `order_type` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT 'ACCREDDIT' COMMENT 'ACCREDDIT 공인, UNACCREDDIT비공인, TESTING: 시험',
  `cust_agent_cali_cycle` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT 'NEXT_CYCLE' COMMENT '신청업체 교정주기 SELF_CYCLE: 자체교정주기, NEXT_CYCLE: 차기교정주기',
  `priority_type` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT 'NORMAL' COMMENT '긴급여부 NORMALl: 일반, EMERGENCY: 긴급',
  `cali_type` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT 'STANDARD' COMMENT '교정유형: STANDARD(고정표준실), SITE(현장교정)',
  `cali_take_type` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT 'SELF' COMMENT '교정 상세유형 SEND: 택배, SELF:  방문, SITE: 현장반입, PICKUP: 픽업, ETC: 기타, SITE_SELF: 현장교정, SITE_AGCY: 현장대행',
  `report_lang` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT 'KR' COMMENT '발행타입 KR: 국문, EN: 영문, BOTH: 둘 다   - 최초 성적서 생성 시, 접수타입 따라감',
  `doc_type` enum('ISO','B') NOT NULL DEFAULT 'ISO' COMMENT '문서타입 ISO: 공개, B: 비공개  ',
  `btrip_start_date` datetime DEFAULT NULL COMMENT '출장일정 시작일시',
  `btrip_end_date` datetime DEFAULT NULL COMMENT '출장일정 종료일시',
  `responsible_manager_id` bigint NOT NULL COMMENT '책임담당자 id',
  `order_manager_id` bigint NOT NULL COMMENT '접수자 id',
  `order_date` date NOT NULL COMMENT '접수일자',
  `complete_date` date DEFAULT NULL COMMENT '완료일자',
  `order_num` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '접수번호',
  `order_group_num` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '접수그룹번호',
  `cust_agent` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '신청업체명',
  `cust_agent_tel` varchar(100) DEFAULT NULL COMMENT '신청업체 연락처',
  `cust_agent_fax` varchar(100) DEFAULT NULL COMMENT '신청업체 FAX',
  `cust_agent_en` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '신청업체 영문명',
  `cust_agent_id` bigint NOT NULL COMMENT '신청업체 id',
  `cust_agent_addr` varchar(250) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '신청업체 주소',
  `cust_agent_addr_en` varchar(250) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '신청업체 주소(영문명)',
  `cust_manager` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '신청업체 담당자',
  `cust_manager_tel` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '신청업체 담당자 연락처',
  `cust_manager_email` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '신청업체 담당자 이메일',
  `report_agent` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '성적서발행처',
  `report_agent_en` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '성적서발행처(영문명)',
  `report_agent_id` bigint NOT NULL COMMENT '성적서발행처 id',
  `report_agent_addr` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '성적서발행처 주소',
  `report_agent_addr_en` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '성적서발행처 주소 (영문)',
  `report_manager` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '성적서발행처 담당자',
  `report_manager_tel` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '성적서발행처 담당자 연락처',
  `report_manager_email` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '성적서발행처 담당자 이메일',
  `site_addr` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '소재지 주소 (실제 교정이 진행되는 곳)',
  `site_addr_en` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '소재지 주소 (영문)',
  `remark` text COMMENT '비고',
  `status_type` enum('wait','complete','cancel','hold') CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT 'wait' COMMENT '접수상태 wait: 기본, complete(완료), cancel(취소), hold(보류)',
  `is_tax` enum('y','n') NOT NULL DEFAULT 'n' COMMENT '세금계산서 발행여부 ',
  `is_order_send` enum('y','n') NOT NULL DEFAULT 'n' COMMENT '메일발송여부 - 교정신청서 발송여부',
  `is_visible` enum('y','n') NOT NULL DEFAULT 'y' COMMENT '삭제여부 y: 미삭제, n:삭제  - 삭제 처리 시, 접수번호에 deleted 문구 추가',
  `create_datetime` datetime NOT NULL DEFAULT (now()) COMMENT '생성일시',
  `create_member_id` bigint NOT NULL COMMENT '생성자 id',
  `update_datetime` datetime DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '수정일시',
  `update_member_id` bigint DEFAULT NULL COMMENT '수정자 id',
  `delete_datetime` datetime DEFAULT NULL COMMENT '삭제일시',
  `delete_member_id` bigint DEFAULT NULL COMMENT '삭제자 id',
  PRIMARY KEY (`id`),
  UNIQUE KEY `order_num` (`order_num`),
  KEY `is_visible` (`is_visible`)
) ENGINE=InnoDB AUTO_INCREMENT=8 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='교정접수 테이블';

CREATE TABLE IF NOT EXISTS `department` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '고유 id',
  `name` varchar(100) NOT NULL COMMENT '부서명',
  `seq` tinyint NOT NULL DEFAULT (0) COMMENT '순서',
  `is_visible` enum('y','n') NOT NULL DEFAULT 'y' COMMENT '사용유무 y:사용,  n: 미사용',
  `create_member_id` bigint NOT NULL COMMENT '생성자',
  `create_datetime` datetime NOT NULL DEFAULT (now()) COMMENT '생성일시',
  `update_member_id` bigint DEFAULT NULL COMMENT '수정자',
  `update_datetime` datetime DEFAULT NULL COMMENT '수정일시',
  `delete_member_id` bigint DEFAULT NULL COMMENT '삭제자',
  `delete_datetime` datetime DEFAULT NULL COMMENT '삭제일시',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=8 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='부서관리';

CREATE TABLE IF NOT EXISTS `equipment_field` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '분야 고유 id',
  `name` varchar(100) NOT NULL COMMENT '분야명 (전기/힘/경도/길이/질량/토크/전도도 등)',
  `code` varchar(2) NOT NULL COMMENT '분야 코드(대표 이니셜 1~2자리)',
  `is_use` enum('y','n') CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT 'y' COMMENT '사용여부 (y인 항목만 선택 가능)',
  `remark` text COMMENT '비고',
  `is_visible` enum('y','n') CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT 'y' COMMENT '삭제유무. n: 삭제',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=20 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='표준장비 분야 코드';

CREATE TABLE IF NOT EXISTS `file_info` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '고유 id',
  `ref_table_name` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '참조 테이블명',
  `ref_table_id` bigint NOT NULL COMMENT '참조 테이블 id',
  `origin_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '원본파일명 (구분자, 확장자 포함',
  `name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '파일명 (확장자 제외)',
  `extension` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '확장자 pdf, xlsx, png 등등',
  `file_size` bigint NOT NULL COMMENT '파일 크기',
  `content_type` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT 'ex) ''application/pdf'' 등',
  `type` varchar(50) DEFAULT NULL COMMENT '파일 역할 구분 (origin: 원본성적서 / signed: 서명삽입본. 성적서 관련에만 사용, 그 외 NULL)',
  `dir` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '스토리지 하위 디렉토리명',
  `is_visible` enum('y','n') NOT NULL DEFAULT 'y' COMMENT '삭제유무 n: 삭제됨',
  `create_datetime` datetime NOT NULL DEFAULT (now()) COMMENT '생성일시',
  `create_member_id` bigint NOT NULL COMMENT '생성자 id',
  `update_datetime` datetime DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '수정일시',
  `update_member_id` bigint DEFAULT NULL COMMENT '수정자',
  `delete_datetime` datetime DEFAULT NULL COMMENT '삭제일시',
  `delete_member_id` bigint DEFAULT NULL COMMENT '삭제자',
  PRIMARY KEY (`id`),
  KEY `is_visible` (`is_visible`),
  KEY `ref_table_id` (`ref_table_id`)
) ENGINE=InnoDB AUTO_INCREMENT=44 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='첨부파일 관리 테이블';

CREATE TABLE IF NOT EXISTS `item` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '품목 고유 id',
  `create_type` varchar(50) NOT NULL DEFAULT 'BASIC' COMMENT 'BASIC: 직접등록, AUTO: 자동등록',
  `middle_item_code_id` bigint DEFAULT NULL COMMENT '중분류코드',
  `small_item_code_id` bigint DEFAULT NULL COMMENT '소분류코드',
  `name` varchar(200) NOT NULL COMMENT '품목명',
  `name_en` varchar(200) DEFAULT NULL COMMENT '품목명(영문)',
  `make_agent` varchar(200) DEFAULT NULL COMMENT '제작회사',
  `make_agent_en` varchar(200) DEFAULT NULL COMMENT '제작회사(영문)',
  `format` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '형식',
  `num` varchar(200) DEFAULT NULL COMMENT '기기번호',
  `fee` bigint NOT NULL DEFAULT '0' COMMENT '수수료',
  `cali_cycle` int DEFAULT NULL COMMENT '교정주기',
  `is_inhouse_possible` enum('y','n') NOT NULL DEFAULT 'y' COMMENT '당사가능여부 y: 가능, n: 불가',
  `is_visible` enum('y','n') NOT NULL DEFAULT 'y' COMMENT '삭제여부.  n: 삭제',
  `create_datetime` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '등록일시',
  `create_member_id` bigint NOT NULL COMMENT '등록자',
  `update_datetime` datetime DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '수정일시',
  `update_member_id` bigint DEFAULT NULL COMMENT '수정자',
  `delete_datetime` datetime DEFAULT NULL COMMENT '삭제일시',
  `delete_member_id` bigint DEFAULT NULL COMMENT '삭제자',
  PRIMARY KEY (`id`),
  KEY `is_visible` (`is_visible`)
) ENGINE=InnoDB AUTO_INCREMENT=2212 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='품목관리';

CREATE TABLE IF NOT EXISTS `item_code` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '고유 id',
  `parent_id` bigint DEFAULT NULL COMMENT '상위 분류코드 id',
  `code_level` varchar(30) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '대분류: LARGE,  중분류: MIDDLE, 소분류: SMALL)',
  `code_num` varchar(50) NOT NULL COMMENT '코드번호',
  `code_name` varchar(200) NOT NULL COMMENT '분류명',
  `code_name_en` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '분류명 (영문)',
  `cali_cycle_unit` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT 'MONTHS' COMMENT '교정주기 단위 MONTHS: 개월, HOURS: 시간, AS_NEEDED: 수시, UNSPECIFIED: 없음',
  `std_cali` int DEFAULT NULL COMMENT '고정용 표준기 교정주기',
  `pre_cali` int DEFAULT NULL COMMENT '정밀기기 교정주기',
  `tracestatement_info` json DEFAULT NULL COMMENT '소급성 문구 JSON으로 관리',
  `is_kolas_standard` enum('y','n') NOT NULL DEFAULT 'y' COMMENT '공식 KOLAS 기준 여부 ',
  `is_visible` enum('y','n') NOT NULL DEFAULT 'y' COMMENT '삭제 유무',
  `create_datetime` datetime NOT NULL DEFAULT (now()) COMMENT '생성일시',
  `create_member_id` bigint NOT NULL COMMENT '생성자',
  `update_datetime` datetime DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '수정일시',
  `update_member_id` bigint DEFAULT NULL COMMENT '수정자',
  `delete_datetime` datetime DEFAULT NULL COMMENT '삭제일시',
  `delete_member_id` bigint DEFAULT NULL COMMENT '삭제자',
  PRIMARY KEY (`id`),
  KEY `parent_id` (`parent_id`),
  KEY `is_visible` (`is_visible`)
) ENGINE=InnoDB AUTO_INCREMENT=666 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='분류코드관리';

CREATE TABLE IF NOT EXISTS `item_fee_history` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '고유 id',
  `item_id` bigint NOT NULL COMMENT '참조 품목 id',
  `base_date` date NOT NULL COMMENT '기준일자',
  `base_fee` bigint NOT NULL DEFAULT (0) COMMENT '기준 교정수수료',
  `remark` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT '비고',
  `is_visible` enum('y','n') CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT 'y' COMMENT '삭제여부.  n: 삭제',
  `create_datetime` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '등록일시',
  `create_member_id` bigint NOT NULL COMMENT '등록자',
  `update_datetime` datetime DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '수정일시',
  `update_member_id` bigint DEFAULT NULL COMMENT '수정자',
  `delete_datetime` datetime DEFAULT NULL COMMENT '삭제일시',
  `delete_member_id` bigint DEFAULT NULL COMMENT '삭제자',
  PRIMARY KEY (`id`),
  KEY `is_visible` (`is_visible`)
) ENGINE=InnoDB AUTO_INCREMENT=10 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='품목 수수료 이력';

CREATE TABLE IF NOT EXISTS `log` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'LOG 고유번호',
  `log_ip` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT 'LOG ip',
  `worker_name` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '작업자 명',
  `log_content` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT 'LOG 이력내용',
  `log_type` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '로그타입 (i:등록 , u:수정 , d:삭제, l : 로그인)',
  `ref_table` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '참조 테이블명',
  `ref_table_id` bigint DEFAULT NULL COMMENT '참조 테이블 id',
  `create_datetime` datetime DEFAULT (now()) COMMENT '등록일시',
  `create_member_id` bigint NOT NULL COMMENT '등록자',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=2282 DEFAULT CHARSET=utf8mb3 COMMENT='LOG 이력관리';

CREATE TABLE IF NOT EXISTS `member` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'member 고유번호',
  `login_id` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '로그인 아이디',
  `pwd` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '비밀번호',
  `agent_id` bigint unsigned NOT NULL DEFAULT (0) COMMENT 'agent 고유번호',
  `name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '직원명',
  `name_eng` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT '' COMMENT '직원명(영어)',
  `birth` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '직원 생년월일',
  `company_no` varchar(30) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '사번',
  `hp` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '직원 휴대번호',
  `email` varchar(125) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '직원 메일주소',
  `tel` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '직원 전화번호',
  `addr1` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '직원 주소1',
  `addr2` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '직원 주소2',
  `remark` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT '비고사항',
  `task_id` bigint DEFAULT NULL COMMENT '직무 고유번호',
  `department_id` bigint DEFAULT NULL COMMENT '부서 고유번호',
  `level_id` bigint DEFAULT NULL COMMENT '직급 고유번호',
  `join_date` date DEFAULT NULL COMMENT '직원 입사일자',
  `leave_date` date DEFAULT NULL COMMENT '직원 퇴사일자',
  `work_type` tinyint DEFAULT '0' COMMENT '재직구분 (0:재직 , 1:휴직 , 2:퇴사)',
  `salary_type` tinyint DEFAULT '0' COMMENT '급여구분 (0:시급 , 1:월급 , 3:연봉)',
  `calbr_manager_type` tinyint DEFAULT '0' COMMENT '실무자 : 1, 기술책임자 : 2, 모두 : 3 (비트연산)',
  `img` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '직원사진',
  `doc_permisson` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT '{"quality":0,"cali_rule":0,"etc":0}' COMMENT '문서 권한',
  `auth` enum('admin','user') CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT 'user' COMMENT '관리자인지 , 유저인지',
  `login_count` int unsigned DEFAULT '0' COMMENT '로그인 횟수',
  `mobile_access` enum('y','n') CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT 'y' COMMENT '모바일 접속제한여부 (n:제한 ,y:허용)',
  `ip_access` enum('y','n') CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT 'y' COMMENT 'ip 접속제한여부 (n:제한 ,y:허용)',
  `day_off_total` decimal(5,1) unsigned NOT NULL DEFAULT '0.0' COMMENT '직원 휴가수',
  `is_visible` enum('y','n') CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT 'y' COMMENT 'Y:표시, N:미표시',
  `is_active` enum('y','n') CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT 'n' COMMENT 'y : 로그인 가능, n : 로그인 불가능 (비밀번호 5회이상 오류시)',
  `dorae_alarm` enum('y','n') CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT 'y' COMMENT '교정관련 도래알림 권한',
  `equipage_dorae_alarm` enum('y','n') CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT 'y' COMMENT '표준장비관련 도래알림 권한',
  `last_pwd_updated` datetime DEFAULT NULL COMMENT '마지막 비밀번호 변경일자',
  `last_login_datetime` datetime DEFAULT NULL COMMENT '마지막 로그인 일시',
  `last_login_fail_datetime` datetime DEFAULT NULL COMMENT '마지막 로그인 실패 일시',
  `create_datetime` datetime NOT NULL DEFAULT (now()) COMMENT '등록일시',
  `create_member_id` bigint NOT NULL COMMENT '등록자',
  `update_datetime` datetime DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '수정일시',
  `update_member_id` bigint DEFAULT NULL COMMENT '수정자',
  `delete_datetime` datetime DEFAULT NULL COMMENT '삭제일시',
  `delete_member_id` bigint DEFAULT NULL COMMENT '삭제자',
  PRIMARY KEY (`id`) USING BTREE,
  KEY `agent_idx` (`agent_id`) USING BTREE,
  KEY `task_idx` (`task_id`) USING BTREE,
  KEY `department_idx` (`department_id`) USING BTREE,
  KEY `level_idx` (`level_id`) USING BTREE,
  KEY `is_visible` (`is_visible`)
) ENGINE=InnoDB AUTO_INCREMENT=10 DEFAULT CHARSET=utf8mb3 COMMENT='사용자(직원) 관리';

CREATE TABLE IF NOT EXISTS `member_code_auth` (
  `member_id` bigint NOT NULL COMMENT '직원 고유 id',
  `middle_item_code_id` bigint NOT NULL COMMENT '분류코드(중분류) id',
  `auth_bitmask` int NOT NULL DEFAULT '0' COMMENT '권한 비트마스크(1=실무자,2=기술책임자(부),4=기술책임자(정))',
  PRIMARY KEY (`member_id`,`middle_item_code_id`),
  KEY `idx_middle_item_code_id` (`middle_item_code_id`) USING BTREE,
  CONSTRAINT `fk_mca_item_code` FOREIGN KEY (`middle_item_code_id`) REFERENCES `item_code` (`id`) ON DELETE RESTRICT ON UPDATE CASCADE,
  CONSTRAINT `fk_mca_member` FOREIGN KEY (`member_id`) REFERENCES `member` (`id`) ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='직원별 중분류 권한';

CREATE TABLE IF NOT EXISTS `member_level` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '고유 id',
  `name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '직급명',
  `seq` tinyint NOT NULL DEFAULT '0' COMMENT '순서',
  `is_visible` enum('y','n') CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT 'y' COMMENT '사용유무 y:사용,  n: 미사용',
  `create_member_id` bigint NOT NULL COMMENT '생성자',
  `create_datetime` datetime NOT NULL DEFAULT (now()) COMMENT '생성일시',
  `update_member_id` bigint DEFAULT NULL COMMENT '수정자',
  `update_datetime` datetime DEFAULT NULL COMMENT '수정일시',
  `delete_member_id` bigint DEFAULT NULL COMMENT '삭제자',
  `delete_datetime` datetime DEFAULT NULL COMMENT '삭제일시',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=10 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='직급관리';

CREATE TABLE IF NOT EXISTS `member_permission_read` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `member_id` bigint NOT NULL,
  `menu_id` bigint NOT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=30 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE IF NOT EXISTS `menu` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `menu_alias` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  `menu_code` varchar(50) NOT NULL,
  `url` varchar(255) DEFAULT NULL,
  `icon_class` varchar(50) DEFAULT NULL,
  `target` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT '_self' COMMENT '_self: 현재페이지에서이동, _blank: 새탭에서 열기',
  `depth` int NOT NULL DEFAULT '1',
  `sort_order` int NOT NULL DEFAULT '1',
  `is_visible` enum('y','n') CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT 'y',
  `parent_id` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `menu_code` (`menu_code`),
  KEY `fk_menu_parent` (`parent_id`),
  CONSTRAINT `fk_menu_parent` FOREIGN KEY (`parent_id`) REFERENCES `menu` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `persistent_logins` (
  `username` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '자동 로그인하려는 계정',
  `series` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '고유ID (쿠키랑 매칭)',
  `token` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '실제 인증 토큰',
  `last_used` timestamp NOT NULL COMMENT '마지막 사용 시각',
  PRIMARY KEY (`series`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='자동로그인 정보 저장';

CREATE TABLE IF NOT EXISTS `report` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '성적서 고유id (기본키)',
  `cali_order_id` bigint NOT NULL COMMENT '참조 접수 id',
  `report_num` varchar(250) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '성적서번호 (자식성적서는 성적서번호를 가지지 않는다 - NULL 허용)',
  `agcy_self_report_num` varchar(100) DEFAULT NULL COMMENT '자체대행성적서번호 (AGCY 전용, 예: BD26-0006-D0001)',
  `parent_id` bigint DEFAULT NULL COMMENT '부모 성적서id (재발행 또는 수정발행 시 생성)',
  `parent_scale_id` bigint DEFAULT NULL COMMENT '분동유형 성적서의 경우, 부모 성적서 id',
  `priority_type` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT 'NORMAL' COMMENT '긴급여부 NORMALl: 일반, EMERGENCY: 긴급',
  `es_sub_id` bigint DEFAULT NULL COMMENT '견적서 품목id  (견적서->접수 시, 생성)',
  `report_type` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT 'SELF' COMMENT '성적서 타입 (SELF: 자체, AGCY: 대행)',
  `agcy_agent` varchar(200) DEFAULT NULL COMMENT '대행의뢰처 (AGCY 전용)',
  `order_type` varchar(50) NOT NULL DEFAULT 'ACCREDIT' COMMENT 'ACCREDDIT: 공인, UNACCREDDIT: 비공식, TESTING: 시험',
  `report_lang` varchar(50) NOT NULL DEFAULT 'KR' COMMENT 'KR: 국문,  EN: 영문, BOTH: 국문+영문',
  `doc_type` varchar(50) NOT NULL DEFAULT 'ISO' COMMENT '문서타입. 일반적으로 공인을 제외한 것은 모두 B',
  `cali_type` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT 'STANDARD' COMMENT '교정유형: STANDARD(고정표준실), SITE(현장교정)',
  `cali_take_type` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT 'SELF' COMMENT '교정 상세유형 SEND: 택배, SELF:  방문, SITE: 현장반입, PICKUP: 픽업, ETC: 기타, SITE_SELF: 현장교정, SITE_AGCY: 현장대행',
  `cali_date` date DEFAULT NULL COMMENT '교정일자 (등록 시 NULL허용)',
  `expect_complete_date` date DEFAULT NULL COMMENT '완료예정일',
  `item_id` bigint DEFAULT NULL COMMENT '품목 고유 id.  (직접 입력해서 등록하는 경우가 존재하므로 NULL 허용)',
  `item_name` varchar(200) NOT NULL COMMENT '품목명 (필수값)',
  `item_name_en` varchar(200) DEFAULT NULL COMMENT '품목명 (영문)',
  `item_format` varchar(200) DEFAULT NULL COMMENT '품목형식  (생략된 경우 고려하여, NULL 허용 기본값NULL)',
  `item_num` varchar(200) DEFAULT NULL COMMENT '기기번호 (임의로 기기번호 생략 후 등록하는 경우가 있으므로 NULL허용)',
  `item_make_agent` varchar(200) DEFAULT NULL COMMENT '제작회사',
  `item_make_agent_en` varchar(200) DEFAULT NULL COMMENT '제작회사(영문)',
  `item_cali_cycle` int NOT NULL COMMENT '교정주기',
  `cali_fee` bigint NOT NULL DEFAULT '0' COMMENT '교정수수료',
  `additional_fee` bigint NOT NULL DEFAULT '0' COMMENT '추가금액',
  `additional_fee_cause` varchar(250) DEFAULT NULL COMMENT '추가금액사유',
  `manage_no` varchar(250) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '관리번호 (성적서번호와 마찬가지로 자식은 NULL을 허용한다)',
  `middle_item_code_id` bigint DEFAULT NULL COMMENT '중분류코드 id ',
  `small_item_code_id` bigint DEFAULT NULL COMMENT '소분류코드 id',
  `environment_info` json DEFAULT NULL COMMENT '환경정보json (최고온도: tempMax, 최저온도: tempMin, 최고습도: humMax,  최저습도: humMin, 최고기압: preMax, 최저기압: preMin',
  `tracestatement_info` json DEFAULT NULL COMMENT '소급성문구: (traceStatement, traceStatement2, traceStatement3, traceStatementEn, traceStatementEn2, traceStatementEn3)',
  `remark` varchar(250) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '품목 비고',
  `request` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT '요청사항',
  `report_status` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT 'NORMAL' COMMENT '진행상태 (NORMAL: 기본, REPAIR: 수리, IMPOSSIBLE: 불가, REJECTED: 반려, RESUBMITTED: 재업로드, SUCCESS: 완료(AGCY 전용), CANCEL: 취소(AGCY 전용))',
  `status_remark` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT '진행상태의 ''취소'', ''불가'', ''반려'' 사유',
  `write_member_id` bigint DEFAULT NULL COMMENT '성적서 작성자 id',
  `write_datetime` datetime DEFAULT NULL COMMENT '성적서 작성일시',
  `work_member_id` bigint DEFAULT NULL COMMENT '실무자 id',
  `work_datetime` datetime DEFAULT NULL COMMENT '실무자 결재일시',
  `write_status` varchar(50) NOT NULL DEFAULT 'IDLE' COMMENT '성적서작성 진행상태 (IDLE: 미작성, PROGRESS: 작성중, SUCCESS: 작성완료, FAIL: 작성실패)',
  `work_status` varchar(50) NOT NULL DEFAULT 'IDLE' COMMENT '결재진행상태 (IDLE: 유휴, READY: 대기, PROGRESS: 진행중, FAIL: 실패, SUCCESS: 성공)',
  `approval_member_id` bigint DEFAULT NULL COMMENT '기술책임자 id',
  `approval_datetime` datetime DEFAULT NULL COMMENT '기술책임자 결재일시',
  `approval_status` varchar(50) NOT NULL DEFAULT 'IDLE' COMMENT '기술책임자 결재진행상태 (IDLE: 유휴, READY: 대기, PROGRESS: 진행중, FAIL: 실패, SUCCESS: 성공)',
  `is_print` enum('y','n') NOT NULL DEFAULT 'n' COMMENT '출력여부 (n: 미출력, y: 출력완료)',
  `is_visible` enum('y','n') CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT 'y' COMMENT 'Y:표시, N:미표시',
  `create_datetime` datetime NOT NULL DEFAULT (now()) COMMENT '등록일시',
  `create_member_id` bigint NOT NULL COMMENT '등록자',
  `update_datetime` datetime DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '수정일시',
  `update_member_id` bigint DEFAULT NULL COMMENT '수정자',
  `delete_datetime` datetime DEFAULT NULL COMMENT '삭제일시',
  `delete_member_id` bigint DEFAULT NULL COMMENT '삭제자',
  PRIMARY KEY (`id`),
  UNIQUE KEY `manage_no` (`manage_no`),
  UNIQUE KEY `num` (`report_num`) USING BTREE,
  UNIQUE KEY `agcy_self_report_num` (`agcy_self_report_num`),
  KEY `is_visible` (`is_visible`),
  KEY `cali_order_id` (`cali_order_id`)
) ENGINE=InnoDB AUTO_INCREMENT=45 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='성적서 테이블';

CREATE TABLE IF NOT EXISTS `sample` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `name` varchar(200) NOT NULL COMMENT '샘플명(기기명)',
  `middle_item_code_id` bigint DEFAULT NULL COMMENT '중분류코드 (item_code.id, code_level=MIDDLE)',
  `small_item_code_id` bigint DEFAULT NULL COMMENT '소분류코드 (item_code.id, code_level=SMALL)',
  `is_visible` enum('y','n') CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT 'y' COMMENT '삭제 유무 - y: 노출됨(삭제되지 않음), n: 삭제처리(노출되지 않음)',
  `create_datetime` datetime NOT NULL DEFAULT (now()) COMMENT '등록일시',
  `create_member_id` bigint NOT NULL COMMENT '등록자',
  `update_datetime` datetime DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '수정일시',
  `update_member_id` bigint DEFAULT NULL COMMENT '수정자',
  `delete_datetime` datetime DEFAULT NULL COMMENT '삭제일시',
  `delete_member_id` bigint DEFAULT NULL COMMENT '삭제자',
  PRIMARY KEY (`id`) USING BTREE,
  KEY `is_visible` (`is_visible`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='샘플(기기)관리';

CREATE TABLE IF NOT EXISTS `standard_equipment` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '고유 id',
  `manage_no` varchar(150) DEFAULT NULL COMMENT '관리번호',
  `name` varchar(200) NOT NULL COMMENT '장비명',
  `name_en` varchar(200) DEFAULT NULL COMMENT '장비명(영문)',
  `make_agent` varchar(200) DEFAULT NULL COMMENT '제작회사',
  `make_agent_en` varchar(200) DEFAULT NULL COMMENT '제작회사(영문)',
  `serial_no` varchar(200) DEFAULT NULL COMMENT '기기번호',
  `equipment_field_id` bigint DEFAULT NULL COMMENT '분야코드 (equipment_field) 테이블과 연동',
  `is_dispose` enum('y','n') NOT NULL DEFAULT 'n' COMMENT '폐기여부 y: 폐기, n: 미폐기(기본값)',
  `model_name` varchar(200) DEFAULT NULL COMMENT '모델명',
  `accuracy_spec` varchar(200) DEFAULT NULL COMMENT '세부사양(정밀도)',
  `primary_manager_id` bigint DEFAULT NULL COMMENT '관리담당자(정) -  member 테이블과 연동 ',
  `secondary_manager_id` bigint DEFAULT NULL COMMENT '관리담당자(부) - member테이블과 연동',
  `install_location` varchar(100) DEFAULT NULL COMMENT '설치위치',
  `purchase_vendor` varchar(200) DEFAULT NULL COMMENT '구입처',
  `purchase_contact_manager` varchar(200) DEFAULT NULL COMMENT '구입처 담당자',
  `purchase_vendor_tel` varchar(50) DEFAULT NULL COMMENT '구입처 연락처',
  `purchase_price` bigint DEFAULT '0' COMMENT '구입가격',
  `purchase_date` date DEFAULT NULL COMMENT '구입일',
  `install_date` date DEFAULT NULL COMMENT '설치일',
  `detail_note` varchar(300) DEFAULT NULL COMMENT '세부사항',
  `is_install_condition` enum('y','n') NOT NULL DEFAULT 'y' COMMENT '설치조건 충족여부',
  `is_interim_check` enum('y','n') NOT NULL DEFAULT 'y' COMMENT '중간점검여부',
  `cali_cycle_months` int DEFAULT NULL COMMENT '교정주기 (개월 단위)',
  `purpose` varchar(200) DEFAULT NULL COMMENT '용도',
  `manage_department_id` bigint DEFAULT NULL COMMENT '관리부서 department 테이블과 연동할 것',
  `due_notify_days` int DEFAULT NULL COMMENT '도래알림일 ex) 30 -> 30일 전에 알림',
  `is_use` enum('y','n') NOT NULL DEFAULT 'y' COMMENT '사용여부',
  `has_manual` enum('y','n') NOT NULL DEFAULT 'y' COMMENT '매뉴얼 존재 여부',
  `has_firmware` enum('y','n') NOT NULL DEFAULT 'y' COMMENT '펌웨어 존재여부',
  `firmware_version` varchar(50) DEFAULT NULL COMMENT '펌웨어 버전',
  `property` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT '특성',
  `accessories_note` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT '부속품',
  `remark` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT '비고',
  `is_visible` enum('y','n') CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT 'y' COMMENT '삭제여부.  n: 삭제',
  `create_datetime` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '등록일시',
  `create_member_id` bigint NOT NULL COMMENT '등록자',
  `update_datetime` datetime DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '수정일시',
  `update_member_id` bigint DEFAULT NULL COMMENT '수정자',
  `delete_datetime` datetime DEFAULT NULL COMMENT '삭제일시',
  `delete_member_id` bigint DEFAULT NULL COMMENT '삭제자',
  PRIMARY KEY (`id`),
  KEY `equipment_field_id` (`equipment_field_id`)
) ENGINE=InnoDB AUTO_INCREMENT=210 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='표준장비관리';

CREATE TABLE IF NOT EXISTS `standard_equipment_ref` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '고유 id',
  `equipment_id` bigint NOT NULL COMMENT '표준장비 id (standard_equipment.id)',
  `ref_table` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '참조 테이블명 (ex: report, business_trip)',
  `ref_table_id` bigint NOT NULL COMMENT '참조 테이블의 id',
  `seq` int NOT NULL DEFAULT '0' COMMENT '표시 순서 (1..N 권장, 0=미지정)',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE KEY `uk_std_eq_ref` (`ref_table`,`ref_table_id`,`equipment_id`),
  KEY `idx_std_eq_ref_lookup` (`ref_table`,`ref_table_id`,`seq`) USING BTREE,
  KEY `idx_std_eq_ref_equipment_id` (`equipment_id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=5 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='사용중인 표준장비(참조 매핑)';

CREATE TABLE IF NOT EXISTS `env` (
  `id` tinyint NOT NULL COMMENT '고정 row 키 (항상 1, 단일 row 운영)',
  `name` varchar(100) DEFAULT NULL COMMENT '회사명',
  `name_en` varchar(100) DEFAULT NULL COMMENT '회사명(영문)',
  `ceo` varchar(50) DEFAULT NULL COMMENT '대표자',
  `tel` varchar(30) DEFAULT NULL COMMENT '전화번호',
  `fax` varchar(30) DEFAULT NULL COMMENT 'FAX',
  `hp` varchar(30) DEFAULT NULL COMMENT '휴대폰 연락처',
  `addr` varchar(200) DEFAULT NULL COMMENT '주소',
  `addr_en` varchar(200) DEFAULT NULL COMMENT '주소(영문)',
  `email` varchar(100) DEFAULT NULL COMMENT '이메일',
  `report_issue_addr` varchar(300) DEFAULT NULL COMMENT '성적서발행처 주소',
  `report_issue_addr_en` varchar(300) DEFAULT NULL COMMENT '성적서발행처 주소(영문)',
  `site_addr` varchar(300) DEFAULT NULL COMMENT '소재지 주소',
  `site_addr_en` varchar(300) DEFAULT NULL COMMENT '소재지 주소(영문)',
  `agent_num` varchar(20) DEFAULT NULL COMMENT '사업자등록번호',
  `back_account` varchar(200) DEFAULT NULL COMMENT '거래은행(계좌번호)',
  `kolas` varchar(500) DEFAULT NULL COMMENT 'KOLAS 인증 이미지 경로 (오브젝트 스토리지 key)',
  `ilac` varchar(500) DEFAULT NULL COMMENT '아일락 이미지 경로 (오브젝트 스토리지 key)',
  `company` varchar(500) DEFAULT NULL COMMENT '사내 로고 이미지 경로 (오브젝트 스토리지 key)',
  `update_member_id` bigint DEFAULT NULL COMMENT '수정자 (member.id)',
  `update_datetime` datetime DEFAULT NULL COMMENT '수정일시',
  `sheet_info_setting` json DEFAULT NULL COMMENT '성적서시트 셀위치/형식 설정 (JSON)',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='회사 기본정보 (단일 row, id=1 고정)';

CREATE TABLE IF NOT EXISTS `number_sequence` (
  `seq_key`    varchar(50)  NOT NULL COMMENT '시퀀스 키 (예: order_2026, report_ACCREDDIT_2026)',
  `next_val`   int          NOT NULL DEFAULT 1 COMMENT '다음에 발급할 순번 (1-based)',
  `update_datetime` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '최종 갱신일시',
  PRIMARY KEY (`seq_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='채번 동시성 제어용 시퀀스 테이블';

CREATE TABLE IF NOT EXISTS `report_job_batch` (
  `id`                bigint      NOT NULL AUTO_INCREMENT          COMMENT '배치 고유 id',
  `job_type`          varchar(30) NOT NULL
    COMMENT '작업 유형 (WRITE: 성적서작성 / WORK_APPROVAL: 실무자결재 / MANAGER_APPROVAL: 기술책임자결재)',
  `request_member_id` bigint      NOT NULL                         COMMENT '작업 요청자 member.id',
  `sample_id`         bigint      DEFAULT NULL                     COMMENT 'WRITE 타입에만 사용. 선택한 샘플 sample.id. 그 외 NULL',
  `total_count`       int         NOT NULL DEFAULT 0               COMMENT '전체 처리 대상 건수',
  `success_count`     int         NOT NULL DEFAULT 0               COMMENT '성공 건수',
  `fail_count`        int         NOT NULL DEFAULT 0               COMMENT '실패 건수',
  `status`            varchar(30) NOT NULL DEFAULT 'READY'
    COMMENT '배치 전체 상태 (READY / PROGRESS / SUCCESS / FAIL / CANCEL_REQUESTED / CANCELED)',
  `token`             varchar(64) DEFAULT NULL                     COMMENT 'ExcelWork 미들웨어 인증 토큰 (UUID hex). cali-worker 방식 배치는 NULL',
  `create_datetime`   datetime    NOT NULL DEFAULT (now())         COMMENT '배치 생성일시',
  `start_datetime`    datetime    DEFAULT NULL                     COMMENT '작업서버에서 처리를 시작한 시각',
  `end_datetime`      datetime    DEFAULT NULL                     COMMENT '배치 전체 처리 완료 시각',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_token` (`token`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='성적서 작업 배치 — 성적서작성/결재 작업의 묶음 단위 (버튼 클릭 1번 = 1배치)';

CREATE TABLE IF NOT EXISTS `report_job_item` (
  `id`             bigint      NOT NULL AUTO_INCREMENT  COMMENT '아이템 고유 id',
  `batch_id`       bigint      NOT NULL                 COMMENT '소속 배치 id (report_job_batch.id)',
  `report_id`      bigint      NOT NULL                 COMMENT '처리 대상 성적서 id (report.id)',
  `file_uuid`      varchar(36) DEFAULT NULL             COMMENT 'ExcelWork 미들웨어 파일 다운로드 식별 UUID. WRITE 배치 생성 시 item별 부여. cali-worker 방식은 NULL',
  `status`         varchar(30) NOT NULL DEFAULT 'READY'
    COMMENT '개별 처리 상태 (READY / PROGRESS / SUCCESS / FAIL / CANCELED)',
  `step`           varchar(50) DEFAULT NULL
    COMMENT '현재 처리 단계 상세명 (작업서버가 진행 중 업데이트. job_type별 단계 정의는 claudeReq.md 참조)',
  `message`        text        DEFAULT NULL             COMMENT '실패 사유 또는 처리 메시지',
  `retry_count`    int         NOT NULL DEFAULT 0       COMMENT '재시도 횟수',
  `start_datetime` datetime    DEFAULT NULL             COMMENT '이 아이템 처리 시작 시각',
  `end_datetime`   datetime    DEFAULT NULL             COMMENT '이 아이템 처리 완료 시각',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_file_uuid` (`file_uuid`),
  KEY `idx_batch_id`  (`batch_id`),
  KEY `idx_report_id` (`report_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='성적서 작업 개별 항목 — batch 내 성적서 1건 = item 1건, item 단위로 독립 처리';

-- -----------------------------------------------------------------------------
-- 출장일정 (v_260325)
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `business_trip` (
  `id`                    bigint        NOT NULL AUTO_INCREMENT               COMMENT '출장일정 고유 id',
  `type`                  varchar(50)   DEFAULT NULL                          COMMENT '출장유형 (추후 코드 정의 예정)',
  `title`                 varchar(200)  NOT NULL                              COMMENT '출장제목',
  `start_datetime`        datetime      NOT NULL                              COMMENT '출장시작일시',
  `end_datetime`          datetime      NOT NULL                              COMMENT '출장종료일시',

  -- 신청업체 정보
  `cust_agent_id`         bigint        DEFAULT NULL                          COMMENT '신청업체 agent.id (업체 조회 후 선택값)',
  `cust_agent`            varchar(200)  DEFAULT NULL                          COMMENT '신청업체명',
  `cust_agent_addr`       varchar(300)  DEFAULT NULL                          COMMENT '신청업체 주소',
  `cust_manager`          varchar(100)  DEFAULT NULL                          COMMENT '신청업체 담당자명',
  `cust_manager_tel`      varchar(50)   DEFAULT NULL                          COMMENT '신청업체 담당자 연락처',
  `cust_manager_email`    varchar(125)  DEFAULT NULL                          COMMENT '신청업체 담당자 이메일',

  -- 성적서발행처 정보
  `report_agent_id`       bigint        DEFAULT NULL                          COMMENT '성적서발행처 agent.id (업체 조회 후 선택값)',
  `report_agent`          varchar(200)  DEFAULT NULL                          COMMENT '성적서발행처명',
  `report_agent_addr`     varchar(300)  DEFAULT NULL                          COMMENT '성적서발행처 주소',
  `report_manager`        varchar(100)  DEFAULT NULL                          COMMENT '성적서발행처 담당자명',
  `report_manager_tel`    varchar(50)   DEFAULT NULL                          COMMENT '성적서발행처 담당자 연락처',

  -- 소재지 정보
  `site_addr`             varchar(300)  DEFAULT NULL                          COMMENT '소재지 주소',
  `site_manager`          varchar(100)  DEFAULT NULL                          COMMENT '소재지 담당자명',
  `site_manager_tel`      varchar(50)   DEFAULT NULL                          COMMENT '소재지 담당자 연락처',
  `site_manager_email`    varchar(125)  DEFAULT NULL                          COMMENT '소재지 담당자 이메일',

  -- 출장자 / 출장차량 (콤마 구분 id 문자열)
  `traveler_ids`          varchar(500)  DEFAULT NULL                          COMMENT '출장자 member.id 목록 (콤마 구분, 예: "1,5,12")',
  `car_ids`               varchar(255)  DEFAULT NULL                          COMMENT '출장차량 car.id 목록 (콤마 구분, car 테이블 미구현 - 추후 연동)',

  `remark`                text          DEFAULT NULL                          COMMENT '비고',

  -- 감사 컬럼
  `create_datetime`       datetime      NOT NULL DEFAULT (now())              COMMENT '등록일시',
  `create_member_id`      bigint        NOT NULL                              COMMENT '등록자 member.id',
  `update_datetime`       datetime      DEFAULT (now()) ON UPDATE CURRENT_TIMESTAMP COMMENT '수정일시',
  `update_member_id`      bigint        DEFAULT NULL                          COMMENT '수정자 member.id',
  `delete_datetime`       datetime      DEFAULT NULL                          COMMENT '삭제일시',
  `delete_member_id`      bigint        DEFAULT NULL                          COMMENT '삭제자 member.id',

  PRIMARY KEY (`id`),
  KEY `idx_btrip_period`  (`start_datetime`, `end_datetime`),
  KEY `idx_btrip_create`  (`create_member_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='출장일정 관리';

-- -----------------------------------------------------------------------------
-- 출장차량 (v_260326)
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `car` (
  `id`                  bigint        NOT NULL AUTO_INCREMENT               COMMENT '차량 고유 id',

  -- 기본 정보
  `name`                varchar(100)  NOT NULL                              COMMENT '차량 이름 (예: 1호차)',
  `number`              varchar(20)   NOT NULL                              COMMENT '차량번호 (예: 서울 12가 3456)',
  `brand`               varchar(50)   DEFAULT NULL                          COMMENT '브랜드/제조사 (예: 현대, 기아)',
  `model_year`          smallint      DEFAULT NULL                          COMMENT '년식 (예: 2022)',
  `fuel_type`           varchar(20)   DEFAULT NULL                          COMMENT '연료 종류 (GASOLINE/DIESEL/LPG/ELECTRIC/HYBRID)',

  -- 운영 정보
  `capacity`            tinyint       DEFAULT NULL                          COMMENT '최대 탑승 인원 (출장 인원 배치 참고용)',
  `insurance_company`   varchar(100)  DEFAULT NULL                          COMMENT '보험사명',
  `insurance_expiry`    date          DEFAULT NULL                          COMMENT '보험 만료일',
  `inspection_expiry`   date          DEFAULT NULL                          COMMENT '정기검사 만료일',
  `remark`              text          DEFAULT NULL                          COMMENT '비고',

  -- 표시 여부 (soft delete)
  `is_visible`          char(1)       NOT NULL DEFAULT 'y'                  COMMENT '표시여부 (y: 표시, n: 삭제/숨김)',

  -- 감사 컬럼
  `create_datetime`     datetime      NOT NULL DEFAULT (now())              COMMENT '등록일시',
  `create_member_id`    bigint        NOT NULL                              COMMENT '등록자 member.id',
  `update_datetime`     datetime      DEFAULT (now()) ON UPDATE CURRENT_TIMESTAMP COMMENT '수정일시',
  `update_member_id`    bigint        DEFAULT NULL                          COMMENT '수정자 member.id',
  `delete_datetime`     datetime      DEFAULT NULL                          COMMENT '삭제일시',
  `delete_member_id`    bigint        DEFAULT NULL                          COMMENT '삭제자 member.id',

  PRIMARY KEY (`id`),
  KEY `idx_car_visible` (`is_visible`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='출장차량 관리';

-- -----------------------------------------------------------------------------
-- alarm: 사용자 알림
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `alarm` (
  `id`              bigint        NOT NULL AUTO_INCREMENT             COMMENT '알림 고유번호',
  `member_id`       bigint        NOT NULL                            COMMENT '수신자 member.id',
  `alarm_type`      varchar(30)   NOT NULL                            COMMENT '알림 유형 (WORK_COMMENT/WORK_NOTICE/REPORT_REJECT/REPORT_SUBMIT/REPORT_REUPLOAD/BOARD_NOTICE)',
  `ref_type`        varchar(30)   DEFAULT NULL                        COMMENT '참조 대상 종류 (WORK/REPORT/BOARD_NOTICE)',
  `ref_id`          bigint        DEFAULT NULL                        COMMENT '참조 대상 ID (ref_type에 따라 다른 테이블의 PK)',
  `content`         varchar(500)  NOT NULL                            COMMENT '알림 내용 (제목 겸 본문 스냅샷)',
  `sender_name`     varchar(50)   DEFAULT NULL                        COMMENT '발신자명 스냅샷 (NULL이면 시스템 발신)',
  `is_read`         char(1)       NOT NULL DEFAULT 'n'                COMMENT '읽음 여부 (y: 읽음, n: 미읽음)',
  `read_datetime`   datetime      DEFAULT NULL                        COMMENT '읽은 일시',
  `create_datetime` datetime      NOT NULL DEFAULT (now())            COMMENT '알림 생성일시',

  PRIMARY KEY (`id`),
  KEY `idx_alarm_member_read` (`member_id`, `is_read`, `create_datetime`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='사용자 알림';
