package com.example.travlediary.dto;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import static org.assertj.core.api.Assertions.assertThat;

class DestinationFormKtoSelectionBindingTest {

    @Test
    void keepsRawKtoJsonSeparateFromExistingMultipartImages() {
        DestinationForm form = new DestinationForm();
        MockMultipartFile image = new MockMultipartFile(
                "images", "palace.jpg", "image/jpeg", new byte[]{1, 2, 3}
        );

        form.setImages(new MockMultipartFile[]{image});
        form.setKtoSelectedPhotosJson("[{\"externalContentId\":\"100\"}]");

        assertThat(form.getKtoSelectedPhotosJson())
                .isEqualTo("[{\"externalContentId\":\"100\"}]");
        assertThat(form.getImages()).containsExactly(image);
    }

    @Test
    void defaultsToAnEmptyKtoSelectionArray() {
        assertThat(new DestinationForm().getKtoSelectedPhotosJson()).isEqualTo("[]");
    }
}
