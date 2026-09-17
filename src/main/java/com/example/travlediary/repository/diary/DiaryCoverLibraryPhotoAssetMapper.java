package com.example.travlediary.repository.diary;

import com.example.travlediary.model.DiaryCoverLibraryPhotoAsset;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.sql.Timestamp;
import java.util.List;

@Mapper
public interface DiaryCoverLibraryPhotoAssetMapper {

    int insert(DiaryCoverLibraryPhotoAsset asset);

    DiaryCoverLibraryPhotoAsset findById(@Param("assetId") Long assetId);

    List<DiaryCoverLibraryPhotoAsset> findAllByLibraryItemIdAndSnapshotVersion(
            @Param("libraryItemId") Long libraryItemId,
            @Param("snapshotVersion") Integer snapshotVersion);

    List<DiaryCoverLibraryPhotoAsset> findAllByLibraryItemIdAndSnapshotVersionForUpdate(
            @Param("libraryItemId") Long libraryItemId,
            @Param("snapshotVersion") Integer snapshotVersion);

    DiaryCoverLibraryPhotoAsset findByIdForUpdate(@Param("assetId") Long assetId);

    int blockByAdmin(@Param("assetId") Long assetId,
                     @Param("blockedAt") Timestamp blockedAt,
                     @Param("blockedReason") String blockedReason);

    int restoreByAdmin(@Param("assetId") Long assetId);
}
