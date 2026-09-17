package com.example.travlediary.model;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.sql.Timestamp;

@Data
@NoArgsConstructor
public class DiaryCoverLibraryModerationAction {
    private Long id;
    private Long reportId;
    private Long libraryItemId;
    private Long photoAssetId;
    private DiaryCoverLibraryModerationActionType actionType;
    private String previousStatus;
    private String resultingStatus;
    private String reason;
    private String adminNote;
    private Long actionByUserId;
    private Timestamp createdAt;
}
