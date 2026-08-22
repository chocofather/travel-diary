document.addEventListener("DOMContentLoaded", () => {
    const toolbar = document.querySelector("[data-category-list]");
    if (!toolbar) return;

    const filterButtons = Array.from(toolbar.querySelectorAll("[data-category-filter]"));
    const searchInput = toolbar.querySelector("[data-category-search]");
    const clearButton = toolbar.querySelector("[data-category-search-clear]");
    const countLabel = toolbar.querySelector("[data-category-count]");
    const cards = Array.from(document.querySelectorAll("[data-category-card]"));
    const noResult = document.querySelector("[data-category-no-result]");

    // 유형 필터와 검색어는 AND 로 함께 적용한다. (표시/숨김만 담당)
    let typeFilter = "ALL";

    function tokensOf(value) {
        return String(value || "").trim().split(/\s+/).filter(token => token !== "");
    }

    function nameOf(card) {
        return card.querySelector("[data-category-name]")?.textContent.trim() ?? "";
    }

    function applyVisibility() {
        const showAllTypes = typeFilter === "ALL";
        const keyword = (searchInput?.value ?? "").trim().toLocaleLowerCase();
        let visible = 0;

        cards.forEach(card => {
            // 부분 문자열이 아니라 공백으로 나눈 token 으로 정확히 비교한다.
            const owned = tokensOf(card.dataset.categoryTypes);
            // 매핑이 없는 [미분류] 카드는 전체에서만 보인다.
            const matchesType = showAllTypes || owned.includes(typeFilter);
            const matchesKeyword = keyword === ""
                || nameOf(card).toLocaleLowerCase().includes(keyword);
            const matches = matchesType && matchesKeyword;
            card.hidden = !matches;
            if (matches) visible++;
        });

        if (noResult) noResult.hidden = cards.length === 0 || visible > 0;
        if (clearButton) clearButton.hidden = (searchInput?.value ?? "") === "";
        if (countLabel) countLabel.textContent = `${visible}개 카테고리`;

        filterButtons.forEach(button => {
            const pressed = button.dataset.categoryFilter === typeFilter;
            button.setAttribute("aria-pressed", String(pressed));
            button.classList.toggle("active", pressed);
        });
    }

    filterButtons.forEach(button => {
        button.addEventListener("click", () => {
            typeFilter = button.dataset.categoryFilter || "ALL";
            applyVisibility();
        });
    });

    searchInput?.addEventListener("input", applyVisibility);

    // 검색어만 비우고 선택된 유형 필터는 그대로 둔다.
    clearButton?.addEventListener("click", () => {
        if (!searchInput) return;
        searchInput.value = "";
        searchInput.focus();
        applyVisibility();
    });

    applyVisibility();
});
