package com.example.travlediary.service.diary;

import com.example.travlediary.model.DiaryCoverDesign;

public interface DiaryCoverLibraryDownloadService {

    DiaryCoverDesign download(Long userId, Long libraryItemId);
}
