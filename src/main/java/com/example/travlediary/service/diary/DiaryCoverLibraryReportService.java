package com.example.travlediary.service.diary;

import com.example.travlediary.dto.DiaryCoverLibraryReportForm;

public interface DiaryCoverLibraryReportService {
    void submitReport(Long reporterUserId,
                      Long libraryItemId,
                      DiaryCoverLibraryReportForm form);
}
