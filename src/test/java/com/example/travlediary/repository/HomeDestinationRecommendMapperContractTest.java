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

    private String resource(String path) throws IOException {
        try (InputStream input = getClass().getResourceAsStream(path)) {
            assertThat(input).as(path).isNotNull();
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
