package com.example.travlediary.repository;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 공개 콘텐츠 작성자 표시의 판정 기준 계약.
 *
 * <p>최종 탈퇴 회원인지는 users.status 로만 판정한다. 익명 닉네임이 "탈퇴"로 시작한다는 사실에
 * 기대어 문자열로 추측하지 않고, 화면 문구를 SQL 에서 만들지도 않는다(문구는 messages 번들).
 */
class WithdrawnAuthorMapperContractTest {

    @ParameterizedTest
    @CsvSource({
            "/mapper/BoardMapper.xml",
            "/mapper/PostMapper.xml",
            "/mapper/CourseMapper.xml",
            "/mapper/MyPageBookmarkMapper.xml",
            "/mapper/PostCommentMapper.xml",
            "/mapper/CourseCommentMapper.xml",
            "/mapper/DestinationCommentMapper.xml"
    })
    void theAuthorStateComesFromUserStatusAndNeverFromTheNicknameText(String path)
            throws IOException {
        String mapper = resource(path);

        // 판정 근거는 users.status 다. 여행지 댓글은 status 자체를, 나머지는 비교 결과를 내려보낸다.
        assertThat(mapper).containsAnyOf("'DEACTIVATED'", "u.status AS u_status");
        // 화면 문구를 SQL 이 만들지 않는다.
        assertThat(mapper).doesNotContain("탈퇴한 회원");
        // 닉네임 패턴으로 탈퇴 여부를 추측하지 않는다.
        assertThat(mapper).doesNotContain("nickname LIKE '탈퇴");
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "/mapper/BoardMapper.xml           | (u.status = 'DEACTIVATED') AS writerWithdrawn",
            "/mapper/PostMapper.xml            | (u.status = 'DEACTIVATED') AS writer_withdrawn",
            "/mapper/CourseMapper.xml          | (u.status = 'DEACTIVATED') AS writer_withdrawn",
            "/mapper/MyPageBookmarkMapper.xml  | (u.status = 'DEACTIVATED') AS writerWithdrawn",
            "/mapper/PostCommentMapper.xml     | (u.status = 'DEACTIVATED') AS writerWithdrawn",
            "/mapper/CourseCommentMapper.xml   | (u.status = 'DEACTIVATED') AS writerWithdrawn"
    })
    void everyAuthorQueryCarriesTheFlagToTheView(String path, String expected) throws IOException {
        assertThat(resource(path)).contains(expected);
    }

    /** 여행지 댓글은 작성자를 User 로 중첩 매핑하므로 status 자체를 실어 보낸다. */
    @Test
    void destinationCommentsMapTheWriterStatus() throws IOException {
        String mapper = resource("/mapper/DestinationCommentMapper.xml");

        assertThat(mapper)
                .contains("<result property=\"status\"        column=\"u_status\"/>")
                .contains("u.status AS u_status");
    }

    /** 답글 멘션(@닉네임)도 같은 기준을 쓴다. */
    @ParameterizedTest
    @CsvSource({"/mapper/PostCommentMapper.xml", "/mapper/CourseCommentMapper.xml"})
    void replyMentionsUseTheSameRule(String path) throws IOException {
        assertThat(resource(path))
                .contains("(reply_target_user.status = 'DEACTIVATED') AS replyToWithdrawn");
    }

    private String resource(String path) throws IOException {
        try (InputStream input = getClass().getResourceAsStream(path)) {
            assertThat(input).as("%s", path).isNotNull();
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
