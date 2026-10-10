package com.tripbora.service.kto;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.net.InetAddress;
import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class KtoPhotoUrlValidatorTest {

    private static final String HTTP_PHOTO_URL =
            "http://tong.visitkorea.or.kr/cms2/website/75/1002175.jpg";
    private static final String HTTPS_PHOTO_URL =
            "https://tong.visitkorea.or.kr/cms2/website/75/1002175.jpg";
    private static final String FESTIVAL_RESOURCE_URL =
            "https://tong.visitkorea.or.kr/cms/resource/35/4100435_image2_1.jpg";

    @Test
    void acceptsHttpAndHttpsKtoWebsiteImagesResolvedToPublicAddresses() throws Exception {
        KtoPhotoUrlValidator validator = publicAddressValidator();

        assertThat(validator.validate(HTTP_PHOTO_URL)).isEqualTo(URI.create(HTTP_PHOTO_URL));
        assertThat(validator.validate(HTTPS_PHOTO_URL)).isEqualTo(URI.create(HTTPS_PHOTO_URL));
    }

    @Test
    void acceptsTourApiFestivalResourceImagesResolvedToPublicAddresses() throws Exception {
        KtoPhotoUrlValidator validator = publicAddressValidator();

        assertThat(validator.validate(FESTIVAL_RESOURCE_URL)).isEqualTo(URI.create(FESTIVAL_RESOURCE_URL));
    }

    /** TourAPI 검색 결과로 실제 내려온 원본 사진 주소. 다중 등록에서 이 주소만 막혀 전체가 실패했다. */
    @Test
    void acceptsHttpsTourApiResourcePhotoImages() throws Exception {
        KtoPhotoUrlValidator validator = publicAddressValidator();
        String resourcePhotoUrl = "https://tong.visitkorea.or.kr/cms/resource_photo/79/3414579_image2_1.jpg";

        assertThat(validator.validate(resourcePhotoUrl)).isEqualTo(URI.create(resourcePhotoUrl));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            // 새 경로는 HTTPS만 허용한다
            "http://tong.visitkorea.or.kr/cms/resource_photo/79/3414579_image2_1.jpg",
            "https://tong.visitkorea.or.kr/cms/resource_photos/79/3414579_image2_1.jpg",
            "https://tong.visitkorea.or.kr/cms/resource_photo%2f79/3414579_image2_1.jpg",
            "https://tong.visitkorea.or.kr/cms/resource_photo/../../etc/passwd",
            "https://tong.visitkorea.or.kr/cms/resource_photo/%2e%2e/cms2/website/75/1002175.jpg",
            "https://example.com/cms/resource_photo/79/3414579_image2_1.jpg",
            "https://tong.visitkorea.or.kr.attacker.com/cms/resource_photo/79/3414579_image2_1.jpg",
            "https://localhost/cms/resource_photo/79/3414579_image2_1.jpg",
            "https://tong.visitkorea.or.kr:8443/cms/resource_photo/79/3414579_image2_1.jpg"
    })
    void rejectsLookalikeOrInsecureResourcePhotoUrls(String imageUrl) throws Exception {
        KtoPhotoUrlValidator validator = publicAddressValidator();

        assertThatThrownBy(() -> validator.validate(imageUrl))
                .isInstanceOf(InvalidKtoPhotoUrlException.class);
    }

    @Test
    void resourcePhotoImagesStillRejectUnsafeResolvedAddresses() throws Exception {
        KtoPhotoUrlValidator validator = new KtoPhotoUrlValidator(
                host -> new InetAddress[]{InetAddress.getByName("127.0.0.1")});

        assertThatThrownBy(() -> validator.validate(
                "https://tong.visitkorea.or.kr/cms/resource_photo/79/3414579_image2_1.jpg"))
                .isInstanceOf(InvalidKtoPhotoUrlException.class);
    }

    @Test
    void commonsValidatorDoesNotAcceptKtoResourcePhotoPath() throws Exception {
        InetAddress publicAddress = InetAddress.getByAddress(new byte[]{(byte) 203, 0, 113, 10});
        KtoPhotoUrlValidator validator = KtoPhotoUrlValidator.wikimediaCommons(
                host -> new InetAddress[]{publicAddress});

        assertThatThrownBy(() -> validator.validate(
                "https://tong.visitkorea.or.kr/cms/resource_photo/79/3414579_image2_1.jpg"))
                .isInstanceOf(InvalidKtoPhotoUrlException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "https://example.com/cms2/website/75/1002175.jpg",
            "https://tong.visitkorea.or.kr.attacker.com/cms2/website/75/1002175.jpg",
            "https://images.tong.visitkorea.or.kr/cms2/website/75/1002175.jpg",
            "http://localhost/cms2/website/75/1002175.jpg",
            "http://127.0.0.1/cms2/website/75/1002175.jpg",
            "file:///etc/passwd",
            "ftp://tong.visitkorea.or.kr/cms2/website/75/1002175.jpg",
            "http://user@tong.visitkorea.or.kr/cms2/website/75/1002175.jpg",
            "http://tong.visitkorea.or.kr:8080/cms2/website/75/1002175.jpg",
            "http://tong.visitkorea.or.kr/not-website/1002175.jpg",
            "https://example.com/cms/resource/35/4100435_image2_1.jpg",
            "https://tong.visitkorea.or.kr/cms/resourceful/35/4100435_image2_1.jpg",
            "https://tong.visitkorea.or.kr/cms/resource%2f35/4100435_image2_1.jpg",
            "https://tong.visitkorea.or.kr/cms/resource/../cms2/website/75/1002175.jpg",
            "https://tong.visitkorea.or.kr/cms/resource/%2e%2e/cms2/website/75/1002175.jpg",
            "http://[broken"
    })
    void rejectsUntrustedOrMalformedUrls(String imageUrl) throws Exception {
        KtoPhotoUrlValidator validator = publicAddressValidator();

        assertThatThrownBy(() -> validator.validate(imageUrl))
                .isInstanceOf(InvalidKtoPhotoUrlException.class)
                .hasMessage("허용되지 않은 관광사진 URL입니다.")
                .hasNoCause();
    }

    @ParameterizedTest
    @ValueSource(strings = {"0.0.0.0", "127.0.0.1", "10.1.2.3", "169.254.1.1", "224.0.0.1", "fc00::1"})
    void rejectsUnsafeResolvedAddresses(String address) throws Exception {
        KtoPhotoUrlValidator validator = new KtoPhotoUrlValidator(
                host -> new InetAddress[]{InetAddress.getByName(address)});

        assertThatThrownBy(() -> validator.validate(HTTPS_PHOTO_URL))
                .isInstanceOf(InvalidKtoPhotoUrlException.class)
                .hasMessage("허용되지 않은 관광사진 URL입니다.");
    }

    /** Pixabay API가 주는 저장용 주소(pixabay.com/get/)와 CDN 주소만 HTTPS로 받는다. */
    @Test
    void pixabayValidatorAcceptsOnlyPixabayImageHostsOverHttps() throws Exception {
        InetAddress publicAddress = InetAddress.getByAddress(new byte[]{(byte) 203, 0, 113, 10});
        KtoPhotoUrlValidator validator = KtoPhotoUrlValidator.pixabay(host -> new InetAddress[]{publicAddress});

        assertThat(validator.validate("https://pixabay.com/get/ed6a99fd0a76647_1280.jpg"))
                .isEqualTo(URI.create("https://pixabay.com/get/ed6a99fd0a76647_1280.jpg"));
        assertThat(validator.validate("https://cdn.pixabay.com/photo/2013/10/15/09/12/flower-195893_1280.jpg"))
                .isEqualTo(URI.create("https://cdn.pixabay.com/photo/2013/10/15/09/12/flower-195893_1280.jpg"));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "http://pixabay.com/get/ed6a99fd0a76647_1280.jpg",
            "https://pixabay.com/api/?key=x",
            "https://pixabay.com/get/../api/",
            "https://pixabay.com.attacker.com/get/ed6a99fd0a76647_1280.jpg",
            "https://attacker.com/get/ed6a99fd0a76647_1280.jpg",
            "https://pixabay.com:8443/get/ed6a99fd0a76647_1280.jpg",
            "https://user@pixabay.com/get/ed6a99fd0a76647_1280.jpg",
            "https://upload.wikimedia.org/wikipedia/commons/a/a8/Tour_Eiffel.jpg",
            "https://tong.visitkorea.or.kr/cms2/website/75/1002175.jpg"
    })
    void pixabayValidatorRejectsOtherHostsPathsAndSchemes(String imageUrl) throws Exception {
        InetAddress publicAddress = InetAddress.getByAddress(new byte[]{(byte) 203, 0, 113, 10});
        KtoPhotoUrlValidator validator = KtoPhotoUrlValidator.pixabay(host -> new InetAddress[]{publicAddress});

        assertThatThrownBy(() -> validator.validate(imageUrl)).isInstanceOf(InvalidKtoPhotoUrlException.class);
    }

    @Test
    void pixabayValidatorRejectsPixabayHostsResolvingToPrivateAddresses() throws Exception {
        KtoPhotoUrlValidator validator = KtoPhotoUrlValidator.pixabay(
                host -> new InetAddress[]{InetAddress.getByName("10.1.2.3")});

        assertThatThrownBy(() -> validator.validate("https://pixabay.com/get/ed6a99fd0a76647_1280.jpg"))
                .isInstanceOf(InvalidKtoPhotoUrlException.class);
    }

    private KtoPhotoUrlValidator publicAddressValidator() throws Exception {
        InetAddress publicAddress = InetAddress.getByAddress(new byte[]{(byte) 203, 0, 113, 10});
        return new KtoPhotoUrlValidator(host -> new InetAddress[]{publicAddress});
    }
}
