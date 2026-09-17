package com.example.travlediary.model;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.sql.Timestamp;

@Data
@NoArgsConstructor
public class DiaryCoverLibraryReport {
    private Long id;
    private Long libraryItemId;
    private Integer reportedSnapshotVersion;
    private Long photoAssetId;
    private Long reporterUserId;
    private DiaryCoverLibraryReportReason reasonCode;
    private String description;
    private DiaryCoverLibraryReportStatus status;
    private DiaryCoverLibraryResolutionAction resolutionAction;
    private Long processedByUserId;
    private Timestamp processedAt;
    private String adminNote;
    private Long targetPhotoAssetKey;
    private Timestamp createdAt;
    private Timestamp updatedAt;
}
