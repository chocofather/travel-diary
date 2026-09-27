package com.example.travlediary.service.kto;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.time.Duration;

final class JdkKtoPhotoHttpTransport implements KtoPhotoHttpTransport {

    private final int connectTimeoutMillis;
    private final int readTimeoutMillis;
    private final String userAgent;

    JdkKtoPhotoHttpTransport(Duration connectTimeout, Duration readTimeout) {
        this(connectTimeout, readTimeout, null);
    }

    /** Wikimedia처럼 식별 가능한 User-Agent를 요구하는 제공처에만 userAgent를 넘긴다. */
    JdkKtoPhotoHttpTransport(Duration connectTimeout, Duration readTimeout, String userAgent) {
        this.connectTimeoutMillis = timeoutMillis(connectTimeout);
        this.readTimeoutMillis = timeoutMillis(readTimeout);
        this.userAgent = userAgent;
    }

    @Override
    public KtoPhotoHttpResponse get(URI uri) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) uri.toURL().openConnection();
        connection.setInstanceFollowRedirects(false);
        connection.setConnectTimeout(connectTimeoutMillis);
        connection.setReadTimeout(readTimeoutMillis);
        connection.setRequestMethod("GET");
        connection.setUseCaches(false);
        if (userAgent != null) {
            connection.setRequestProperty("User-Agent", userAgent);
        }

        try {
            int statusCode = connection.getResponseCode();
            InputStream body = statusCode >= 400
                    ? connection.getErrorStream()
                    : connection.getInputStream();
            return new KtoPhotoHttpResponse(
                    statusCode,
                    connection.getContentType(),
                    connection.getContentLengthLong(),
                    body,
                    connection::disconnect,
                    connection.getHeaderField("Retry-After"));
        } catch (IOException exception) {
            connection.disconnect();
            throw exception;
        }
    }

    private int timeoutMillis(Duration timeout) {
        if (timeout == null || timeout.isZero() || timeout.isNegative()
                || timeout.toMillis() > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("timeout must be a positive millisecond duration");
        }
        return (int) timeout.toMillis();
    }
}
