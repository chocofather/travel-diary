package com.example.travlediary.service.kto;

import java.io.IOException;
import java.net.URI;

@FunctionalInterface
interface KtoPhotoHttpTransport {
    KtoPhotoHttpResponse get(URI uri) throws IOException;
}
