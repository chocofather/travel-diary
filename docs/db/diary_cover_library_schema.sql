-- Travel Diary cover library schema
--
-- New-install DDL for the member-shared diary cover library.
-- Do not store shared PHOTO files below the publicly mapped /uploads/** path.

/* =========================================================
   1. Library items
   ========================================================= */
CREATE TABLE `diary_cover_library_items` (
  `id` bigint NOT NULL AUTO_INCREMENT,

  /* Account deletion clears the ID; the published display name is snapshotted. */
  `creator_user_id` bigint DEFAULT NULL,
  `creator_display_name` varchar(50) NOT NULL,

  /* Provenance only. Deleting the source personal design must not delete this snapshot. */
  `source_cover_design_id` bigint DEFAULT NULL,

  `title` varchar(50) NOT NULL,
  `description` text DEFAULT NULL,

  /* Same types/defaults as diary_cover_designs. */
  `base_cover_style` varchar(30) NOT NULL DEFAULT 'DEFAULT',
  `background_color` varchar(7) DEFAULT NULL,

  `status` varchar(20) NOT NULL DEFAULT 'PUBLISHED',
  `download_count` bigint unsigned NOT NULL DEFAULT 0,
  `snapshot_version` int unsigned NOT NULL DEFAULT 1,

  `published_at` timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `withdrawn_at` timestamp(6) NULL DEFAULT NULL,
  `deleted_at` timestamp(6) NULL DEFAULT NULL,
  `blocked_at` timestamp(6) NULL DEFAULT NULL,
  `blocked_reason` varchar(500) DEFAULT NULL,

  `created_at` timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `updated_at` timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
      ON UPDATE CURRENT_TIMESTAMP(6),

  PRIMARY KEY (`id`),

  KEY `idx_dcli_latest`
      (`status`, `published_at` DESC, `id` DESC),

  KEY `idx_dcli_popular`
      (`status`, `download_count` DESC,
       `published_at` DESC, `id` DESC),

  KEY `idx_dcli_creator`
      (`creator_user_id`, `status`, `updated_at` DESC, `id` DESC),

  KEY `idx_dcli_source_design`
      (`source_cover_design_id`),

  CONSTRAINT `fk_dcli_creator_user`
    FOREIGN KEY (`creator_user_id`)
    REFERENCES `users` (`id`)
    ON DELETE SET NULL,

  CONSTRAINT `fk_dcli_source_design`
    FOREIGN KEY (`source_cover_design_id`)
    REFERENCES `diary_cover_designs` (`id`)
    ON DELETE SET NULL,

  CONSTRAINT `chk_dcli_status`
    CHECK (
      `status` IN (
        'PUBLISHED',
        'WITHDRAWN',
        'DELETED',
        'BLOCKED'
      )
    ),

  CONSTRAINT `chk_dcli_snapshot_version`
    CHECK (`snapshot_version` >= 1),

  CONSTRAINT `chk_dcli_creator_display_name`
    CHECK (CHAR_LENGTH(TRIM(`creator_display_name`)) > 0)

) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_general_ci;


/* =========================================================
   2. Private shared PHOTO assets

   storage_key is an opaque private-storage key, not a public URL.
   The application serves an asset through:
   /diaries/cover-library/assets/{assetId}
   ========================================================= */
