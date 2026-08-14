package com.example.travlediary.repository.event;

import com.example.travlediary.model.Event;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface EventMapper {
    List<Event> selectSlideEvents();

    /** 새 이벤트 저장 */
    int insert(Event event);     // 성공 시 1 반환

    int updateEvent(Event event);

    void deleteEventById(Long id);

    List<Event> selectAllEvents(); // 모든 이벤트
    List<Event> selectOngoingEvents(); // 진행중
    List<Event> selectUpcomingEvents(); // 진행예정
    List<Event> selectEndedEvents(); // 종료된
    Event selectEventById(Long id); // 이벤트 단일조회


}
