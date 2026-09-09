-- Google 사용자 콘텐츠 번역 월간 하드캡 적용 SQL 초안.
-- 이 파일은 애플리케이션에서 자동 실행되지 않으며, 배포 전에 DBA 검토 후 별도로 적용한다.

CREATE TABLE `google_translation_monthly_usage` (
  `month_key` char(7) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `used_characters` bigint unsigned NOT NULL DEFAULT '0',
  `provider_calls` bigint unsigned NOT NULL DEFAULT '0',
  `created_at` timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `updated_at` timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`month_key`),
  CONSTRAINT `chk_google_translation_month_key`
    CHECK (`month_key` REGEXP '^[0-9]{4}-(0[1-9]|1[0-2])$')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