CREATE TABLE `diary_cover_library_photo_assets` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `library_item_id` bigint NOT NULL,

  /* Account deletion clears the ID; the upload display name is snapshotted. */
  `original_uploader_user_id` bigint DEFAULT NULL,
  `original_uploader_display_name` varchar(50) NOT NULL,

  `snapshot_version` int unsigned NOT NULL DEFAULT 1,

  `storage_key` varchar(500)
      CHARACTER SET ascii COLLATE ascii_bin NOT NULL,

  `content_type` varchar(100)
      CHARACTER SET ascii COLLATE ascii_general_ci NOT NULL,

  `file_size` bigint unsigned NOT NULL,
  `status` varchar(20) NOT NULL DEFAULT 'ACTIVE',

  `rights_confirmed_at` timestamp(6) NOT NULL,
  `rights_terms_version` varchar(50) NOT NULL,

  `blocked_at` timestamp(6) NULL DEFAULT NULL,
  `blocked_reason` varchar(500) DEFAULT NULL,

  /* No longer used by the current snapshot, but still usable by old downloads. */
  `retired_at` timestamp(6) NULL DEFAULT NULL,

  `created_at` timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `updated_at` timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
      ON UPDATE CURRENT_TIMESTAMP(6),

  PRIMARY KEY (`id`),

  UNIQUE KEY `uq_dclpa_storage_key`
      (`storage_key`),

  /* Required as the target key for the same-library-item composite FK. */
  UNIQUE KEY `uq_dclpa_item_asset`
      (`library_item_id`, `id`),

  KEY `idx_dclpa_item_version`
      (`library_item_id`, `snapshot_version`, `id`),

  KEY `idx_dclpa_uploader`
      (`original_uploader_user_id`, `created_at`, `id`),

  CONSTRAINT `fk_dclpa_library_item`
    FOREIGN KEY (`library_item_id`)
    REFERENCES `diary_cover_library_items` (`id`)
    ON DELETE RESTRICT,

  CONSTRAINT `fk_dclpa_uploader`
    FOREIGN KEY (`original_uploader_user_id`)
    REFERENCES `users` (`id`)
    ON DELETE SET NULL,

  CONSTRAINT `chk_dclpa_status`
    CHECK (`status` IN ('ACTIVE', 'BLOCKED')),

  CONSTRAINT `chk_dclpa_snapshot_version`
    CHECK (`snapshot_version` >= 1),

  CONSTRAINT `chk_dclpa_file_size`
    CHECK (`file_size` > 0),

  CONSTRAINT `chk_dclpa_uploader_display_name`
    CHECK (
      CHAR_LENGTH(TRIM(`original_uploader_display_name`)) > 0
    ),

  CONSTRAINT `chk_dclpa_rights_terms_version`
    CHECK (
      CHAR_LENGTH(TRIM(`rights_terms_version`)) > 0
    )

) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_general_ci;


/* =========================================================
   3. Relational library element snapshot

   PHOTO:
   - EXCLUDED: photo_asset_id IS NULL
   - INCLUDED: photo_asset_id IS NOT NULL
   - image_url is not used for library PHOTO rows.

   STICKER:
   - image_url stores the existing public catalog asset path.
   ========================================================= */
CREATE TABLE `diary_cover_library_elements` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `library_item_id` bigint NOT NULL,
  `snapshot_version` int unsigned NOT NULL DEFAULT 1,

  `element_type` varchar(10) NOT NULL,
  `text_content` text DEFAULT NULL,
  `image_url` varchar(255) DEFAULT NULL,
  `style_type` varchar(30) DEFAULT NULL,
  `color_type` varchar(20) DEFAULT NULL,
  `photo_style` varchar(20) DEFAULT NULL,
  `text_font` varchar(30) DEFAULT NULL,
  `text_color` varchar(7) DEFAULT NULL,

  `photo_share_mode` varchar(20) DEFAULT NULL,
  `photo_asset_id` bigint DEFAULT NULL,

  `position_x` decimal(6,5) NOT NULL DEFAULT '0.00000',
  `position_y` decimal(6,5) NOT NULL DEFAULT '0.00000',
  `width` decimal(6,5) NOT NULL DEFAULT '0.30000',
  `height` decimal(6,5) NOT NULL DEFAULT '0.30000',
  `rotation` decimal(6,2) NOT NULL DEFAULT '0.00',
  `z_index` int NOT NULL DEFAULT 0,

  `created_at` timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `updated_at` timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
      ON UPDATE CURRENT_TIMESTAMP(6),

  PRIMARY KEY (`id`),

  KEY `idx_dcle_item_version_order`
      (`library_item_id`, `snapshot_version`, `z_index`, `id`),

  KEY `idx_dcle_photo_asset`
      (`library_item_id`, `photo_asset_id`),

  CONSTRAINT `fk_dcle_library_item`
    FOREIGN KEY (`library_item_id`)
    REFERENCES `diary_cover_library_items` (`id`)
    ON DELETE CASCADE,

  /* A PHOTO asset must belong to this same library item. */
  CONSTRAINT `fk_dcle_photo_asset`
    FOREIGN KEY (`library_item_id`, `photo_asset_id`)
    REFERENCES `diary_cover_library_photo_assets`
               (`library_item_id`, `id`)
    ON DELETE RESTRICT,

  CONSTRAINT `chk_dcle_type`
    CHECK (
      `element_type` IN ('PHOTO', 'STICKER', 'NOTE', 'TEXT')
    ),

  CONSTRAINT `chk_dcle_snapshot_version`
    CHECK (`snapshot_version` >= 1),

  CONSTRAINT `chk_dcle_payload`
    CHECK (
      (
        `element_type` = 'PHOTO'
        AND `text_content` IS NULL
        AND `image_url` IS NULL
        AND `style_type` IS NULL
        AND `color_type` IS NULL
        AND `photo_style` IS NOT NULL
        AND `photo_style` IN ('FULL', 'POLAROID')
        AND `text_font` IS NULL
        AND `text_color` IS NULL
        AND `photo_share_mode` IS NOT NULL
        AND (
          (
            `photo_share_mode` = 'EXCLUDED'
            AND `photo_asset_id` IS NULL
          )
          OR
          (
            `photo_share_mode` = 'INCLUDED'
            AND `photo_asset_id` IS NOT NULL
          )
        )
      )
      OR
      (
        `element_type` = 'STICKER'
        AND `image_url` IS NOT NULL
        AND `text_content` IS NULL
        AND `style_type` IS NULL
        AND `color_type` IS NULL
        AND `photo_style` IS NULL
        AND `text_font` IS NULL
        AND `text_color` IS NULL
        AND `photo_share_mode` IS NULL
        AND `photo_asset_id` IS NULL
      )
      OR
      (
        `element_type` = 'NOTE'
        AND `text_content` IS NOT NULL
        AND `image_url` IS NULL
        AND `style_type` IS NOT NULL
        AND `photo_style` IS NULL
        AND `text_font` IS NULL
        AND `text_color` IS NULL
        AND `photo_share_mode` IS NULL
        AND `photo_asset_id` IS NULL
      )
      OR
      (
        `element_type` = 'TEXT'
        AND `text_content` IS NOT NULL
        AND `image_url` IS NULL
        AND `style_type` IS NULL
        AND `color_type` IS NULL
        AND `photo_style` IS NULL
        AND `photo_share_mode` IS NULL
        AND `photo_asset_id` IS NULL
      )
    ),

  CONSTRAINT `chk_dcle_position`
    CHECK (
      `position_x` BETWEEN -0.5 AND 1.5
      AND `position_y` BETWEEN -0.5 AND 1.5
    ),

  CONSTRAINT `chk_dcle_size`
    CHECK (
      `width` > 0
      AND `width` <= 1
      AND `height` > 0
      AND `height` <= 1
    ),

  CONSTRAINT `chk_dcle_rotation`
    CHECK (`rotation` BETWEEN -360 AND 360),

  CONSTRAINT `chk_dcle_z_index`
    CHECK (`z_index` >= 0)

) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_general_ci;


