package com.example.travlediary.repository;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 자동 추천 닉네임 중복 확인 계약.
 * 같은 조합의 언어별 표기를 한 번의 조회로 확인하므로 매퍼 인터페이스와 XML 이 어긋나면 안 된다.
 */
class NicknameRecommendationMapperContractTest {

    @Test
    void bulkNicknameLookupIsDeclaredInBothTheMapperAndItsXml() throws IOException {
        String mapper = file("src/main/java/com/example/travlediary/repository/user/UserMapper.java");
        String xml = resource("/mapper/UserMapper.xml");

        assertThat(mapper).contains(
                "int countByNicknameIn(@Param(\"nicknames\") Collection<String> nicknames);");
        assertThat(xml)
                .contains("<select id=\"countByNicknameIn\"")
                .contains("WHERE nickname IN")
                .contains("collection=\"nicknames\"");
        // 직접 입력 닉네임 검사는 기존 단건 조회를 그대로 쓴다.
        assertThat(xml).contains("<select id=\"countByNickname\"");
    }

    @Test
    void recommendationChecksTheCanonicalCombinationInsteadOfASingleString() throws IOException {
        String controller = file(
                "src/main/java/com/example/travlediary/controller/user/UserApiController.java");
        String service = file(
                "src/main/java/com/example/travlediary/service/user/UserService.java");

        // 추천은 조합의 5개 언어 표기를 한 번에 확인한다.
        assertThat(controller)
                .contains("userService.isAnyNicknameExists(combination.allDisplayNames())")
                .contains("combination.displayIn(language)");
        assertThat(service)
                .contains("public boolean isAnyNicknameExists(Collection<String> nicknames)")
                .contains("userMapper.countByNicknameIn(normalized)")
                // 사용자가 직접 입력한 닉네임 검사는 기존 정책 그대로다.
                .contains("public boolean isNicknameExists(String nickname)")
                .contains("userMapper.countByNickname(normalized)");
    }

    private String resource(String path) throws IOException {
        try (InputStream input = getClass().getResourceAsStream(path)) {
            assertThat(input).as("resource %s", path).isNotNull();
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private String file(String path) throws IOException {
        return Files.readString(Path.of(path), StandardCharsets.UTF_8);
    }
}
