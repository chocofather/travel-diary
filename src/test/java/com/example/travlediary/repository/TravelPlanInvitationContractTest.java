package com.example.travlediary.repository;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 초대 링크의 매퍼 / 보안 / 화면 계약.
 * raw token 은 DB 어디에도 남지 않고, 관리 기능은 OWNER 화면에만 있다.
 */
class TravelPlanInvitationContractTest {

    @Test
    void everyDeclaredMapperMethodHasAStatement() throws IOException {
        String mapper = mapperXml();
        String mapperInterface = Files.readString(
                Path.of("src/main/java/com/example/travlediary/repository/travelplan/"
                        + "TravelPlanInvitationMapper.java"),
                StandardCharsets.UTF_8);

        assertThat(mapper).contains("namespace=\"com.example.travlediary.repository.travelplan."
                + "TravelPlanInvitationMapper\"");
        for (String id : new String[]{
                "findActiveByPlanId", "insertInvitation", "invalidateActiveInvitation",
                "findActiveByTokenHash"}) {
            assertThat(mapperInterface).as("interface declares %s", id).contains(id);
            assertThat(mapper).as("xml defines %s", id).contains("id=\"" + id + "\"");
        }
    }

    @Test
    void invitationColumnsExistInTheSchemaReference() throws IOException {
        String schema = Files.readString(
                Path.of("docs/db/travel_diary_schema_reference.md"), StandardCharsets.UTF_8);
        String table = between(schema,
                "CREATE TABLE `travel_plan_invitations`", ") ENGINE=InnoDB");

        for (String column : new String[]{
                "travel_plan_id", "created_by_user_id", "token_hash", "status", "invalidated_at"}) {
            assertThat(table).as("travel_plan_invitations.%s", column).contains("`" + column + "`");
        }
        // SHA-256 hex 가 그대로 들어가는 자리다
        assertThat(table).contains("`token_hash` char(64)");
        // raw token 을 담을 컬럼은 애초에 없다
        assertThat(table).doesNotContain("`token`");
    }

    @Test
    void onlyTheHashIsWrittenAndNeverTheRawToken() throws IOException {
        String insert = between(mapperXml(), "<insert id=\"insertInvitation\"", "</insert>");

        assertThat(insert)
                .contains("INSERT INTO travel_plan_invitations")
                .contains("travel_plan_id, created_by_user_id, token_hash, status")
                .contains("#{travelPlanId}, #{createdByUserId}, #{tokenHash}, #{status}")
                // invalidated_at / created_at / updated_at 은 DB DEFAULT 에 맡긴다
                .contains("useGeneratedKeys=\"true\"")
                .doesNotContain("invalidated_at")
                .doesNotContain("${");
        assertThat(insert).doesNotContain("rawToken");

        // Service 도 해시만 넣는다
        String service = Files.readString(
                Path.of("src/main/java/com/example/travlediary/service/travelplan/"
                        + "TravelPlanInvitationService.java"),
                StandardCharsets.UTF_8);
        assertThat(service).contains("invitation.setTokenHash(TravelPlanInviteToken.hash(rawToken))");
        assertThat(service).doesNotContain("setToken(rawToken)");
    }

    @Test
    void aLinkIsOnlyEverResolvedByHashAndOnlyWhileActive() throws IOException {
        String select = between(mapperXml(), "<select id=\"findActiveByTokenHash\"", "</select>");

        assertThat(select)
                .contains("FROM travel_plan_invitations")
                .contains("WHERE token_hash = #{tokenHash}")
                // REPLACED / DISABLED 는 여기서 걸러진다
                .contains("AND status = #{status}")
                .doesNotContain("${");
    }

    @Test
    void turningALinkOffStampsTheTimeAndOnlyTouchesTheLiveOne() throws IOException {
        String update = between(mapperXml(),
                "<update id=\"invalidateActiveInvitation\"", "</update>");

        assertThat(update)
                .contains("UPDATE travel_plan_invitations")
                .contains("SET status = #{toStatus}")
                .contains("invalidated_at = CURRENT_TIMESTAMP")
                .contains("WHERE travel_plan_id = #{travelPlanId}")
                // 이미 꺼진 링크의 invalidated_at 을 덮어쓰지 않는다
                .contains("AND status = #{fromStatus}")
                .doesNotContain("${");
    }

