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
    void onlyTheHashAndCiphertextAreWrittenNeverTheRawToken() throws IOException {
        String insert = between(mapperXml(), "<insert id=\"insertInvitation\"", "</insert>");

        assertThat(insert)
                .contains("INSERT INTO travel_plan_invitations")
                .contains("travel_plan_id, created_by_user_id, token_hash, token_encrypted, status")
                .contains("#{travelPlanId}, #{createdByUserId}, #{tokenHash}, "
                        + "#{tokenEncrypted}, #{status}")
                // invalidated_at / created_at / updated_at 은 DB DEFAULT 에 맡긴다
                .contains("useGeneratedKeys=\"true\"")
                .doesNotContain("invalidated_at")
                .doesNotContain("${");
        assertThat(insert).doesNotContain("rawToken");

        // Service 는 검증용 해시와 재표시용 암호문만 넣는다. 평문은 넣지 않는다
        String service = Files.readString(
                Path.of("src/main/java/com/example/travlediary/service/travelplan/"
                        + "TravelPlanInvitationService.java"),
                StandardCharsets.UTF_8);
        assertThat(service)
                .contains("invitation.setTokenHash(TravelPlanInviteToken.hash(rawToken))")
                .contains("invitation.setTokenEncrypted("
                        + "travelPlanInviteTokenCipher.encrypt(rawToken))")
                .doesNotContain("setTokenEncrypted(rawToken)")
                .doesNotContain("setToken(rawToken)");

        // 검증은 계속 해시로만 한다. 복호화 기반으로 바뀌지 않았다
        assertThat(between(mapperXml(), "<select id=\"findActiveByTokenHash\"", "</select>"))
                .contains("WHERE token_hash = #{tokenHash}")
                .doesNotContain("token_encrypted");
    }

    @Test
    void turningALinkOffAlsoRemovesTheWayToShowItAgain() throws IOException {
        String update = between(mapperXml(),
                "<update id=\"invalidateActiveInvitation\"", "</update>");

        // REPLACED / DISABLED 가 되면 다시 보여 줄 이유가 없다
        assertThat(update).contains("token_encrypted = NULL");
        // 무효 판정에 계속 쓰이는 해시는 남긴다
        assertThat(between(update, "SET", "WHERE")).doesNotContain("token_hash");
    }

    @Test
    void theStoredCiphertextIsAesGcmAndTheKeyComesFromTheEnvironment() throws IOException {
        String cipher = Files.readString(
                Path.of("src/main/java/com/example/travlediary/service/travelplan/"
                        + "TravelPlanInviteTokenCipher.java"),
                StandardCharsets.UTF_8);

        assertThat(cipher)
                .contains("AES/GCM/NoPadding")
                .contains("IV_BYTES = 12")
                .contains("TAG_BITS = 128")
                .contains("KEY_BYTES = 32")
                .contains("RANDOM.nextBytes(iv)")
                // 키는 환경변수에서만 온다
                .contains("@Value(\"${custom.invite-token-encryption-key:}\")");
        // 약한 방식은 쓰지 않는다
        assertThat(cipher)
                .doesNotContain("AES/ECB")
                .doesNotContain("AES/CBC");

        String applicationYml = Files.readString(
                Path.of("src/main/resources/application.yml"), StandardCharsets.UTF_8);
        // yml 에는 환경변수 참조만 있고 값이 없다
        assertThat(applicationYml).contains(
                "invite-token-encryption-key: ${TRAVEL_PLAN_INVITE_ENCRYPTION_KEY:}");
    }

    @Test
    void theCiphertextNeverRidesAlongInLogsOrDtos() throws IOException {
        String model = Files.readString(
                Path.of("src/main/java/com/example/travlediary/model/TravelPlanInvitation.java"),
                StandardCharsets.UTF_8);

        // toString 으로 새어 나가지 않게 뺀다
        assertThat(model)
                .contains("@ToString.Exclude")
                .contains("private String tokenEncrypted");

        // 미리보기 DTO 에는 토큰 계열 필드가 없다
        String previewDto = Files.readString(
                Path.of("src/main/java/com/example/travlediary/dto/"
                        + "TravelPlanInvitePreviewDto.java"),
                StandardCharsets.UTF_8);
        assertThat(previewDto)
                .doesNotContain("token")
                .doesNotContain("Token");
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
                .contains("새 링크를 발급하면 기존 초대 링크는 더 이상 사용할 수 없습니다.");

        // 예전 방식으로 만들어져 주소를 풀 수 없는 링크만 재발급 안내를 받는다
        assertThat(detail)
                .contains("th:unless=\"${travelPlanInviteUrl}\"")
                .contains("이전 저장 방식으로 만들어져 주소를 다시 표시할 수 없습니다")
                .contains("새 링크를 한 번 재발급하면 이후부터는 언제든 다시 복사할 수 있습니다");
    }

    @Test
    void theLivingLinkComesBackAfterARefreshAndStaysCopyable() throws IOException {
        String detail = detailHtml();
        String script = resource("/static/js/travel-plan-scheduler.js");

        // 발급 직후든 새로고침 뒤든 같은 자리에서 같은 주소를 보여 준다
        assertThat(detail)
                .contains("th:if=\"${travelPlanInviteUrl}\"")
                .contains("th:value=\"${travelPlanInviteUrl}\"")
                .contains("class=\"travel-plan-invite-url\" readonly")
                .contains(">복사</button>")
                .contains("data-travel-plan-invite-copy");

        // 방금 만들었을 때만 패널이 저절로 열린다
        assertThat(detail).contains("data-travel-plan-invite-issued=${travelPlanInviteIssued}");
        assertThat(script)
                .contains("[data-travel-plan-invite-issued]")
                // 복사 실패 시 값을 직접 고를 수 있게 남긴다
                .contains("url.select()")
                .contains("navigator.clipboard");
    }

    @Test
    void theInviteAreaStaysOwnerOnly() throws IOException {
        String detail = detailHtml();

        // 주소와 관리 버튼이 모두 OWNER 조건 안에 들어 있다
        int ownerGate = detail.indexOf("travelPlan.currentMember.role.name() == 'OWNER'");
        assertThat(ownerGate).isGreaterThan(0);
        assertThat(detail.indexOf("data-travel-plan-invite-url")).isGreaterThan(ownerGate);
        assertThat(detail.indexOf("/invitations/regenerate|}")).isGreaterThan(ownerGate);
    }

    @Test
    void thePreviewShowsTheRoomWithoutAnyPersonalData() throws IOException {
        String preview = resource("/templates/travelplan/invitation-preview.html");

        assertThat(preview)
                .contains("th:object=\"${travelPlanInvitePreview}\"")
                .contains("*{title}")
                .contains("${#temporals.format(travelPlanInvitePreview.startDate, 'yyyy.MM.dd')}")
                .contains("${#temporals.format(travelPlanInvitePreview.endDate, 'yyyy.MM.dd')}")
                // 정원은 서버가 내려 준 값을 쓴다 (화면에 8을 박지 않는다)
                .contains("*{memberCount} + '/' + *{memberLimit}")
                .contains("*{ownerDisplayName}")
                // 대표 이미지가 없으면 깨진 img 대신 단색 자리를 쓴다 (목록과 같은 관례)
                .contains("${#strings.isEmpty(travelPlanInvitePreview.representativeImageUrl)}");

        // 회원 개인정보는 화면에 없다
        for (String personal : new String[]{"email", "username", "nickname", "이메일", "아이디"}) {
            assertThat(preview).as("개인정보 노출: %s", personal).doesNotContain(personal);
        }
    }

    @Test
    void thePreviewOffersJoinAndTheJoinScreenOnlyAsksForARoomName() throws IOException {
        String preview = resource("/templates/travelplan/invitation-preview.html");

        // 공개 미리보기의 참여 진입점. 이 GET 은 로그인이 필요해 로그인 복귀 흐름을 탄다
        assertThat(preview)
                .contains(">참여하기</a>")
                .contains("/travel-plans/invitations/${travelPlanInviteToken}/join|}");

        // 이름 입력은 이 여행에서 쓸 이름 하나뿐이다
        assertThat(preview)
                .contains("th:object=\"${travelPlanJoinForm}\"")
                .contains("이 여행에서 사용할 이름")
                .contains("th:field=\"*{displayName}\"")
                .contains("maxlength=\"50\"")
                .contains("이 이름은 이 여행 안에서 다른 사람에게 보여집니다.")
                .contains(">여행에 참여하기</button>");

        // 서버가 정하는 값을 화면에서 받지 않는다
        for (String trusted : new String[]{
                "name=\"userId\"", "name=\"planId\"", "name=\"travelPlanId\"",
                "name=\"role\"", "name=\"memberId\"", "type=\"hidden\""}) {
            assertThat(preview).as("클라이언트가 정하면 안 되는 값: %s", trusted)
                    .doesNotContain(trusted);
        }
    }

    @Test
    void aFullOrBlockedVisitorSeesAnExplanationInsteadOfTheJoinControls() throws IOException {
        String preview = resource("/templates/travelplan/invitation-preview.html");

        assertThat(preview)
                .contains("참여 인원이 모두 찼어요.")
                .contains("현재 이 여행에 다시 참여할 수 없습니다.")
                // 참여 링크와 폼 모두 정원/차단 상태에서는 나오지 않는다
                .contains("th:if=\"${travelPlanJoinScreen == null} and not *{full} and not *{joinBlocked}\"")
                .contains("th:if=\"${travelPlanJoinForm != null} and not *{full} and not *{joinBlocked}\"");
        // 거절 사유는 같은 화면에서 알려 준다
        assertThat(preview).contains("th:if=\"${travelPlanError}\"");
    }

    @Test
    void aReturningMemberConfirmsWithTheirOldNameInsteadOfTypingANewOne() throws IOException {
        String preview = resource("/templates/travelplan/invitation-preview.html");

        assertThat(preview)
                .contains("th:if=\"${travelPlanJoinScreen != null} and *{rejoinAvailable}")
                .contains("이 여행에 다시 참여할까요?")
                .contains("${travelPlanInvitePreview.rejoinDisplayName}")
                .contains(">다시 참여하기</button>");

        // 재참여 자리에는 이름 입력이 없다 (템플릿에서 먼저 나오는 join-form 이 재참여다)
        String rejoinForm = between(preview,
                "class=\"travel-plan-join-form\" method=\"post\"", ">다시 참여하기</button>");
        assertThat(rejoinForm)
                .contains("*{rejoinAvailable}")
                .doesNotContain("<input")
                .doesNotContain("displayName\"");
    }

    @Test
    void theRejoinRevivesTheOldRowInsteadOfCreatingANewOne() throws IOException {
        String update = between(resource("/mapper/TravelPlanMapper.xml"),
                "<update id=\"reactivateMember\"", "</update>");

        assertThat(update)
                .contains("UPDATE travel_plan_members")
                .contains("SET status = #{toStatus}")
                // 떠난 흔적만 지운다 (ACTIVE row 에는 남지 않는다)
                .contains("left_at = NULL")
                .contains("removed_at = NULL")
                .contains("WHERE id = #{id}")
                .contains("AND travel_plan_id = #{travelPlanId}")
                .contains("AND user_id = #{userId}")
                .contains("AND status = #{fromStatus}")
                // 내보내진 사람은 여기서 걸린다
                .contains("AND rejoin_allowed = 1")
                .doesNotContain("${");

        // id / display_name / role 을 건드리지 않아야 작성 기록 연결이 유지된다
        assertThat(between(update, "SET", "WHERE"))
                .doesNotContain("display_name")
                .doesNotContain("role")
                .doesNotContain("user_id")
                .doesNotContain("rejoin_allowed");
    }

    @Test
    void theJoinFlowNeverInsertsForSomeoneWhoAlreadyHasARow() throws IOException {
        String service = Files.readString(
                Path.of("src/main/java/com/example/travlediary/service/travelplan/"
                        + "TravelPlanInvitationService.java"),
                StandardCharsets.UTF_8);
        String join = between(service, "public Long join(", "private void reactivate");

        // 기존 row 가 있으면 되살리고 끝난다. INSERT 로 빠지는 경로가 없다
        assertThat(join.indexOf("if (existing != null)"))
                .isLessThan(join.indexOf("insertMember("));
        assertThat(join).contains("reactivate(existing, travelPlanId, userId)");
        // 정원은 신규/재참여 갈리기 전에 본다
        assertThat(join.indexOf("countMembersByPlanAndStatus"))
                .isLessThan(join.indexOf("if (existing != null)"));
        // 이름은 신규 참여 쪽에서만 검사한다
        assertThat(join.indexOf("if (existing != null)"))
                .isLessThan(join.indexOf("TravelPlanDisplayName.normalize"));
    }

    @Test
    void theJoinPostIsCsrfProtectedWhileTheOpenPreviewStaysPublic() throws IOException {
        String securityConfig = securityConfig();

        assertThat(securityConfig)
                .contains("\"^/travel-plans/invitations/[A-Za-z0-9_-]+/join$\"");

        // 공개 미리보기 matcher 는 토큰 끝에 $ 가 있어 /join 까지 열어 주지 않는다
        assertThat(securityConfig).contains("\"^/travel-plans/invitations/[A-Za-z0-9_-]+$\"");
        assertThat(securityConfig)
                .doesNotContain("\"^/travel-plans/invitations/[A-Za-z0-9_-]+/join$\", "
                        + "HttpMethod.GET.name())).permitAll()");
    }

    @Test
    void joiningIsSerialisedByLockingThatRoomsRowBeforeCounting() throws IOException {
        String select = between(resource("/mapper/TravelPlanMapper.xml"),
                "<select id=\"findPlanByIdAndStatusForUpdate\"", "</select>");

        // 잠금 없이 세고 넣으면 동시에 들어온 두 사람이 9번째 자리를 만들 수 있다
        assertThat(select)
                .contains("FROM travel_plans")
                .contains("WHERE id = #{travelPlanId}")
                .contains("AND status = #{planStatus}")
                .contains("FOR UPDATE")
                .doesNotContain("${");

        String service = Files.readString(
                Path.of("src/main/java/com/example/travlediary/service/travelplan/"
                        + "TravelPlanInvitationService.java"),
                StandardCharsets.UTF_8);
        String join = between(service, "public Long join(", "private boolean insertMember");
        // 잠금 -> 초대 재확인 -> 참여 기록 재확인 -> 정원 -> 이름 -> INSERT
        assertThat(join.indexOf("findPlanByIdAndStatusForUpdate"))
                .isLessThan(join.indexOf("countMembersByPlanAndStatus"));
        assertThat(join.indexOf("countMembersByPlanAndStatus"))
                .isLessThan(join.indexOf("insertMember("));
        assertThat(join).contains("MAX_MEMBERS = 8".substring(0, 11));
    }

    @Test
    void theActiveCountIgnoresPeopleWhoLeftOrWereRemoved() throws IOException {
        // 정원은 ACTIVE 만 센다. 상태 조건이 없는 count 는 쓰지 않는다
        assertThat(between(resource("/mapper/TravelPlanMapper.xml"),
                "<select id=\"countMembersByPlanAndStatus\"", "</select>"))
                .contains("AND status = #{memberStatus}");

        String statuses = Files.readString(
                Path.of("src/main/java/com/example/travlediary/model/"
                        + "TravelPlanMemberStatus.java"),
                StandardCharsets.UTF_8);
        // LEFT / REMOVED row 를 읽을 수 있어야 새 참여로 덮어쓰지 않는다
        assertThat(statuses).contains("ACTIVE, LEFT, REMOVED");
    }

    @Test
    void aRoomNameIsReservedByEveryPastMemberRowNotJustActiveOnes() throws IOException {
        String select = between(resource("/mapper/TravelPlanMapper.xml"),
                "<select id=\"countMembersByPlanAndDisplayName\"", "</select>");

        assertThat(select)
                .contains("FROM travel_plan_members")
                .contains("WHERE travel_plan_id = #{travelPlanId}")
                .contains("AND display_name = #{displayName}")
                // status 조건을 걸면 나갔던 사람의 이름을 새 사람이 가져가 버린다
                .doesNotContain("status")
                .doesNotContain("${");

        // DB UNIQUE 가 최종 방어선이다
        String schema = Files.readString(
                Path.of("docs/db/travel_diary_schema_reference.md"), StandardCharsets.UTF_8);
        assertThat(between(schema, "CREATE TABLE `travel_plan_members`", ") ENGINE=InnoDB"))
                .contains("uk_travel_plan_members_plan_display_name")
                .contains("uk_travel_plan_members_plan_user");
    }

    @Test
    void aDeadLinkGetsItsOwnNoticeInsteadOfAnErrorPage() throws IOException {
        String preview = resource("/templates/travelplan/invitation-preview.html");

        assertThat(preview)
                .contains("th:if=\"${travelPlanInviteInvalid}\"")
                .contains("유효하지 않거나 만료된 초대 링크입니다.");
    }

    @Test
    void theJoinScreenStopsAtJoiningAndAddsNoMemberManagement() throws IOException {
        String preview = resource("/templates/travelplan/invitation-preview.html");

        // 신규 참여 폼과 재참여 확인 폼 둘뿐이다. 멤버 관리 계열은 다음 단계다
        assertThat(countOf(preview, "<form")).isEqualTo(2);
        for (String notYet : new String[]{
                "나가기", "내보내기", "강퇴", "멤버 목록", "이름 변경", "권한"}) {
            assertThat(preview).as("아직 없는 기능: %s", notYet).doesNotContain(notYet);
        }
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
