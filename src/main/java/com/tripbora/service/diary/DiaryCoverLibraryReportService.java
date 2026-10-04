package com.tripbora.service.diary;

import com.tripbora.dto.DiaryCoverLibraryReportForm;

public interface DiaryCoverLibraryReportService {
    void submitReport(Long reporterUserId,
                      Long libraryItemId,
                      DiaryCoverLibraryReportForm form);
}
