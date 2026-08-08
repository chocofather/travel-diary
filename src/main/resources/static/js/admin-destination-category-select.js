document.addEventListener("DOMContentLoaded", () => {
    document.querySelectorAll("[data-category-select]").forEach(categorySelect => {
        const searchInput = categorySelect.querySelector("[data-category-search]");
        const chipsContainer = categorySelect.querySelector("[data-category-chips]");
        const emptyMessage = categorySelect.querySelector("[data-category-empty]");
        const options = Array.from(categorySelect.querySelectorAll("[data-category-option]"));

        function getOptionName(option) {
            return option.querySelector("[data-category-name]")?.textContent.trim() ?? "";
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

        searchInput?.addEventListener("input", () => {
            const keyword = searchInput.value.trim().toLocaleLowerCase();
            options.forEach(option => {
                option.hidden = !getOptionName(option).toLocaleLowerCase().includes(keyword);
            });
        });

        searchInput?.addEventListener("keydown", event => {
            if (event.key === "Enter") event.preventDefault();
        });

        syncSelectedCategories();
    });
});
