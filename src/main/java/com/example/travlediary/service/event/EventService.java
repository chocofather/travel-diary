package com.example.travlediary.service.event;

import com.example.travlediary.model.Event;
import com.example.travlediary.repository.event.EventMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.util.List;

@Service
@RequiredArgsConstructor
public class EventService {

    private final EventMapper eventMapper;

    @Value("${custom.upload-path}")
    private String uploadDir;

    public List<Event> getSlideEvents() {
        return eventMapper.selectSlideEvents();
    }

    public void save(Event event) {
        eventMapper.insert(event);
    }

    @Transactional
    public void deleteEventById(Long id) {
        Event event = eventMapper.selectEventById(id);
        if (event != null) {
            // 1. 대표 이미지 삭제
            String imageUrl = event.getEventImg();
            if (imageUrl != null && !imageUrl.isBlank()) {
                String fullPath = uploadDir + File.separator + imageUrl.replace("/uploads/", "");
                File file = new File(fullPath);
                if (file.exists()) file.delete();
            }

            // 2. 포스터 이미지 삭제
            String posterUrl = event.getPosterImg();
            if (posterUrl != null && !posterUrl.isBlank()) {
                String fullPath = uploadDir + File.separator + posterUrl.replace("/uploads/", "");
                File file = new File(fullPath);
                if (file.exists()) file.delete();
            }

            // 3. DB에서 이벤트 삭제
            eventMapper.deleteEventById(id);
        }
    }


    public List<Event> selectAllEvents() {
        return eventMapper.selectAllEvents();
    }


    // 이벤트 리스트
    public List<Event> getEventsByStatus(String status) {
        if ("ongoing".equals(status)) return eventMapper.selectOngoingEvents();
        if ("upcoming".equals(status)) return eventMapper.selectUpcomingEvents();
        if ("ended".equals(status)) return eventMapper.selectEndedEvents();
        return eventMapper.selectAllEvents();
    }

    //이벤트 상세
    public Event getEventDetail(Long id) {
        return eventMapper.selectEventById(id);
    }

}
