package com.example.travlediary.repository;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class HomeDestinationRecommendMapperContractTest {

    @Test
    void homeRecommendationQueriesExposeStableRegionIdsWithoutChangingSelectionRules()
            throws IOException {
        String popular = resource("/mapper/PopularRecommendMapper.xml");
        String seasonal = resource("/mapper/DestinationRecommendMapper.xml");

        assertThat(popular)
                .contains("property=\"regionId\" column=\"region_id\"")
                .contains("d.region_id AS region_id")
                .contains("ORDER BY (d.views +")
                .contains("ORDER BY RAND()")
                .contains("LIMIT #{limit}");
        assertThat(seasonal)
                .contains("property=\"regionId\"")
                .contains("column=\"region_id\"")
                .contains("d.region_id AS region_id")
                .contains("WHERE d.season = #{season}")
                .contains("ORDER BY RAND()")
                .contains("LIMIT #{limit}");
    }

    @Test
    void homeLandmarksUseTheCategoryNameAStableOrderAndOnlyDestinationsWithAMainPhoto()
            throws IOException {
        String seasonal = resource("/mapper/DestinationRecommendMapper.xml");
        int start = seasonal.indexOf("<select id=\"findByCategoryName\"");
        assertThat(start).isNotNegative();
        String landmarks = seasonal.substring(start, seasonal.indexOf("</select>", start));

        assertThat(landmarks)
                .contains("c.name = #{categoryName}")
                .contains("JOIN destination_translations dt ON d.id = dt.destination_id AND dt.language_code = 'ko'")
                .contains("pcc.region_name AS parent_region_name")
                .contains("di.is_main = 1 AND TRIM(di.image_url) &lt;&gt; ''")
                // 인기 여행지 추천과 같은 점수, 같으면 id 순. 요청마다 바뀌는 순서는 쓰지 않는다.
                .contains("ORDER BY (d.views + (")
                .contains("d.id ASC")
                .contains("LIMIT #{limit}")
                .doesNotContain("RAND()");
    }

    private String resource(String path) throws IOException {
        try (InputStream input = getClass().getResourceAsStream(path)) {
            assertThat(input).as(path).isNotNull();
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
