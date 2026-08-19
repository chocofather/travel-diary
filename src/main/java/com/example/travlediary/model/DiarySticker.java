package com.example.travlediary.model;

/**
 * 공용 스티커 한 개. 목록은 resources/json/diary_stickers.json 이 관리한다.
 * 클라이언트는 id 만 보내고 실제 경로(imageUrl)는 서버가 이 목록에서 고른다.
 *
 * 파일은 사용자 업로드가 아니라 사이트 공용 정적 asset 이므로 요소를 지워도 파일은 남긴다.
 */
public record DiarySticker(String id, String name, String category, String imageUrl) {
}
