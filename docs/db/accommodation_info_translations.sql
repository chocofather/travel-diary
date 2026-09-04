-- 숙박 상세정보 다국어 테이블 (관광지/식당 translation 테이블과 같은 구조)
--
-- 실행은 사람이 직접 Workbench 에서 한다. 애플리케이션은 이 파일을 읽지 않는다.
--
-- 담는 값은 자유 텍스트뿐이다.
--   room_type(객실 유형) / etc(기타 안내사항)
-- 체크인·체크아웃 시각(varchar(10) "15:00"), 객실 수, 등급, 조식/주차/반려동물 여부,
-- 연락처, 홈페이지는 언어와 무관해 담지 않는다.
--
-- 언어 코드는 화면이 쓰는 다섯 개뿐이다: ko / en / ja / zh-CN / zh-TW
-- 한 여행지에 언어별로 한 행이며(UNIQUE), 숙박 정보가 지워지면 함께 지워진다(CASCADE).
-- 길이는 accommodation_info 원본 컬럼과 같게 맞췄다.
--
-- 적용 완료: 아래 정의는 실제 DB(SHOW CREATE TABLE accommodation_info_translations)와 같은 내용이다.

CREATE TABLE `accommodation_info_translations` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `destination_id` bigint NOT NULL,
  `language_code` varchar(10) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `room_type` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `etc` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_accommodation_info_translation` (`destination_id`,`language_code`),
  KEY `idx_accommodation_info_translation_lang` (`language_code`,`destination_id`),
  CONSTRAINT `fk_accommodation_info_translation`
    FOREIGN KEY (`destination_id`)
    REFERENCES `accommodation_info` (`destination_id`)
    ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 확인
-- SHOW CREATE TABLE `accommodation_info_translations`;
-- SELECT language_code, COUNT(*) FROM accommodation_info_translations GROUP BY language_code;

-- 번역 데이터는 관리자 화면에서 입력한다. 여기서 만들지 않는다.
