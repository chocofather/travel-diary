package com.example.travlediary.service.diary;

import com.example.travlediary.dto.DiaryCoverLibraryModerationForm;
import com.example.travlediary.dto.DiaryCoverLibraryReportDetailDto;
import com.example.travlediary.dto.DiaryCoverLibraryReportPageDto;
import com.example.travlediary.dto.DiaryCoverLibraryReportStatusFilter;
import com.example.travlediary.dto.DiaryCoverLibraryRestoreForm;

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
