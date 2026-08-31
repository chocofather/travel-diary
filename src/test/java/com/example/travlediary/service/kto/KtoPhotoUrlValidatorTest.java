package com.example.travlediary.service.kto;

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

    private KtoPhotoUrlValidator publicAddressValidator() throws Exception {
        InetAddress publicAddress = InetAddress.getByAddress(new byte[]{(byte) 203, 0, 113, 10});
        return new KtoPhotoUrlValidator(host -> new InetAddress[]{publicAddress});
    }
}
