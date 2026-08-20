package com.example.travlediary.service.kto;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.time.Duration;

final class JdkKtoPhotoHttpTransport implements KtoPhotoHttpTransport {

    private final int connectTimeoutMillis;
    private final int readTimeoutMillis;

    JdkKtoPhotoHttpTransport(Duration connectTimeout, Duration readTimeout) {
        this.connectTimeoutMillis = timeoutMillis(connectTimeout);
        this.readTimeoutMillis = timeoutMillis(readTimeout);
    }

    @Override
    public KtoPhotoHttpResponse get(URI uri) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) uri.toURL().openConnection();
        connection.setInstanceFollowRedirects(false);
        connection.setConnectTimeout(connectTimeoutMillis);
        connection.setReadTimeout(readTimeoutMillis);
        connection.setRequestMethod("GET");
        connection.setUseCaches(false);

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
                    connection::disconnect);
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
