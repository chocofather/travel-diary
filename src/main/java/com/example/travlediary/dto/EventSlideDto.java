package com.example.travlediary.dto;

import com.example.travlediary.model.Event;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 메인 홈 슬라이더가 쓰는 값만 담은 응답.
 *
 * <p>슬라이더(/js/slider.js)는 제목·설명·대표 이미지와 상세 링크용 번호만 쓴다.
 * Event 를 그대로 내려보내면 등록자 번호(user_id)나 생성일처럼 화면이 쓰지 않는 값까지
 * 로그인 없이 볼 수 있게 되므로 여기서 잘라 낸다.
 *
 * <p>대표 이미지는 언어와 무관한 공용 이미지다. 슬라이더는 인포그래픽 포스터를 쓰지 않는다.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class EventSlideDto {
    private Long id;
    private String title;
    private String description;
    private String eventImg;

    /** 이미 언어 대체를 마친 표시용 이벤트에서 슬라이더가 쓸 값만 옮긴다. */
    public static EventSlideDto from(Event event) {
        return new EventSlideDto(
                event.getId(), event.getTitle(), event.getDescription(), event.getEventImg());
    }
}
