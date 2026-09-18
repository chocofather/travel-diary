package com.example.travlediary.service.file;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 일반 이미지 업로드(에디터·게시글·댓글·다이어리·표지·이벤트)가 함께 쓰는 {@link FileUploadService#saveFile}.
 *
 * <p>/uploads/** 는 같은 origin 에서 누구나 열 수 있으므로, 실행될 수 있는 파일이 저장되면 안 된다.
 * 파일 이름·확장자·클라이언트 MIME 이 아니라 실제 bytes 로 JPEG/PNG/WEBP 인지 보고,
 * 저장 확장자도 서버가 판별한 형식으로 정한다.
 */
class FileUploadServiceGeneralImageTest {

    /** 1x1 WEBP (VP8). 스티커 업로드 테스트와 같은 표본이다. */
    private static final byte[] VALID_WEBP = Base64.getDecoder().decode(
            "UklGRiIAAABXRUJQVlA4IBYAAAAwAQCdASoBAAEAAUAmJaQAA3AA/v89");

    @TempDir
    Path uploadRoot;

    /** 진짜 JPEG/PNG/WEBP 는 받고, 저장 이름은 UUID + 판별한 확장자다. */
    @Test
    void realJpegPngAndWebpAreStoredWithTheDetectedExtension() throws IOException {
        FileUploadService service = service();

        String jpeg = service.saveFile(file("a.jpeg", "image/jpeg", raster("jpg")), "editor");
        String png = service.saveFile(file("b.png", "image/png", raster("png")), "posts");
        String webp = service.saveFile(file("c.webp", "image/webp", VALID_WEBP), "comments");

        assertThat(jpeg).matches("^/uploads/editor/[0-9a-f-]{36}\\.jpg$");
        assertThat(png).matches("^/uploads/posts/[0-9a-f-]{36}\\.png$");
        assertThat(webp).matches("^/uploads/comments/[0-9a-f-]{36}\\.webp$");
        assertThat(stored(jpeg)).isRegularFile();
        assertThat(stored(png)).isRegularFile();
        assertThat(Files.readAllBytes(stored(webp))).isEqualTo(VALID_WEBP);
    }

    /** 원본 확장자·MIME 이 틀려도 실제 형식으로 저장한다. (파일명의 경로 조각도 쓰지 않는다) */
    @Test
    void theOriginalFileNameAndMimeAreNotTrusted() throws IOException {
        String url = service().saveFile(
                file("../../photo.html", "text/html", raster("png")), "editor");

        assertThat(url).matches("^/uploads/editor/[0-9a-f-]{36}\\.png$");
        assertThat(stored(url)).isRegularFile();
    }

    /** HTML 은 이미지 이름·MIME 을 달아도 받지 않는다. */
    @Test
    void htmlIsRejectedEvenWhenItPretendsToBeAnImage() {
        byte[] html = "<!doctype html><script>alert(document.cookie)</script>"
                .getBytes(StandardCharsets.UTF_8);

        assertRejected(file("evil.html", "text/html", html));
        assertRejected(file("evil.jpg", "image/jpeg", html));
        assertRejected(file("evil.png", "image/png", html));
    }

    /** 스크립트를 품은 SVG 는 받지 않는다. (XML 문서도 마찬가지다) */
    @Test
    void scriptSvgAndXmlAreRejected() {
        byte[] svg = ("<svg xmlns=\"http://www.w3.org/2000/svg\" onload=\"alert(1)\">"
                + "<script>alert(1)</script></svg>").getBytes(StandardCharsets.UTF_8);
        byte[] xml = "<?xml version=\"1.0\"?><a/>".getBytes(StandardCharsets.UTF_8);

        assertRejected(file("x.svg", "image/svg+xml", svg));
        assertRejected(file("x.png", "image/png", svg));
        assertRejected(file("x.xml", "application/xml", xml));
    }

    /** GIF·PDF·실행 파일, signature 만 흉내 낸 파일, 깨진 이미지는 받지 않는다. */
    @Test
    void otherFormatsDisguisedAndCorruptedImagesAreRejected() throws IOException {
        byte[] gif = raster("gif");
        byte[] pdf = "%PDF-1.7\n1 0 obj<<>>endobj".getBytes(StandardCharsets.UTF_8);
        byte[] exe = new byte[]{'M', 'Z', (byte) 0x90, 0x00, 0x03, 0x00, 0x00, 0x00};
        byte[] jpegHeaderOnly = new byte[]{(byte) 0xff, (byte) 0xd8, (byte) 0xff, (byte) 0xe0, 0x00, 0x10};
        byte[] pngThenScript = concat(
                new byte[]{(byte) 0x89, 'P', 'N', 'G', 0x0d, 0x0a, 0x1a, 0x0a},
                "<script>alert(1)</script>".getBytes(StandardCharsets.UTF_8));
        byte[] brokenWebp = Base64.getDecoder().decode("UklGRgQAAABXRUJQ");

        assertRejected(file("a.gif", "image/gif", gif));
        assertRejected(file("a.pdf", "application/pdf", pdf));
        assertRejected(file("a.exe", "application/octet-stream", exe));
        assertRejected(file("a.jpg", "image/jpeg", jpegHeaderOnly));
        assertRejected(file("a.png", "image/png", pngThenScript));
        assertRejected(file("a.webp", "image/webp", brokenWebp));
    }

    /** 거부된 파일은 업로드 폴더에 아무것도 남기지 않는다. */
    @Test
    void aRejectedFileLeavesNothingOnDisk() throws IOException {
        FileUploadService service = service();
        byte[] html = "<script>alert(1)</script>".getBytes(StandardCharsets.UTF_8);

        assertThatThrownBy(() -> service.saveFile(file("x.jpg", "image/jpeg", html), "editor"))
                .isInstanceOf(UnsupportedImageFormatException.class)
                .hasMessage(FileUploadService.UNSUPPORTED_IMAGE_MESSAGE);

        Path editor = uploadRoot.resolve("editor");
        assertThat(Files.notExists(editor) || isEmpty(editor)).isTrue();
    }

    /** multipart 한도(10MB)를 넘는 파일은 펼쳐 보기 전에 끊는다. */
    @Test
    void anOversizedFileIsRejectedBeforeItIsRead() {
        byte[] huge = new byte[10 * 1024 * 1024 + 1];
        huge[0] = (byte) 0xff;
        huge[1] = (byte) 0xd8;
        huge[2] = (byte) 0xff;

        assertThatThrownBy(() -> service().saveFile(file("big.jpg", "image/jpeg", huge), "editor"))
                .isInstanceOf(UnsupportedImageFormatException.class)
                .hasMessage(FileUploadService.OVERSIZED_IMAGE_MESSAGE);
    }

    /* ===== 도우미 ===== */

    private void assertRejected(MockMultipartFile file) {
        assertThatThrownBy(() -> service().saveFile(file, "editor"))
                .isInstanceOf(UnsupportedImageFormatException.class)
                .hasMessage(FileUploadService.UNSUPPORTED_IMAGE_MESSAGE);
        assertThat(Files.notExists(uploadRoot.resolve("editor"))
                || isEmpty(uploadRoot.resolve("editor"))).isTrue();
    }

    private FileUploadService service() {
        return new FileUploadService(uploadRoot.toString());
    }

    private MockMultipartFile file(String name, String contentType, byte[] content) {
        return new MockMultipartFile("image", name, contentType, content);
    }

    private Path stored(String url) {
        return uploadRoot.resolve(url.substring("/uploads/".length()));
    }

    private boolean isEmpty(Path directory) {
        try (var files = Files.list(directory)) {
            return files.findAny().isEmpty();
        } catch (IOException exception) {
            throw new IllegalStateException(exception);
        }
    }

    /** 실제로 펼쳐지는 작은 이미지 한 장. */
    private byte[] raster(String format) throws IOException {
        BufferedImage drawn = new BufferedImage(8, 6, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        assertThat(ImageIO.write(drawn, format, bytes)).isTrue();
        return bytes.toByteArray();
    }

    private byte[] concat(byte[] first, byte[] second) {
        byte[] joined = new byte[first.length + second.length];
        System.arraycopy(first, 0, joined, 0, first.length);
        System.arraycopy(second, 0, joined, first.length, second.length);
        return joined;
    }
}
