package com.example.travlediary.repository;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class FestivalInfoMapperContractTest {

    @Test
    void resultMapIncludesEveryFestivalInfoColumn() throws IOException {
        String mapper = mapper();
        String resultMap = between(mapper, "<resultMap id=\"FestivalInfoResultMap\"", "</resultMap>");

        assertThat(resultMap)
                .contains("property=\"infoId\" column=\"info_id\"")
                .contains("property=\"eventPlace\" column=\"event_place\"")
                .contains("property=\"address\" column=\"address\"")
                .contains("property=\"playTime\" column=\"play_time\"")
                .contains("property=\"useTime\" column=\"use_time\"")
                .contains("property=\"sponsor1\" column=\"sponsor1\"")
                .contains("property=\"sponsor1Tel\" column=\"sponsor1_tel\"")
                .contains("property=\"sponsor2\" column=\"sponsor2\"")
                .contains("property=\"sponsor2Tel\" column=\"sponsor2_tel\"")
                .contains("property=\"contactTel\" column=\"contact_tel\"")
                .contains("property=\"homepageUrl\" column=\"homepage_url\"")
                .contains("property=\"sourceType\" column=\"source_type\"")
                .contains("property=\"externalContentId\" column=\"external_content_id\"")
                .contains("property=\"createdAt\" column=\"created_at\"")
                .contains("property=\"updatedAt\" column=\"updated_at\"");
    }

    @Test
    void findUsesFestivalInfoByInfoId() throws IOException {
        String query = between(mapper(), "<select id=\"findByInfoId\"", "</select>");

        assertThat(query)
                .contains("FROM festival_info")
                .contains("WHERE info_id = #{infoId}")
                .contains("source_type", "external_content_id")
                .doesNotContain("travel_info");
    }

    @Test
    void insertAndUpdatePersistSourceMetadata() throws IOException {
        String mapper = mapper();
        String insert = between(mapper, "<insert id=\"insert\"", "</insert>");
        String update = between(mapper, "<update id=\"update\"", "</update>");

        assertThat(insert)
                .contains("INSERT INTO festival_info")
                .contains("info_id", "source_type", "external_content_id")
                .contains("#{infoId}", "COALESCE(#{sourceType}, 'ADMIN')", "#{externalContentId}");
        assertThat(update)
                .contains("UPDATE festival_info")
                .contains("source_type = COALESCE(#{sourceType}, 'ADMIN')")
                .contains("external_content_id = #{externalContentId}")
                .contains("WHERE info_id = #{infoId}");
    }

    @Test
    void deleteTargetsOnlyFestivalInfoByInfoId() throws IOException {
        String delete = between(mapper(), "<delete id=\"deleteByInfoId\"", "</delete>");

        assertThat(delete)
                .contains("DELETE FROM festival_info")
                .contains("WHERE info_id = #{infoId}")
                .doesNotContain("travel_info");
    }

    @Test
    void duplicateTourApiSourceCanBeCheckedBeforeInsert() throws IOException {
        String select = between(mapper(),
                "<select id=\"countBySourceTypeAndExternalContentId\"", "</select>");

        assertThat(select)
                .contains("FROM festival_info")
                .contains("source_type = #{sourceType}")
                .contains("external_content_id = #{externalContentId}");
    }

    private String mapper() throws IOException {
        try (InputStream input = getClass().getResourceAsStream("/mapper/FestivalInfoMapper.xml")) {
            assertThat(input).as("FestivalInfoMapper XML").isNotNull();
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private String between(String source, String start, String end) {
        int startIndex = source.indexOf(start);
        int endIndex = source.indexOf(end, startIndex);
        assertThat(startIndex).isGreaterThanOrEqualTo(0);
        assertThat(endIndex).isGreaterThan(startIndex);
        return source.substring(startIndex, endIndex);
    }
}
