-- 사용자 콘텐츠 번역 1차 적용 SQL 초안.
-- 이 파일은 애플리케이션에서 자동 실행되지 않으며, 배포 전에 DBA 검토 후 별도로 적용한다.

ALTER TABLE `destination_comments`
  ADD COLUMN `source_language` varchar(10) CHARACTER SET ascii COLLATE ascii_bin
  NOT NULL DEFAULT 'und' AFTER `content`;

CREATE TABLE `content_translation_cache` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `content_type` varchar(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `content_id` bigint NOT NULL,
  `source_field` varchar(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `target_language` varchar(10) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `source_hash` binary(32) NOT NULL,
  `detected_source_language` varchar(10) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL,
  `translated_text` mediumtext CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci,
  `provider` varchar(20) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'GOOGLE',
  `status` varchar(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `lease_token` char(36) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL,
  `lease_expires_at` datetime(6) DEFAULT NULL,
  `retry_after` datetime(6) DEFAULT NULL,
  `created_at` timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `updated_at` timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_content_translation_target`
    (`content_type`,`content_id`,`source_field`,`target_language`),
  KEY `idx_content_translation_status_retry` (`status`,`retry_after`),
  KEY `idx_content_translation_lease` (`status`,`lease_expires_at`),
  CONSTRAINT `chk_content_translation_status`
    CHECK (`status` IN ('PROCESSING','READY','FAILED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 기존 댓글 로컬 언어 backfill은 위 DDL 적용 후 프로젝트 루트에서 한 번만 실행한다.
-- Google Translation API는 호출하지 않는다. 이미 und가 아닌 행은 조회/갱신하지 않는다.
-- ./gradlew backfillDestinationCommentLanguages
