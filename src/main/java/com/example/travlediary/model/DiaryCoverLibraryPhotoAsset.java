package com.example.travlediary.model;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.sql.Timestamp;

@Data
@NoArgsConstructor
public class DiaryCoverLibraryPhotoAsset {

    private Long id;
    private Long libraryItemId;
    private Long originalUploaderUserId;
    private String originalUploaderDisplayName;
    private Integer snapshotVersion;
    private String storageKey;
    private String contentType;
    private Long fileSize;
    private DiaryCoverLibraryPhotoAssetStatus status;
    private Timestamp rightsConfirmedAt;
    private String rightsTermsVersion;
    private Timestamp blockedAt;
    private String blockedReason;
    private Timestamp retiredAt;
    private Timestamp createdAt;
    private Timestamp updatedAt;
}
