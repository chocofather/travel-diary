package com.tripbora.repository.diary;

import com.tripbora.dto.DiaryCoverLibraryReportListItemDto;
import com.tripbora.model.DiaryCoverLibraryReport;
import com.tripbora.model.DiaryCoverLibraryReportStatus;
import com.tripbora.model.DiaryCoverLibraryResolutionAction;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.sql.Timestamp;
import java.util.List;

@Mapper
public interface DiaryCoverLibraryReportMapper {
    int insert(DiaryCoverLibraryReport report);

    DiaryCoverLibraryReport findById(@Param("reportId") Long reportId);

    DiaryCoverLibraryReport findByIdForUpdate(@Param("reportId") Long reportId);

    int countForAdmin(@Param("status") DiaryCoverLibraryReportStatus status);

    List<DiaryCoverLibraryReportListItemDto> findForAdmin(
            @Param("status") DiaryCoverLibraryReportStatus status,
            @Param("offset") int offset,
            @Param("limit") int limit);

    int markProcessed(@Param("reportId") Long reportId,
                      @Param("status") DiaryCoverLibraryReportStatus status,
                      @Param("resolutionAction") DiaryCoverLibraryResolutionAction resolutionAction,
                      @Param("processedByUserId") Long processedByUserId,
                      @Param("processedAt") Timestamp processedAt,
                      @Param("adminNote") String adminNote);
}
