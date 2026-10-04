package com.tripbora.service.destination;

import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * 관리 화면의 나눠 올리기에서 "이 사진은 이미 저장했다"는 영수증.
 *
 * <p>화면은 사진마다 업로드 키를 하나 정해 두고, 실패한 사진만 같은 키로 다시 보낸다.
 * 서버가 저장을 마친 뒤 응답이 끊겨 화면이 실패로 본 경우에도, 같은 키가 다시 오면 새로 저장하지 않고
 * 앞서 저장한 이미지 번호를 돌려준다. (DB 구조는 바꾸지 않는다 — 서버 메모리에만 잠시 둔다)
 *
 * <p>재시도는 같은 화면에서 곧바로 일어나므로 몇 시간만 기억한다. 서버가 재시작되면 잊는다.
 */
@Component
public class DestinationImageUploadReceipts {

    static final int MAX_RECEIPTS = 5000;
    static final Duration RETENTION = Duration.ofHours(6);

    /** 화면이 만드는 키(UUID 등). 모양이 다르면 영수증을 쓰지 않고 평소처럼 저장한다. */
    private static final Pattern UPLOAD_KEY = Pattern.compile("[A-Za-z0-9-]{8,64}");

    private final Clock clock;
    private final Map<String, Receipt> receipts = new LinkedHashMap<>() {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Receipt> eldest) {
            return size() > MAX_RECEIPTS;
        }
    };

    public DestinationImageUploadReceipts() {
        this(Clock.systemUTC());
    }

    DestinationImageUploadReceipts(Clock clock) {
        this.clock = clock;
    }

    /** 같은 여행지·같은 업로드 키로 이미 저장한 이미지 번호. */
    public synchronized Optional<Long> savedImageId(Long destinationId, String uploadKey) {
        String key = key(destinationId, uploadKey);
        if (key == null) {
            return Optional.empty();
        }
        Receipt receipt = receipts.get(key);
        if (receipt == null) {
            return Optional.empty();
        }
        if (receipt.savedAt().plus(RETENTION).isBefore(clock.instant())) {
            receipts.remove(key);
            return Optional.empty();
        }
        return Optional.of(receipt.imageId());
    }

    public synchronized void remember(Long destinationId, String uploadKey, Long imageId) {
        String key = key(destinationId, uploadKey);
        if (key != null && imageId != null) {
            receipts.put(key, new Receipt(imageId, clock.instant()));
        }
    }

    private static String key(Long destinationId, String uploadKey) {
        if (destinationId == null || uploadKey == null || !UPLOAD_KEY.matcher(uploadKey).matches()) {
            return null;
        }
        return destinationId + ":" + uploadKey;
    }

    private record Receipt(Long imageId, Instant savedAt) {
    }
}
