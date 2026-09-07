/**
 * 번역 입력 영역 접기/펼치기.
 *
 * <p>[data-translation-collapsible] 안의 번역 그룹만 접는다. 감추기만 하므로 입력값은
 * 폼에 그대로 실리고, 다시 펼쳤을 때도 남아 있다. 스크립트가 없으면 지금처럼 펼쳐진 채로 둔다.
 * 언어 탭 전환은 admin-translation-tabs.js 가 그대로 맡는다.
 */
document.addEventListener("DOMContentLoaded", () => {
    let sequence = 0;

    document.querySelectorAll("[data-translation-collapsible] [data-translation-tabs]")
        .forEach(group => {
            const heading = group.querySelector("[data-translation-tabs-heading]");
            const body = group.querySelector("[data-translation-tabs-body]");
            if (!heading || !body) return;

            if (!body.id) body.id = `translation-collapse-${++sequence}`;

            const label = document.createElement("span");
            const chevron = document.createElement("span");
            chevron.className = "admin-translation-collapse-chevron";
            chevron.setAttribute("aria-hidden", "true");
            chevron.textContent = "▾";

            const toggle = document.createElement("button");
            toggle.type = "button";
            toggle.className = "admin-translation-collapse-toggle";
            toggle.setAttribute("aria-controls", body.id);
            toggle.append(label, chevron);
            heading.append(toggle);

            function setExpanded(expanded) {
                toggle.setAttribute("aria-expanded", String(expanded));
                body.hidden = !expanded;
                label.textContent = expanded ? "접기" : "펼치기";
            }

            // 검증 오류로 화면이 다시 그려졌을 때처럼 이미 입력된 값이 있으면 열어 둔다.
            setExpanded(hasEnteredValue(body));
            toggle.addEventListener("click",
                () => setExpanded(toggle.getAttribute("aria-expanded") !== "true"));
        });

    /** 감춘 채로 두면 안 되는 상태인지 본다. 입력값이나 오류가 있으면 펼친다. */
    function hasEnteredValue(body) {
        if (body.querySelector(".admin-field-error")) return true;
        return Array.from(body.querySelectorAll("input, textarea"))
            .some(field => field.type !== "hidden" && field.value.trim() !== "");
    }
});
