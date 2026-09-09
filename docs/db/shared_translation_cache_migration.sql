-- 사용자 콘텐츠의 동일 원문 번역 결과를 콘텐츠 ID와 무관하게 재사용하는 공용 캐시.
-- 이 파일은 애플리케이션에서 자동 실행되지 않으며, 배포 전에 DBA 검토 후 별도로 적용한다.

CREATE TABLE `shared_translation_cache` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `source_hash` binary(32) NOT NULL,
  `source_language` varchar(10) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `target_language` varchar(10) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `provider` varchar(20) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'GOOGLE',
  `translation_profile` varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `detected_source_language` varchar(10) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL,
  `translated_text` mediumtext CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci,
  `status` varchar(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `lease_token` char(36) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL,
  `lease_expires_at` datetime(6) DEFAULT NULL,
  `retry_after` datetime(6) DEFAULT NULL,
  `created_at` timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `updated_at` timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
    ON UPDATE CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_shared_translation_exact`
    (`source_hash`,`source_language`,`target_language`,`provider`,`translation_profile`),
  KEY `idx_shared_translation_status_retry` (`status`,`retry_after`),
  KEY `idx_shared_translation_lease` (`status`,`lease_expires_at`),
  CONSTRAINT `chk_shared_translation_status`
    CHECK (`status` IN ('PROCESSING','READY','FAILED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
