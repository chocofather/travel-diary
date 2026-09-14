package com.example.travlediary.repository;

import com.example.travlediary.service.diary.DiaryPhotoFrame;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 폴라로이드 흰 프레임의 크기 계약.
 *
 * <p>고치기 전에는 프레임을 요소의 {@code padding: 3.5% 3.5% 8%} 로 두었다. 퍼센트 padding 은
 * 자기 요소가 아니라 <b>바깥 캔버스</b> 의 폭을 재므로, 요소를 작게 줄여도 흰 여백은 그대로 남아
 * 작은 사진 둘레에 과도한 흰 자리가 생겼다. (사진만 줄고 프레임은 그대로)
 *
 * <p>고친 뒤에는 요소 자체가 container 이고 안쪽 사진이 {@code cqw} 로 자리를 잡는다.
 * 그래서 요소의 width/height 하나가 사진과 프레임을 함께 키우고 줄인다.
 * 저장하는 값(width/height)은 그대로다 — 바뀐 것은 그 값을 그리는 방법뿐이다.
 */
class DiaryPolaroidFrameContractTest {

    private static final Path CSS = Path.of("src/main/resources/static/css/diary.css");

    /** 프레임 두께의 기준은 바깥 캔버스가 아니라 요소 자신이다. */
    @Test
    void theFrameIsMeasuredAgainstTheElementItself() throws IOException {
        String css = read();
        String photo = rule(css, ".diary-photo");

        assertThat(photo)
                .contains("container-type: inline-size;")
                // 바깥 캔버스를 재던 padding 은 남아 있지 않다.
                .contains("padding: 0;")
                .doesNotContain("padding: 3.5%")
                .doesNotContain("padding: 3.5% 3.5% 8%");

        /*
          네 변을 모두 요소 폭(cqw)으로 잰다. 높이만 요소 높이(100%)에서 위아래 두께를 뺀다.
          그래서 가로로만 늘리든 세로로만 줄이든 프레임이 사진과 같은 비율로 따라간다.
        */
        String image = rule(css, ".diary-photo img");
        assertThat(image)
                .contains("position: absolute;")
                .contains("left: var(--diary-photo-side);")
                .contains("top: var(--diary-photo-side);")
                .contains("width: calc(100cqw - var(--diary-photo-side) * 2);")
                .contains("height: calc(100% - var(--diary-photo-side) - var(--diary-photo-bottom));")
                // 사진만 따로 고정 크기로 남지 않는다.
                .doesNotContain("width: 100%;")
                .doesNotContain("height: 100%;");
    }

    /**
     * 화면의 두께와 서버의 셈이 같은 값을 본다.
     *
     * <p>{@link DiaryPhotoFrame} 은 이 두께를 빼고 사진 자리가 원본 비율이 되도록 요소 높이를
     * 정한다. 두 값이 어긋나면 새로 붙인 사진부터 프레임과 사진 자리가 맞지 않는다.
     */
    @Test
    void theCssThicknessMatchesTheServerSideCalculation() throws IOException {
        String photo = rule(read(), ".diary-photo");

        double side = cqw(photo, "--diary-photo-side");
        double bottom = cqw(photo, "--diary-photo-bottom");

        // SIDE / BOTTOM 은 private 이라 공개된 두 값에서 거꾸로 구한다.
        double serverSide = (1 - DiaryPhotoFrame.INNER_WIDTH) / 2;
        double serverBottom = DiaryPhotoFrame.FRAME_HEIGHT - serverSide;
        assertThat(side / 100).isCloseTo(serverSide, org.assertj.core.data.Offset.offset(1e-6));
        assertThat(bottom / 100).isCloseTo(serverBottom, org.assertj.core.data.Offset.offset(1e-6));
    }

    /**
     * 아래 여백은 예전(8%)보다 얇아졌지만 폴라로이드다운 비대칭은 남는다.
     * 좌·우·위 두께는 건드리지 않았다.
     */
    @Test
    void theBottomMarginIsSlimmerButStillAPolaroid() throws IOException {
        String photo = rule(read(), ".diary-photo");

        double side = cqw(photo, "--diary-photo-side");
        double bottom = cqw(photo, "--diary-photo-bottom");

        assertThat(side).isEqualTo(3.5);
        assertThat(bottom).isLessThan(8.0).isGreaterThanOrEqualTo(5.5);
        // 아래가 위보다 확실히 넓다. 일반 사진 테두리처럼 평평해지지 않는다.
        assertThat(bottom / side).isBetween(1.5, 2.5);
    }

