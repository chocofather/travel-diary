package com.example.travlediary.service.category;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Supplier;

/**
 * 국가·지역 기준 데이터를 요청 사이에도 들고 있는 자리.
 *
 * <p>이 표는 기동할 때 JSON 으로 채우고 그 뒤로는 관리자가 아이콘을 바꿀 때 말고는 변하지 않는다.
 * 반면 여행지 목록 한 번을 그리는 데 지역 조회가 수십 번 일어난다. 읽기만 하는 기준 자료라
 * 계정 상태처럼 요청마다 다시 확인해야 하는 값과 성격이 다르다.
 *
 * <p>담는 양은 표 크기를 넘지 않는다. 열쇠가 지역 번호나 (부모, 깊이) 조합이라
 * 아무리 많아도 지역 수만큼이고, 지금은 500건이 되지 않는다. 그래서 내보내는 규칙을 두지 않는다.
 *
 * <p><b>자료를 바꾸는 쪽은 반드시 {@link #invalidate()} 를 부른다.</b> 지금 바꾸는 길은 둘뿐이다 —
 * 기동 때의 기준 데이터 적재와 관리자 아이콘 올리기. 나중에 지역을 고치거나 지우는 길이 생기면
 * 거기서도 불러야 한다. 부르지 않으면 바꾼 내용이 재시작 전까지 보이지 않는다.
 */
@Component
public class CountryCategoryCache {

    private final ConcurrentMap<Key, Object> entries = new ConcurrentHashMap<>();

    /**
     * 들고 있으면 그것을 주고, 없으면 읽어서 들고 있는다.
     *
     * <p>읽는 동안 자물쇠를 걸지 않는다. 드물게 두 번 읽을 수는 있어도 같은 값이라 문제가 없고,
     * DB 를 부르는 동안 다른 요청이 이 자리에서 멈추는 편이 더 나쁘다.
     *
     * <p>없는 지역을 물어 {@code null} 이 오면 들고 있지 않는다. 그런 조회는 드물고,
     * 그 사이에 지역이 생기면 바로 보여야 하기 때문이다.
     */
    @SuppressWarnings("unchecked")
    <T> T read(Key key, Supplier<T> loader) {
        Object cached = entries.get(key);
        if (cached != null) {
            return (T) cached;
        }
        T loaded = loader.get();
        if (loaded != null) {
            entries.putIfAbsent(key, loaded);
        }
        return loaded;
    }

    /**
     * 목록을 들고 있을 때는 바꿀 수 없는 형태로 바꿔 둔다.
     *
     * <p>Mapper 가 돌려주는 목록은 수정할 수 있는 것이라, 받아 간 쪽이 정렬하거나 지우면
     * 들고 있던 값이 조용히 망가진다. 바꾸려 하면 그 자리에서 실패하는 편이 낫다.
     */
    <T> List<T> readList(Key key, Supplier<List<T>> loader) {
        return read(key, () -> {
            List<T> loaded = loader.get();
            return loaded == null ? null : List.copyOf(loaded);
        });
    }

    /** 들고 있던 것을 전부 버린다. 자료를 바꾼 쪽이 부른다. */
    public void invalidate() {
        entries.clear();
    }

    /** 지금 들고 있는 항목 수. 확인용이다. */
    int size() {
        return entries.size();
    }

    /** 어떤 조회였는지와 그 인자. 인자가 없는 조회는 이름만 쓴다. */
    record Key(String lookup, Object first, Object second) {

        static Key of(String lookup) {
            return new Key(lookup, null, null);
        }

        static Key of(String lookup, Object first) {
            return new Key(lookup, first, null);
        }

        static Key of(String lookup, Object first, Object second) {
            return new Key(lookup, first, second);
        }
    }

    /** 확인용. 지금 들고 있는 열쇠들. */
    Map<Key, Object> snapshot() {
        return Map.copyOf(entries);
    }
}
