package com.example.travlediary.repository;

import com.example.travlediary.model.TravelInfoContentType;
import com.example.travlediary.model.TravelInfoScope;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 여행정보·축제 목록이 만들어 내는 SQL 의 계약.
 *
 * <p>고정하는 것은 두 가지다. <b>보이는 결과가 그대로인지</b> — 거르는 조건, 정렬식(동점일 때의
 * 차례까지), 대표 행사기간을 고르는 우선순위, 대표 이미지를 고르는 우선순위를 본다.
 * 그리고 <b>읽는 양이 줄었는지</b> — 일반 여행정보에서 행사기간을 계산하지 않는지,
 * 대표 이미지를 쪽을 자르기 전에 행마다 찾지 않는지, 개수 세기가 화면용 계산을 하지 않는지 본다.
 *
 * <p>테스트용 DB 가 없어 실제 행을 비교할 수는 없다. 그래서 결과를 정하는 술어와 식을
 * 문자열로 고정한다. ({@code BoardListSqlContractTest} 와 같은 방식)
 */
class TravelInfoListSqlContractTest {

    private static final String NAMESPACE =
            "com.example.travlediary.repository.travelinfo.TravelInfoMapper";

    /** 대표 행사기간을 고르는 우선순위. 한 글자라도 달라지면 다른 기간이 뽑힌다. */
    private static final String REPRESENTATIVE_PERIOD_ORDER =
            "ORDER BY CASE WHEN period_candidate.start_date <= CURDATE()"
                    + " AND period_candidate.end_date >= CURDATE() THEN 0"
                    + " WHEN period_candidate.start_date > CURDATE() THEN 1"
                    + " ELSE 2 END ASC,"
                    + " CASE WHEN period_candidate.start_date > CURDATE()"
                    + " THEN period_candidate.start_date END ASC,"
                    + " CASE WHEN period_candidate.end_date < CURDATE()"
                    + " THEN period_candidate.end_date END DESC,"
                    + " period_candidate.start_date ASC, period_candidate.id ASC LIMIT 1";

    // ---------- 일반 여행정보에서 행사기간을 계산하지 않는다 ----------

    @Test
    void generalListDoesNotTouchEventPeriodsAtAll() throws IOException {
        for (String sort : List.of("latest", "views")) {
            String sql = listSql(TravelInfoContentType.GENERAL, null, sort);

            assertThat(sql).as("sort=%s", sort)
                    .doesNotContain("info_periods")
                    .doesNotContain("representative_period")
                    .doesNotContain("CURDATE()");
        }
    }

    /** 기간 칸 자체는 사라지지 않는다. 예전에도 일반 여행정보에서는 늘 비어 있었다. */
    @Test
    void generalListStillReportsEmptyPeriodColumns() throws IOException {
        assertThat(listSql(TravelInfoContentType.GENERAL, null, "latest"))
                .contains("AS start_date")
                .contains("AS end_date");
    }

    @Test
    void festivalListKeepsTheRepresentativePeriod() throws IOException {
        String sql = listSql(TravelInfoContentType.FESTIVAL, null, "event");

        assertThat(sql)
                .contains("LEFT JOIN info_periods representative_period")
                .contains("period_candidate.info_id = ti.id")
                .contains(REPRESENTATIVE_PERIOD_ORDER);
    }

    /**
     * 일반 여행정보라도 행사 상태로 거르거나 행사일순으로 줄 세우면 기간이 있어야 한다.
     * 예전에는 그 경우 대표 기간이 비어 결과가 없었는데, 그 동작이 그대로여야 한다.
     */
    @Test
    void generalListStillJoinsPeriodsWhenTheRequestActuallyNeedsThem() throws IOException {
        assertThat(listSql(TravelInfoContentType.GENERAL, "ongoing", "latest"))
                .contains("LEFT JOIN info_periods representative_period");
        assertThat(listSql(TravelInfoContentType.GENERAL, null, "event"))
                .contains("LEFT JOIN info_periods representative_period");
    }

