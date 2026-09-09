package com.example.travlediary.service.translation;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TranslationIpSubjectHasherTest {

    @Test
    void producesAStableFixedLengthIdentifierWithoutEmbeddingTheIp() {
        TranslationIpSubjectHasher hasher = new TranslationIpSubjectHasher("test-only-secret");

        byte[] first = hasher.hash("203.0.113.9");
        byte[] second = hasher.hash("203.0.113.9");

        assertThat(first).hasSize(32).isEqualTo(second);
        assertThat(new String(first, StandardCharsets.UTF_8)).doesNotContain("203.0.113.9");
        assertThat(hasher.hash("203.0.113.10")).isNotEqualTo(first);
    }

    @Test
    void refusesToHashWithoutAConfiguredSecret() {
        assertThatThrownBy(() -> new TranslationIpSubjectHasher(" ").hash("203.0.113.9"))
                .isInstanceOf(IllegalStateException.class);
    }
}
