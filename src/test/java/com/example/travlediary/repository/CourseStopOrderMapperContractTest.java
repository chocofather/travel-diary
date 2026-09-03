package com.example.travlediary.repository;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * STOP 번호를 다시 매길 때 쓰는 조회와 갱신.
 *
 * <p>여행지가 지워지면 연결 행이 FK CASCADE 로 함께 사라진다.
 * 그 전에 어느 코스였는지 받아 두고, 지운 뒤 남은 STOP 을 보던 차례 그대로 읽는다.
 */
class CourseStopOrderMapperContractTest {

    @Test
    void theCoursesHoldingADestinationAreEachNamedOnce() throws IOException {
        String query = between(courseMapper(),
                "<select id=\"findCourseIdsByDestinationId\"", "</select>");

        assertThat(query)
                .contains("SELECT DISTINCT course_id")
                .contains("FROM course_destinations")
                .contains("WHERE destination_id = #{destinationId}");
        // 한 코스에 같은 여행지가 두 번 담겨 있어도 그 코스를 두 번 손보지 않는다
        assertThat(query).doesNotContain("SELECT course_id");
    }

    @Test
    void theRemainingStopsComeBackInTheOrderPeopleAreLookingAt() throws IOException {
        String query = between(courseMapper(),
                "<select id=\"findCourseStopOrders\"", "</select>");

        /*
          화면이 STOP 목록을 읽는 기준과 같아야 다시 매긴 번호가 차례를 뒤집지 않는다.
          visit_order 가 겹쳐 있는 어긋난 데이터에서도 id 로 갈려 결과가 늘 같다.
        */
        assertThat(query).contains("ORDER BY visit_order ASC, id ASC");
        assertThat(between(courseMapper(), "<select id=\"findCourseStops\"", "</select>"))
                .contains("ORDER BY cd.visit_order ASC, cd.id ASC");

        // 번호를 매기는 데 필요한 두 칸만 읽는다
        assertThat(query)
                .contains("SELECT id, visit_order AS visitOrder")
                .contains("WHERE course_id = #{courseId}")
                .doesNotContain("JOIN");
    }

    @Test
    void oneStopMovesAtATimeWithoutAnyTemporaryNumbers() throws IOException {
        String update = between(courseMapper(),
                "<update id=\"updateCourseDestinationVisitOrder\"", "</update>");

        assertThat(update)
                .contains("UPDATE course_destinations")
                .contains("SET visit_order = #{visitOrder}")
                .contains("WHERE id = #{id}");
        // 한 방에 밀어 넣는 창 함수를 쓰지 않는다. 관리자 삭제 때만 도는 자리다
        assertThat(update).doesNotContain("ROW_NUMBER");
    }

    @Test
    void thereIsNoUniqueNumberPerCourseSoNoShufflingIsNeeded() throws IOException {
        String schema = Files.readString(
                Path.of("docs/db/travel_diary_schema_reference.md"), StandardCharsets.UTF_8);
        String table = between(schema, "CREATE TABLE `course_destinations`", "ENGINE=");

        /*
          (course_id, visit_order) UNIQUE 가 없어 옮기는 도중 번호가 잠깐 겹쳐도 막히지 않는다.
          임시 음수나 큰 값으로 밀어 두었다가 되돌리는 절차가 필요 없는 이유다.
        */
        assertThat(table).doesNotContain("UNIQUE");
    }

    @Test
    void theScreenStillPrintsTheNumberThatIsStoredInTheDatabase() throws IOException {
        String detail = resource("/templates/course/detail.html");

        // index + 1 로 가리지 않는다. 고치는 것은 저장된 값 쪽이다
        assertThat(detail)
                .contains("#{course.detail.stop.order(${stop.visitOrder})}")
                .contains("th:text=\"${stop.visitOrder}\"");
        // STOP 이라는 말은 그대로 둔다
        assertThat(detail)
                .doesNotContain("STEP ")
                .doesNotContain("stat.index + 1");
    }

    private String courseMapper() throws IOException {
        return resource("/mapper/CourseMapper.xml");
    }

    private String resource(String path) throws IOException {
        try (InputStream input = getClass().getResourceAsStream(path)) {
            assertThat(input).as("resource %s", path).isNotNull();
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private String between(String source, String start, String end) {
        int startIndex = source.indexOf(start);
        int endIndex = source.indexOf(end, startIndex + start.length());
        assertThat(startIndex).as("start %s", start).isGreaterThanOrEqualTo(0);
        assertThat(endIndex).as("end %s", end).isGreaterThan(startIndex);
        return source.substring(startIndex, endIndex);
    }
}
