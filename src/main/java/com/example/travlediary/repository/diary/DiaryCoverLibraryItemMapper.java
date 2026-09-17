package com.example.travlediary.repository.diary;

import com.example.travlediary.model.DiaryCoverLibraryItem;
import com.example.travlediary.model.DiaryCoverLibraryItemStatus;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.sql.Timestamp;
import java.util.List;

@Mapper
public interface DiaryCoverLibraryItemMapper {

    int insert(DiaryCoverLibraryItem item);

    DiaryCoverLibraryItem findById(@Param("itemId") Long itemId);

    DiaryCoverLibraryItem findByIdAndCreatorUserId(@Param("itemId") Long itemId,
                                                    @Param("creatorUserId") Long creatorUserId);

    int countPublished();

    List<DiaryCoverLibraryItem> findPublished(@Param("sort") String sort,
                                              @Param("offset") int offset,
                                              @Param("limit") int limit);

    List<DiaryCoverLibraryItem> findManageableByCreatorUserId(
            @Param("creatorUserId") Long creatorUserId);

    DiaryCoverLibraryItem findPublishedById(@Param("itemId") Long itemId);

    DiaryCoverLibraryItem findPublishedByIdForUpdate(@Param("itemId") Long itemId);

    int incrementDownloadCountIfPublished(@Param("itemId") Long itemId);

    DiaryCoverLibraryItem findByIdForUpdate(@Param("itemId") Long itemId);

    int withdrawByOwner(@Param("itemId") Long itemId,
                        @Param("creatorUserId") Long creatorUserId,
                        @Param("withdrawnAt") Timestamp withdrawnAt);

    int republishByOwner(@Param("itemId") Long itemId,
                         @Param("creatorUserId") Long creatorUserId);

    int softDeleteByOwner(@Param("itemId") Long itemId,
                          @Param("creatorUserId") Long creatorUserId,
                          @Param("expectedStatus") String expectedStatus,
                          @Param("deletedAt") Timestamp deletedAt);

    int blockByAdmin(@Param("itemId") Long itemId,
                     @Param("expectedStatus") DiaryCoverLibraryItemStatus expectedStatus,
                     @Param("blockedAt") Timestamp blockedAt,
                     @Param("blockedReason") String blockedReason);

    int restoreByAdmin(@Param("itemId") Long itemId,
                       @Param("restoredStatus") DiaryCoverLibraryItemStatus restoredStatus);
}
