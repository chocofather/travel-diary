package com.example.travlediary.service.diary;

import com.example.travlediary.dto.DiaryCoverLibraryDownloadResult;

public interface DiaryCoverLibraryDownloadService {

    /**
     * 공개된 라이브러리 표지를 회원의 보유 디자인으로 복사한다.
     * 직접 공유한 표지면 422(UNPROCESSABLE_ENTITY)로 거부한다.
     * 그 표지에서 받은 디자인을 지금 가지고 있으면 409(CONFLICT)로 거부한다. (지운 뒤에는 다시 받을 수 있다)
     */
    DiaryCoverLibraryDownloadResult download(Long userId, Long libraryItemId);
}
