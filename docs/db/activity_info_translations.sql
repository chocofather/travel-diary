-- 체험/액티비티 상세정보 다국어 테이블
-- (관광지/식당/숙박 translation 테이블과 같은 명명 규칙: uk_… / idx_…_lang / fk_…)
--
-- 실행은 사람이 직접 Workbench 에서 한다. 애플리케이션은 이 파일을 읽지 않는다.
-- 적용 완료: 아래 정의는 실제 DB(SHOW CREATE TABLE activity_info_translations)와 같은 내용이다.
--
-- 담는 값은 자유 텍스트뿐이다.
--   opening_hours(운영 시간) / required_time(소요 시간) / admission_fee(참가비)
--   age_limit(연령 제한) / guide(이용 안내)
-- 사전 예약·장비 포함·주차 여부(Boolean), 연락처, 홈페이지는 언어와 무관해 담지 않는다.
--
-- 언어 코드는 화면이 쓰는 다섯 개뿐이다: ko / en / ja / zh-CN / zh-TW
-- 한 여행지에 언어별로 한 행이며(UNIQUE), 액티비티 정보가 지워지면 함께 지워진다(CASCADE).
-- 길이는 activity_info 원본 컬럼과 같게 맞췄다.

CREATE TABLE `activity_info_translations` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `destination_id` bigint NOT NULL,
  `language_code` varchar(10) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `opening_hours` varchar(1000) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `required_time` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `admission_fee` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci,
  `age_limit` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `guide` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_activity_info_translation` (`destination_id`,`language_code`),
  KEY `idx_activity_info_translation_lang` (`language_code`,`destination_id`),
  CONSTRAINT `fk_activity_info_translation`
    FOREIGN KEY (`destination_id`)
    REFERENCES `activity_info` (`destination_id`)
    ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 확인
-- SHOW CREATE TABLE `activity_info_translations`;
-- SELECT language_code, COUNT(*) FROM activity_info_translations GROUP BY language_code;

-- 번역 데이터는 관리자 화면에서 입력한다. 여기서 만들지 않는다.
