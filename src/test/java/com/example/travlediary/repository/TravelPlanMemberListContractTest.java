package com.example.travlediary.repository;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 플래너의 참여자 목록 계약.
 * ACTIVE 인 사람만, 방 표시 이름과 역할만 보여 주는 조회 전용 화면이다.
 */
class TravelPlanMemberListContractTest {

    @Test
    void theListOnlyReadsActiveMembersAndNeverTouchesAccounts() throws IOException {
        String select = between(resource("/mapper/TravelPlanMapper.xml"),
                "<select id=\"findActiveMembersByPlanId\"", "</select>");

        assertThat(select)
                .contains("FROM travel_plan_members")
                .contains("WHERE travel_plan_id = #{travelPlanId}")
                // LEFT / REMOVED 는 여기서 걸러진다
                .contains("AND status = #{memberStatus}")
                // 화면에 쓰는 컬럼만 읽는다
                .contains("SELECT id, display_name, role")
                .doesNotContain("user_id")
                .doesNotContain("JOIN users")
                .doesNotContain("email")
                .doesNotContain("nickname")
                .doesNotContain("${");
    }

    @Test
    void theOwnerComesFirstAndTheRestKeepTheirJoinOrder() throws IOException {
        String select = between(resource("/mapper/TravelPlanMapper.xml"),
                "<select id=\"findActiveMembersByPlanId\"", "</select>");

        // 실제 스키마에 있는 joined_at 을 참여 순서로 쓴다
        assertThat(select)
                .contains("ORDER BY CASE WHEN role = 'OWNER' THEN 0 ELSE 1 END")
                .contains("joined_at ASC")
                .contains("id ASC");

        String schema = Files.readString(
                Path.of("docs/db/travel_diary_schema_reference.md"), StandardCharsets.UTF_8);
        assertThat(between(schema, "CREATE TABLE `travel_plan_members`", ") ENGINE=InnoDB"))
                .contains("`joined_at`");
    }

    @Test
    void theViewOnlyEverSeesADisplayNameAndARole() throws IOException {
        String dto = Files.readString(
                Path.of("src/main/java/com/example/travlediary/dto/TravelPlanMemberDto.java"),
                StandardCharsets.UTF_8);

        // 모델을 그대로 넘기지 않고 필요한 값만 옮겨 담는다
        assertThat(dto)
                .contains("private String displayName")
                .contains("private TravelPlanRole role")
                .contains("private boolean currentUser")
                // 내보내기 대상만 가리키는 방 참여 id 다. users.id 가 아니다
                .contains("private Long memberId")
                .doesNotContain("private Long userId")
                .doesNotContain("email")
                .doesNotContain("nickname");
    }

    @Test
    void theCountComesFromTheListItselfNotASecondQuery() throws IOException {
        String detailDto = Files.readString(
                Path.of("src/main/java/com/example/travlediary/dto/TravelPlanDetailDto.java"),
                StandardCharsets.UTF_8);

        assertThat(detailDto)
                .contains("private List<TravelPlanMemberDto> members")
                .contains("private int memberLimit")
                .contains("return members == null ? 0 : members.size()");
    }

    @Test
    void thePlannerShowsTheCountAsAnEntryPointForEveryMember() throws IOException {
        String detail = detailHtml();

        assertThat(detail)
                .contains("data-travel-plan-members")
                .contains("'참여자 ' + ${travelPlan.memberCount} + '/'")
                .contains("${travelPlan.memberLimit}");

        // 참여자 진입점에는 OWNER 조건이 걸려 있지 않다
        String membersBlock = between(detail,
                "<div class=\"travel-plan-members\"", "data-travel-plan-members-panel");
        assertThat(membersBlock).doesNotContain("OWNER");
    }

    @Test
    void onlyTheOwnerKeepsTheInviteEntryPointBesideIt() throws IOException {
        String detail = detailHtml();

        // 초대는 그대로 OWNER 전용이고, 참여자와 나란히 놓인다
        int members = detail.indexOf("data-travel-plan-members");
        int invite = detail.indexOf("class=\"travel-plan-invite\"");
        assertThat(members).isGreaterThan(0);
        assertThat(invite).isGreaterThan(members);
        assertThat(between(detail, "class=\"travel-plan-invite\"", "data-travel-plan-invite>"))
                .contains("travelPlan.currentMember.role.name() == 'OWNER'");
        assertThat(detail).contains("class=\"travel-plan-top-actions\"");
    }

