-- 식당 상세정보 다국어 테이블 (Phase 3C-1 관광명소 attraction_info_translations 와 같은 구조)
--
-- 실행은 사람이 직접 Workbench 에서 한다. 애플리케이션은 이 파일을 읽지 않는다.
-- 이 테이블이 없으면 공개 식당 상세가 번역 조회에서 실패하므로, 배포 전에 먼저 실행해야 한다.
--
-- 담는 값은 자유 텍스트뿐이다.
--   main_menu(대표메뉴) / price_range(가격대) / opening_hours(영업시간)
--   break_time(브레이크 타임) / closed_days(휴무일) / etc(기타 안내사항)
-- 전화번호·홈페이지·좌석 수·주차/반려동물/포장/배달/예약 가능 여부는 언어와 무관해 담지 않는다.
--
-- 언어 코드는 화면이 쓰는 다섯 개뿐이다: ko / en / ja / zh-CN / zh-TW
-- 한 여행지에 언어별로 한 행이며(UNIQUE), 식당 정보가 지워지면 함께 지워진다(CASCADE).
-- 길이는 restaurant_info 원본 컬럼과 같게 맞췄다.

-- 아래 정의는 실제 DB(SHOW CREATE TABLE restaurant_info_translations)와 같은 내용이다.
CREATE TABLE `restaurant_info_translations` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `destination_id` bigint NOT NULL,
  `language_code` varchar(10) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `main_menu` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `price_range` varchar(32) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `opening_hours` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `break_time` varchar(32) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `closed_days` varchar(32) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `etc` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_restaurant_info_translation` (`destination_id`,`language_code`),
  KEY `idx_restaurant_info_translation_lang` (`language_code`,`destination_id`),
  CONSTRAINT `fk_restaurant_info_translation`
    FOREIGN KEY (`destination_id`)
    REFERENCES `restaurant_info` (`destination_id`)
    ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 확인
-- SHOW CREATE TABLE `restaurant_info_translations`;
-- SELECT language_code, COUNT(*) FROM restaurant_info_translations GROUP BY language_code;

-- 번역 데이터는 이번 단계에서 만들지 않았다. 넣을 때는 언어별로 한 행씩,
-- 다섯 canonical 코드만 쓴다. (예시이며 그대로 실행하지 말 것)
-- INSERT INTO restaurant_info_translations
--     (destination_id, language_code, main_menu, price_range, opening_hours,
--      break_time, closed_days, etc)
-- VALUES (1, 'en', 'Signature bibimbap', 'KRW 10,000-20,000', '11:00-21:00',
--         '15:00-17:00', 'Every Monday', 'Group reservations available')
-- ON DUPLICATE KEY UPDATE
--     main_menu = VALUES(main_menu), price_range = VALUES(price_range),
--     opening_hours = VALUES(opening_hours), break_time = VALUES(break_time),
--     closed_days = VALUES(closed_days), etc = VALUES(etc);
