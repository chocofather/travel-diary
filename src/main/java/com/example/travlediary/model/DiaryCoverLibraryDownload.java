package com.example.travlediary.model;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.sql.Timestamp;

@Data
@NoArgsConstructor
public class DiaryCoverLibraryDownload {

    private Long id;
    private Long libraryItemId;
    private Long downloaderUserId;
    private Timestamp firstDownloadedAt;
}
