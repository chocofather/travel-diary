package com.example.travlediary.model;

/**
 * 되풀이해서 그리는 스티커(마스킹테이프)의 조각 경로.
 * 양끝(left/right)은 비율 그대로 두고 가운데(center)만 가로로 되풀이한다.
 *
 * 화면에 그릴 때만 쓰는 값이라 DB 에는 저장하지 않는다.
 * (요소에는 지금처럼 완성형 imageUrl 하나만 남고, 이 정보는 목록에서 다시 찾는다)
 */
public record DiaryStickerRepeat(String leftUrl, String centerUrl, String rightUrl) {
}
