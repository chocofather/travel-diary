document.addEventListener("DOMContentLoaded", () => {
    const form = document.getElementById("admin-destination-filter-form");
    if (!form) return;

    const typeInput = document.getElementById("destination-filter-type");
    const continentSelect = document.getElementById("destination-continent-filter");
    const countrySelect = document.getElementById("destination-country-filter");
    const citySelect = document.getElementById("destination-city-filter");
    const regionSelect = document.getElementById("destination-region-filter");
    const districtSelect = document.getElementById("destination-district-filter");
    const pendingRequests = new WeakMap();

    function resetSelect(select, placeholder, disabled = true) {
        if (!select) return;
        select.replaceChildren(new Option(placeholder, ""));
        select.disabled = disabled;
    }

    async function loadChildren(parentId, targetSelect, placeholder) {
        if (!targetSelect) return;

        const previousRequest = pendingRequests.get(targetSelect);
        if (previousRequest) previousRequest.abort();

        resetSelect(targetSelect, placeholder);
        if (!parentId) return;

        const controller = new AbortController();
        pendingRequests.set(targetSelect, controller);

        try {
            const response = await fetch(`/api/regions?parentId=${encodeURIComponent(parentId)}`, {
                headers: { "Accept": "application/json" },
                signal: controller.signal
            });
            if (!response.ok) throw new Error(`지역 조회 실패: ${response.status}`);

            const regions = await response.json();
            if (!Array.isArray(regions)) throw new Error("올바르지 않은 지역 응답입니다.");

            regions.forEach(region => {
                if (region.id == null) return;
                targetSelect.add(new Option(region.regionName ?? "이름 없음", String(region.id)));
            });
            targetSelect.disabled = false;
        } catch (error) {
            if (error.name !== "AbortError") {
                console.warn("하위 지역을 불러오지 못했습니다.", error);
                resetSelect(targetSelect, placeholder);
            }
        } finally {
            if (pendingRequests.get(targetSelect) === controller) {
                pendingRequests.delete(targetSelect);
            }
        }
    }

    continentSelect?.addEventListener("change", () => {
        resetSelect(countrySelect, "- 국가 선택 -");
        resetSelect(citySelect, "- 도시 선택 -");
        loadChildren(continentSelect.value, countrySelect, "- 국가 선택 -");
    });

    countrySelect?.addEventListener("change", () => {
        resetSelect(citySelect, "- 도시 선택 -");
        loadChildren(countrySelect.value, citySelect, "- 도시 선택 -");
    });

    regionSelect?.addEventListener("change", () => {
        resetSelect(districtSelect, "- 시/군/구 선택 -");
        loadChildren(regionSelect.value, districtSelect, "- 시/군/구 선택 -");
    });

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
