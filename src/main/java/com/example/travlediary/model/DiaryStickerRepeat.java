package com.example.travlediary.model;

/**
 * 되풀이해서 그리는 스티커(마스킹테이프)의 조각 경로.
 * 양끝(left/right)은 비율 그대로 두고 가운데(center)만 가로로 되풀이한다.
 *
 * 요소 테이블에는 저장하지 않고 카탈로그에서 다시 찾는다.
 * (요소에는 지금처럼 완성형 imageUrl 하나만 남는다)
 */
public record DiaryStickerRepeat(String leftUrl, String centerUrl, String rightUrl) {
}
