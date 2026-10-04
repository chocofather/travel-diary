package com.tripbora.dto;

import com.tripbora.model.Event;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 메인에 나가는 이벤트 한 장에 필요한 값만 담는다.
 * 메인 이벤트 프로모션 배너(HomeController → home.html)와 예전 슬라이더 API(/api/events/slide)가 쓴다.
 *
 * <p>제목·설명·대표 이미지와 상세 링크용 번호만 쓴다.
 * Event 를 그대로 내보내면 등록자 번호(user_id)나 생성일처럼 화면이 쓰지 않는 값까지
 * 로그인 없이 볼 수 있게 되므로 여기서 잘라 낸다.
 *
 * <p>대표 이미지는 언어와 무관한 공용 이미지다. 메인은 인포그래픽 포스터를 쓰지 않는다.
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
