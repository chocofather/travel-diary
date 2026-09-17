package com.example.travlediary.service.diary;

import com.example.travlediary.dto.DiaryCoverLibraryAssetFile;
import com.example.travlediary.dto.DiaryCoverLibraryDetailDto;
import com.example.travlediary.dto.DiaryCoverLibraryMineDto;
import com.example.travlediary.dto.DiaryCoverLibraryPageDto;
import com.example.travlediary.dto.DiaryCoverLibrarySort;

public interface DiaryCoverLibraryQueryService {

    DiaryCoverLibraryPageDto getPublishedPage(DiaryCoverLibrarySort sort, int page);

    DiaryCoverLibraryDetailDto getPublishedDetail(Long itemId);

    DiaryCoverLibraryMineDto getMine(Long userId);

    DiaryCoverLibraryAssetFile getActiveAsset(Long assetId);
}
