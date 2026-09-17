package com.example.travlediary.repository.diary;

import com.example.travlediary.model.DiaryCoverLibraryModerationAction;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface DiaryCoverLibraryModerationActionMapper {
    int insert(DiaryCoverLibraryModerationAction action);

    DiaryCoverLibraryModerationAction findLatestBlockItemByLibraryItemId(
            @Param("libraryItemId") Long libraryItemId);

    DiaryCoverLibraryModerationAction findLatestBlockPhotoByPhotoAssetId(
            @Param("photoAssetId") Long photoAssetId);
}
