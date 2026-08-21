document.addEventListener("DOMContentLoaded", () => {
    const form = document.getElementById("admin-destination-filter-form");
    if (!form) return;

    const SEARCH_DEBOUNCE_MS = 300;

    const typeInput = document.getElementById("destination-filter-type");
    const keywordInput = document.getElementById("destination-keyword-filter");
    const continentSelect = document.getElementById("destination-continent-filter");
    const countrySelect = document.getElementById("destination-country-filter");
    const citySelect = document.getElementById("destination-city-filter");
    const regionSelect = document.getElementById("destination-region-filter");
    const districtSelect = document.getElementById("destination-district-filter");

    let searchTimer = null;
    let composing = false;

    // 검색/조회 버튼 없이 현재 폼 상태 그대로 GET 조회한다. (URL 에 조건이 그대로 남는다)
    function submitFilters() {
        if (searchTimer) {
            clearTimeout(searchTimer);
            searchTimer = null;
        }
        form.submit();
    }

    function scheduleSearch() {
        if (searchTimer) clearTimeout(searchTimer);
        searchTimer = setTimeout(() => {
            searchTimer = null;
            form.submit();
        }, SEARCH_DEBOUNCE_MS);
    }

    function resetSelect(select, placeholder, disabled = true) {
        if (!select) return;
        select.replaceChildren(new Option(placeholder, ""));
        select.disabled = disabled;
    }

    // 하위 지역 목록은 선택 즉시 재조회되는 서버 렌더링 결과로 채워진다.

    // 검색창만 debounce, 그 외 필터는 변경 즉시 적용한다.
    keywordInput?.addEventListener("compositionstart", () => {
        composing = true;
    });
    keywordInput?.addEventListener("compositionend", () => {
        composing = false;
        scheduleSearch();
    });
    keywordInput?.addEventListener("input", () => {
        if (composing) return;
        scheduleSearch();
    });
    keywordInput?.addEventListener("keydown", event => {
        if (event.key !== "Enter") return;
        // 기본 submit(첫 필터 버튼 클릭)과 debounce 가 겹치지 않게 직접 조회한다.
        event.preventDefault();
        if (composing) return;
        submitFilters();
    });

    // 부모 지역을 바꾸면 하위 조건은 비운 상태로 조회한다.
    continentSelect?.addEventListener("change", () => {
        resetSelect(countrySelect, "- 국가 선택 -");
        resetSelect(citySelect, "- 도시 선택 -");
        submitFilters();
    });

    countrySelect?.addEventListener("change", () => {
        resetSelect(citySelect, "- 도시 선택 -");
        submitFilters();
    });

    citySelect?.addEventListener("change", submitFilters);

    regionSelect?.addEventListener("change", () => {
        resetSelect(districtSelect, "- 시/군/구 선택 -");
        submitFilters();
    });

    districtSelect?.addEventListener("change", submitFilters);

    form.querySelectorAll(".admin-filter-tab[value]").forEach(button => {
        button.addEventListener("click", () => {
            const nextType = button.value;
            if (typeInput) typeInput.value = nextType;

            if (nextType === "domestic") {
                resetSelect(continentSelect, "- 대륙 선택 -");
                resetSelect(countrySelect, "- 국가 선택 -");
                resetSelect(citySelect, "- 도시 선택 -");
            } else if (nextType === "overseas") {
                resetSelect(regionSelect, "- 시/도 선택 -");
                resetSelect(districtSelect, "- 시/군/구 선택 -");
            } else {
                resetSelect(continentSelect, "- 대륙 선택 -");
                resetSelect(countrySelect, "- 국가 선택 -");
                resetSelect(citySelect, "- 도시 선택 -");
                resetSelect(regionSelect, "- 시/도 선택 -");
                resetSelect(districtSelect, "- 시/군/구 선택 -");
            }
        });
    });
});
