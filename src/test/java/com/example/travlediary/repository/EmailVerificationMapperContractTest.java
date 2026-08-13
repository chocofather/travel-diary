package com.example.travlediary.repository;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class EmailVerificationMapperContractTest {

    @Test
    void mapperRestrictsVerificationLifecycleToPendingNonDeletedUsers() throws IOException {
        String mapper = resource("mapper/UserMapper.xml");
        String obsoleteColumn = "verification_" + "sent_at";
        String obsoleteProperty = "verification" + "SentAt";

        assertThat(mapper)
                .contains("id=\"findPendingVerificationByToken\"")
                .contains("id=\"activatePendingUser\"")
                .contains("id=\"refreshVerificationToken\"")
                .contains("verification_token_exp = NULL")
                .contains("status = 'INACTIVE'")
                .contains("verification_token = #{token}")
                .contains("deleted_at IS NULL")
                .contains("verification_requested_at &lt;= #{cooldownCutoff}")
                .doesNotContain(obsoleteColumn, obsoleteProperty)
                .doesNotContain("id=\"updateUser\"");
    }

    @Test
    void schemaReferenceDocumentsVerificationExpirationAndCooldownColumns() throws IOException {
        String schema = workspaceFile("docs/db/travel_diary_schema_reference.md");
        String obsoleteColumn = "verification_" + "sent_at";
        String obsoleteProperty = "verification" + "SentAt";

        assertThat(schema)
                .contains("`verification_token_exp` datetime DEFAULT NULL")
                .contains("`verification_requested_at` datetime DEFAULT NULL")
                .doesNotContain(obsoleteColumn, obsoleteProperty);
    }

    private String resource(String path) throws IOException {
        try (var input = getClass().getClassLoader().getResourceAsStream(path)) {
            if (input == null) throw new IOException("Missing resource: " + path);
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private String workspaceFile(String path) throws IOException {
        return java.nio.file.Files.readString(java.nio.file.Path.of(path), StandardCharsets.UTF_8);
    }
}
