package com.example.travlediary.service.diary;

import org.springframework.stereotype.Service;

/** 현재 정책은 로그인 회원 모두 허용하며, 향후 PREMIUM 정책은 이 구현만 교체한다. */
@Service
public class CoverLibraryAccessServiceImpl implements CoverLibraryAccessService {

    @Override
    public boolean canDownload(Long userId) {
        return userId != null;
    }
}
