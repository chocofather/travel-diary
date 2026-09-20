package com.example.travlediary.repository;

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
 * 통합검색이 만들어 내는 SQL 의 계약.
 *
 * <p>고정하는 것은 두 가지다. <b>찾는 범위와 차례가 그대로인지</b> — 어떤 글을 찾는지 정하는
 * LIKE 조건과 정렬식(동점일 때의 차례까지)을 본다. 그리고 <b>읽는 양이 줄었는지</b> —
 * 고른 갈래만 읽는지, 본문 TEXT 를 쪽을 자르기 전에 실어 나르지 않는지, 개수 세기가
 * 화면용 값을 만들지 않는지 본다.
 *
 * <p>테스트용 DB 가 없어 실제 행을 비교할 수는 없다. 그래서 결과를 정하는 술어와 정렬식을
 * 문자열로 고정한다. ({@code BoardListSqlContractTest} 와 같은 방식)
 */
class GlobalSearchSqlContractTest {

    private static final String NAMESPACE =
            "com.example.travlediary.repository.search.GlobalSearchMapper";

    /** GlobalSearchType 이 걸러 주는 값들. 이 밖의 값은 서비스가 all 로 바꾼다. */
    private static final List<String> SPECIFIC_TYPES = List.of(
            "destination", "community", "course", "travel-info", "event", "notice");

    private static final Map<String, String> MAIN_TABLE_OF = Map.of(
            "destination", "FROM destinations d",
            "community", "FROM user_posts p",
            "course", "FROM courses c",
            "travel-info", "FROM travel_info ti",
            "event", "FROM events e",
            "notice", "FROM notices n");

    // ---------- 갈래 고르기 ----------

    @Test
    void allTypeStillSearchesEverySource() throws IOException {
        String sql = searchSql("all");

        for (String table : MAIN_TABLE_OF.values()) {
            assertThat(sql).as(table).contains(table);
        }
        assertThat(countOf(sql, "UNION ALL")).isEqualTo(5);
    }

    @Test
    void eachTypeReadsOnlyItsOwnSource() throws IOException {
        for (String type : SPECIFIC_TYPES) {
            String sql = searchSql(type);

            assertThat(sql).as("type=%s", type).contains(MAIN_TABLE_OF.get(type));
            assertThat(sql).as("type=%s 는 UNION 이 필요 없다", type).doesNotContain("UNION ALL");
            MAIN_TABLE_OF.forEach((other, table) -> {
                if (!other.equals(type)) {
                    assertThat(sql).as("type=%s 가 %s 를 읽으면 안 된다", type, table)
                            .doesNotContain(table);
                }
            });
        }
    }

    /** 갈래를 고르는 일은 MyBatis 가 한다. SQL 안에서 type 을 다시 거르지 않는다. */
    @Test
    void typeIsNoLongerFilteredInsideTheSql() throws IOException {
        assertThat(searchSql("all")).doesNotContain("IN ('all'");
        assertThat(countSql("community")).doesNotContain("IN ('all'");
    }

    // ---------- 찾는 범위 (결과 보존) ----------

    @Test
    void destinationKeepsEveryMatchingField() throws IOException {
        String sql = searchSql("destination");

        assertThat(sql)
                .contains("dt.name LIKE CONCAT('%', ?, '%') ESCAPE '!'")
                .contains("dt.short_description LIKE")
                .contains("dt.description LIKE")
                .contains("cc.region_name LIKE")
                .contains("pcc.region_name LIKE")
                .contains("FROM destination_categories dcat");
    }

    @Test
    void userWrittenSourcesKeepTitleAndBodyMatching() throws IOException {
        assertThat(searchSql("community"))
                .contains("p.title LIKE")
                .contains("p.content LIKE")
                .contains("p.post_type IN ('QUESTION', 'TIP')")
                .contains("p.deleted = 0")
                .contains("p.deleted_at IS NULL");
        assertThat(searchSql("course"))
                .contains("c.title LIKE")
                .contains("c.content LIKE")
                .contains("c.deleted = 0")
                .contains("c.deleted_at IS NULL");
        assertThat(searchSql("travel-info"))
                .contains("ti.title LIKE")
                .contains("ti.content LIKE")
                .contains("ic.is_visible = 1");
        assertThat(searchSql("event"))
                .contains("e.title LIKE")
                .contains("e.description LIKE");
        assertThat(searchSql("notice"))
                .contains("n.title LIKE")
                .contains("n.content LIKE");
    }

