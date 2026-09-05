package com.example.travlediary.service.kto;

/**
 * 국문 축제 TourAPI 좌표.
 *
 * <p>외국어 축제 매칭에 쓸 좌표를 그때그때 복구해 넘기기 위한 값이며 저장하지 않는다.
 */
public record KtoFestivalCoordinates(String mapX, String mapY) {
}
