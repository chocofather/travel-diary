package com.example.travlediary.dto;

import java.nio.file.Path;

public record DiaryCoverLibraryAssetFile(
        Path path,
        String contentType,
        long contentLength) {
}
