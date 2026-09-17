package com.example.travlediary.service.diary;

/** 표지 라이브러리의 다운로드 자격을 한 곳에서 판단하는 경계다. */
public interface CoverLibraryAccessService {

    boolean canDownload(Long userId);
}
