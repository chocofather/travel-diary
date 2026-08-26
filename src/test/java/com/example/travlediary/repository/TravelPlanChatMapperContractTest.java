package com.example.travlediary.repository;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 방 채팅 조회/등록 계약.
 * 컬럼명은 docs/db/travel_diary_schema_reference.md 의 실제 구조와 일치해야 한다.
 * 이번 단계에서 표를 새로 만들거나 컬럼을 더하지 않는다.
 */
class TravelPlanChatMapperContractTest {

    @Test
    void everyDeclaredMapperMethodHasAStatement() throws IOException {
        String mapper = mapperXml();
        String mapperInterface = Files.readString(
                Path.of("src/main/java/com/example/travlediary/repository/travelplan/"
                        + "TravelPlanChatMapper.java"),
                StandardCharsets.UTF_8);

        assertThat(mapper).contains("namespace=\"com.example.travlediary.repository.travelplan."
                + "TravelPlanChatMapper\"");
        for (String id : new String[]{
                "findRecentMessages", "findMessagesBefore", "findByIdAndPlanId",
                "findLatestMessageId", "insertMessage", "markMessageDeleted",
                "findLastReadMessageId", "upsertReadPosition", "countUnread"}) {
            assertThat(mapperInterface).as("interface declares %s", id).contains(id);
            assertThat(mapper).as("xml defines %s", id).contains("id=\"" + id + "\"");
        }
    }

    @Test
    void chatColumnsExistInTheSchemaReference() throws IOException {
        String schema = schemaReference();
        String messages = between(schema,
                "CREATE TABLE `travel_plan_chat_messages`", ") ENGINE=InnoDB");

        for (String column : new String[]{
                "travel_plan_id", "sender_member_id", "message_type", "content",
                "system_event_type", "deleted_at", "created_at"}) {
            assertThat(messages).as("travel_plan_chat_messages.%s", column)
                    .contains("`" + column + "`");
        }
        // 지운 메시지를 표시만 하고 남겨 두려면 이 컬럼이 있어야 한다
        assertThat(messages).contains("`deleted_at` timestamp");
        // USER / SYSTEM 만 허용된다
        assertThat(messages).contains("`message_type` in (_utf8mb4'USER',_utf8mb4'SYSTEM')");
        // 최근 N개와 [이전 메시지 보기] 가 그대로 타는 인덱스
        assertThat(messages).contains("(`travel_plan_id`,`id`)");
    }

    @Test
    void readPositionColumnsExistInTheSchemaReference() throws IOException {
        String positions = between(schemaReference(),
                "CREATE TABLE `travel_plan_chat_read_positions`", ") ENGINE=InnoDB");

        for (String column : new String[]{
                "travel_plan_id", "member_id", "last_read_message_id", "last_read_at"}) {
            assertThat(positions).as("travel_plan_chat_read_positions.%s", column)
                    .contains("`" + column + "`");
        }
        // 한 사람당 한 줄이라 한 문장으로 넣고 고칠 수 있다
        assertThat(positions).contains("UNIQUE KEY `uk_travel_plan_chat_read_positions_plan_member`"
                + " (`travel_plan_id`,`member_id`)");
    }

    @Test
    void theMapperOnlyTouchesTheTwoChatTables() throws IOException {
        String mapper = mapperXml();

        assertThat(mapper)
                .contains("travel_plan_chat_messages")
                .contains("travel_plan_chat_read_positions")
                // 이름은 그 방의 참여자 표에서 함께 읽는다
                .contains("travel_plan_members")
                // 계정 표는 들여다보지 않는다
                .doesNotContain("FROM users")
                .doesNotContain("JOIN users");
    }

    @Test
    void recentMessagesComeNewestFirstAndAreCutByCount() throws IOException {
        String select = between(mapperXml(), "<select id=\"findRecentMessages\"", "</select>");

        assertThat(select)
                .contains("FROM travel_plan_chat_messages")
                .contains("WHERE c.travel_plan_id = #{travelPlanId}")
                .contains("ORDER BY c.id DESC")
                .contains("LIMIT #{limit}");
    }

    @Test
    void olderMessagesAreCutByIdNotByOffset() throws IOException {
        String select = between(mapperXml(), "<select id=\"findMessagesBefore\"", "</select>");

        assertThat(select)
                .contains("c.id &lt; #{beforeMessageId}")
                .contains("ORDER BY c.id DESC")
                .contains("LIMIT #{limit}")
                // 앞 페이지를 세어 건너뛰지 않는다
                .doesNotContain("OFFSET");
    }

    @Test
    void theSenderNameComesFromTheRoomNotTheAccount() throws IOException {
        String mapper = mapperXml();
        String columns = between(mapper, "<sql id=\"messageColumns\">", "</sql>");

        assertThat(columns).contains("m.display_name AS sender_display_name");
        // 나갔거나 내보내진 사람의 지난 메시지도 이름이 그대로 보여야 한다
        assertThat(mapper)
                .contains("LEFT JOIN travel_plan_members m ON m.id = c.sender_member_id")
                .doesNotContain("m.status = 'ACTIVE'");
        // 계정 정보는 어떤 조회에도 실리지 않는다
        assertThat(mapper)
                .doesNotContain("user_email")
                .doesNotContain("nickname")
                .doesNotContain("u.username");
    }

    @Test
    void aDeletedMessageKeepsItsRow() throws IOException {
        String update = between(mapperXml(), "<update id=\"markMessageDeleted\"", "</update>");

        assertThat(update)
                .contains("UPDATE travel_plan_chat_messages")
                .contains("SET deleted_at = CURRENT_TIMESTAMP")
                // 보낸 사람 조건이 SQL 안에 있어 남의 메시지는 한 건도 바뀌지 않는다
                .contains("AND sender_member_id = #{senderMemberId}")
                // 이미 지워진 것을 다시 지우지 않는다
                .contains("AND deleted_at IS NULL");
        // 행을 지우는 문장은 아예 두지 않는다
        assertThat(mapperXml()).doesNotContain("DELETE FROM travel_plan_chat_messages");
    }

    @Test
    void theReadPositionIsOneRowPerMemberAndOnlyMovesForward() throws IOException {
        String insert = between(mapperXml(), "<insert id=\"upsertReadPosition\"", "</insert>");

        assertThat(insert)
                .contains("INSERT INTO travel_plan_chat_read_positions")
                .contains("ON DUPLICATE KEY UPDATE")
                // 늦게 도착한 옛 위치가 읽음을 뒤로 되돌리지 못하게 한다
                .contains("GREATEST(");
    }

    @Test
    void unreadSkipsMyOwnMessagesAndDeletedOnes() throws IOException {
        String select = between(mapperXml(), "<select id=\"countUnread\"", "</select>");

        assertThat(select)
                .contains("FROM travel_plan_chat_messages")
                .contains("sender_member_id &lt;&gt; #{memberId}")
                .contains("deleted_at IS NULL")
                .contains("message_type = 'USER'")
                .contains("id &gt; #{lastReadMessageId}");
        // 한 번도 읽지 않았으면 남의 메시지 전부가 안 읽은 것이다
        assertThat(select).contains("<if test=\"lastReadMessageId != null\">");
    }

    private String schemaReference() throws IOException {
        return Files.readString(
                Path.of("docs/db/travel_diary_schema_reference.md"), StandardCharsets.UTF_8);
    }

    private String mapperXml() throws IOException {
        try (InputStream input = getClass()
                .getResourceAsStream("/mapper/TravelPlanChatMapper.xml")) {
            assertThat(input).as("TravelPlanChatMapper.xml").isNotNull();
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
