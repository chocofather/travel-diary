package com.example.travlediary.repository;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class AdminDestinationImageManagementUiContractTest {

    @Test
    void editFormContainsOnlyAnImageManagementLinkAndNoImageSubmitControls() throws IOException {
        String source = resource("/templates/admin/destinations/edit.html");
        Document edit = Jsoup.parse(source);

        assertThat(edit.select("input[type=file][name=images]")).isEmpty();
        assertThat(edit.select("input[name=main], input[name=slide], input[name=ktoSelectedPhotosJson]")).isEmpty();
        assertThat(edit.select("[th\\:replace*=kto-photo-search]")).isEmpty();
        assertThat(source)
                .contains("@{/admin/destinations/{id}/images")
                .contains("이미지 관리로 이동");
    }

    @Test
    void createFormKeepsDirectUploadAndKtoSelection() throws IOException {
        String source = resource("/templates/admin/destinations/create.html");
        Document create = Jsoup.parse(source);

        assertThat(create.select("input[type=file][name=images][multiple]")).hasSize(1);
        assertThat(source)
                .contains("th:field=\"*{main}\"")
                .contains("th:field=\"*{slide}\"")
                .contains("admin/destinations/fragments/kto-photo-search");
    }

    @Test
    void imageManagementUsesUploadAndImageActionCardsWithoutNumericIndexes() throws IOException {
        String source = resource("/templates/admin/destinations/image-upload.html");
        Document page = Jsoup.parse(source);

        assertThat(page.select("input[type=file][name=files][multiple]")).hasSize(1);
        assertThat(page.select("input[name=mainIdx], input[name=slideIdx]")).isEmpty();
        assertThat(page.select(".admin-destination-image-grid .admin-destination-image-card")).hasSize(1);
        assertThat(source)
                .contains("/main(imageId=${img.id})")
                .contains("/slide(imageId=${img.id})")
                .contains("/delete(id=${img.id})")
                .contains("img.sourceType == 'KTO_PHOTO_GALLERY'");
    }

    @Test
    void imageManagementPostsSelectedKtoPhotosForTheCurrentDestination() throws IOException {
        String source = resource("/templates/admin/destinations/image-upload.html");
        Document page = Jsoup.parse(source);

        assertThat(source)
                .contains("/images/kto(id=${destinationId})")
                .contains("admin/destinations/fragments/kto-photo-search");
        assertThat(page.select("button[type=submit][data-kto-photo-submit]")).hasSize(1);
    }

    private String resource(String path) throws IOException {
        try (InputStream input = getClass().getResourceAsStream(path)) {
            assertThat(input).as("resource %s", path).isNotNull();
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
