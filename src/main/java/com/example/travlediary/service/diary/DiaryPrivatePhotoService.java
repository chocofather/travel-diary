package com.example.travlediary.service.diary;

import com.example.travlediary.service.file.DiaryPrivatePhotoStorage;

/**
 * 통제된 개인 사진 응답이 쓰는 조회 경계.
 *
 * <p>순서는 어느 사진이든 같다 — DB 번호 → 현재 사용자 → 소유권 → 부모 관계 → PIN →
 * 저장 키 → private 저장소. 파일 이름이나 저장 키는 바깥에서 받지 않는다.
 */
public interface DiaryPrivatePhotoService {

    /** 다이어리 대표 이미지 (소유자 + PIN 해제) */
    DiaryPrivatePhotoStorage.StoredPhotoFile getCoverImage(Long diaryId, Long userId);

    /** 페이지에 붙인 사진 (소유자 + 부모 관계 + PIN 해제) */
    DiaryPrivatePhotoStorage.StoredPhotoFile getPageElementPhoto(
            Long diaryId, Long pageId, Long elementId, Long userId);

    /** 다이어리에 적용된 표지의 사진 (소유자 + 부모 관계 + PIN 해제) */
    DiaryPrivatePhotoStorage.StoredPhotoFile getCoverElementPhoto(
            Long diaryId, Long elementId, Long userId);

    /** 보관함 "내 표지 디자인"의 사진 (디자인 소유자만, PIN 무관) */
    DiaryPrivatePhotoStorage.StoredPhotoFile getCoverDesignElementPhoto(
            Long designId, Long elementId, Long userId);
}
