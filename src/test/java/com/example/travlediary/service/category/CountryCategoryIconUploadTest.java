package com.example.travlediary.service.category;

import com.example.travlediary.repository.category.CountryCategoryMapper;
import com.example.travlediary.service.file.FileUploadService;
import com.example.travlediary.service.file.UnsupportedImageFormatException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.mock.web.MockMultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * 관리자 지역 아이콘 업로드가 실제로 열리는 이미지만 받는지.
 *
 * <p>이 파일들은 {@code /uploads/icons/**} 에서 그대로 공개된다. HTML 이나 SVG 가 들어가면
 * 서비스와 같은 origin 에서 스크립트가 도는 자리가 되므로, 다른 이미지 업로드와 같은 검증을
 * 지나는지 여기에서 고정한다.
 */
class CountryCategoryIconUploadTest {

    @TempDir
    Path uploadRoot;

    private final CountryCategoryMapper mapper = mock(CountryCategoryMapper.class);

    /** 실제로 펼쳐지는 사진은 받는다. 저장 이름은 서버가 정한 UUID 다. */
    @ParameterizedTest
    @CsvSource({"jpg, image/jpeg, .jpg", "png, image/png, .png"})
    void realRasterIconsAreStoredUnderAServerChosenName(
            String format, String contentType, String expectedSuffix) throws IOException {
        CountryCategoryService service = service();

        service.saveIcon(7L, file("icon." + format, contentType, image(format)));

        var iconPath = forClass(String.class);
        verify(mapper).updateIconPath(org.mockito.ArgumentMatchers.eq(7L), iconPath.capture());
        assertThat(iconPath.getValue())
                .startsWith("/uploads/icons/")
                .endsWith(expectedSuffix)
                // 올린 이름은 저장 이름에 쓰이지 않는다
                .doesNotContain("icon." + format);
        assertThat(uploadRoot.resolve(iconPath.getValue().substring("/uploads/".length())))
                .isRegularFile();
    }

    /** WEBP 도 일반 이미지 업로드와 같은 기준으로 받는다. */
    @Test
    void webpIconsAreAccepted() {
        CountryCategoryService service = service();

        service.saveIcon(7L, file("icon.webp", "image/webp", webp()));

        var iconPath = forClass(String.class);
        verify(mapper).updateIconPath(org.mockito.ArgumentMatchers.eq(7L), iconPath.capture());
        assertThat(iconPath.getValue()).endsWith(".webp");
    }

    /**
     * 브라우저가 실행하는 내용은 받지 않는다.
     *
     * <p>HTML·SVG·XML 은 같은 origin 에서 열리면 그대로 스크립트가 된다.
     * GIF 는 일반 이미지 업로드에서도 받지 않는 형식이라 여기서도 막힌다.
     */
    @ParameterizedTest
    @CsvSource({
            "icon.html, text/html",
            "icon.svg, image/svg+xml",
            "icon.xml, application/xml",
            "icon.sh, application/x-sh"
    })
    void activeContentIsRejectedAndNothingIsStored(String fileName, String contentType)
            throws IOException {
        CountryCategoryService service = service();
        byte[] payload = ("<svg xmlns=\"http://www.w3.org/2000/svg\">"
                + "<script>alert(1)</script></svg>").getBytes(StandardCharsets.UTF_8);

        assertThatThrownBy(() -> service.saveIcon(7L, file(fileName, contentType, payload)))
                .isInstanceOf(UnsupportedImageFormatException.class);

        assertNothingStored();
    }

    /** GIF 는 일반 이미지 업로드가 받지 않는 형식이라 아이콘에서도 막힌다. */
    @Test
    void gifIsRejected() throws IOException {
        CountryCategoryService service = service();

        assertThatThrownBy(() -> service.saveIcon(7L, file("icon.gif", "image/gif", image("gif"))))
                .isInstanceOf(UnsupportedImageFormatException.class);

        assertNothingStored();
    }

    /** 확장자와 MIME 만 사진으로 바꿔 붙인 파일은 통과하지 못한다. */
    @Test
    void aFileDisguisedAsAnImageIsRejected() throws IOException {
        CountryCategoryService service = service();
        byte[] html = "<html><body><script>alert(1)</script></body></html>"
                .getBytes(StandardCharsets.UTF_8);

        assertThatThrownBy(() -> service.saveIcon(7L, file("icon.png", "image/png", html)))
                .isInstanceOf(UnsupportedImageFormatException.class);

        assertNothingStored();
    }

    /** 머리말만 그럴듯하고 실제로는 펼쳐지지 않는 파일도 막힌다. */
    @Test
    void aTruncatedImageIsRejected() throws IOException {
        CountryCategoryService service = service();
        byte[] headerOnly = {(byte) 0xff, (byte) 0xd8, (byte) 0xff, (byte) 0xe0, 0x00, 0x10};

        assertThatThrownBy(() ->
                service.saveIcon(7L, file("icon.jpg", "image/jpeg", headerOnly)))
                .isInstanceOf(UnsupportedImageFormatException.class);

        assertNothingStored();
    }

    /** 거부된 업로드는 DB 의 아이콘 경로도 바꾸지 않는다. (기존 아이콘이 그대로 남는다) */
    @Test
    void aRejectedUploadLeavesTheExistingIconPathUntouched() {
        CountryCategoryService service = service();

        assertThatThrownBy(() -> service.saveIcon(7L,
                file("icon.png", "image/png", "not an image".getBytes(StandardCharsets.UTF_8))))
                .isInstanceOf(UnsupportedImageFormatException.class);

        verify(mapper, never()).updateIconPath(org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyString());
    }

    /** 새로 올리면 경로가 갱신된다. 예전 파일은 남아 있어도 DB 는 새 아이콘을 가리킨다. */
    @Test
    void uploadingAgainPointsTheCategoryAtTheNewIcon() throws IOException {
        CountryCategoryService service = service();

        service.saveIcon(7L, file("first.png", "image/png", image("png")));
        service.saveIcon(7L, file("second.png", "image/png", image("png")));

        var iconPath = forClass(String.class);
        verify(mapper, org.mockito.Mockito.times(2))
                .updateIconPath(org.mockito.ArgumentMatchers.eq(7L), iconPath.capture());
        assertThat(iconPath.getAllValues()).hasSize(2)
                .doesNotHaveDuplicates();
    }

    /** 파일을 고르지 않았으면 아무것도 하지 않는다. (기존 동작) */
    @Test
    void anEmptySelectionChangesNothing() {
        CountryCategoryService service = service();

        service.saveIcon(7L, null);
        service.saveIcon(7L, file("icon.png", "image/png", new byte[0]));

        verify(mapper, never()).updateIconPath(org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyString());
    }

    /* ===== 도우미 ===== */

    private CountryCategoryService service() {
        return new CountryCategoryService(mapper, new FileUploadService(uploadRoot.toString()));
    }

    private void assertNothingStored() throws IOException {
        Path icons = uploadRoot.resolve("icons");
        assertThat(Files.notExists(icons) || Files.list(icons).findAny().isEmpty()).isTrue();
    }

    private MockMultipartFile file(String fileName, String contentType, byte[] content) {
        return new MockMultipartFile("icon", fileName, contentType, content);
    }

    private byte[] image(String format) throws IOException {
        BufferedImage drawn = new BufferedImage(8, 8, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        assertThat(ImageIO.write(drawn, format, bytes)).isTrue();
        return bytes.toByteArray();
    }

    /**
     * 가장 작은 RIFF/VP8L WEBP 한 장(1x1).
     *
     * <p>WEBP 는 표준 ImageIO 에 reader 가 없어 업로드 검증이 RIFF 구조를 직접 읽는다.
     * 그래서 여기서도 그 검증이 실제로 지나는 바이트를 만들어 준다 —
     * 청크 크기를 짝수로 두어 padding 없이 파일 끝과 정확히 맞춘다.
     */
    private byte[] webp() {
        // VP8L 페이로드: 서명(0x2f) + 크기/버전 4바이트(전부 0 → 1x1, version 0) + 여분 1바이트
        byte[] payload = {0x2f, 0, 0, 0, 0, 0};
        byte[] data = new byte[12 + 8 + payload.length];
        data[0] = 'R'; data[1] = 'I'; data[2] = 'F'; data[3] = 'F';
        writeLittleEndian(data, 4, data.length - 8);
        data[8] = 'W'; data[9] = 'E'; data[10] = 'B'; data[11] = 'P';
        data[12] = 'V'; data[13] = 'P'; data[14] = '8'; data[15] = 'L';
        writeLittleEndian(data, 16, payload.length);
        System.arraycopy(payload, 0, data, 20, payload.length);
        return data;
    }

    private void writeLittleEndian(byte[] data, int offset, int value) {
        for (int index = 0; index < 4; index++) {
            data[offset + index] = (byte) ((value >> (8 * index)) & 0xff);
        }
    }
}
