package com.example.travlediary.repository;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class HomeWithdrawalToastUiContractTest {

    @Test
    void withdrawalToastRemovesOnlyItsOwnQueryParameterWithoutDroppingOthersOrHash()
            throws IOException {
        String javascript = resource("static/js/home-withdrawal-toast.js");

        assertThat(javascript)
                .contains("new URL(window.location.href)")
                .contains("currentUrl.searchParams.delete(\"withdrawn\")")
                .contains("window.history.replaceState(")
                .contains("currentUrl.pathname + currentUrl.search + currentUrl.hash")
                .contains("}, 3600)");
    }

    private String resource(String path) throws IOException {
        try (var input = getClass().getClassLoader().getResourceAsStream(path)) {
            if (input == null) {
                throw new IOException("Missing test resource: " + path);
            }
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