    /** 현재 언어로도 찾는 계약. 언어가 주어질 때만 붙는 것도 그대로다. */
    @Test
    void currentLanguageMatchingIsKeptAndStaysOptional() throws IOException {
        String withLanguage = searchSql("destination", "en", 0L, 10);
        String withoutLanguage = normalize(boundSql("search", params("destination", "", 0L, 10)));

        assertThat(withLanguage)
                .contains("FROM destination_translations dtr")
                .contains("FROM country_category_translations cctr")
                .contains("JOIN category_translations ctr");
        assertThat(withoutLanguage)
                .doesNotContain("FROM destination_translations dtr")
                .doesNotContain("FROM country_category_translations cctr");
    }

    // ---------- 차례 ----------

    @Test
    void orderingAndTieBreakersAreUnchanged() throws IOException {
        for (String type : List.of("all", "destination", "community", "notice")) {
            assertThat(searchSql(type))
                    .as("type=%s", type)
                    .contains("ORDER BY relevance DESC, created_at DESC,"
                            + " search_type ASC, id DESC");
        }
    }

    /**
     * 어느 줄을 남길지 정하는 정렬(파생 테이블 안)과, 남은 줄을 내보내는 정렬(바깥)은
     * 언제나 같은 뜻이어야 한다. 둘이 어긋나면 쪽마다 차례가 흔들린다.
     *
     * <p>바깥 정렬이 아예 없으면 파생 테이블이 담아 둔 순서에 기대게 되는데,
     * 그 순서는 SQL 이 보장하는 것이 아니라 그것도 함께 막는다.
     */
    @Test
    void theCandidateOrderAndTheOutputOrderAlwaysAgree() throws IOException {
        for (String type : allSearchInputs()) {
            String sql = searchSql(type);
            String candidate = orderInside(derivedTableOf(sql));
            String output = orderInside(sql.substring(sql.lastIndexOf(") r")));

            assertThat(output).as("type=%s 에 바깥 정렬이 없다", type).isNotEmpty();
            assertThat(stripAlias(output))
                    .as("type=%s 의 두 정렬식이 다르다", type)
                    .isEqualTo(stripAlias(candidate));
        }
    }

    @Test
    void relevanceStillPrefersATitleHit() throws IOException {
        assertThat(searchSql("community"))
                .contains("CASE WHEN p.title LIKE CONCAT('%', ?, '%') ESCAPE '!'"
                        + " THEN 2 ELSE 1 END AS relevance");
    }

    @Test
    void paginationStaysLimitOffset() throws IOException {
        assertThat(searchSql("all")).contains("LIMIT ? OFFSET ?");
    }

    // ---------- 본문 TEXT 운반 ----------

    /**
     * 본문은 쪽을 자른 뒤에 읽는다.
     *
     * <p>예전에는 조건에 맞는 모든 줄의 본문(MEDIUMTEXT)이 UNION 임시 결과에 들어간 뒤
     * 열 줄만 남기고 버려졌다. 지금은 LIMIT 뒤에 나오므로 그 열 줄만 읽는다.
     */
    @Test
    void bodyTextIsResolvedAfterThePageIsCut() throws IOException {
        for (String type : List.of("all", "destination", "community",
                "course", "travel-info", "event", "notice")) {
            String sql = searchSql(type);
            String candidates = derivedTableOf(sql);

            // 후보를 고르고 줄 세우는 동안에는 미리보기를 만들지 않는다.
            assertThat(candidates).as("type=%s 후보에 summary 가 실렸다", type)
                    .doesNotContain("AS summary");
            assertThat(candidates).as("type=%s 후보에 thumbnail 이 실렸다", type)
                    .doesNotContain("AS thumbnail_url");
            // 쪽을 자른 뒤 바깥에서 만든다.
            assertThat(candidates).as("type=%s", type).contains("LIMIT ? OFFSET ?");
            assertThat(sql).as("type=%s", type)
                    .contains("AS summary")
                    .contains("AS thumbnail_url");
        }
    }

