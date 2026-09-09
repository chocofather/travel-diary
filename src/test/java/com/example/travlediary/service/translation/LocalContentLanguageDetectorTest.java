package com.example.travlediary.service.translation;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LocalContentLanguageDetectorTest {

    private final LocalContentLanguageDetector detector = new LocalContentLanguageDetector();

    @Test
    void detectsEachSupportedLanguageAndLeavesAmbiguousTextUndetermined() {
        assertThat(detector.detect("제주도의 바다 풍경이 정말 아름다웠어요.").code()).isEqualTo("ko");
        assertThat(detector.detect("The ocean view from this quiet trail was beautiful.").code()).isEqualTo("en");
        assertThat(detector.detect("この場所から見える海の景色がとてもきれいでした。").code()).isEqualTo("ja");
        assertThat(detector.detect("这个地方的风景很好，值得推荐给朋友。").code()).isEqualTo("zh-CN");
        assertThat(detector.detect("這個地方的風景很好，值得推薦給朋友。").code()).isEqualTo("zh-TW");
        assertThat(detector.detect("OK").code()).isEqualTo("und");
        assertThat(detector.detect("123 !!!").code()).isEqualTo("und");
    }

    @Test
    void detectsShortHangulOnlyContentAsKorean() {
        assertThat(detector.detect("테스트").code()).isEqualTo("ko");
        assertThat(detector.detect("안녕").code()).isEqualTo("ko");
        assertThat(detector.detect("맛집").code()).isEqualTo("ko");
        assertThat(detector.detect("ㅋㅋ").code()).isEqualTo("ko");
        assertThat(detector.detect("ㅎㅎ").code()).isEqualTo("ko");
    }

    @Test
    void doesNotTreatMixedOrNonLetterContentAsShortKorean() {
        assertThat(detector.detect("A한").code()).isEqualTo("und");
        assertThat(detector.detect("123 !!! 😀").code()).isEqualTo("und");
    }
}