    // ---------- 행사 상태 필터 ----------

    @Test
    void eventStatusFilterKeepsItsMeaning() throws IOException {
        assertThat(listSql(TravelInfoContentType.FESTIVAL, "ongoing", "event"))
                .contains("representative_period.start_date <= CURDATE()"
                        + " AND representative_period.end_date >= CURDATE()");
        assertThat(listSql(TravelInfoContentType.FESTIVAL, "upcoming", "event"))
                .contains("representative_period.start_date > CURDATE()");
        assertThat(listSql(TravelInfoContentType.FESTIVAL, "ended", "event"))
                .contains("representative_period.end_date < CURDATE()");
    }

    // ---------- 정렬 (동점 차례까지) ----------

    @Test
    void everySortKeepsItsOrderingAndTieBreakers() throws IOException {
        assertThat(orderOf(listSql(TravelInfoContentType.GENERAL, null, "latest")))
                .isEqualTo("ORDER BY ti.created_at DESC, ti.id DESC");
        assertThat(orderOf(listSql(TravelInfoContentType.GENERAL, null, "views")))
                .isEqualTo("ORDER BY ti.views DESC, ti.created_at DESC, ti.id DESC");
        assertThat(orderOf(listSql(TravelInfoContentType.FESTIVAL, null, "event")))
                .isEqualTo("ORDER BY CASE WHEN representative_period.start_date <= CURDATE()"
                        + " AND representative_period.end_date >= CURDATE() THEN 0"
                        + " WHEN representative_period.start_date > CURDATE() THEN 1"
                        + " ELSE 2 END ASC,"
                        + " CASE WHEN representative_period.start_date <= CURDATE()"
                        + " AND representative_period.end_date >= CURDATE()"
                        + " THEN representative_period.end_date END ASC,"
                        + " CASE WHEN representative_period.start_date > CURDATE()"
                        + " THEN representative_period.start_date END ASC,"
                        + " CASE WHEN representative_period.end_date < CURDATE()"
                        + " THEN representative_period.end_date END DESC,"
                        + " ti.id DESC");
    }

    /**
     * 어느 줄을 남길지 정하는 정렬(파생 테이블 안)과, 남은 줄을 내보내는 정렬(바깥)은
     * 같은 뜻이어야 한다. 둘이 어긋나면 쪽마다 차례가 흔들린다.
     */
    @Test
    void theCandidateOrderAndThePageOrderAlwaysAgree() throws IOException {
        for (TravelInfoContentType contentType : TravelInfoContentType.values()) {
            for (String sort : List.of("latest", "views", "event")) {
                String sql = listSql(contentType, null, sort);
                String candidate = orderInside(derivedTableOf(sql));
                String page = orderInside(sql.substring(sql.lastIndexOf(") page")));

                assertThat(stripAliases(page))
                        .as("%s/sort=%s 의 두 정렬식이 다르다", contentType, sort)
                        .isEqualTo(stripAliases(candidate));
            }
        }
    }

    @Test
    void paginationStaysLimitOffset() throws IOException {
        assertThat(listSql(TravelInfoContentType.GENERAL, null, "latest"))
                .contains("LIMIT ? OFFSET ?");
    }

    // ---------- 대표 이미지 ----------

    /** 일반 여행정보는 예전에도 is_main 한 가지만 봤다. (CASE 는 늘 같은 값이라 무의미했다) */
    @Test
    void generalThumbnailPicksTheMainImageInOrder() throws IOException {
        String sql = listSql(TravelInfoContentType.GENERAL, null, "latest");

        assertThat(sql)
                .contains("ii.is_main = 1")
                .contains("ORDER BY ii.order_index ASC, ii.id ASC")
                .doesNotContain("ii.is_thumbnail");
    }

