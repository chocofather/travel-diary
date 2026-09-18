package com.example.travlediary.service.diary;

import com.example.travlediary.dto.DiaryCoverLibraryRegistrationRequest;
import com.example.travlediary.model.DiaryCoverLibraryItem;

public interface DiaryCoverLibraryRegistrationService {

    /** 라이브러리에서 받은 디자인(source_library_item_id 가 있는 디자인)을 공유하려 할 때의 안내 */
    String LIBRARY_SOURCED_SHARE_MESSAGE = "라이브러리에서 받은 디자인은 다시 공유할 수 없습니다.";

    /**
     * 내가 직접 만든 표지 디자인을 라이브러리에 공유한다.
     * 라이브러리에서 받은 디자인이면 409(CONFLICT)로 거부한다.
     */
    DiaryCoverLibraryItem register(Long userId, DiaryCoverLibraryRegistrationRequest request);
}