    @Test
    void thePanelSeparatesTheOwnerWithSmallTextRatherThanAColouredCard() throws IOException {
        String detail = detailHtml();
        String css = resource("/static/css/travel-plan.css");

        assertThat(detail)
                .contains("함께 계획하는 사람들")
                .contains("th:each=\"member : ${travelPlan.members}\"")
                .contains("th:text=\"${member.displayName}\"")
                .contains("${member.role.name() == 'OWNER'} ? '방장' : '멤버'")
                .contains("th:if=\"${member.currentUser}\"")
                .contains(">(나)</span>")
                .contains("'명 참여 중'");

        // 역할은 작은 글자로만 구분한다
        assertThat(between(css, ".travel-plan-member-role {", "}")).contains("font-size: 11px");
        assertThat(between(css, ".travel-plan-member-role.is-owner {", "}"))
                .contains("font-weight: 700")
                .doesNotContain("background");
        assertThat(detail).doesNotContain("modal").doesNotContain("dialog");
    }

    @Test
    void thePanelNeverPutsAUserIdOnThePage() throws IOException {
        String detail = detailHtml();
        String panel = between(detail,
                "data-travel-plan-members-panel", "</div>\n        </div>");

        for (String personal : new String[]{
                "userId", "user_id", "username", "email", "nickname"}) {
            assertThat(panel).as("개인정보 노출: %s", personal).doesNotContain(personal);
        }
    }

    @Test
    void theOwnerGetsARemoveActionOnMemberRowsOnly() throws IOException {
        String detail = detailHtml();

        // ⋯ 는 OWNER 가 볼 때, MEMBER 줄에만 붙는다 (OWNER 자신의 줄은 role 로 걸러진다)
        assertThat(detail)
                .contains("th:if=\"${viewerIsOwner and member.role.name() == 'MEMBER'}\"")
                .contains("data-travel-plan-member-menu")
                .contains(">⋯</button>")
                .contains("/members/${member.memberId}/remove|}")
                .contains("내보내기");
        // 확인 문구를 거쳐야 POST 된다
        assertThat(detail)
                .contains("님을 이 여행에서 내보낼까요?")
                .contains("내보낸 뒤에는 현재 초대 링크만으로 다시 참여할 수 없습니다.");

        // 이번 단계에 없는 관리 액션
        for (String notYet : new String[]{
                "재참여 허용", "강퇴", "이름 변경", "과거 참여자"}) {
            assertThat(detail).as("아직 없는 기능: %s", notYet).doesNotContain(notYet);
        }
        // ⋯ 메뉴 안에는 방장 넘기기와 내보내기 둘뿐이다
        assertThat(countOf(detail, "data-travel-plan-member-menu-list")).isEqualTo(1);
        assertThat(countOf(detail, "class=\"travel-plan-member-remove\"")).isEqualTo(1);
        assertThat(countOf(detail, "class=\"travel-plan-member-action\"")).isEqualTo(1);
    }

    @Test
    void theOwnerCanHandTheRoomOverFromTheSameMenu() throws IOException {
        String detail = detailHtml();

        // 방장 넘기기도 MEMBER 줄에만 붙는다 (OWNER 자신의 줄에는 메뉴 자체가 없다)
        int menu = detail.indexOf("th:if=\"${viewerIsOwner and member.role.name() == 'MEMBER'}\"");
        int transfer = detail.indexOf("/members/${member.memberId}/transfer-owner|}");
        int remove = detail.indexOf("/members/${member.memberId}/remove|}");
        assertThat(menu).isGreaterThan(0);
        assertThat(transfer).isGreaterThan(menu);
        // 메뉴 안에서 방장 넘기기가 내보내기보다 먼저 온다
        assertThat(transfer).isLessThan(remove);
        assertThat(detail).contains("방장 넘기기");

        // 바로 POST 하지 않고 확인을 거친다
        assertThat(detail)
                .contains("님에게 방장을 넘길까요?")
                .contains("초대와 멤버 관리 권한을 갖게 됩니다")
                .contains("나는 일반 멤버가 됩니다");
        // 새 UI framework 를 만들지 않는다
        assertThat(detail).doesNotContain("modal").doesNotContain("dialog");
    }