    /** 축제는 썸네일로 지정한 이미지를 먼저, 없으면 대표 이미지를 쓴다. */
    @Test
    void festivalThumbnailPrefersTheThumbnailFlag() throws IOException {
        String sql = listSql(TravelInfoContentType.FESTIVAL, null, "event");

        assertThat(sql)
                .contains("ii.is_thumbnail = 1 OR ii.is_main = 1")
                .contains("ORDER BY CASE WHEN ii.is_thumbnail = 1 THEN 0 ELSE 1 END ASC,"
                        + " ii.order_index ASC, ii.id ASC");
    }

    /** 대표 이미지는 쪽을 자른 뒤 그 쪽에 남은 줄에 대해서만 찾는다. */
    @Test
    void thumbnailIsResolvedAfterThePageIsCut() throws IOException {
        for (TravelInfoContentType contentType : TravelInfoContentType.values()) {
            String sql = listSql(contentType, null,
                    contentType == TravelInfoContentType.FESTIVAL ? "event" : "latest");
            String page = derivedTableOf(sql);

            assertThat(page).as("%s 후보에 썸네일이 실렸다", contentType)
                    .doesNotContain("info_images");
            assertThat(page).as("%s", contentType).contains("LIMIT ? OFFSET ?");
            assertThat(sql).as("%s", contentType).contains("AS thumbnail_url");
        }
    }

    // ---------- 큰 컬럼 ----------

    @Test
    void listNeverReadsTheArticleBody() throws IOException {
        for (TravelInfoContentType contentType : TravelInfoContentType.values()) {
            String sql = listSql(contentType, null, "latest");

            // ti.content_type 은 목록에 필요하다. 본문(ti.content)만 없어야 한다.
            assertThat(countOf(sql, "ti.content") - countOf(sql, "ti.content_type"))
                    .as("%s 목록이 본문을 읽는다", contentType)
                    .isZero();
        }
    }

    // ---------- 개수 세기 ----------

    @Test
    void countSkipsEventPeriodsWhenNoStatusFilterIsUsed() throws IOException {
        for (TravelInfoContentType contentType : TravelInfoContentType.values()) {
            assertThat(countSql(contentType, null))
                    .as("%s", contentType)
                    .doesNotContain("info_periods")
                    .doesNotContain("representative_period");
        }
    }

    @Test
    void countStillJoinsPeriodsWhenTheStatusFilterNeedsThem() throws IOException {
        assertThat(countSql(TravelInfoContentType.FESTIVAL, "ongoing"))
                .contains("LEFT JOIN info_periods representative_period")
                .contains(REPRESENTATIVE_PERIOD_ORDER);
    }

    @Test
    void countBuildsNoneOfTheDisplayValues() throws IOException {
        for (String status : new String[]{null, "ongoing"}) {
            assertThat(countSql(TravelInfoContentType.FESTIVAL, status))
                    .as("eventStatus=%s", status)
                    .contains("SELECT COUNT(*)")
                    .doesNotContain("info_images")
                    .doesNotContain("thumbnail_url")
                    .doesNotContain("ic.name")
                    // 줄 세우기도 쪽 나누기도 필요 없다.
                    // (대표기간을 고르는 서브쿼리 안의 ORDER BY ... LIMIT 1 은 조건 계산이라 남는다)
                    .doesNotContain("ORDER BY ti.")
                    .doesNotContain("LIMIT ? OFFSET ?");
        }
    }

    /** 세는 조건과 뽑는 조건이 어긋나면 쪽 수가 맞지 않는다. */
    @Test
    void countAndListShareTheSameFilters() throws IOException {
        for (TravelInfoContentType contentType : TravelInfoContentType.values()) {
            for (String status : new String[]{null, "ongoing"}) {
                String list = listSql(contentType, status, "latest");
                String count = countSql(contentType, status);

                for (String predicate : List.of(
                        "ic.is_visible = 1",
                        "ti.scope = ?",
                        "ti.content_type = ?",
                        "ti.category_id IN",
                        "ti.title LIKE CONCAT('%', ?, '%') ESCAPE '!'",
                        "REGEXP_LIKE(ti.title, ?)")) {
                    assertThat(count).as("%s/%s 에 %s 가 없다", contentType, status, predicate)
                            .contains(predicate);
                    assertThat(list).as("%s/%s 에 %s 가 없다", contentType, status, predicate)
                            .contains(predicate);
                }
            }
        }
    }

