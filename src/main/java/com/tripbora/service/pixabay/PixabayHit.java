package com.tripbora.service.pixabay;

import java.util.ArrayList;
import java.util.List;

/**
 * Pixabay 검색 응답의 사진 한 장. 응답에 있던 값만 담고 URL을 규칙으로 만들어 내지 않는다.
 * imageURL·fullHDURL 은 계정 권한(전체 API 접근)이 있을 때만 응답에 있으므로 없으면 null 이다.
 */
public record PixabayHit(long id,
                         String pageUrl,
                         String previewUrl,
                         String webformatUrl,
                         String largeImageUrl,
                         String fullHdUrl,
                         String imageUrl,
                         int imageWidth,
                         int imageHeight,
                         String user,
                         String tags) {

    /**
     * 저장용 이미지 후보. 응답에 실제로 있는 가장 좋은 것부터 imageURL → fullHDURL → largeImageURL 순서다.
     * previewURL·webformatURL 은 화면 미리보기 전용이라 저장 후보에 넣지 않는다.
     */
    public List<DownloadCandidate> downloadCandidates() {
        List<DownloadCandidate> candidates = new ArrayList<>();
        add(candidates, "imageURL", imageUrl);
        add(candidates, "fullHDURL", fullHdUrl);
        add(candidates, "largeImageURL", largeImageUrl);
        return List.copyOf(candidates);
    }

    /** 관리 화면 카드 미리보기. 검색 결과를 잠시 보여 주는 용도로만 쓴다(저장하지 않는다). */
    public String thumbnailUrl() {
        return webformatUrl != null ? webformatUrl : previewUrl;
    }

    private static void add(List<DownloadCandidate> candidates, String field, String url) {
        if (url != null && !url.isBlank()) {
            candidates.add(new DownloadCandidate(field, url.strip()));
        }
    }

    /** field 는 응답 필드 이름(imageURL 등). 어떤 판을 저장했는지 출처 기록에 남긴다. */
    public record DownloadCandidate(String field, String url) {
    }
}