    /** 본문은 결과로 나르지 않을 뿐, 찾는 대상에서는 빠지지 않는다. */
    @Test
    void bodyTextIsStillSearchedEvenThoughItIsNotCarried() throws IOException {
        Map<String, String> bodyColumnOf = Map.of(
                "community", "p.content LIKE",
                "course", "c.content LIKE",
                "travel-info", "ti.content LIKE",
                "event", "e.description LIKE",
                "notice", "n.content LIKE");

        for (Map.Entry<String, String> entry : bodyColumnOf.entrySet()) {
            assertThat(derivedTableOf(searchSql(entry.getKey())))
                    .as("type=%s", entry.getKey())
                    .contains(entry.getValue());
        }
    }

    /**
     * WHEN 이 하나도 없는 {@code CASE} 는 MySQL 문법 오류다.
     * 대표 이미지를 가진 갈래는 둘뿐이라 나머지 갈래만 고르면 빈 CASE 가 되기 쉽다.
     * 갈래를 고르는 조건이 어긋나면 뜻밖의 type 에서도 같은 일이 생기므로 함께 확인한다.
     */
    @Test
    void noBranchEverBuildsAnEmptyCase() throws IOException {
        List<String> allInputs = new java.util.ArrayList<>(SPECIFIC_TYPES);
        allInputs.add("all");
        // GlobalSearchType 이 all 로 바꿔 주지만, 매퍼 혼자서도 무너지지 않아야 한다.
        allInputs.add("뜻밖의값");

        for (String type : allInputs) {
            assertThat(searchSql(type))
                    .as("type=%s 에 빈 CASE 가 생겼다", type)
                    .doesNotContain("CASE r.search_type END");
        }
    }

    @Test
    void summaryExpressionsKeepTheirOriginalMeaning() throws IOException {
        // 여행지는 짧은 소개를 먼저 쓰고, 비어 있으면 본문으로 내려간다. (예전 COALESCE 그대로)
        assertThat(searchSql("destination"))
                .contains("COALESCE(NULLIF(dt.short_description, ''), dt.description, '')");
        assertThat(searchSql("event"))
                .contains("COALESCE(NULLIF(e.event_img, ''), NULLIF(e.poster_img, ''))");
    }

    /** 여행지 대표 이미지도 열 줄을 고른 뒤에 찾는다. 고르는 규칙은 예전 그대로다. */
    @Test
    void destinationThumbnailIsResolvedAfterThePageIsCut() throws IOException {
        String sql = searchSql("destination");

        assertThat(derivedTableOf(sql)).doesNotContain("FROM destination_images di");
        assertThat(sql)
                .contains("FROM destination_images di")
                .contains("di.is_main = 1")
                .contains("ORDER BY di.order_index ASC, di.id ASC");
    }

    // ---------- 개수 세기 ----------

    @Test
    void countBuildsNoneOfTheDisplayValues() throws IOException {
        for (String type : List.of("all", "destination", "community", "event")) {
            assertThat(countSql(type))
                    .as("type=%s", type)
                    .doesNotContain("AS summary")
                    .doesNotContain("detail_url")
                    .doesNotContain("thumbnail_url")
                    .doesNotContain("AS relevance")
                    .doesNotContain("ORDER BY relevance")
                    .doesNotContain("LIMIT");
        }
    }

    @Test
    void countReadsOnlyTheSourceThatTheTypeNeeds() throws IOException {
        for (String type : SPECIFIC_TYPES) {
            String sql = countSql(type);

            assertThat(sql).as("type=%s", type).contains(MAIN_TABLE_OF.get(type));
            MAIN_TABLE_OF.forEach((other, table) -> {
                if (!other.equals(type)) {
                    assertThat(sql).as("type=%s 가 %s 를 세면 안 된다", type, table)
                            .doesNotContain(table);
                }
            });
        }
    }

    @Test
    void countForAllTypesAddsUpEachSourceWithoutBuildingTheUnion() throws IOException {
        String sql = countSql("all");

        assertThat(countOf(sql, "SELECT COUNT(*)")).isEqualTo(6);
        assertThat(sql).doesNotContain("UNION ALL");
    }

