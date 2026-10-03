package com.example.travlediary.service.travelinfo.structured;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class StructuredContentImageUrlsTest {

    private static final String A = url("aaaaaaaa");
    private static final String B = url("bbbbbbbb");
    private static final String C = url("cccccccc");
    private static final String D = url("dddddddd");

    private final StructuredContentService service = StructuredContentTestSupport.structuredContentService();

    @Test
    void collectsImagesOfEveryImageBlockInBlockOrderWithoutDuplicates() {
        StructuredContent content = new StructuredContent(1, List.of(
                new StructuredBlock.SectionTitle("intro", "제목"),
                new StructuredBlock.FullImage("full", image(B), "설명", null),
                new StructuredBlock.RichText("text", "본문"),
                new StructuredBlock.ImageText("split", StructuredBlock.ImagePosition.LEFT, image(A),
                        "설명", null, "본문"),
                new StructuredBlock.ImageSlider("slider", "슬라이더", List.of(
                        new StructuredBlock.SliderItem("i1", image(C), "설명", null, null),
                        new StructuredBlock.SliderItem("i2", image(A), "설명", null, null),
                        new StructuredBlock.SliderItem("i3", image(D), "설명", null, null))),
                new StructuredBlock.FullImage("again", image(B), "설명", null),
                new StructuredBlock.Callout("tip", "강조")));

        assertThat(service.collectImageUrls(content)).containsExactly(B, A, C, D);
    }

    @Test
    void textOnlyOrMissingContentHasNoImages() {
        assertThat(service.collectImageUrls(new StructuredContent(1, List.of(
                new StructuredBlock.SectionTitle("intro", "제목"),
                new StructuredBlock.Callout("tip", "강조"))))).isEmpty();
        assertThat(service.collectImageUrls(null)).isEmpty();
    }

    @Test
    void imageListIsNotWrittenToJson() {
        String json = new StructuredContentSerializer().write(new StructuredContent(1, List.of(
                new StructuredBlock.FullImage("full", image(A), "설명", null))));

        assertThat(json).doesNotContain("\"images\"");
    }

    private static StructuredImage image(String url) {
        return new StructuredImage(url, 1200, 800);
    }

    private static String url(String prefix) {
        return "/uploads/travel-info/content/" + prefix + "-1111-4222-8333-444444444444.webp";
    }
}