    // ---------- 도우미 ----------

    private String listSql(TravelInfoContentType contentType, String eventStatus, String sort)
            throws IOException {
        Map<String, Object> parameters = baseParams(contentType, eventStatus);
        parameters.put("sort", sort);
        parameters.put("offset", 0L);
        parameters.put("limit", 12);
        return normalize(boundSql("findPublicList", parameters));
    }

    private String countSql(TravelInfoContentType contentType, String eventStatus)
            throws IOException {
        return normalize(boundSql("countPublicList", baseParams(contentType, eventStatus)));
    }

    /** 모든 필터를 켠 상태로 본다. 조건이 하나라도 빠지면 목록과 개수가 갈라진다. */
    private Map<String, Object> baseParams(TravelInfoContentType contentType, String eventStatus) {
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("scope", TravelInfoScope.DOMESTIC);
        parameters.put("contentType", contentType);
        parameters.put("categoryIds", List.of(1L, 2L));
        parameters.put("keywordPattern", "제주");
        parameters.put("koreanPattern", "^제주");
        parameters.put("eventStatus", eventStatus);
        return parameters;
    }

    /** 후보를 고르고 줄 세우고 쪽을 자르는 부분. 바깥 SELECT 는 뺀다. */
    private String derivedTableOf(String sql) {
        int start = sql.indexOf("FROM (");
        int end = sql.lastIndexOf(") page");
        assertThat(start).as("파생 테이블 시작을 찾지 못했다: %s", sql).isGreaterThan(0);
        assertThat(end).as("파생 테이블 끝을 찾지 못했다: %s", sql).isGreaterThan(start);
        return sql.substring(start, end);
    }

    /**
     * 조각의 줄 세우기 식. 대표기간을 고르는 서브쿼리 안에도 ORDER BY 가 있으므로
     * 맨 뒤의 것(후보를 줄 세우는 쪽)을 본다.
     */
    private String orderInside(String fragment) {
        int start = fragment.lastIndexOf("ORDER BY");
        assertThat(start).as("ORDER BY 가 없다: %s", fragment).isGreaterThanOrEqualTo(0);
        int end = fragment.indexOf(" LIMIT", start);
        return (end < 0 ? fragment.substring(start) : fragment.substring(start, end)).trim();
    }

    /** 표 이름만 걷어낸다. 남는 것은 어떤 값을 어떤 차례로 보는지다. */
    private String stripAliases(String order) {
        return order.replace("representative_period.", "")
                .replace("page.", "")
                .replace("ti.", "");
    }

    private int countOf(String sql, String token) {
        int count = 0;
        for (int i = sql.indexOf(token); i >= 0; i = sql.indexOf(token, i + token.length())) {
            count++;
        }
        return count;
    }

    private String orderOf(String sql) {
        int start = sql.lastIndexOf("ORDER BY ti.");
        if (start < 0) {
            start = sql.indexOf("ORDER BY CASE WHEN representative_period");
        }
        assertThat(start).as("정렬식을 찾지 못했다: %s", sql).isGreaterThanOrEqualTo(0);
        int end = sql.indexOf(" LIMIT", start);
        return (end < 0 ? sql.substring(start) : sql.substring(start, end)).trim();
    }

    private String boundSql(String statement, Map<String, Object> parameters) throws IOException {
        return mapperConfiguration()
                .getMappedStatement(NAMESPACE + "." + statement)
                .getBoundSql(parameters)
                .getSql();
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

    private String normalize(String sql) {
        return sql.replaceAll("\\s+", " ")
                .replaceAll("\\( ", "(")
                .replaceAll(" \\)", ")")
                .trim();
    }
}
