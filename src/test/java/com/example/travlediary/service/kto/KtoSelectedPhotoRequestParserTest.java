package com.example.travlediary.service.kto;

import com.example.travlediary.dto.kto.KtoSelectedPhotoRequest;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class KtoSelectedPhotoRequestParserTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
    private final KtoSelectedPhotoRequestParser parser =
            new KtoSelectedPhotoRequestParser(objectMapper, validator);

    @Test
    void acceptsBlankOrEmptySelectionAsNoPhotos() {
        assertThat(parser.parse(null)).isEmpty();
        assertThat(parser.parse("   ")).isEmpty();
        assertThat(parser.parse("[]")).isEmpty();
    }

    @Test
    void parsesOnlyTheFiveSupportedFields() {
        List<KtoSelectedPhotoRequest> photos = parser.parse("""
                [
                  {
                    "externalContentId":"1002290",
                    "imageUrl":"https://images.example.test/palace.jpg",
                    "title":"경복궁",
                    "photographer":"한국관광공사 김지호",
                    "isMain":true
                  },
                  {
                    "externalContentId":"1002291",
                    "imageUrl":"https://images.example.test/night.jpg",
                    "title":"경복궁 야경",
                    "photographer":null,
                    "isMain":false
                  }
                ]
                """);

        assertThat(photos).hasSize(2);
        assertThat(photos.get(0).externalContentId()).isEqualTo("1002290");
        assertThat(photos.get(0).imageUrl()).isEqualTo("https://images.example.test/palace.jpg");
        assertThat(photos.get(0).title()).isEqualTo("경복궁");
        assertThat(photos.get(0).photographer()).isEqualTo("한국관광공사 김지호");
        assertThat(photos.get(0).isMain()).isTrue();
        assertThat(photos.get(1).isMain()).isFalse();
    }

    @Test
    void rejectsMalformedOrNonArrayJson() {
        assertInvalid("[{");
        assertInvalid("{}");
        assertInvalid("null");
    }

    @Test
    void rejectsMoreThanThirtyPhotos() throws JsonProcessingException {
        List<Map<String, Object>> photos = IntStream.range(0, 31)
                .mapToObj(index -> Map.<String, Object>of(
                        "externalContentId", "id-" + index,
                        "imageUrl", "https://images.example.test/" + index + ".jpg",
                        "isMain", false
                ))
                .toList();

        assertInvalid(objectMapper.writeValueAsString(photos));
    }

    @Test
    void rejectsMissingRequiredIdentifiers() {
        assertInvalid("""
                [{"externalContentId":" ","imageUrl":"https://images.example.test/a.jpg","isMain":false}]
                """);
        assertInvalid("""
                [{"externalContentId":"100","imageUrl":" ","isMain":false}]
                """);
    }

    @Test
    void rejectsMoreThanOneMainPhotoAndDuplicateSelections() {
        assertInvalid("""
                [
                  {"externalContentId":"100","imageUrl":"https://images.example.test/a.jpg","isMain":true},
                  {"externalContentId":"101","imageUrl":"https://images.example.test/b.jpg","isMain":true}
                ]
                """);
        assertInvalid("""
                [
                  {"externalContentId":"100","imageUrl":"https://images.example.test/a.jpg","isMain":false},
                  {"externalContentId":"100","imageUrl":"https://images.example.test/a.jpg","isMain":false}
                ]
                """);
    }

    @Test
    void rejectsClientSuppliedSourceOrLicenseFields() {
        assertInvalid("""
                [{
                  "externalContentId":"100",
                  "imageUrl":"https://images.example.test/a.jpg",
                  "isMain":false,
                  "sourceType":"KTO_PHOTO_GALLERY"
                }]
                """);
    }

    private void assertInvalid(String json) {
        assertThatThrownBy(() -> parser.parse(json))
                .isInstanceOf(InvalidKtoSelectedPhotosException.class)
                .hasMessage("선택한 관광사진 정보가 올바르지 않습니다.");
    }
}
