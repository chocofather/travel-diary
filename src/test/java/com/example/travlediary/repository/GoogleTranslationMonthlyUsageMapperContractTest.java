package com.example.travlediary.repository;

import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class GoogleTranslationMonthlyUsageMapperContractTest {

    @Test
    void monthlyReservationUsesAConditionalAtomicUpdate() {
        Configuration configuration = mapperConfiguration();
        Map<String, Object> parameters = Map.of(
                "monthKey", "2026-09",
                "characters", 120L,
                "characterLimit", 400_000L,
                "now", Timestamp.from(Instant.parse("2026-09-09T00:00:00Z")));

        String insert = sql(configuration, "insertMonthIfAbsent", parameters);
        assertThat(insert)
                .contains("INSERT IGNORE INTO google_translation_monthly_usage")
                .contains("used_characters, provider_calls");

        String reserve = sql(configuration, "reserveIfWithinLimit", parameters);
        assertThat(reserve)
                .startsWith("UPDATE google_translation_monthly_usage")
                .contains("used_characters = used_characters + ?")
                .contains("provider_calls = provider_calls + 1")
                .contains("used_characters + ? <= ?")
                .doesNotContain("SELECT");
    }

    private Configuration mapperConfiguration() {
        Configuration configuration = new Configuration();
        try (InputStream input = getClass().getResourceAsStream(
                "/mapper/GoogleTranslationMonthlyUsageMapper.xml")) {
            assertThat(input).isNotNull();
            new XMLMapperBuilder(input, configuration,
                    "mapper/GoogleTranslationMonthlyUsageMapper.xml",
                    configuration.getSqlFragments()).parse();
            return configuration;
        } catch (Exception e) {
            throw new AssertionError("월간 번역 사용량 mapper XML을 파싱할 수 없습니다.", e);
        }
    }

    private String sql(Configuration configuration, String statement, Object parameters) {
        BoundSql boundSql = configuration.getMappedStatement(
                        "com.example.travlediary.repository.translation.GoogleTranslationMonthlyUsageMapper."
                                + statement)
                .getBoundSql(parameters);
        return boundSql.getSql().replaceAll("\\s+", " ").trim();
    }
}