    @Test
    void theOwnerOnlyUiFollowsTheMembershipRoleWithNoSecondSourceOfTruth() throws IOException {
        String detail = detailHtml();

        // 초대 버튼과 관리 메뉴 모두 membership role 하나만 본다.
        // 이전이 끝나면 다음 렌더링에서 자연스럽게 뒤바뀐다.
        assertThat(countOf(detail, "travelPlan.currentMember.role.name() == 'OWNER'"))
                .isEqualTo(2);
        assertThat(detail).doesNotContain("createdByUserId").doesNotContain("created_by_user_id");
    }

    @Test
    void onlyAPlainMemberSeesTheLeaveAction() throws IOException {
        String detail = detailHtml();

        // 방장에게는 나가기를 노출하지 않는다
        assertThat(detail)
                .contains("th:unless=\"${viewerIsOwner}\"")
                .contains("/members/leave|}")
                .contains(">\n                여행에서 나가기\n              </button>");
        // 최소한의 확인을 거친다
        assertThat(detail)
                .contains("이 여행 계획에서 나갈까요?")
                .contains("작성했던 일정은 여행 계획에 그대로 남습니다.");
        // 새 modal framework 를 만들지 않는다
        assertThat(detail).doesNotContain("modal").doesNotContain("dialog");
    }

    @Test
    void theMemberActionsStayLowKeyRatherThanBigRedButtons() throws IOException {
        String css = resource("/static/css/travel-plan.css");

        String actions = between(css,
                ".travel-plan-member-remove,\n.travel-plan-member-leave {", "}");
        assertThat(actions)
                .contains("background: none")
                .contains("font-size: 12px")
                .contains("border: 0");
        // ⋯ 는 평소에 거의 보이지 않는다
        assertThat(between(css, ".travel-plan-member-menu-button {", "}"))
                .contains("color: #cfc4b2");
        assertThat(css).contains(".travel-plan-member:hover .travel-plan-member-menu-button");
    }

    @Test
    void bothTopPopoversShareOneOpenCloseRuleWithoutANewFramework() throws IOException {
        String script = resource("/static/js/travel-plan-scheduler.js");

        assertThat(script)
                .contains("registerPopover(")
                .contains("data-travel-plan-members-toggle")
                .contains("data-travel-plan-invite-toggle")
                // 하나를 열면 다른 하나는 닫힌다
                .contains("closePopovers(popover)")
                // 바깥 클릭 / Esc 로 닫힌다
                .contains("closePopovers(null)")
                .contains("if (event.key !== \"Escape\") return")
                // 참여자 줄의 ⋯ 도 같은 방식으로 하나만 열어 둔다
                .contains("closeMemberMenus(menu)")
                .contains("closeMemberMenus(null)");
        assertThat(script)
                .doesNotContain("WebSocket")
                .doesNotContain("setInterval")
                .doesNotContain("fetch(");
    }

    @Test
    void neitherActionEverDeletesAMemberRow() throws IOException {
        String mapper = resource("/mapper/TravelPlanMapper.xml");

        // 과거 참여 기록과 일정/대안의 created_by_member_id 가 남아야 한다
        assertThat(mapper).doesNotContain("DELETE FROM travel_plan_members");

        String left = between(mapper, "<update id=\"markMemberLeft\"", "</update>");
        assertThat(left)
                .contains("UPDATE travel_plan_members")
                .contains("SET status = #{toStatus}")
                .contains("left_at = CURRENT_TIMESTAMP")
                // 나중에 다시 들어올 여지는 남긴다
                .contains("rejoin_allowed = 1")
                .contains("updated_at = CURRENT_TIMESTAMP")
                .doesNotContain("${");

        String removed = between(mapper, "<update id=\"markMemberRemoved\"", "</update>");
        assertThat(removed)
                .contains("SET status = #{toStatus}")
                .contains("removed_at = CURRENT_TIMESTAMP")
                // 지금 가진 초대 링크만으로 되돌아오지 못한다
                .contains("rejoin_allowed = 0")
                .doesNotContain("${");

        // 작성자 기록은 어느 쪽에서도 건드리지 않는다
        for (String update : new String[]{left, removed}) {
            assertThat(update)
                    .doesNotContain("display_name")
                    .doesNotContain("created_by_member_id");
        }
    }

