package com.example.travlediary.service.file;

import com.example.travlediary.model.DiaryStickerType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FileUploadServiceDiaryStickerTest {

    private static final byte[] VALID_WEBP = java.util.Base64.getDecoder().decode(
            "UklGRiIAAABXRUJQVlA4IBYAAAAwAQCdASoBAAEAAUAmJaQAA3AA/v89");

    @Test
    void pngStickerIsStoredUnderTheUploadDirectoryForItsType(@TempDir Path uploadRoot)
            throws Exception {
        byte[] png = java.util.Base64.getDecoder().decode(
                "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=");
        FileUploadService service = new FileUploadService(uploadRoot.toString());

        String url = service.saveDiaryStickerImage(new MockMultipartFile(
                "image", "star.png", "image/png", png), DiaryStickerType.NORMAL);

        assertThat(url).startsWith("/uploads/diary-stickers/normal/").endsWith(".png");
        assertThat(uploadRoot.resolve(url.substring("/uploads/".length()))).isRegularFile();
    }

    @Test
    void anImageSignatureWithoutDecodableImageDataIsRejected(@TempDir Path uploadRoot) {
        FileUploadService service = new FileUploadService(uploadRoot.toString());
        byte[] headerOnly = new byte[]{(byte) 0x89, 'P', 'N', 'G', 13, 10, 26, 10};

        assertThatThrownBy(() -> service.saveDiaryStickerImage(new MockMultipartFile(
                "image", "broken.png", "image/png", headerOnly), DiaryStickerType.NORMAL))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("실제 PNG 또는 WebP");
        assertThat(Files.exists(uploadRoot.resolve("diary-stickers"))).isFalse();
    }

    @Test
    void jpegStickerIsRejectedEvenWhenItsMimeAndExtensionMatch(@TempDir Path uploadRoot) {
        FileUploadService service = new FileUploadService(uploadRoot.toString());
        byte[] jpeg = new byte[]{(byte) 0xff, (byte) 0xd8, (byte) 0xff, (byte) 0xd9};

        assertThatThrownBy(() -> service.saveDiaryStickerImage(new MockMultipartFile(
                "image", "photo.jpg", "image/jpeg", jpeg), DiaryStickerType.NORMAL))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("PNG 또는 WebP");
    }

    @Test
    void structurallyValidWebpStickerIsStored(@TempDir Path uploadRoot) throws Exception {
        FileUploadService service = new FileUploadService(uploadRoot.toString());

        String url = service.saveDiaryStickerImage(new MockMultipartFile(
                "image", "tape.webp", "image/webp", VALID_WEBP),
                DiaryStickerType.MASKING_TAPE);

        assertThat(url).startsWith("/uploads/diary-stickers/masking-tape/").endsWith(".webp");
        assertThat(uploadRoot.resolve(url.substring("/uploads/".length()))).isRegularFile();
    }

    @Test
    void webpContainerWithoutDecodableFrameIsRejected(@TempDir Path uploadRoot) {
        FileUploadService service = new FileUploadService(uploadRoot.toString());
        byte[] headerOnly = java.util.Base64.getDecoder().decode(
                "UklGRhYAAABXRUJQVlA4WAoAAAAAAAAAAAAAAAAA");

        assertThatThrownBy(() -> service.saveDiaryStickerImage(new MockMultipartFile(
                "image", "broken.webp", "image/webp", headerOnly),
                DiaryStickerType.NORMAL))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("실제 PNG 또는 WebP");
        assertThat(Files.exists(uploadRoot.resolve("diary-stickers"))).isFalse();
    }
}