    /** 세는 조건과 뽑는 조건이 어긋나면 쪽 수가 맞지 않는다. */
    @Test
    void countAndSearchShareTheSameMatchingConditions() throws IOException {
        assertThat(countSql("community"))
                .contains("p.title LIKE")
                .contains("p.content LIKE")
                .contains("p.post_type IN ('QUESTION', 'TIP')")
                .contains("p.deleted = 0")
                .contains("p.deleted_at IS NULL");
        assertThat(countSql("travel-info"))
                .contains("ic.is_visible = 1")
                .contains("ti.title LIKE")
                .contains("ti.content LIKE");
        assertThat(countSql("destination"))
                .contains("cc.region_name LIKE")
                .contains("pcc.region_name LIKE")
                .contains("FROM destination_categories dcat");
    }

    @Test
    void countKeepsTheCurrentLanguageMatchingToo() throws IOException {
        assertThat(countSql("destination"))
                .contains("FROM destination_translations dtr")
                .contains("FROM country_category_translations cctr");
    }

    // ---------- 도우미 ----------

    private String searchSql(String type) throws IOException {
        return searchSql(type, "ko", 0L, 10);
    }

    private String searchSql(String type, String languageCode, long offset, int limit)
            throws IOException {
        return normalize(boundSql("search", params(type, languageCode, offset, limit)));
    }

    private String countSql(String type) throws IOException {
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("keywordPattern", "제주");
        parameters.put("type", type);
        parameters.put("languageCode", "ko");
        return normalize(boundSql("count", parameters));
    }

    private Map<String, Object> params(String type, String languageCode,
                                       long offset, int limit) {
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("keywordPattern", "제주");
        parameters.put("type", type);
        parameters.put("languageCode", languageCode);
        parameters.put("offset", offset);
        parameters.put("limit", limit);
        return parameters;
    }

    private String boundSql(String statement, Map<String, Object> parameters) throws IOException {
        return mapperConfiguration()
                .getMappedStatement(NAMESPACE + "." + statement)
                .getBoundSql(parameters)
                .getSql();
    }

    /** 갈래를 고르는 값들. 예상 밖의 값도 섞어 본다(서비스가 all 로 바꿔 주지만). */
    private List<String> allSearchInputs() {
        List<String> inputs = new java.util.ArrayList<>(SPECIFIC_TYPES);
        inputs.add("all");
        inputs.add("뜻밖의값");
        return inputs;
    }

    /**
     * 조각의 줄 세우기 식. 대표 이미지를 고르는 서브쿼리 안에도 ORDER BY 가 있으므로
     * 맨 뒤의 것(줄을 세우는 쪽)을 본다.
     */
    private String orderInside(String fragment) {
        int start = fragment.lastIndexOf("ORDER BY");
        assertThat(start).as("ORDER BY 가 없다: %s", fragment).isGreaterThanOrEqualTo(0);
        int end = fragment.indexOf(" LIMIT", start);
        return (end < 0 ? fragment.substring(start) : fragment.substring(start, end)).trim();
    }

    /** 표 이름만 걷어낸다. 남는 것은 어떤 값을 어떤 차례로 보는지다. */
    private String stripAlias(String order) {
        return order.replace("r.", "");
    }

    /** 후보를 고르고 줄 세우고 쪽을 자르는 부분. 바깥 SELECT 는 뺀다. */
    private String derivedTableOf(String sql) {
        int start = sql.indexOf("FROM (");
        int end = sql.lastIndexOf(") r");
        assertThat(start).as("파생 테이블 시작을 찾지 못했다: %s", sql).isGreaterThan(0);
        assertThat(end).as("파생 테이블 끝을 찾지 못했다: %s", sql).isGreaterThan(start);
        return sql.substring(start, end);
    }

    private int countOf(String sql, String token) {
        int count = 0;
        for (int i = sql.indexOf(token); i >= 0; i = sql.indexOf(token, i + token.length())) {
            count++;
        }
        return count;
    }

    private Configuration mapperConfiguration() throws IOException {
        Configuration configuration = new Configuration();
        configuration.setMapUnderscoreToCamelCase(true);
        try (InputStream input = getClass().getResourceAsStream("/mapper/GlobalSearchMapper.xml")) {
            assertThat(input).isNotNull();
            new XMLMapperBuilder(input, configuration, "mapper/GlobalSearchMapper.xml",
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
