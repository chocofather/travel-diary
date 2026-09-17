package com.example.travlediary.repository.diary;

import com.example.travlediary.model.DiaryCoverLibraryElement;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface DiaryCoverLibraryElementMapper {

    int insert(DiaryCoverLibraryElement element);

    List<DiaryCoverLibraryElement> findAllByLibraryItemIdAndSnapshotVersion(
            @Param("libraryItemId") Long libraryItemId,
            @Param("snapshotVersion") Integer snapshotVersion);

    List<DiaryCoverLibraryElement> findAllByLibraryItemIds(
            @Param("libraryItemIds") List<Long> libraryItemIds);
}