    @Test
    void theActiveLookupIsScopedToItsPlan() throws IOException {
        String select = between(mapperXml(), "<select id=\"findActiveByPlanId\"", "</select>");

        assertThat(select)
                .contains("WHERE travel_plan_id = #{travelPlanId}")
                .contains("AND status = #{status}")
                .contains("LIMIT 1")
                .doesNotContain("${");
    }

    @Test
    void thePreviewReadsTheRoomsOwnNameAndNeverTheAccount() throws IOException {
        String select = between(resource("/mapper/TravelPlanMapper.xml"),
                "<select id=\"findMemberByPlanAndRole\"", "</select>");

        assertThat(select)
                .contains("FROM travel_plan_members")
                .contains("WHERE travel_plan_id = #{travelPlanId}")
                .contains("AND role = #{role}")
                .contains("AND status = #{memberStatus}")
                .contains("display_name")
                // users 를 JOIN 하지 않으므로 이메일/아이디/닉네임이 따라올 수 없다
                .doesNotContain("JOIN users")
                .doesNotContain("email")
                .doesNotContain("nickname")
                .doesNotContain("${");

        assertThat(between(resource("/mapper/TravelPlanMapper.xml"),
                "<select id=\"countMembersByPlanAndStatus\"", "</select>"))
                .contains("SELECT COUNT(*)")
                .contains("FROM travel_plan_members")
                .contains("WHERE travel_plan_id = #{travelPlanId}")
                .contains("AND status = #{memberStatus}");
    }

    @Test
    void theOwnerActionsAreCsrfProtectedAndThePreviewGetIsPublic() throws IOException {
        String securityConfig = securityConfig();

        // 생성 / 재발급 / 비활성화 POST
        assertThat(securityConfig)
                .contains("\"^/travel-plans/[0-9]+/invitations$\", HttpMethod.POST.name()")
                .contains("\"^/travel-plans/[0-9]+/invitations/regenerate$\"")
                .contains("\"^/travel-plans/[0-9]+/invitations/disable$\"");

        // 미리보기 GET 만 공개한다. 방 관리 경로는 그대로 인증이 필요하다
        assertThat(securityConfig)
                .contains("\"^/travel-plans/invitations/[A-Za-z0-9_-]+$\"")
                .doesNotContain("/travel-plans/**");
    }

    @Test
    void theInviteEntryPointIsOwnerOnlyAndStaysASmallTopAction() throws IOException {
        String detail = detailHtml();

        // OWNER 만 본다
        assertThat(detail)
                .contains("travelPlan.currentMember.role.name() == 'OWNER'")
                .contains("data-travel-plan-invite")
                .contains(">초대</button>")
                .contains("class=\"travel-plan-page-top-row\"");
        // 큰 관리 화면을 만들지 않는다 (플래너 안의 작은 패널 하나뿐)
        assertThat(countOf(detail, "data-travel-plan-invite-panel")).isEqualTo(1);
        assertThat(detail).doesNotContain("modal").doesNotContain("dialog");
    }

    @Test
    void theIssuedLinkIsShownWithACopyControlAndOnlyRightAfterIssuing() throws IOException {
        String detail = detailHtml();
        String script = resource("/static/js/travel-plan-scheduler.js");

        // flash 로 온 값이라 새로고침하면 사라진다
        assertThat(detail)
                .contains("th:if=\"${travelPlanInviteUrl}\"")
                .contains("th:value=\"${travelPlanInviteUrl}\"")
                // 복사가 막혀도 값을 직접 고를 수 있다
                .contains("class=\"travel-plan-invite-url\" readonly")
                .contains(">복사</button>")
                .contains("data-travel-plan-invite-copy");
        assertThat(script)
                .contains("navigator.clipboard")
                .contains("url.select()");
    }

