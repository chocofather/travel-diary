package com.example.travlediary.model;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.sql.Timestamp;

@Data
@NoArgsConstructor
public class DiaryCoverLibraryItem {

    private Long id;
    private Long creatorUserId;
    private String creatorDisplayName;
    private Long sourceCoverDesignId;
    private String title;
    private String description;
    private String baseCoverStyle;
    private String backgroundColor;
    private DiaryCoverLibraryItemStatus status;
    private Long downloadCount;
    private Integer snapshotVersion;
    private Timestamp publishedAt;
    private Timestamp withdrawnAt;
    private Timestamp deletedAt;
    private Timestamp blockedAt;
    private String blockedReason;
    private Timestamp createdAt;
    private Timestamp updatedAt;

    public String getBaseCoverStyleClass() {
        return DiaryCoverStyle.toCssClass(baseCoverStyle);
    }
}
