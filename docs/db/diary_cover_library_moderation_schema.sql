CREATE TABLE `diary_cover_library_reports` (
  `id` bigint NOT NULL AUTO_INCREMENT,

  `library_item_id` bigint NOT NULL,
  `reported_snapshot_version` int unsigned NOT NULL,
  `photo_asset_id` bigint DEFAULT NULL,
  `reporter_user_id` bigint DEFAULT NULL,

  `reason_code` varchar(30)
      CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `description` varchar(1000) DEFAULT NULL,

  `status` varchar(20)
      CHARACTER SET ascii COLLATE ascii_bin
      NOT NULL DEFAULT 'PENDING',

  `resolution_action` varchar(30)
      CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL,

  `processed_by_user_id` bigint DEFAULT NULL,
  `processed_at` timestamp(6) NULL DEFAULT NULL,
  `admin_note` text DEFAULT NULL,

  `target_photo_asset_key` bigint
      GENERATED ALWAYS AS (coalesce(`photo_asset_id`,0)) STORED,

  `created_at` timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `updated_at` timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
      ON UPDATE CURRENT_TIMESTAMP(6),

  PRIMARY KEY (`id`),

  UNIQUE KEY `uq_dclr_id_item` (`id`,`library_item_id`),

  UNIQUE KEY `uq_dclr_reporter_target`
      (`reporter_user_id`,`library_item_id`,`reported_snapshot_version`,`target_photo_asset_key`),

  KEY `idx_dclr_status_created` (`status`,`created_at`,`id`),

  KEY `idx_dclr_item_created` (`library_item_id`,`created_at`,`id`),

  KEY `idx_dclr_item_snapshot_created`
      (`library_item_id`,`reported_snapshot_version`,`created_at`,`id`),

  KEY `idx_dclr_item_photo` (`library_item_id`,`photo_asset_id`),

  KEY `idx_dclr_photo_created` (`photo_asset_id`,`created_at`,`id`),

  KEY `idx_dclr_processor` (`processed_by_user_id`,`processed_at`,`id`),

  CONSTRAINT `fk_dclr_library_item`
    FOREIGN KEY (`library_item_id`)
    REFERENCES `diary_cover_library_items` (`id`)
    ON DELETE RESTRICT,

  CONSTRAINT `fk_dclr_photo_asset`
    FOREIGN KEY (`library_item_id`,`photo_asset_id`)
    REFERENCES `diary_cover_library_photo_assets` (`library_item_id`,`id`)
    ON DELETE RESTRICT,

  CONSTRAINT `fk_dclr_reporter`
    FOREIGN KEY (`reporter_user_id`)
    REFERENCES `users` (`id`)
    ON DELETE SET NULL,

  CONSTRAINT `fk_dclr_processor`
    FOREIGN KEY (`processed_by_user_id`)
    REFERENCES `users` (`id`)
    ON DELETE SET NULL,

  CONSTRAINT `chk_dclr_reported_snapshot_version`
    CHECK (`reported_snapshot_version` >= 1),

  CONSTRAINT `chk_dclr_reason_code`
    CHECK (
      `reason_code` IN (
        'COPYRIGHT',
        'PORTRAIT_PRIVACY',
        'INAPPROPRIATE',
        'SPAM',
        'OTHER'
      )
    ),

  CONSTRAINT `chk_dclr_description`
    CHECK (
      (
        `description` IS NULL
        OR char_length(trim(`description`)) > 0
      )
      AND (
        `reason_code` <> 'OTHER'
        OR (
          `description` IS NOT NULL
          AND char_length(trim(`description`)) > 0
        )
      )
    ),

  CONSTRAINT `chk_dclr_status`
    CHECK (`status` IN ('PENDING','RESOLVED','REJECTED')),

  CONSTRAINT `chk_dclr_resolution_action`
    CHECK (
      `resolution_action` IS NULL
      OR `resolution_action` IN (
        'NO_ACTION',
        'ITEM_BLOCKED',
        'PHOTO_BLOCKED',
        'ITEM_AND_PHOTO_BLOCKED'
      )
    ),

  CONSTRAINT `chk_dclr_processing_state`
    CHECK (
      (
        `status` = 'PENDING'
        AND `processed_at` IS NULL
        AND `resolution_action` IS NULL
      )
      OR
      (
        `status` = 'RESOLVED'
        AND `processed_at` IS NOT NULL
        AND `resolution_action` IS NOT NULL
        AND `resolution_action` IN (
          'NO_ACTION',
          'ITEM_BLOCKED',
          'PHOTO_BLOCKED',
          'ITEM_AND_PHOTO_BLOCKED'
        )
      )
      OR
      (
        `status` = 'REJECTED'
        AND `processed_at` IS NOT NULL
        AND `resolution_action` IS NOT NULL
        AND `resolution_action` = 'NO_ACTION'
      )
    )
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_general_ci;


CREATE TABLE `diary_cover_library_moderation_actions` (
  `id` bigint NOT NULL AUTO_INCREMENT,

  `report_id` bigint NOT NULL,
  `library_item_id` bigint NOT NULL,
  `photo_asset_id` bigint DEFAULT NULL,

  `action_type` varchar(30)
      CHARACTER SET ascii COLLATE ascii_bin NOT NULL,

  `previous_status` varchar(20)
      CHARACTER SET ascii COLLATE ascii_bin NOT NULL,

  `resulting_status` varchar(20)
      CHARACTER SET ascii COLLATE ascii_bin NOT NULL,

  `reason` varchar(500) NOT NULL,
  `admin_note` text DEFAULT NULL,

  `action_by_user_id` bigint DEFAULT NULL,
  `created_at` timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

  PRIMARY KEY (`id`),

  KEY `idx_dclma_report_item_created`
      (`report_id`,`library_item_id`,`created_at`,`id`),

  KEY `idx_dclma_item_created` (`library_item_id`,`created_at`,`id`),

  KEY `idx_dclma_item_photo` (`library_item_id`,`photo_asset_id`),

  KEY `idx_dclma_photo_created` (`photo_asset_id`,`created_at`,`id`),

  KEY `idx_dclma_admin_created` (`action_by_user_id`,`created_at`,`id`),

  CONSTRAINT `fk_dclma_report_item`
    FOREIGN KEY (`report_id`,`library_item_id`)
    REFERENCES `diary_cover_library_reports` (`id`,`library_item_id`)
    ON DELETE RESTRICT,

  CONSTRAINT `fk_dclma_photo_asset`
    FOREIGN KEY (`library_item_id`,`photo_asset_id`)
    REFERENCES `diary_cover_library_photo_assets` (`library_item_id`,`id`)
    ON DELETE RESTRICT,

  CONSTRAINT `fk_dclma_admin`
    FOREIGN KEY (`action_by_user_id`)
    REFERENCES `users` (`id`)
    ON DELETE SET NULL,

  CONSTRAINT `chk_dclma_reason`
    CHECK (char_length(trim(`reason`)) > 0),

  CONSTRAINT `chk_dclma_transition`
    CHECK (
      (
        `action_type` = 'BLOCK_ITEM'
        AND `photo_asset_id` IS NULL
        AND `previous_status` IN ('PUBLISHED','WITHDRAWN')
        AND `resulting_status` = 'BLOCKED'
      )
      OR
      (
        `action_type` = 'RESTORE_ITEM'
        AND `photo_asset_id` IS NULL
        AND `previous_status` = 'BLOCKED'
        AND `resulting_status` IN ('PUBLISHED','WITHDRAWN')
      )
      OR
      (
        `action_type` = 'BLOCK_PHOTO'
        AND `photo_asset_id` IS NOT NULL
        AND `previous_status` = 'ACTIVE'
        AND `resulting_status` = 'BLOCKED'
      )
      OR
      (
        `action_type` = 'RESTORE_PHOTO'
        AND `photo_asset_id` IS NOT NULL
        AND `previous_status` = 'BLOCKED'
        AND `resulting_status` = 'ACTIVE'
      )
    )
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_general_ci;