    @Test
    void theStatusChangesOnlyLandOnAnActiveMemberOfThatRoom() throws IOException {
        String mapper = resource("/mapper/TravelPlanMapper.xml");

        // 조건부 UPDATE 라 두 번 눌러도 한 번만 반영되고 OWNER 는 절대 걸리지 않는다
        for (String id : new String[]{"markMemberLeft", "markMemberRemoved"}) {
            assertThat(between(mapper, "<update id=\"" + id + "\"", "</update>"))
                    .as("%s", id)
                    .contains("WHERE id = #{id}")
                    .contains("AND travel_plan_id = #{travelPlanId}")
                    .contains("AND status = #{fromStatus}")
                    .contains("AND role = #{role}");
        }

        // 대상 조회도 방 소속을 함께 확인한다
        assertThat(between(mapper, "<select id=\"findMemberByPlanAndId\"", "</select>"))
                .contains("WHERE travel_plan_id = #{travelPlanId}")
                .contains("AND id = #{memberId}")
                .doesNotContain("${");
    }

    @Test
    void everyMemberPostIsCsrfProtectedAndAuthenticated() throws IOException {
        String securityConfig = Files.readString(
                Path.of("src/main/java/com/example/travlediary/config/SecurityConfig.java"),
                StandardCharsets.UTF_8);

        assertThat(securityConfig)
                .contains("\"^/travel-plans/[0-9]+/members/leave$\", HttpMethod.POST.name()")
                .contains("\"^/travel-plans/[0-9]+/members/[0-9]+/remove$\"")
                .contains("\"^/travel-plans/[0-9]+/members/[0-9]+/transfer-owner$\"");
        // 인가는 그대로 anyRequest().authenticated() 를 쓴다
        assertThat(securityConfig).doesNotContain("/travel-plans/**");
    }

    @Test
    void theHandoverOnlyEverSwapsTheRoomRole() throws IOException {
        String update = between(resource("/mapper/TravelPlanMapper.xml"),
                "<update id=\"changeMemberRole\"", "</update>");

        assertThat(update)
                .contains("UPDATE travel_plan_members")
                .contains("SET role = #{toRole}")
                .contains("WHERE id = #{id}")
                .contains("AND travel_plan_id = #{travelPlanId}")
                .contains("AND status = #{memberStatus}")
                // 이미 바뀐 상태에는 두 번 반영되지 않는다
                .contains("AND role = #{fromRole}")
                .doesNotContain("${");

        // 방 안의 역할만 바꾼다. 신분/상태/이름은 그대로다
        assertThat(between(update, "SET", "WHERE"))
                .doesNotContain("status")
                .doesNotContain("user_id")
                .doesNotContain("display_name");

        // OWNER 유일성을 보장하는 DB 제약은 없으므로 Service 가 지킨다
        String schema = Files.readString(
                Path.of("docs/db/travel_diary_schema_reference.md"), StandardCharsets.UTF_8);
        assertThat(between(schema, "CREATE TABLE `travel_plan_members`", ") ENGINE=InnoDB"))
                .doesNotContain("UNIQUE KEY `uk_travel_plan_members_plan_role`");

        String service = Files.readString(
                Path.of("src/main/java/com/example/travlediary/service/travelplan/"
                        + "TravelPlanMemberService.java"),
                StandardCharsets.UTF_8);
        String transfer = between(service, "public void transferOwnership(", "private void requireActivePlan");
        // 잠금 -> 방장 재확인 -> 내려놓기 -> 넘기기 순서다
        assertThat(transfer.indexOf("findPlanByIdAndStatusForUpdate"))
                .isLessThan(transfer.indexOf("requireActiveMember"));
        assertThat(transfer.indexOf("OWNER.name(), TravelPlanRole.MEMBER.name()"))
                .isLessThan(transfer.indexOf("MEMBER.name(), TravelPlanRole.OWNER.name()"));
        // 두 UPDATE 모두 영향 행이 1 인지 본다
        assertThat(countOf(transfer, "!= 1")).isEqualTo(2);
    }

    private int countOf(String source, String needle) {
        int count = 0;
        for (int index = source.indexOf(needle); index >= 0;
             index = source.indexOf(needle, index + needle.length())) {
            count++;
        }
        return count;
    }

    private String detailHtml() throws IOException {
        return resource("/templates/travelplan/detail.html");
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
