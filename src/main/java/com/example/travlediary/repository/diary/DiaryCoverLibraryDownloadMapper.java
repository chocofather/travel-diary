package com.example.travlediary.repository.diary;

import com.example.travlediary.model.DiaryCoverLibraryDownload;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface DiaryCoverLibraryDownloadMapper {

    int insertIgnore(DiaryCoverLibraryDownload download);

    DiaryCoverLibraryDownload findByLibraryItemIdAndDownloaderUserId(
            @Param("libraryItemId") Long libraryItemId,
            @Param("downloaderUserId") Long downloaderUserId);
}
