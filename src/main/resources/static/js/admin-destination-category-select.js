document.addEventListener("DOMContentLoaded", () => {
    document.querySelectorAll("[data-category-select]").forEach(categorySelect => {
        const searchInput = categorySelect.querySelector("[data-category-search]");
        const chipsContainer = categorySelect.querySelector("[data-category-chips]");
        const emptyMessage = categorySelect.querySelector("[data-category-empty]");
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

        function syncSelectedCategories() {
            if (!chipsContainer || !emptyMessage) return;

            chipsContainer.replaceChildren();
            let selectedCount = 0;

            options.forEach(option => {
                const checkbox = option.querySelector("[data-category-checkbox]");
                const isSelected = Boolean(checkbox?.checked);
                option.classList.toggle("is-selected", isSelected);
                if (!checkbox || !isSelected) return;

                selectedCount += 1;
                const categoryName = getOptionName(option);
                const chip = document.createElement("span");
                chip.className = "admin-category-chip";
                chip.append(document.createTextNode(categoryName));

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

            emptyMessage.hidden = selectedCount > 0;
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
