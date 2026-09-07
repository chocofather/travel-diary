package com.example.travlediary.repository;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 한국관광공사 API 인증키 설정 계약.
 *
 * <p>관광정보(TourAPI)와 관광사진은 같은 공공데이터포털 인증키를 쓴다.
 * 사진용 키를 따로 발급받은 환경만 KTO_PHOTO_API_KEY 로 덮어쓴다.
 * 인증키 값 자체는 환경변수에만 두고 Git 에 올리지 않는다.
 */
class KtoApiKeyConfigurationContractTest {

    @Test
    void photoApiFallsBackToTheSharedTourApiKeyFromTheEnvironment() throws IOException {
        String configuration = resource("application.yml");

        assertThat(configuration)
                .contains("api-key: ${KTO_PHOTO_API_KEY:${KTO_TOUR_API_KEY:}}")
                .contains("api-key: ${KTO_TOUR_API_KEY:}")
                .contains("base-url: https://apis.data.go.kr/B551011/PhotoGalleryService1")
                // 인증키 값은 설정 파일에 적지 않는다
                .doesNotContainPattern("(?m)^\\s*api-key: [^$\\s].*$");
    }

    private String resource(String path) throws IOException {
        try (var input = getClass().getClassLoader().getResourceAsStream(path)) {
            if (input == null) {
                throw new IOException("Missing test resource: " + path);
            }
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
