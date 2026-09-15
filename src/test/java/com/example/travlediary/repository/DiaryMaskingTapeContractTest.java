package com.example.travlediary.repository;

import com.example.travlediary.model.DiaryStickerKind;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 마스킹테이프가 어느 화면에서나 같은 모습으로 그려지는지.
 *
 * <p>고치기 전에는 비회원 쪽이 "꾸미기 목록(.diary-sticker-option)에 되풀이 조각이 있는가" 로
 * 테이프인지를 가렸다. 목록이 없는 화면(체험 책장·가져오기 미리보기)에서는 그 물음에 답할 수 없어
 * 테이프가 일반 스티커처럼 그려졌고, 가져오기로 옮긴 뒤 회원 화면과 모습이 달라졌다.
 * 서버는 저장된 image_url 하나로 판정하므로(DiaryStickerKind), 브라우저도 같은 규칙을 쓴다.
 *
 * <p>클리어 테이프는 그림 자체에 깔려 있던 반투명 흰 필름 때문에 어두운 표지에서
 * 회색 직사각형처럼 보였다. 그 필름만 걷어냈고 무늬는 그대로 둔다.
 */
class DiaryMaskingTapeContractTest {

    private static final Path TAPES =
            Path.of("src/main/resources/static/images/diary/stickers/masking-tape");

    /** 판정 규칙은 그림 경로 하나다. 서버와 브라우저가 같은 앞머리를 본다. */
    @Test
    void oneRuleDecidesWhatIsAMaskingTape() throws IOException {
        assertThat(source("model/DiaryStickerKind.java"))
                .contains("\"/images/diary/stickers/\" + MASKING_TAPE + \"/\"")
                .contains("imageUrl.startsWith(MASKING_TAPE_PREFIX)");

        String tape = script("diary-tape-repeat.js");
        assertThat(tape)
                .contains("const MASKING_TAPE_PREFIX = '/images/diary/stickers/masking-tape/';")
                .contains("function isMaskingTape(imageUrl)")
                .contains("imageUrl.startsWith(MASKING_TAPE_PREFIX)")
                // 조각 경로도 같은 자리에서 찾는다.
                .contains("function lookup(imageUrl)")
                .contains("global.diaryTape = {render, lookup, isMaskingTape, MASKING_TAPE_PREFIX};");
    }

    /** 비회원 렌더러는 목록(picker)이 아니라 그 규칙으로 판정한다. */
    @Test
    void theGuestRenderersAskTheSharedRuleNotThePicker() throws IOException {
        for (String name : new String[]{
                "guest-diary-cover-preview.js", "guest-diary-cover-editor.js",
                "guest-diary-editor.js"}) {
            String script = script(name);
            assertThat(script).as(name)
                    .contains("global.diaryTape?.lookup(")
                    // 되풀이 조각이 있는지로 테이프인지를 가리던 자리는 남아 있지 않다.
                    .doesNotContain("maskingTape: Boolean(option")
                    .doesNotContain("Boolean(option && option.dataset.tapeCenter)");
        }
        // 목록이 없는 화면에도 조각 표를 실어 준다. (책장 카드 / 가져오기 미리보기)
        assertThat(resource("templates/diary/tape-repeats.html"))
                .contains("th:fragment=\"table(repeats)\"")
                .contains("data-sticker-image=${entry.key}")
                .contains("data-tape-center=${entry.value.centerUrl}");
        for (String name : new String[]{"templates/diary/demo.html", "templates/diary/import.html"}) {
            assertThat(resource(name)).as(name)
                    .contains("~{diary/tape-repeats :: table(${stickerRepeats})}");
        }
        assertThat(source("controller/diary/GuestDiaryDemoController.java"))
                .contains("model.addAttribute(\"stickerRepeats\", diaryStickerCatalog.getRepeatsByImageUrl());");
        assertThat(source("controller/diary/GuestDiaryImportController.java"))
                .contains("model.addAttribute(\"stickerRepeats\", diaryStickerCatalog.getRepeatsByImageUrl());");
    }

    /** 일반 스티커와 테이프가 뒤바뀌지 않는다. 테이프만 늘려 그리고 되풀이한다. */
    @Test
    void anOrdinaryStickerIsNeverDrawnAsATape() throws IOException {
        assertThat(DiaryStickerKind.isMaskingTape(
                "/images/diary/stickers/masking-tape/tape.svg")).isTrue();
        assertThat(DiaryStickerKind.isMaskingTape(
                "/uploads/diary-stickers/masking-tape/tape.webp")).isTrue();
        assertThat(DiaryStickerKind.isMaskingTape(
                "/uploads/diary-stickers/normal/sticker.webp")).isFalse();

        String css = read(Path.of("src/main/resources/static/css/diary.css"));
        assertThat(css)
                .contains(".diary-sticker[data-sticker-kind=\"masking-tape\"] img {")
                .contains(".diary-sticker.is-tape-repeat > img {");
    }

    /** 가져오기는 저장된 그림 경로를 바꾸지 않는다. 자리·크기·각도·겹침도 그대로다. */
    @Test
    void importKeepsTheTapeAssetAndItsGeometry() throws IOException {
        String service = source("service/diary/GuestDiaryImportService.java");

        assertThat(service)
                // 아는 스티커일 때만, 목록이 가진 경로 그대로 저장한다.
                .contains("diaryStickerCatalog.findByImageUrl(element.imageUrl())")
                .contains(".imageUrl();")
                .contains("prepared.setPositionX(position(element.positionX(), \"가로 위치\"));")
                .contains("prepared.setRotation(rotation(element.rotation()));")
                .contains("prepared.setZIndex(zIndex(element.zIndex()));");
        // 회원 화면은 저장된 경로에서 테이프인지와 조각을 다시 찾아 그린다.
        assertThat(source("model/DiaryCoverElement.java")).contains("DiaryStickerKind.of(imageUrl)");
        assertThat(source("model/DiaryElement.java")).contains("DiaryStickerKind.of(imageUrl)");
        assertThat(resource("templates/diary/cover-preview.html"))
                .contains("th:with=\"tape=${stickerRepeats.get(element.imageUrl)}\"")
                .contains("th:data-sticker-kind=\"${element.stickerKind}\"");
    }

    /** 색을 걷어낸 것은 그림뿐이다. 요소 전체를 흐리게 만드는 규칙을 새로 두지 않았다. */
    @Test
    void theClearLookIsNotFakedWithOpacity() throws IOException {
        String css = read(Path.of("src/main/resources/static/css/diary.css"));

        assertThat(css)
                .doesNotContain("data-tape-type")
                .doesNotContain(".diary-sticker.is-tape-clear");
        // 테이프 조각을 그리는 규칙은 예전 그대로다. (한 벌만 있고 화면마다 다르지 않다)
        assertThat(css)
                .contains(".diary-tape-cap {")
                .contains(".diary-tape-fill {");
    }

    private String script(String name) throws IOException {
        return resource("static/js/" + name);
    }

    private String resource(String relativePath) throws IOException {
        return read(Path.of("src/main/resources").resolve(relativePath));
    }

    private String source(String relativePath) throws IOException {
        return read(Path.of("src/main/java/com/example/travlediary").resolve(relativePath));
    }

    private String read(Path path) throws IOException {
        return Files.readString(path, StandardCharsets.UTF_8);
    }
}
