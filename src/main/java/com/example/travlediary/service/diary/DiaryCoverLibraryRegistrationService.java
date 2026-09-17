package com.example.travlediary.service.diary;

import com.example.travlediary.dto.DiaryCoverLibraryRegistrationRequest;
import com.example.travlediary.model.DiaryCoverLibraryItem;

public interface DiaryCoverLibraryRegistrationService {

    DiaryCoverLibraryItem register(Long userId, DiaryCoverLibraryRegistrationRequest request);
}