/* =========================================================
   4. Unique-member successful-download history

   MySQL permits multiple NULL values in this UNIQUE key.
   Thus, account deletion can SET NULL without losing a past
   unique-member row or colliding with other deleted members.
   ========================================================= */
CREATE TABLE `diary_cover_library_downloads` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `library_item_id` bigint NOT NULL,
  `downloader_user_id` bigint DEFAULT NULL,
  `first_downloaded_at` timestamp(6) NOT NULL
      DEFAULT CURRENT_TIMESTAMP(6),

  PRIMARY KEY (`id`),

  UNIQUE KEY `uq_dcld_item_downloader`
      (`library_item_id`, `downloader_user_id`),

  KEY `idx_dcld_item_first`
      (`library_item_id`, `first_downloaded_at`, `id`),

  KEY `idx_dcld_downloader`
      (`downloader_user_id`, `first_downloaded_at` DESC, `id`),

  CONSTRAINT `fk_dcld_library_item`
    FOREIGN KEY (`library_item_id`)
    REFERENCES `diary_cover_library_items` (`id`)
    ON DELETE RESTRICT,

  CONSTRAINT `fk_dcld_downloader`
    FOREIGN KEY (`downloader_user_id`)
    REFERENCES `users` (`id`)
    ON DELETE SET NULL

) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_general_ci;


/* =========================================================
   5. Downloaded personal design provenance
   ========================================================= */
ALTER TABLE `diary_cover_designs`
  ADD COLUMN `source_library_item_id` bigint DEFAULT NULL
      AFTER `user_id`,

  ADD KEY `idx_diary_cover_designs_source_library`
      (`source_library_item_id`),

  ADD CONSTRAINT `fk_diary_cover_designs_source_library`
    FOREIGN KEY (`source_library_item_id`)
    REFERENCES `diary_cover_library_items` (`id`)
    ON DELETE SET NULL;


/* =========================================================
   6. Personal cover design PHOTO source extension

   Allowed PHOTO states:
   A. Personal: image_url NOT NULL, library_photo_asset_id NULL
   B. Included shared asset: image_url NULL, library_photo_asset_id NOT NULL
   C. Excluded placeholder: image_url NULL, library_photo_asset_id NULL

   Existing payload restrictions for STICKER, NOTE, and TEXT remain.
   Existing payload scope for color_type, photo_style, text_font, and
   text_color is unchanged.
   ========================================================= */
