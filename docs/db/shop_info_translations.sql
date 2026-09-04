-- 쇼핑 상세정보 다국어 테이블
-- (관광지/식당/숙박/체험 translation 테이블과 같은 명명 규칙: uk_… / idx_…_lang / fk_…)
--
-- 실행은 사람이 직접 Workbench 에서 한다. 애플리케이션은 이 파일을 읽지 않는다.
-- 적용 완료: 아래 정의는 실제 DB(SHOW CREATE TABLE shop_info_translations)와 같은 내용이다.
--
-- 담는 값은 자유 텍스트뿐이다.
--   closed_days(휴점일) / opening_hours(영업시간) / main_products(주요상품·카테고리)
--   guide(기타 안내사항)
-- 주차 가능 여부(Boolean), 연락처, 홈페이지는 언어와 무관해 담지 않는다.
--
-- 언어 코드는 화면이 쓰는 다섯 개뿐이다: ko / en / ja / zh-CN / zh-TW
-- 한 여행지에 언어별로 한 행이며(UNIQUE), 쇼핑 정보가 지워지면 함께 지워진다(CASCADE).
-- 길이는 shop_info 원본 컬럼과 같게 맞췄다.

CREATE TABLE `shop_info_translations` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `destination_id` bigint NOT NULL,
  `language_code` varchar(10) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `closed_days` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `opening_hours` varchar(1000) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `main_products` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `guide` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_shop_info_translation` (`destination_id`,`language_code`),
  KEY `idx_shop_info_translation_lang` (`language_code`,`destination_id`),
  CONSTRAINT `fk_shop_info_translation`
    FOREIGN KEY (`destination_id`)
    REFERENCES `shop_info` (`destination_id`)
    ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 확인
-- SHOW CREATE TABLE `shop_info_translations`;
-- SELECT language_code, COUNT(*) FROM shop_info_translations GROUP BY language_code;

-- 번역 데이터는 관리자 화면에서 입력한다. 여기서 만들지 않는다.
