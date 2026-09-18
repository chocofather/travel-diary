package com.example.travlediary.service.file;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Optional;

/**
 * 이미 저장된 파일의 앞머리만 보고 정하는 이미지 형식.
 *
 * <p>확장자와 DB 에 적힌 Content-Type 은 믿지 않는다. 통제된 응답이 내려보낼 Content-Type 은
 * 여기에서 판별한 형식에서만 나온다. 저장할 때 정한 확장자와 실제 형식이 어긋난 파일은
 * 부르는 쪽이 {@link #matchesStorageKey(String)} 로 걸러낸다.
 *
 * <p>표지 라이브러리 공유 사진과 개인 다이어리 사진이 같은 판별을 쓰도록 여기 한 곳에 둔다.
 */
public enum StoredImageFormat {

    JPEG("jpg", "image/jpeg"),
    PNG("png", "image/png"),
    GIF("gif", "image/gif"),
    WEBP("webp", "image/webp");

    private final String extension;
    private final String contentType;

    StoredImageFormat(String extension, String contentType) {
        this.extension = extension;
        this.contentType = contentType;
    }

    public String extension() {
        return extension;
    }

    public String contentType() {
        return contentType;
    }

    /**
     * 저장 키의 확장자가 실제 형식과 같은지. (JPEG 는 jpg/jpeg 를 모두 같은 형식으로 본다)
     */
    public boolean matchesStorageKey(String storageKey) {
        if (storageKey == null) {
            return false;
        }
        String lower = storageKey.toLowerCase(Locale.ROOT);
        return lower.endsWith("." + extension)
                || (this == JPEG && lower.endsWith(".jpeg"));
    }

    /**
     * 파일 앞머리 signature 로 형식을 정한다.
     *
     * @return 아는 형식이면 그 형식, 그 밖에는 비어 있음
     * @throws IOException 파일을 열지 못했을 때. 부르는 쪽이 자기 문맥의 오류로 바꾼다.
     */
    public static Optional<StoredImageFormat> detect(Path source) throws IOException {
        byte[] header = new byte[12];
        int length;
        try (InputStream input = Files.newInputStream(source)) {
            length = input.readNBytes(header, 0, header.length);
        }

        if (length >= 3 && unsigned(header[0]) == 0xff
                && unsigned(header[1]) == 0xd8 && unsigned(header[2]) == 0xff) {
            return Optional.of(JPEG);
        }
        if (length >= 8 && unsigned(header[0]) == 0x89 && header[1] == 'P'
                && header[2] == 'N' && header[3] == 'G'
                && unsigned(header[4]) == 0x0d && unsigned(header[5]) == 0x0a
                && unsigned(header[6]) == 0x1a && unsigned(header[7]) == 0x0a) {
            return Optional.of(PNG);
        }
        if (length >= 6 && header[0] == 'G' && header[1] == 'I' && header[2] == 'F'
                && header[3] == '8' && (header[4] == '7' || header[4] == '9')
                && header[5] == 'a') {
            return Optional.of(GIF);
        }
        if (length >= 12 && header[0] == 'R' && header[1] == 'I'
                && header[2] == 'F' && header[3] == 'F'
                && header[8] == 'W' && header[9] == 'E'
                && header[10] == 'B' && header[11] == 'P') {
            return Optional.of(WEBP);
        }
        return Optional.empty();
    }

    private static int unsigned(byte value) {
        return value & 0xff;
    }
}
