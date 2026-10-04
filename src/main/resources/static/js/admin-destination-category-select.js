(function (root) {
    /*
      관리자 여행지 등록·수정 - 카테고리 선택.
      여러 카테고리를 고르고, 그중 하나를 대표로 지정한다(공개 상세 화면에는 대표 하나만 보인다).
      대표 규칙은 저장 서비스(DestinationService.resolveMainCategoryId)와 같다.
     */

    /**
     * 선택이 바뀐 뒤의 대표 카테고리 ID(문자열).
     * 지금 대표가 선택에 남아 있으면 그대로, 아니면 남은 것 중 가장 작은 ID, 선택이 없으면 "".
     * 첫 선택은 대표가 없던 상태이므로 그 항목이 대표가 된다.
     */
    function nextMainCategoryId(selectedIds, currentMainId) {
        const ids = selectedIds.map(id => String(id)).filter(id => /^\d+$/.test(id));
        const current = currentMainId == null ? "" : String(currentMainId);
        if (ids.includes(current)) return current;
        if (ids.length === 0) return "";
        return ids.reduce((min, id) => (Number(id) < Number(min) ? id : min));
    }

    const api = { nextMainCategoryId };
    root.TripBoraCategorySelect = api;
    root.TravelDiaryCategorySelect = root.TripBoraCategorySelect; // legacy alias (P10a 제거)
    if (typeof module !== "undefined" && module.exports) module.exports = api;
    if (typeof document === "undefined") return;

    document.addEventListener("DOMContentLoaded", () => {
        document.querySelectorAll("[data-category-select]").forEach(categorySelect => {
            const searchInput = categorySelect.querySelector("[data-category-search]");
            const chipsContainer = categorySelect.querySelector("[data-category-chips]");
            const emptyMessage = categorySelect.querySelector("[data-category-empty]");
            const mainInput = categorySelect.querySelector("[data-category-main]");
            const mainNote = categorySelect.querySelector("[data-category-main-note]");
            const options = Array.from(categorySelect.querySelectorAll("[data-category-option]"));
            const filterButtons = Array.from(categorySelect.querySelectorAll("[data-category-filter]"));
            const typeSelect = document.querySelector('select[name="type"]');

            // 유형 필터와 검색어는 AND 로 함께 적용한다. (표시/숨김만 담당)
            let typeFilter = "ALL";

            function getOptionName(option) {
                return option.querySelector("[data-category-name]")?.textContent.trim() ?? "";
            }

            function tokensOf(value) {
                return String(value || "").trim().split(/\s+/).filter(token => token !== "");
            }

            function applyVisibility() {
                const wanted = tokensOf(typeFilter);
                const showAllTypes = wanted.length === 0 || wanted.includes("ALL");
                const keyword = (searchInput?.value ?? "").trim().toLocaleLowerCase();

                options.forEach(option => {
                    const owned = tokensOf(option.dataset.categoryTypes);
                    const matchesType = showAllTypes || owned.some(type => wanted.includes(type));
                    const matchesKeyword = keyword === ""
                        || getOptionName(option).toLocaleLowerCase().includes(keyword);
                    option.hidden = !(matchesType && matchesKeyword);
                });

                filterButtons.forEach(button => {
                    const pressed = button.dataset.categoryFilter === typeFilter;
                    button.setAttribute("aria-pressed", String(pressed));
                    button.classList.toggle("active", pressed);
                });
                categorySelect.dataset.categoryMode = typeFilter;
            }

            function defaultTypeFilter() {
                const type = typeSelect?.value ?? "";
                if (type === "RESTAURANTS" || type === "CAFE") return "RESTAURANTS CAFE";
                return type === "" ? "ALL" : type;
            }

            function showMainNote(message) {
                if (!mainNote) return;
                mainNote.textContent = message;
                mainNote.hidden = message === "";
            }

            function syncSelectedCategories() {
                if (!chipsContainer || !emptyMessage) return;

                const selected = options.filter(option =>
                    Boolean(option.querySelector("[data-category-checkbox]")?.checked));
                const selectedIds = selected.map(option => option.querySelector("[data-category-checkbox]").value);

                // 대표가 해제되면 남은 것 중 가장 작은 ID가 대표가 된다. 모두 해제하면 대표도 없다.
                const previousMain = mainInput?.value ?? "";
                const main = nextMainCategoryId(selectedIds, previousMain);
                if (mainInput) mainInput.value = main;
                const mainOption = selected.find(option =>
                    option.querySelector("[data-category-checkbox]").value === main);
                showMainNote(previousMain !== "" && previousMain !== main && mainOption
                    ? `대표 카테고리가 '${getOptionName(mainOption)}'(으)로 바뀌었습니다.`
                    : "");

                chipsContainer.replaceChildren();
                options.forEach(option => option.classList.toggle("is-selected", selected.includes(option)));

                selected.forEach(option => {
                    const checkbox = option.querySelector("[data-category-checkbox]");
                    const categoryName = getOptionName(option);
                    const isMain = mainInput != null && checkbox.value === main;
                    const chip = document.createElement("span");
                    chip.className = "admin-category-chip";
                    chip.classList.toggle("is-main", isMain);

                    if (isMain) {
                        const badge = document.createElement("span");
                        badge.className = "admin-category-main-badge";
                        badge.textContent = "대표";
                        chip.append(badge);
                    }
                    chip.append(document.createTextNode(categoryName));

                    if (mainInput && !isMain) {
                        const mainButton = document.createElement("button");
                        mainButton.type = "button";
                        mainButton.className = "admin-category-main-button";
                        mainButton.setAttribute("aria-label", `${categoryName} 대표로 지정`);
                        mainButton.textContent = "대표로";
                        mainButton.addEventListener("click", () => {
                            // 대표만 바꾼다. 선택한 카테고리 목록은 그대로다.
                            mainInput.value = checkbox.value;
                            syncSelectedCategories();
                        });
                        chip.append(mainButton);
                    }

                    const removeButton = document.createElement("button");
                    removeButton.type = "button";
                    removeButton.className = "admin-category-chip-remove";
                    removeButton.setAttribute("aria-label", `${categoryName} 선택 해제`);
                    removeButton.textContent = "×";
                    removeButton.addEventListener("click", () => {
                        checkbox.checked = false;
                        checkbox.dispatchEvent(new Event("change", { bubbles: true }));
                    });

                    chip.append(removeButton);
                    chipsContainer.append(chip);
                });

                emptyMessage.hidden = selected.length > 0;
            }

            options.forEach(option => {
                option.querySelector("[data-category-checkbox]")
                    ?.addEventListener("change", syncSelectedCategories);
            });

            searchInput?.addEventListener("input", applyVisibility);

            searchInput?.addEventListener("keydown", event => {
                if (event.key === "Enter") event.preventDefault();
            });

            filterButtons.forEach(button => button.addEventListener("click", () => {
                // 유형 탭만 바꾸고 검색어와 체크 상태는 그대로 둔다.
                typeFilter = button.dataset.categoryFilter || "ALL";
                applyVisibility();
            }));

            // 여행지 유형이 바뀌면 해당 유형 탭으로 자동 전환한다.
            typeSelect?.addEventListener("change", () => {
                typeFilter = defaultTypeFilter();
                applyVisibility();
            });

            typeFilter = defaultTypeFilter();
            applyVisibility();
            syncSelectedCategories();
        });
    });
})(typeof window !== "undefined" ? window : globalThis);