    @Test
    void anActiveLinkOffersRegenerateAndDisableWhileAnEmptyOneOnlyOffersCreate()
            throws IOException {
        String detail = detailHtml();

        assertThat(detail)
                .contains("th:if=\"${travelPlanInviteActive}\"")
                .contains("th:unless=\"${travelPlanInviteActive}\"")
                .contains("/invitations/regenerate|}")
                .contains("/invitations/disable|}")
                .contains("새 링크 재발급")
                .contains(">초대 링크 비활성화</button>")
                .contains(">초대 링크 만들기</button>")
                // 이전 링크 주소는 되살릴 수 없다는 점을 화면에서 알린다
                .contains("이전 링크 주소는 다시 볼 수 없어요")
                .contains("새 링크를 발급하면 기존 초대 링크는 더 이상 사용할 수 없습니다.");
    }

    @Test
    void thePreviewShowsTheRoomWithoutAnyPersonalData() throws IOException {
        String preview = resource("/templates/travelplan/invitation-preview.html");

        assertThat(preview)
                .contains("th:object=\"${travelPlanInvitePreview}\"")
                .contains("*{title}")
                .contains("${#temporals.format(travelPlanInvitePreview.startDate, 'yyyy.MM.dd')}")
                .contains("${#temporals.format(travelPlanInvitePreview.endDate, 'yyyy.MM.dd')}")
                .contains("*{memberCount} + '/8'")
                .contains("*{ownerDisplayName}")
                // 대표 이미지가 없으면 깨진 img 대신 단색 자리를 쓴다 (목록과 같은 관례)
                .contains("${#strings.isEmpty(travelPlanInvitePreview.representativeImageUrl)}");

        // 회원 개인정보는 화면에 없다
        for (String personal : new String[]{"email", "username", "nickname", "이메일", "아이디"}) {
            assertThat(preview).as("개인정보 노출: %s", personal).doesNotContain(personal);
        }
    }

    @Test
    void aDeadLinkGetsItsOwnNoticeInsteadOfAnErrorPage() throws IOException {
        String preview = resource("/templates/travelplan/invitation-preview.html");

        assertThat(preview)
                .contains("th:if=\"${travelPlanInviteInvalid}\"")
                .contains("유효하지 않거나 만료된 초대 링크입니다.");
    }

    @Test
    void thePreviewHasNoJoinActionYet() throws IOException {
        String preview = resource("/templates/travelplan/invitation-preview.html");

        // 참여는 다음 단계다. 지금은 폼도 POST 도 없다
        assertThat(preview)
                .doesNotContain("method=\"post\"")
                .doesNotContain("<form")
                .doesNotContain("displayName");
        assertThat(preview).contains("참여하기는 준비 중이에요.");
    }

    private String mapperXml() throws IOException {
        return resource("/mapper/TravelPlanInvitationMapper.xml");
    }

    private String detailHtml() throws IOException {
        return resource("/templates/travelplan/detail.html");
    }

    private String securityConfig() throws IOException {
        return Files.readString(
                Path.of("src/main/java/com/example/travlediary/config/SecurityConfig.java"),
                StandardCharsets.UTF_8);
    }

    private String resource(String path) throws IOException {
        try (InputStream input = getClass().getResourceAsStream(path)) {
            assertThat(input).as("resource %s", path).isNotNull();
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private int countOf(String source, String needle) {
        int count = 0;
        for (int index = source.indexOf(needle); index >= 0;
             index = source.indexOf(needle, index + needle.length())) {
            count++;
        }
        return count;
    }

    private String between(String source, String start, String end) {
        int startIndex = source.indexOf(start);
        int endIndex = source.indexOf(end, startIndex + start.length());
        assertThat(startIndex).as("start %s", start).isGreaterThanOrEqualTo(0);
        assertThat(endIndex).isGreaterThan(startIndex);
        return source.substring(startIndex, endIndex);
    }
}
