package com.example.travlediary.service.user;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ResetTokenHasherTest {

    @Test
    void hashesWithDeterministicLowercaseSha256Hex() {
        String first = ResetTokenHasher.hash("abc");
        String second = ResetTokenHasher.hash("abc");

        assertThat(first)
                .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad")
                .isEqualTo(second)
                .matches("[0-9a-f]{64}");
    }
}
