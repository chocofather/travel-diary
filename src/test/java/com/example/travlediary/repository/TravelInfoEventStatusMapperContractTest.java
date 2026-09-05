package com.example.travlediary.repository;

import com.example.travlediary.model.TravelInfoContentType;
import com.example.travlediary.model.TravelInfoScope;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 축제·행사 목록의 상태 필터와 행사일순 정렬을 SQL 수준에서 본다.
 *
 * <p>상태 판정·정렬·목록에 찍는 기간이 모두 같은 대표기간 한 줄을 보도록 묶여 있어야 한다.
 */
class TravelInfoEventStatusMapperContractTest {

    private static final String NAMESPACE =
            "com.example.travlediary.repository.travelinfo.TravelInfoMapper";

    @ParameterizedTest
    @CsvSource({
            "ongoing,  representative_period.start_date <= CURDATE()",
            "upcoming, representative_period.start_date > CURDATE()",
            "ended,    representative_period.end_date < CURDATE()"
    })
    void eachEventStatusNarrowsTheListByTheRepresentativePeriod(String eventStatus,
                                                                String expected)
            throws IOException {
        String sql = normalize(listSql(eventStatus, "event"));

        assertThat(sql).contains(expected);
        if ("ongoing".equals(eventStatus)) {
            assertThat(sql).contains("representative_period.end_date >= CURDATE()");
        }
    }

    @Test
    void noEventStatusLeavesTheListUnfiltered() throws IOException {
        String sql = normalize(listSql(null, "event"));

        // '전체' 는 상태 조건이 붙지 않는다. 대표기간 조인은 정렬 때문에 그대로 남는다.
        assertThat(sql)
                .doesNotContain("WHERE ic.is_visible = 1 AND representative_period")
                .contains("LEFT JOIN info_periods representative_period");
    }

    @Test
    void theCountQuerySeesTheSameStatusConditionAsTheList() throws IOException {
        String count = normalize(countSql("upcoming"));

        // 페이지 수가 목록과 어긋나지 않으려면 개수 쿼리도 같은 대표기간을 봐야 한다.
        assertThat(count)
                .contains("LEFT JOIN info_periods representative_period")
                .contains("representative_period.start_date > CURDATE()");
    }

    @Test
    void theEventDateOrderPutsOngoingThenUpcomingThenEndedFestivalsFirst() throws IOException {
        String sql = normalize(listSql(null, "event"));
        String order = orderClause(sql);

        // 묶음 순서: 진행중(0) → 예정(1) → 종료(2)
        assertThat(order)
                .contains("WHEN representative_period.start_date <= CURDATE() "
                        + "AND representative_period.end_date >= CURDATE() THEN 0")
                .contains("WHEN representative_period.start_date > CURDATE() THEN 1")
                .contains("ELSE 2");
        // 진행중은 먼저 끝나는 순, 예정은 먼저 시작하는 순, 종료는 최근에 끝난 순.
        assertThat(order)
                .contains("THEN representative_period.end_date END ASC")
                .contains("THEN representative_period.start_date END ASC")
                .contains("THEN representative_period.end_date END DESC")
                // 같은 값이면 언제 불러도 같은 순서가 되도록 id 로 끊는다.
                .endsWith("ti.id DESC");
    }

    @Test
    void viewsOrderIsUnchangedAndStillWorksWithAStatusFilter() throws IOException {
        String sql = normalize(listSql("ongoing", "views"));
        String order = orderClause(sql);

        assertThat(order).isEqualTo("ORDER BY ti.views DESC, ti.created_at DESC, ti.id DESC");
        // 조회순을 골라도 상태 필터는 그대로 걸린다.
        assertThat(sql).contains("representative_period.end_date >= CURDATE()");
    }

    @Test
    void theGeneralListKeepsItsNewestFirstOrder() throws IOException {
        String sql = normalize(listSql(null, "latest"));
        String order = orderClause(sql);

        assertThat(order).isEqualTo("ORDER BY ti.created_at DESC, ti.id DESC");
    }

    private String listSql(String eventStatus, String sort) throws IOException {
        Map<String, Object> parameters = baseParameters(eventStatus);
        parameters.put("sort", sort);
        parameters.put("offset", 0L);
        parameters.put("limit", 12);
        return boundSql("findPublicList", parameters);
    }

    private String countSql(String eventStatus) throws IOException {
        return boundSql("countPublicList", baseParameters(eventStatus));
    }

    private Map<String, Object> baseParameters(String eventStatus) {
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("scope", TravelInfoScope.DOMESTIC);
        parameters.put("contentType", TravelInfoContentType.FESTIVAL);
        parameters.put("categoryIds", List.of());
        parameters.put("keywordPattern", null);
        parameters.put("koreanPattern", null);
        parameters.put("eventStatus", eventStatus);
        return parameters;
    }

    private String boundSql(String statement, Map<String, Object> parameters) throws IOException {
        BoundSql boundSql = mapperConfiguration()
                .getMappedStatement(NAMESPACE + "." + statement)
                .getBoundSql(parameters);
        return boundSql.getSql();
    }

    private Configuration mapperConfiguration() throws IOException {
        Configuration configuration = new Configuration();
        configuration.setMapUnderscoreToCamelCase(true);
        try (InputStream input = getClass().getResourceAsStream("/mapper/TravelInfoMapper.xml")) {
            assertThat(input).isNotNull();
            new XMLMapperBuilder(input, configuration, "mapper/TravelInfoMapper.xml",
                    configuration.getSqlFragments()).parse();
        }
        return configuration;
    }

    /** 바깥 ORDER BY 만 떼어 낸다. 대표기간 서브쿼리의 정렬은 앞쪽에 있어 섞이지 않는다. */
    private String orderClause(String sql) {
        String order = sql.substring(sql.lastIndexOf("ORDER BY"));
        int limitIndex = order.indexOf(" LIMIT ");
        return limitIndex < 0 ? order : order.substring(0, limitIndex);
    }

    private String normalize(String sql) {
        return sql.replaceAll("\\s+", " ").trim();
    }
}
