package com.example.travlediary.repository;

import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class GoogleTranslationDailyUsageMapperContractTest {

    @Test
    void userAndIpReservationsUseConditionalAtomicUpdates() {
        Configuration configuration = mapperConfiguration();
        Map<String, Object> userParameters = Map.of(
                "usageDate", LocalDate.of(2026, 9, 9),
                "userId", 42L,
                "characters", 120L,
                "characterLimit", 20_000L,
                "now", Timestamp.from(Instant.parse("2026-09-09T00:00:00Z")));
        Map<String, Object> ipParameters = Map.of(
                "usageDate", LocalDate.of(2026, 9, 9),
                "ipHash", new byte[32],
                "characters", 120L,
                "characterLimit", 5_000L,
                "now", Timestamp.from(Instant.parse("2026-09-09T00:00:00Z")));

        String userInsert = sql(configuration, "insertUserDayIfAbsent", userParameters);
        assertThat(userInsert)
                .contains("INSERT IGNORE INTO google_translation_daily_usage")
                .contains("usage_date, subject_type, user_id")
                .doesNotContain("ip_hash");

        String userReserve = sql(configuration, "reserveUserIfWithinLimit", userParameters);
        assertAtomicReservation(userReserve, "subject_type = 'USER'", "user_id = ?");

        String ipInsert = sql(configuration, "insertIpDayIfAbsent", ipParameters);
        assertThat(ipInsert)
                .contains("INSERT IGNORE INTO google_translation_daily_usage")
                .contains("usage_date, subject_type, ip_hash")
                .doesNotContain("user_id");

        String ipReserve = sql(configuration, "reserveIpIfWithinLimit", ipParameters);
        assertAtomicReservation(ipReserve, "subject_type = 'IP'", "ip_hash = ?");
    }

    private void assertAtomicReservation(String sql, String subjectType, String subjectColumn) {
        assertThat(sql)
                .startsWith("UPDATE google_translation_daily_usage")
                .contains("used_characters = used_characters + ?")
                .contains("provider_calls = provider_calls + 1")
                .contains(subjectType)
                .contains(subjectColumn)
                .contains("? <= ?")
                .contains("used_characters <= ? - ?")
                .doesNotContain("SELECT");
    }

    private Configuration mapperConfiguration() {
        Configuration configuration = new Configuration();
        try (InputStream input = getClass().getResourceAsStream(
                "/mapper/GoogleTranslationDailyUsageMapper.xml")) {
            assertThat(input).isNotNull();
            new XMLMapperBuilder(input, configuration,
                    "mapper/GoogleTranslationDailyUsageMapper.xml",
                    configuration.getSqlFragments()).parse();
            return configuration;
        } catch (Exception e) {
            throw new AssertionError("일일 번역 사용량 mapper XML을 파싱할 수 없습니다.", e);
        }
    }

    private String sql(Configuration configuration, String statement, Object parameters) {
        BoundSql boundSql = configuration.getMappedStatement(
                        "com.example.travlediary.repository.translation.GoogleTranslationDailyUsageMapper."
                                + statement)
                .getBoundSql(parameters);
        return boundSql.getSql().replaceAll("\\s+", " ").trim();
    }
}