ALTER TABLE `diary_cover_design_elements`
  ADD COLUMN `library_photo_asset_id` bigint DEFAULT NULL
      AFTER `image_url`,

  ADD KEY `idx_diary_cover_design_elements_library_asset`
      (`library_photo_asset_id`),

  ADD CONSTRAINT `fk_dcde_library_photo_asset`
    FOREIGN KEY (`library_photo_asset_id`)
    REFERENCES `diary_cover_library_photo_assets` (`id`)
    ON DELETE RESTRICT,

  DROP CHECK `chk_diary_cover_design_elements_payload`,

  ADD CONSTRAINT `chk_diary_cover_design_elements_payload`
    CHECK (
      (
        `element_type` = 'PHOTO'
        AND `text_content` IS NULL
        AND `style_type` IS NULL
        AND (
          (
            `image_url` IS NOT NULL
            AND `library_photo_asset_id` IS NULL
          )
          OR
          (
            `image_url` IS NULL
            AND `library_photo_asset_id` IS NOT NULL
          )
          OR
          (
            `image_url` IS NULL
            AND `library_photo_asset_id` IS NULL
          )
        )
      )
      OR
      (
        `element_type` = 'STICKER'
        AND `image_url` IS NOT NULL
        AND `library_photo_asset_id` IS NULL
        AND `text_content` IS NULL
        AND `style_type` IS NULL
      )
      OR
      (
        `element_type` = 'NOTE'
        AND `text_content` IS NOT NULL
        AND `image_url` IS NULL
        AND `library_photo_asset_id` IS NULL
        AND `style_type` IS NOT NULL
      )
      OR
      (
        `element_type` = 'TEXT'
        AND `text_content` IS NOT NULL
        AND `image_url` IS NULL
        AND `library_photo_asset_id` IS NULL
        AND `style_type` IS NULL
      )
    ),

  ADD CONSTRAINT `chk_dcde_library_asset_usage`
    CHECK (
      `library_photo_asset_id` IS NULL
      OR (
        `element_type` = 'PHOTO'
        AND `image_url` IS NULL
      )
    );


/* =========================================================
   7. Applied diary-cover PHOTO source extension

   Uses the same three PHOTO states and preserves the existing
   STICKER, NOTE, and TEXT payload constraints.
   ========================================================= */
ALTER TABLE `diary_cover_elements`
  ADD COLUMN `library_photo_asset_id` bigint DEFAULT NULL
      AFTER `image_url`,

  ADD KEY `idx_diary_cover_elements_library_asset`
      (`library_photo_asset_id`),

  ADD CONSTRAINT `fk_dce_library_photo_asset`
    FOREIGN KEY (`library_photo_asset_id`)
    REFERENCES `diary_cover_library_photo_assets` (`id`)
    ON DELETE RESTRICT,

  DROP CHECK `chk_diary_cover_elements_payload`,

  ADD CONSTRAINT `chk_diary_cover_elements_payload`
    CHECK (
      (
        `element_type` = 'PHOTO'
        AND `text_content` IS NULL
        AND `style_type` IS NULL
        AND (
          (
            `image_url` IS NOT NULL
            AND `library_photo_asset_id` IS NULL
          )
          OR
          (
            `image_url` IS NULL
            AND `library_photo_asset_id` IS NOT NULL
          )
          OR
          (
            `image_url` IS NULL
            AND `library_photo_asset_id` IS NULL
          )
        )
      )
      OR
      (
        `element_type` = 'STICKER'
        AND `image_url` IS NOT NULL
        AND `library_photo_asset_id` IS NULL
        AND `text_content` IS NULL
        AND `style_type` IS NULL
      )
      OR
      (
        `element_type` = 'NOTE'
        AND `text_content` IS NOT NULL
        AND `image_url` IS NULL
        AND `library_photo_asset_id` IS NULL
        AND `style_type` IS NOT NULL
      )
      OR
      (
        `element_type` = 'TEXT'
        AND `text_content` IS NOT NULL
        AND `image_url` IS NULL
        AND `library_photo_asset_id` IS NULL
        AND `style_type` IS NULL
      )
    ),

  ADD CONSTRAINT `chk_dce_library_asset_usage`
    CHECK (
      `library_photo_asset_id` IS NULL
      OR (
        `element_type` = 'PHOTO'
        AND `image_url` IS NULL
      )
    );
