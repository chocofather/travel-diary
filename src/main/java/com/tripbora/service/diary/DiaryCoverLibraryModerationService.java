package com.tripbora.service.diary;

import com.tripbora.dto.DiaryCoverLibraryModerationForm;
import com.tripbora.dto.DiaryCoverLibraryReportDetailDto;
import com.tripbora.dto.DiaryCoverLibraryReportPageDto;
import com.tripbora.dto.DiaryCoverLibraryReportStatusFilter;
import com.tripbora.dto.DiaryCoverLibraryRestoreForm;

public interface DiaryCoverLibraryModerationService {
    DiaryCoverLibraryReportPageDto getReports(Long adminUserId,
                                              DiaryCoverLibraryReportStatusFilter filter,
                                              int page);

    DiaryCoverLibraryReportDetailDto getReportDetail(Long adminUserId, Long reportId);

    void process(Long adminUserId,
                 Long reportId,
                 DiaryCoverLibraryModerationForm form);

    Long restoreItem(Long adminUserId,
                     Long itemId,
                     DiaryCoverLibraryRestoreForm form);

    Long restorePhoto(Long adminUserId,
                      Long photoAssetId,
                      DiaryCoverLibraryRestoreForm form);
}
