package com.example.travlediary.dto;

import com.example.travlediary.model.DiaryCoverLibraryReportReason;
import com.example.travlediary.model.DiaryCoverLibraryReportStatus;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.sql.Timestamp;

@Data
@NoArgsConstructor
public class DiaryCoverLibraryReportListItemDto {
    private Long reportId;
    private Long libraryItemId;
    private Integer reportedSnapshotVersion;
    private Long photoAssetId;
    private String libraryTitle;
    private String creatorDisplayName;
    private DiaryCoverLibraryReportReason reasonCode;
    private String reporterDisplayName;
    private DiaryCoverLibraryReportStatus status;
    private Timestamp createdAt;

    public String getTargetTypeLabel() {
        return photoAssetId == null ? "표지" : "사진";
    }
}
