package com.example.travlediary.controller.diary;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase 1 다이어리 PDF는 읽기 화면의 실제 한 페이지 DOM을 그대로 캡처해야 한다.
 */
class DiaryPdfExportAssetTest {

    private static final Path BUILD_GRADLE = Path.of("build.gradle");
    private static final Path DETAIL_TEMPLATE =
            Path.of("src/main/resources/templates/diary/detail.html");
    private static final Path PDF_SCRIPT =
            Path.of("src/main/resources/static/js/diary-pdf.js");

    @Test
    void pdfLibrariesArePinnedAndLoadedLocallyBeforeTheExporter() throws IOException {
        String build = Files.readString(BUILD_GRADLE);
        String template = Files.readString(DETAIL_TEMPLATE);

        assertThat(build)
                .contains("org.webjars.npm:html-to-image:1.11.13")
                .contains("org.webjars.npm:jspdf:3.0.1");

        String htmlToImage = "/webjars/html-to-image/1.11.13/dist/html-to-image.js";
        String jsPdf = "/webjars/jspdf/3.0.1/dist/jspdf.umd.min.js";
        String exporter = "/js/diary-pdf.js";
        assertThat(template).contains(htmlToImage, jsPdf, exporter);
        assertThat(template.indexOf(htmlToImage)).isLessThan(template.indexOf(exporter));
        assertThat(template.indexOf(jsPdf)).isLessThan(template.indexOf(exporter));

        String pdfAssets = template.substring(template.indexOf(htmlToImage));
        pdfAssets = pdfAssets.substring(0, pdfAssets.indexOf(exporter) + exporter.length());
        assertThat(pdfAssets).doesNotContain("cdn.jsdelivr.net", "unpkg.com");
    }

    @Test
    void eachRenderedReadPageHasItsOwnPdfTestButtonAndTarget() throws IOException {
        String template = Files.readString(DETAIL_TEMPLATE);

        int readBoard = template.indexOf("id=\"diary-read-board\"");
        int leftButton = template.indexOf("data-diary-pdf-button=\"left\"");
        int rightButton = template.indexOf("data-diary-pdf-button=\"right\"");
        assertThat(readBoard).isPositive();
        assertThat(template.substring(readBoard, template.indexOf('>', readBoard)))
                .contains("th:unless=\"${editMode}\"");
        assertThat(leftButton).isGreaterThan(readBoard);
        assertThat(rightButton).isGreaterThan(leftButton);
        assertThat(template).contains(
                "data-diary-pdf-page=\"left\"",
                "data-diary-pdf-page=\"right\"",
                "th:data-diary-page-order=\"${leftPage.pageOrder}\"",
                "th:data-diary-page-order=\"${rightPage.pageOrder}\"",
                "PDF 테스트");
    }

    @Test
    void exporterClonesTheExistingSheetAtTheReferencePageSize() throws IOException {
        String script = Files.readString(PDF_SCRIPT);

        assertThat(script)
                .contains("const PAGE_WIDTH = 720;")
                .contains("const PAGE_HEIGHT = PAGE_WIDTH * 210 / 148;")
                .contains("const PIXEL_RATIO = 2;")
                .contains(".diary-sheet[data-diary-pdf-page=\"")
                .contains("source.cloneNode(true)")
                .contains("host.className = 'diary-pdf-capture-host';")
                .contains("clone.style.width = `${PAGE_WIDTH}px`;")
                .contains("clone.style.height = `${PAGE_HEIGHT}px`;")
                .contains("clone.style.transform = 'none';")
                .contains("clone.style.setProperty('--diary-page-scale', '1');")
                .doesNotContain("PAGE_WIDTH * 38 / 41")
                .doesNotContain("const PAGE_WIDTH = 576;")
                .doesNotContain("source.style.width", "source.style.height");
    }

    @Test
    void captureCloneKeepsTheDiaryPageAncestorThatOwnsThePaperVariables() throws IOException {
        String script = Files.readString(PDF_SCRIPT);

        assertThat(script)
                .contains("source.closest('.diary-detail-page')")
                .contains("pageContext.append(host);")
                .doesNotContain("document.body.append(host);");
    }

    @Test
    void captureCloneMaterializesTheExistingComputedPaperBackground() throws IOException {
        String script = Files.readString(PDF_SCRIPT);

        assertThat(script)
                .contains("copyComputedPaperBackground(source, clone);")
                .contains("const computed = global.getComputedStyle(source);")
                .contains("clone.style.backgroundImage = computed.backgroundImage;")
                .contains("clone.style.backgroundColor = computed.backgroundColor;")
                .contains("clone.style.backgroundSize = computed.backgroundSize;");
    }

    @Test
    void exporterWaitsForFontsImagesAndMaskingTapeBeforeCapture() throws IOException {
        String script = Files.readString(PDF_SCRIPT);

        assertThat(script)
                .contains("await document.fonts.ready;")
                .contains("document.fonts.load(")
                .contains("document.fonts.check(")
                .contains("image.decode()")
                .contains("image.naturalWidth")
                .contains("window.diaryTape.render(item)")
                .contains("await waitForFrame();")
                .contains("await waitForBackgroundImages(clone);")
                .contains("throw new Error(")
                .contains("window.alert(");
    }

    @Test
    void exporterCreatesOneUncroppedPageAndSanitizesTheFilename() throws IOException {
        String script = Files.readString(PDF_SCRIPT);

        assertThat(script)
                .contains("htmlToImage.getFontEmbedCSS(clone)")
                .contains("htmlToImage.toPng(clone")
                .contains("pixelRatio: PIXEL_RATIO")
                .contains("const PDF_WIDTH_MM = 148;")
                .contains("const PDF_HEIGHT_MM = 210;")
                .contains("orientation: 'portrait'")
                .contains("unit: 'mm'")
                .contains("format: [PDF_WIDTH_MM, PDF_HEIGHT_MM]")
                .contains("pdf.addImage(png, 'PNG', 0, 0, PDF_WIDTH_MM, PDF_HEIGHT_MM")
                .doesNotContain("const PDF_WIDTH_MM = 200;")
                .doesNotContain("PDF_WIDTH_MM * 38 / 41")
                .contains("${safeTitle}_page_${pageOrder}.pdf")
                .contains("INVALID_FILENAME_CHARACTERS")
                .contains("finally {")
                .contains("host.remove();");
    }
}
