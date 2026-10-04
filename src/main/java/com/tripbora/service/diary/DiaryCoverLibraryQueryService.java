package com.tripbora.service.diary;

import com.tripbora.dto.DiaryCoverLibraryAssetFile;
import com.tripbora.dto.DiaryCoverLibraryDetailDto;
import com.tripbora.dto.DiaryCoverLibraryMineDto;
import com.tripbora.dto.DiaryCoverLibraryPageDto;
import com.tripbora.dto.DiaryCoverLibrarySort;

public interface DiaryCoverLibraryQueryService {

    DiaryCoverLibraryPageDto getPublishedPage(DiaryCoverLibrarySort sort, int page);

    DiaryCoverLibraryDetailDto getPublishedDetail(Long itemId);

    DiaryCoverLibraryMineDto getMine(Long userId);

    DiaryCoverLibraryAssetFile getActiveAsset(Long assetId);
}
