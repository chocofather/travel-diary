package com.example.travlediary.controller.diary;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/** 표지와 모든 내지를 기존 화면 renderer로 순차 캡처하는 PDF 계약. */
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
    void readModeOffersOneFullDiaryPdfDownloadOutsideTheReplaceableSpread() throws IOException {
        String template = Files.readString(DETAIL_TEMPLATE);

        int readBoard = template.indexOf("id=\"diary-read-board\"");
        int button = template.indexOf("data-diary-pdf-button");
        assertThat(readBoard).isPositive();
        assertThat(button).isPositive().isLessThan(readBoard);
        assertThat(template).contains(
                "PDF 다운로드",
                "th:data-diary-pdf-cover-template",
                "th:data-diary-pdf-spread-url",
                "th:data-diary-pdf-total-spreads",
                "th:data-diary-pdf-page-count",
                "th:data-diary-page-order=\"${leftPage?.pageOrder}\"",
                "th:data-diary-page-order=\"${rightPage?.pageOrder}\"")
                .doesNotContain("PDF 테스트", "data-diary-pdf-page=\"");
    }

    @Test
    void exporterClonesEachExistingSheetAtTheA5ReferencePageSize() throws IOException {
        String script = Files.readString(PDF_SCRIPT);

        assertThat(script)
                .contains("const PAGE_WIDTH = 720;")
                .contains("const PAGE_HEIGHT = PAGE_WIDTH * 210 / 148;")
                .contains("const PIXEL_RATIO = 2;")
                .contains("querySelectorAll('.diary-sheet[data-diary-page-order]')")
                .contains("source.cloneNode(true)")
                .contains("host.className = 'diary-pdf-capture-host';")
                .contains("setFixedSize(clone, width, height);")
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
                .contains("source.closest('.diary-book-spread')")
                .contains("pageContext.append(host);")
                .doesNotContain("document.body.append(host);");
    }

    @Test
    void captureCloneMaterializesTheExistingComputedPaperBackground() throws IOException {
        String script = Files.readString(PDF_SCRIPT);

        assertThat(script)
                .contains("copyComputedBackground(source, clone);")
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
                .contains("global.diaryTape.render(item)")
                .contains("await waitForFrame();")
                .contains("await waitForBackgroundImages(clone);")
                .contains("throw new Error(")
                .contains("window.alert(");
    }

    @Test
    void exporterBuildsCoverThenEveryOrderedDiaryPageIntoOneA5Pdf() throws IOException {
        String script = Files.readString(PDF_SCRIPT);

        assertThat(script)
                .contains("htmlToImage.getFontEmbedCSS(capture.clone)")
                .contains("htmlToImage.toPng(capture.clone")
                .contains("pixelRatio: PIXEL_RATIO")
                .contains("const PDF_WIDTH_MM = 148;")
                .contains("const PDF_HEIGHT_MM = 210;")
                .contains("orientation: 'portrait'")
                .contains("unit: 'mm'")
                .contains("format: [PDF_WIDTH_MM, PDF_HEIGHT_MM]")
                .contains("pdf.addImage(png, 'PNG', 0, 0, PDF_WIDTH_MM, PDF_HEIGHT_MM")
                .contains("await captureCover(")
                .contains("for (let spread = 0; spread < spreadsToFetch; spread += 1)")
                .contains("await fetchSpread(")
                .contains("pdf.addPage([PDF_WIDTH_MM, PDF_HEIGHT_MM], 'portrait')")
                .doesNotContain("const PDF_WIDTH_MM = 200;")
                .doesNotContain("PDF_WIDTH_MM * 38 / 41")
                .contains("pdf.save(`${safeTitle}.pdf`)")
                .contains("INVALID_FILENAME_CHARACTERS")
                .contains("finally {")
                .contains("host.remove();");
    }

    @Test
    void exporterProcessesOneCaptureAtATimeAndOnlyDownloadsAfterEveryPageSucceeds()
            throws IOException {
        String script = Files.readString(PDF_SCRIPT);

        assertThat(script)
                .contains("const totalPdfPages = pageCount + 1;")
                .contains("PDF 만드는 중... ${completed} / ${total}")
                .contains("button.disabled = true;")
                .contains("button.disabled = false;")
                .contains("capture.dispose();")
                .contains("spreadHost.remove();")
                .contains("png = null;")
                .contains("pdf.save(`${safeTitle}.pdf`)");
        assertThat(script.indexOf("pdf.save(`${safeTitle}.pdf`)"))
                .isGreaterThan(script.indexOf("for (let spread = 0; spread < spreadsToFetch; spread += 1)"));
    }

    @Test
    void coverUsesTheSharedA5CoverRendererInsteadOfAListThumbnail() throws IOException {
        String detail = Files.readString(DETAIL_TEMPLATE);
        String preview = Files.readString(
                Path.of("src/main/resources/templates/diary/cover-preview.html"));

        assertThat(detail)
                .contains("id=\"diary-pdf-cover-template\"")
                .contains("data-diary-pdf-cover")
                .contains("diary/cover-preview :: appliedCover(");
        assertThat(preview)
                .contains("th:fragment=\"appliedCover(diary, cover, elements)\"")
                .contains("diary/cover-preview :: canvas(${cover}, ${elements})");
    }

    @Test
    void coverCaptureCloneIsFullBleedWithoutTheScreenCoverCornerOrShadow() throws IOException {
        String script = Files.readString(PDF_SCRIPT);

        assertThat(script)
                .contains("htmlToImage, false, prepareCoverClone")
                .contains("function prepareCoverClone(clone)")
                .contains("clone.style.borderRadius = '0';")
                .contains("clone.style.boxShadow = 'none';")
                .contains("clone.style.margin = '0';")
                .contains("clone.style.width = '100%';")
                .contains("clone.style.height = '100%';")
                .contains("clone.style.overflow = 'hidden';");
    }
}
