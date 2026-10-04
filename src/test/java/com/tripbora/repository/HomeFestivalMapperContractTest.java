package com.tripbora.repository;

import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.binding.MapperMethod;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class HomeFestivalMapperContractTest {
    @Test
    void homeUsesApplicationDateAndOneUnfinishedPeriodPerVisibleFestival() throws Exception {
        Configuration configuration = new Configuration();
        try (var input = getClass().getResourceAsStream("/mapper/TravelInfoMapper.xml")) {
            new XMLMapperBuilder(input, configuration, "mapper/TravelInfoMapper.xml",
                    configuration.getSqlFragments()).parse();
        }
        // 실제 @Param 호출은 존재하지 않는 키에 예외를 내는 ParamMap을 사용한다.
        var parameters = new MapperMethod.ParamMap<Object>();
        parameters.put("today", LocalDate.of(2026, 10, 2));
        parameters.put("limit", 8);
        var bound = configuration.getMappedStatement(
                "com.tripbora.repository.travelinfo.TravelInfoMapper.findHomeFestivals")
                .getBoundSql(parameters);
        String sql = bound.getSql().replaceAll("\\s+", " ").trim();
        assertThat(sql).contains("ti.content_type = 'FESTIVAL'", "ic.is_visible = 1",
                "candidate.end_date >= ?", "candidate.start_date <= ? THEN 0 ELSE 1 END",
                "candidate.start_date ASC, candidate.id ASC LIMIT 1",
                "ORDER BY CASE WHEN period.start_date <= ? THEN 0 ELSE 1 END",
                "period.start_date ASC, ti.id ASC LIMIT ?",
                "page.start_date ASC, page.id ASC", "ii.is_thumbnail = 1")
                .doesNotContain("CURDATE()", "courses");
        assertThat(bound.getParameterMappings()).extracting(mapping -> mapping.getProperty())
                .contains("today", "limit");
    }
}
