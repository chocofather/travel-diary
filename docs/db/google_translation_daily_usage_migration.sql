-- Google 사용자 콘텐츠 번역 사용자/IP 일일 문자 제한 적용 SQL 초안.
-- 이 파일은 애플리케이션에서 자동 실행되지 않으며, 배포 전에 DBA 검토 후 별도로 적용한다.

CREATE TABLE `google_translation_daily_usage` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `usage_date` date NOT NULL,
  `subject_type` varchar(8) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `user_id` bigint DEFAULT NULL,
  `ip_hash` binary(32) DEFAULT NULL,
  `used_characters` bigint unsigned NOT NULL DEFAULT '0',
  `provider_calls` bigint unsigned NOT NULL DEFAULT '0',
  `created_at` timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `updated_at` timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_google_translation_daily_user` (`usage_date`,`subject_type`,`user_id`),
  UNIQUE KEY `uq_google_translation_daily_ip` (`usage_date`,`subject_type`,`ip_hash`),
  KEY `idx_google_translation_daily_user` (`user_id`),
  CONSTRAINT `fk_google_translation_daily_user`
    FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE,
  CONSTRAINT `chk_google_translation_daily_subject_type`
    CHECK (`subject_type` IN ('USER','IP')),
  CONSTRAINT `chk_google_translation_daily_subject`
    CHECK ((`subject_type` = 'USER' AND `user_id` IS NOT NULL AND `ip_hash` IS NULL)
      OR (`subject_type` = 'IP' AND `user_id` IS NULL AND `ip_hash` IS NOT NULL))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
