package com.example.travlediary.service.diary;

import jakarta.servlet.http.HttpSession;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 가져오기 한 번을 가리키는 표.
 *
 * <p>확인 화면을 열 때 한 장 끊어 주고, 저장 요청이 그 표를 들고 온다.
 * 같은 표로 두 번 저장되지 않게 하는 것이 목적이다. 버튼을 잠그는 것만으로는
 * 새로고침이나 빠른 두 번 누르기를 막지 못한다.
 *
 * <p>표는 세션 안에만 있다. 다른 사람의 세션에는 없으므로 남의 표로 저장할 수 없고,
 * 로그아웃하면 함께 사라진다. 새 테이블을 만들지 않는다.
 *
 * <p>이미 저장을 마친 표는 그 결과(새 여행일기 번호)를 기억해 둔다. 저장은 끝났는데
 * 응답이 도중에 끊겨 브라우저가 다시 보내는 경우, 한 권을 더 만들지 않고 그때의 결과를 돌려주기 위해서다.
 */
public final class GuestDiaryImportTokens {

    /** 세션에 표를 담아 두는 자리. */
    static final String SESSION_ATTRIBUTE = "guestDiaryImportTokens";
    /** 기억해 둘 표의 개수. 한 사람이 잇달아 여러 번 시도해도 넘칠 일이 없는 정도다. */
    private static final int MAX_TOKENS = 8;
    /** 아직 저장하지 않은 표의 표시. */
    private static final Long PENDING = null;

    private GuestDiaryImportTokens() {
    }

    /** 확인 화면에서 한 장 끊어 준다. */
    public static String issue(HttpSession session) {
        String token = UUID.randomUUID().toString();
        tokens(session).put(token, PENDING);
        return token;
    }

    /**
     * 저장 요청이 들고 온 표를 확인한다.
     *
     * @return 이 세션에서 끊어 준 표가 맞으면 true
     */
    public static boolean isIssued(HttpSession session, String token) {
        return token != null && tokens(session).containsKey(token);
    }

    /**
     * 이 표로 이미 저장을 마쳤다면 그때 만들어진 여행일기 번호.
     * 아직 저장하지 않았으면 비어 있다.
     */
    public static Long completedDiaryId(HttpSession session, String token) {
        return token == null ? null : tokens(session).get(token);
    }

    /** 저장을 마쳤다고 적어 둔다. 같은 표가 다시 오면 이 번호를 돌려준다. */
    public static void complete(HttpSession session, String token, Long diaryId) {
        if (token == null) {
            return;
        }
        tokens(session).put(token, diaryId);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Long> tokens(HttpSession session) {
        Object saved = session.getAttribute(SESSION_ATTRIBUTE);
        if (saved instanceof Map) {
            return (Map<String, Long>) saved;
        }
        // 오래된 표부터 밀어낸다. 세션이 끝없이 커지지 않게 한다.
        Map<String, Long> created = new LinkedHashMap<>() {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, Long> eldest) {
                return size() > MAX_TOKENS;
            }
        };
        session.setAttribute(SESSION_ATTRIBUTE, created);
        return created;
    }
}
