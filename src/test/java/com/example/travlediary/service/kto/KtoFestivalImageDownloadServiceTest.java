package com.example.travlediary.service.kto;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class KtoFestivalImageDownloadServiceTest {

    private static final String SOURCE_URL =
            "https://tong.visitkorea.or.kr/cms2/website/75/gyeongbokgung.jpg";
    private static byte[] jpeg;

    @TempDir
    Path uploadRoot;

    @BeforeAll
    static void createImage() throws IOException {
        BufferedImage image = new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB);
        image.setRGB(0, 0, Color.BLUE.getRGB());
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        assertThat(ImageIO.write(image, "jpg", output)).isTrue();
        jpeg = output.toByteArray();
    }

    @Test
    void storesVerifiedType3ImageBytesUnchangedInFestivalOnlyDirectory() throws Exception {
        KtoFestivalImageDownloadService service = service(
                response(200, "image/jpeg", jpeg.length, jpeg), 10 * 1024);

        KtoDownloadedFestivalImage image = service.download(SOURCE_URL);

        assertThat(image.localImageUrl()).startsWith("/uploads/travel-info/festivals/").endsWith(".jpg");
        assertThat(image.sourceImageUrl()).isEqualTo(SOURCE_URL);
        assertThat(Files.readAllBytes(storedPath(image))).isEqualTo(jpeg);
    }

    @Test
    void rejectsUnsafeUrlNonImageAndOversizedResponseWithoutCreatingFestivalFile() throws Exception {
        assertThatThrownBy(() -> service(response(200, "image/jpeg", jpeg.length, jpeg), 10 * 1024)
                .download("https://example.com/not-kto.jpg"))
                .isInstanceOf(InvalidKtoPhotoUrlException.class);
        assertThatThrownBy(() -> service(response(200, "text/html", 13, "<html></html>".getBytes()), 10 * 1024)
                .download(SOURCE_URL))
                .isInstanceOf(KtoPhotoDownloadException.class);
        assertThatThrownBy(() -> service(response(200, "image/jpeg", 1025, jpeg), 1024)
                .download(SOURCE_URL))
                .isInstanceOf(KtoPhotoDownloadException.class);
        assertThat(festivalFiles()).isEmpty();
    }

    @Test
    void deletesOnlyManagedFestivalImage() throws Exception {
        KtoFestivalImageDownloadService service = service(
                response(200, "image/jpeg", jpeg.length, jpeg), 10 * 1024);
        KtoDownloadedFestivalImage image = service.download(SOURCE_URL);
        Path stored = storedPath(image);

        assertThat(service.deleteDownloadedFestivalImage(image)).isTrue();
        assertThat(stored).doesNotExist();
        assertThat(service.deleteDownloadedFestivalImage("/uploads/destinations/not-ours.jpg")).isFalse();
        assertThat(service.deleteDownloadedFestivalImage("https://example.com/image.jpg")).isFalse();
        assertThat(service.deleteDownloadedFestivalImage(
                "/uploads/travel-info/festivals/../../outside.jpg")).isFalse();
        assertThat(service.deleteDownloadedFestivalImage(
                "/uploads/travel-info/festivals/not-a-managed-name.jpg")).isFalse();
        assertThat(service.deleteDownloadedFestivalImage(
                "/uploads/travel-info/festivals/11111111-1111-4111-8111-111111111111.jpg?download=1"))
                .isFalse();
    }

    private KtoFestivalImageDownloadService service(KtoPhotoHttpResponse response, long maxBytes) {
        return new KtoFestivalImageDownloadService(publicAddressValidator(), uri -> response, uploadRoot, maxBytes);
    }

    private KtoPhotoHttpResponse response(int status, String contentType, long contentLength, byte[] body) {
        return new KtoPhotoHttpResponse(status, contentType, contentLength, new ByteArrayInputStream(body));
    }

    private KtoPhotoUrlValidator publicAddressValidator() {
        try {
            InetAddress publicAddress = InetAddress.getByAddress(new byte[]{(byte) 203, 0, 113, 10});
            return new KtoPhotoUrlValidator(host -> new InetAddress[]{publicAddress});
        } catch (IOException exception) {
            throw new AssertionError(exception);
        }
    }

    private Path storedPath(KtoDownloadedFestivalImage image) {
        return uploadRoot.resolve(image.localImageUrl().substring("/uploads/".length()));
    }

    private java.util.List<Path> festivalFiles() throws IOException {
        Path directory = uploadRoot.resolve("travel-info/festivals");
        if (Files.notExists(directory)) {
            return java.util.List.of();
        }
        try (var files = Files.list(directory)) {
            return files.toList();
        }
    }
}
