/**
 * 관리자 폼의 번역 언어 탭.
 *
 * <p>탭을 눌러도 다른 언어의 입력값은 지우지 않는다. 보이기만 감추므로 저장에는 모두 실린다.
 * 유형별 입력 칸 구성은 각 화면이 정하고, 여기서는 탭 전환만 맡는다.
 */
document.addEventListener("DOMContentLoaded", () => {
    document.querySelectorAll("[data-translation-tabs]").forEach(group => {
        const tabs = Array.from(group.querySelectorAll("[data-translation-tab]"));
        const panels = Array.from(group.querySelectorAll("[data-translation-panel]"));
        if (tabs.length === 0 || panels.length === 0) return;

        function activate(languageCode) {
            tabs.forEach(tab => {
                const active = tab.dataset.translationTab === languageCode;
                tab.classList.toggle("is-active", active);
                tab.setAttribute("aria-selected", String(active));
            });
            panels.forEach(panel => {
                panel.hidden = panel.dataset.translationPanel !== languageCode;
            });
        }

        tabs.forEach(tab => tab.addEventListener("click", () => activate(tab.dataset.translationTab)));
        // 처음에는 첫 번째 언어(영어) 탭을 연다.
        activate(tabs[0].dataset.translationTab);
    });
});