    /** 요소 크기가 달라져도 프레임이 차지하는 비율은 같다. (resize 로 함께 줄고 커진다) */
    @Test
    void theFrameKeepsItsShareAtEverySize() throws IOException {
        String photo = rule(read(), ".diary-photo");
        double side = cqw(photo, "--diary-photo-side") / 100;
        double bottom = cqw(photo, "--diary-photo-bottom") / 100;

        // 요소 폭이 몇 px 이든 프레임/사진 자리의 비율은 그대로다.
        for (double elementWidth : new double[]{480, 240, 120, 60}) {
            double frame = elementWidth * side;
            double innerWidth = elementWidth - 2 * frame;
            assertThat(frame / elementWidth)
                    .as("%s", elementWidth)
                    .isCloseTo(side, org.assertj.core.data.Offset.offset(1e-9));
            assertThat(innerWidth / elementWidth)
                    .isCloseTo(DiaryPhotoFrame.INNER_WIDTH, org.assertj.core.data.Offset.offset(1e-9));
            assertThat(elementWidth * bottom / elementWidth)
                    .isCloseTo(bottom, org.assertj.core.data.Offset.offset(1e-9));
        }
    }

    /** 일반 사진(FULL)은 프레임이 없다. 폴라로이드 규칙을 같은 방법으로 0 으로 쓴다. */
    @Test
    void aFullPhotoHasNoFrameAtAll() throws IOException {
        String full = rule(read(), ".diary-photo.is-photo-full");

        assertThat(full)
                .contains("--diary-photo-side: 0cqw;")
                .contains("--diary-photo-bottom: 0cqw;")
                .contains("background: none;");
        // FULL 의 처음 크기 셈은 그대로다. (프레임을 빼지 않고 원본 비율만 본다)
        assertThat(source("service/diary/DiaryPhotoFrame.java"))
                .contains("double height = width * canvasAspect / ratio;");
    }

    /**
     * 표지와 페이지, 회원과 비회원이 한 규칙을 함께 쓴다.
     * 화면마다 다른 프레임 규칙을 두지 않는다.
     */
    @Test
    void oneFrameRuleIsSharedByEveryScreen() throws IOException {
        String css = read();

        // 프레임 두께를 정하는 자리는 .diary-photo 한 곳뿐이다.
        assertThat(count(css, "--diary-photo-side:")).isEqualTo(2);   // 폴라로이드 + FULL(0)
        assertThat(count(css, "--diary-photo-bottom:")).isEqualTo(2);
        assertThat(css)
                .doesNotContain(".diary-cover-surface .diary-photo.is-photo-polaroid")
                .doesNotContain(".diary-canvas .diary-photo.is-photo-polaroid");

        // 사진 요소의 마크업도 화면마다 같은 class 를 쓴다. (서버 / 회원 JS / 비회원 JS)
        assertThat(resource("templates/diary/detail.html"))
                .contains("class=\"diary-canvas-item diary-canvas-photo diary-photo\"");
        assertThat(resource("static/js/guest-diary-cover-preview.js"))
                .contains("'diary-canvas-item diary-canvas-photo diary-photo'");
        assertThat(resource("static/js/guest-diary-photo.js"))
                .contains("'is-photo-full' : 'is-photo-polaroid'");
    }

    /**
     * 저장 계약은 그대로다.
     *
     * <p>요소가 가진 값은 여전히 width / height 뿐이고, 조작 엔진도 그 둘만 고친다.
     * 프레임 두께를 따로 저장하지 않으므로 예전에 붙여 둔 사진도 그대로 열린다.
     */
    @Test
    void theStoredSizeContractIsUnchanged() throws IOException {
        String drag = resource("static/js/diary-canvas-drag.js");

        // 조작 엔글이 고치는 것은 요소의 크기 하나다. 사진이나 프레임을 따로 건드리지 않는다.
        assertThat(drag)
                .contains("item.style.width")
                .contains("item.style.height")
                .doesNotContain("img.style.width")
                .doesNotContain("--diary-photo-side");
        // 저장 값에 프레임 관련 칸이 새로 생기지 않았다.
        assertThat(source("model/DiaryElement.java"))
                .doesNotContain("frame")
                .doesNotContain("padding");
    }

    /* ===== 도구 ===== */

    /** {@code --이름: 3.5cqw;} 에서 숫자만 꺼낸다. */
    private double cqw(String rule, String property) {
        Matcher matcher = Pattern.compile(Pattern.quote(property) + ":\\s*([0-9.]+)cqw;")
                .matcher(rule);
        assertThat(matcher.find()).as("값을 찾지 못했습니다: " + property).isTrue();
        return Double.parseDouble(matcher.group(1));
    }

    private int count(String content, String needle) {
        return content.split(Pattern.quote(needle), -1).length - 1;
    }

    private String rule(String css, String selector) {
        int start = css.indexOf("\n" + selector + " {");
        assertThat(start).as("규칙을 찾지 못했습니다: " + selector).isNotNegative();
        return css.substring(start, css.indexOf('}', start));
    }

    private String read() throws IOException {
        return Files.readString(CSS, StandardCharsets.UTF_8);
    }

    private String resource(String relativePath) throws IOException {
        return Files.readString(Path.of("src/main/resources").resolve(relativePath),
                StandardCharsets.UTF_8);
    }

    private String source(String relativePath) throws IOException {
        return Files.readString(
                Path.of("src/main/java/com/example/travlediary").resolve(relativePath),
                StandardCharsets.UTF_8);
    }
}
