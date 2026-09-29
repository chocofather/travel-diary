package com.example.travlediary.controller.destination;

import jakarta.servlet.http.HttpSession;
import org.springframework.web.util.WebUtils;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;

/**
 * 여행지 일별 조회 집계(destination_view_daily)의 세션 중복 판정.
 * 같은 세션에서 같은 여행지는 KST 하루 1회만 집계한다.
 *
 * 세션에는 '오늘 날짜 + 오늘 본 여행지 id' 한 묶음만 둔다. 날짜가 바뀌면 이전 묶음을 버리고 새로 시작하므로
 * 날짜별 기록이 쌓이지 않는다. (하루치 id 는 여행지 수를 넘지 않는다)
 */
final class DestinationDailyViewSession {

    static final String ATTRIBUTE = DestinationDailyViewSession.class.getName() + ".VIEWED_TODAY";

    private DestinationDailyViewSession() {
    }

    /**
     * 이 세션에서 today 에 처음 본 여행지면 기록하고 true, 이미 봤으면 false.
     * 같은 세션의 동시 요청이 둘 다 '처음'으로 판정되지 않게 세션 mutex 안에서 확인과 기록을 함께 한다.
     */
    static boolean markFirstView(HttpSession session, Long destinationId, LocalDate today) {
        synchronized (WebUtils.getSessionMutex(session)) {
            ViewedToday viewed = session.getAttribute(ATTRIBUTE) instanceof ViewedToday current
                    && today.equals(current.date)
                    ? current
                    : new ViewedToday(today);
            boolean first = viewed.destinationIds.add(destinationId);
            // 세션 저장소가 바뀐 값을 알도록 매번 다시 넣는다.
            session.setAttribute(ATTRIBUTE, viewed);
            return first;
        }
    }

    static final class ViewedToday implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

        private final LocalDate date;
        private final Set<Long> destinationIds = new HashSet<>();

        private ViewedToday(LocalDate date) {
            this.date = date;
        }

        LocalDate date() {
            return date;
        }

        Set<Long> destinationIds() {
            return Set.copyOf(destinationIds);
        }
    }
}
