package com.example.travlediary.repository;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 통합검색의 여행지 번역 검색.
 *
 * <p>한국어 원문 조건은 그대로 두고 요청 언어 번역만 EXISTS 로 덧붙인다.
 * JOIN 이 늘지 않아야 같은 여행지가 두 번 나오지 않고 개수·정렬도 흔들리지 않는다.
 */
class GlobalSearchTranslationMapperContractTest {

    @Test
    void destinationBranchKeepsKoreanConditionsAndAddsTranslationLookups() throws IOException {
        String destinationBranch = destinationBranch();

        // 기존 한국어(ko 번역)·지역 조건은 그대로다
        assertThat(destinationBranch)
                .contains("dt.language_code = 'ko'")
                .contains("dt.name LIKE CONCAT('%', #{keywordPattern}, '%') ESCAPE '!'")
                .contains("dt.short_description LIKE CONCAT('%', #{keywordPattern}, '%') ESCAPE '!'")
                .contains("dt.description LIKE CONCAT('%', #{keywordPattern}, '%') ESCAPE '!'")
                .contains("cc.region_name LIKE CONCAT('%', #{keywordPattern}, '%') ESCAPE '!'")
                .contains("pcc.region_name LIKE CONCAT('%', #{keywordPattern}, '%') ESCAPE '!'");

        // 카테고리 원문과 요청 언어 번역(여행지·지역·카테고리)
        assertThat(destinationBranch)
                .contains("JOIN categories cat ON cat.id = dcat.category_id")
                .contains("FROM destination_translations dtr")
                .contains("dtr.language_code = #{languageCode}")
                .contains("dtr.short_description LIKE CONCAT('%', #{keywordPattern}, '%') ESCAPE '!'")
                .contains("FROM country_category_translations cctr")
                .contains("cctr.country_category_id IN (cc.id, pcc.id)")
                .contains("cctr.language_code = #{languageCode}")
                .contains("JOIN category_translations ctr ON ctr.category_id = dcat.category_id")
                .contains("ctr.language_code = #{languageCode}")
                .contains("languageCode != null and languageCode != ''");

        // 번역 조회는 전부 EXISTS 다. 여행지 행을 늘리는 JOIN 은 예전 그대로 (ko 번역·지역·상위 지역)
        assertThat(count(destinationBranch, "EXISTS (")).isEqualTo(4);
        assertThat(count(destinationBranch, "JOIN destination_translations dt\n")).isEqualTo(1);
        assertThat(count(destinationBranch, "LEFT JOIN country_categories")).isEqualTo(2);
        assertThat(destinationBranch).doesNotContain("${");
    }

    @Test
    void translatedTitleMatchRanksLikeTheKoreanTitleMatch() throws IOException {
        String mapper = mapperXml();

        // 제목이 맞으면 relevance 2 로 올리는 기존 규칙에 번역 이름도 포함된다
        assertThat(mapper)
                .contains("<sql id=\"DestinationTranslatedNameMatch\">")
                .contains("OR <include refid=\"DestinationTranslatedNameMatch\"/>")
                .contains("THEN 2 ELSE 1 END AS relevance");
        // 정렬·페이징 규칙은 그대로다
        assertThat(between(mapper, "<select id=\"search\"", "</select>"))
                .contains("ORDER BY relevance DESC, created_at DESC, search_type ASC, id DESC")
                .contains("LIMIT #{limit}")
                .contains("OFFSET #{offset}");
    }

    @Test
    void otherContentTypesAreLeftUntouched() throws IOException {
        String mapper = mapperXml();
        String union = between(mapper, "<sql id=\"GlobalSearchUnion\">", "</sql>");
        String others = union.substring(union.indexOf("'community' AS search_type"));

        // 커뮤니티·코스·여행정보·이벤트·공지사항은 예전 조건 그대로 (번역 테이블 없음)
        assertThat(others)
                .contains("p.title LIKE", "p.content LIKE")
                .contains("c.title LIKE", "c.content LIKE")
                .contains("ti.title LIKE", "ti.content LIKE")
                .contains("e.title LIKE", "e.description LIKE")
                .contains("n.title LIKE", "n.content LIKE")
                .doesNotContain("#{languageCode}")
                .doesNotContain("_translations");
    }

    private String destinationBranch() throws IOException {
        String union = between(mapperXml(), "<sql id=\"GlobalSearchUnion\">", "</sql>");
        return union.substring(0, union.indexOf("'community' AS search_type"));
    }

    private int count(String source, String token) {
        return source.split(Pattern.quote(token), -1).length - 1;
    }

    private String between(String source, String start, String end) {
        int from = source.indexOf(start);
        assertThat(from).as("start %s", start).isNotNegative();
        int to = source.indexOf(end, from);
        assertThat(to).as("end %s", end).isNotNegative();
        return source.substring(from, to);
    }

    private String mapperXml() throws IOException {
        try (InputStream input = getClass().getResourceAsStream("/mapper/GlobalSearchMapper.xml")) {
            assertThat(input).isNotNull();
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
