package com.example.travlediary.repository;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class RandomRecommendMapperContractTest {

    @Test
    void regionTreeRandomQueryResolvesCountryAndExcludesHiddenPaths() throws IOException {
        String mapper = resource("/mapper/RandomRecommendMapper.xml");
        String query = between(mapper, "<select id=\"findRandomByRegionIds\"", "</select>");

        assertThat(query)
                .contains("WITH RECURSIVE destination_regions")
                .contains("SUBSTRING_INDEX(region.code, '-', 1) AS country_code")
                .contains("JOIN country_categories parent ON parent.id = destination_regions.parent_id")
                .contains("country_name")
                .contains("region_name")
                .contains("is_visible")
                .contains("ORDER BY RAND()")
                .contains("LIMIT #{limit}")
                .doesNotContain("= 7", "=7");
    }

    @Test
    void randomResultMapKeepsExistingFieldsAndAddsCardLocationFields() throws IOException {
        String mapper = resource("/mapper/RandomRecommendMapper.xml");
        String resultMap = between(mapper, "<resultMap id=\"RandomDestinationDtoMap\"", "</resultMap>");

        assertThat(resultMap)
                .contains("property=\"destinationId\"")
                .contains("property=\"destinationName\"")
                .contains("property=\"shortDescription\"")
                .contains("property=\"imageUrl\"")
                .contains("property=\"countryName\"")
                .contains("property=\"regionName\"");
    }

    @Test
    void eligibleCountryQueryStartsFromActualCountryIdsAndRequiresADisplayableDestination()
            throws IOException {
        String mapper = resource("/mapper/RandomRecommendMapper.xml");
        String query = between(mapper, "<select id=\"findRandomEligibleCountry\"", "</select>");

        assertThat(query)
                .contains("WITH RECURSIVE country_regions")
                .contains("collection=\"countryIds\"")
                .contains("JOIN destinations")
                .contains("language_code = 'ko'")
                .contains("excludeRegionId")
                .contains("ORDER BY")
                .contains("RAND()")
                .doesNotContain("depth =", "parent_id IN (1,2,3,4,5,6)", "= 7", "=7");
    }

    @Test
    void eligibleRegionQueryAnchorsCandidatesAtTheCountrysDirectChildren() throws IOException {
        String mapper = resource("/mapper/RandomRecommendMapper.xml");
        String query = between(
                mapper, "<select id=\"findRandomEligibleChildRegion\"", "</select>");

        assertThat(query)
                .contains("WITH RECURSIVE child_regions")
                .contains("child.parent_id = #{countryId}")
                .contains("JOIN destinations")
                .contains("language_code = 'ko'")
                .contains("excludeRegionId")
                .doesNotContain("depth =");
    }

    @Test
    void selectedRegionDescendantsUseOneVisibleRecursiveQuery() throws IOException {
        String mapper = resource("/mapper/RandomRecommendMapper.xml");
        String query = between(
                mapper, "<select id=\"findAllVisibleRegionIdsUnder\"", "</select>");

        assertThat(query)
                .contains("WITH RECURSIVE region_tree")
                .contains("id = #{regionId}")
                .contains("parent_id = region_tree.id")
                .contains("is_visible = 1")
                .doesNotContain("depth =");
    }

    private String resource(String path) throws IOException {
        try (InputStream input = getClass().getResourceAsStream(path)) {
            assertThat(input).as("resource %s", path).isNotNull();
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
