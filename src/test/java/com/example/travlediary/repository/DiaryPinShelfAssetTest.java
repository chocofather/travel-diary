package com.example.travlediary.repository;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 책장의 잠금 UI 가 화면과 스크립트에서 같은 이름을 쓰는지.
 *
 * <p>마크업과 스크립트가 서로 다른 이름을 보면 버튼을 눌러도 아무 일도 일어나지 않는다.
 * 화면 테스트는 마크업만, 서비스 테스트는 서버만 보므로 그 어긋남을 아무도 잡지 못한다.
 * 그래서 두 파일의 이름을 여기서 맞춰 둔다.
 */
class DiaryPinShelfAssetTest {

    private static final Path LIST = Path.of("src/main/resources/templates/diary/list.html");
    private static final Path SHELF = Path.of("src/main/resources/static/js/diary-pin-shelf.js");
    private static final Path MENU = Path.of("src/main/resources/static/js/diary-book-menu.js");

    @Test
    void theShelfScriptLooksForExactlyTheAttributesTheCardsCarry() throws IOException {
        String list = read(LIST);
        String shelf = read(SHELF);

        // 잠긴 책을 눌렀을 때 가로챌 표시
        assertThat(list).contains("data-pin-diary-id");
        assertThat(shelf).contains("[data-pin-diary-id]");
        // ⋯ 메뉴의 잠금 항목
        assertThat(list).contains("th:data-pin-set").contains("th:data-pin-manage");
        assertThat(shelf).contains("[data-pin-set]").contains("[data-pin-manage]");
        // 관리 판
        assertThat(read(Path.of("src/main/resources/templates/diary/pin-modal.html")))
                .contains("id=\"diary-pin-manage-backdrop\"");
        assertThat(shelf).contains("diary-pin-manage-backdrop");
    }

    /**
     * ⋯ 메뉴 안의 클릭은 카드 링크로 번지지 않도록 메뉴 쪽이 전파를 끊는다.
     * 그래서 잠금 항목을 document 에서 받으려면 <b>내려가는 길(capture)</b> 에서 잡아야 한다.
     * (올라오는 길에서 잡으면 메뉴 항목을 눌러도 아무 일도 일어나지 않는다)
     */
    @Test
    void theShelfListensOnTheWayDownBecauseTheMenuStopsBubbling() throws IOException {
        // 메뉴는 지금도 전파를 끊는다. (메뉴가 카드 링크를 눌러 버리지 않게 하려는 것이다)
        assertThat(read(MENU)).contains("event.stopPropagation()");
        // 그래서 잠금 쪽은 capture 로 듣는다
        assertThat(read(SHELF)).contains("'click', handleClick, true");
    }

    /** 목록은 검색으로 통째로 다시 그려진다. 그래도 살아 있도록 document 에서 듣는다. */
    @Test
    void theShelfUsesDelegationSoItSurvivesAReRenderedList() throws IOException {
        assertThat(read(SHELF)).contains("document.addEventListener('click'");
    }

    private String read(Path path) throws IOException {
        return Files.readString(path, StandardCharsets.UTF_8);
    }
}
