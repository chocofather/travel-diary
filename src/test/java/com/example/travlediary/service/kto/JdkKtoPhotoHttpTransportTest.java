package com.example.travlediary.service.kto;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.net.URI;
import java.net.SocketTimeoutException;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JdkKtoPhotoHttpTransportTest {

    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void doesNotFollowRedirects() throws Exception {
        AtomicInteger targetRequests = new AtomicInteger();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/redirect", exchange -> {
            exchange.getResponseHeaders().add("Location", "/target");
            exchange.sendResponseHeaders(302, -1);
            exchange.close();
        });
        server.createContext("/target", exchange -> {
            targetRequests.incrementAndGet();
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
        });
        server.start();

        JdkKtoPhotoHttpTransport transport =
                new JdkKtoPhotoHttpTransport(Duration.ofSeconds(1), Duration.ofSeconds(1));
        URI redirect = URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/redirect");

        try (KtoPhotoHttpResponse response = transport.get(redirect)) {
            assertThat(response.statusCode()).isEqualTo(302);
        }
        assertThat(targetRequests).hasValue(0);
    }

    @Test
    void appliesRequestTimeout() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/slow", exchange -> {
            try {
                Thread.sleep(500);
                exchange.sendResponseHeaders(200, -1);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            } finally {
                exchange.close();
            }
        });
        server.start();

        JdkKtoPhotoHttpTransport transport =
                new JdkKtoPhotoHttpTransport(Duration.ofSeconds(1), Duration.ofMillis(50));
        URI slow = URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/slow");

        assertThatThrownBy(() -> transport.get(slow))
                .isInstanceOf(SocketTimeoutException.class);
    }
}
