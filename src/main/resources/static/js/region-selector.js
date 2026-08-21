document.addEventListener("DOMContentLoaded", function () {
    const continentSelect = document.getElementById("continent");
    const countrySelect = document.getElementById("country");
    const citySelect = document.getElementById("city");
    const districtSelect = document.getElementById("district");
    const regionIdHidden = document.getElementById("regionIdHidden");
    const selects = [continentSelect, countrySelect, citySelect, districtSelect];
    let regionRequestGeneration = 0;
    let regionSelectionChanged = false;

    if (selects.some(select => !select) || !regionIdHidden) return;

    function parseRegionPathIds(value) {
        return (value || "")
            .split(",")
            .map(regionId => regionId.trim())
            .filter(regionId => regionId !== "")
            .map(regionId => ({id: regionId}));
    }

    // 수정 화면에서 서버가 내려준 기존 지역 값 (신규 등록에서는 비어 있다)
    const initialRegionId = regionIdHidden.value;
    const initialRegionPath = parseRegionPathIds(regionIdHidden.dataset.initialRegionPath);
    // 국내/해외 구분 기준은 서버가 내려준 국내 root id 뿐이다 (숫자 하드코딩 없음)
    const domesticRootId = (regionIdHidden.dataset.domesticRootId || "").trim();

    const regionField = regionIdHidden.closest("[data-region-field]");
    const modeButtons = regionField
        ? Array.from(regionField.querySelectorAll("[data-region-mode-button]"))
        : [];
    const modeHelp = regionField ? regionField.querySelector("[data-region-mode-help]") : null;
    let regionMode = "";

    function isDomesticRoot(regionId) {
        return domesticRootId !== "" && String(regionId) === domesticRootId;
    }

    /** 버튼/표시 상태만 바꾼다. 선택값은 건드리지 않는다. */
    function markMode(mode) {
        regionMode = mode;
        if (regionField) regionField.dataset.regionMode = mode;
        modeButtons.forEach(button => {
            const pressed = button.dataset.regionModeButton === mode;
            button.setAttribute("aria-pressed", String(pressed));
            button.classList.toggle("active", pressed);
        });
        if (modeHelp) modeHelp.hidden = mode !== "";
    }

    /** 첫 select 에서 현재 모드에 해당하지 않는 최상위 지역을 없앤다. */
    function filterRootOptions() {
        if (!domesticRootId || regionMode === "") return;
        Array.from(continentSelect.options).forEach(option => {
            if (!option.value) return;
            if (isDomesticRoot(option.value) !== (regionMode === "domestic")) option.remove();
        });
    }

    function resetSelect(select) {
        select.replaceChildren(new Option("선택", ""));
    }

    async function fetchRegions(parentId, targetSelect, requestGeneration) {
        resetSelect(targetSelect);
        let url = "/api/regions";
        if (parentId) url += `?parentId=${encodeURIComponent(parentId)}`;

        try {
            const response = await fetch(url, {headers: {Accept: "application/json"}});
            if (!response.ok) throw new Error("지역 정보를 불러오지 못했습니다.");
            const data = await response.json();
            if (requestGeneration !== regionRequestGeneration) return false;
            if (!Array.isArray(data)) return false;
            data.forEach(region => {
                const option = new Option(region.regionName || "", String(region.id));
                targetSelect.appendChild(option);
            });
            return true;
        } catch (error) {
            if (requestGeneration === regionRequestGeneration) {
                console.error("지역 정보를 불러오지 못했습니다.");
            }
            return false;
        }
    }

    function updateRegionId() {
        // 국내 모드의 첫 select(대한민국)는 자동 선택이므로 저장 대상 지역으로 보지 않는다.
        const rootValue = regionMode === "domestic" ? "" : continentSelect.value;
        const selectedId =
            districtSelect.value || citySelect.value || countrySelect.value || rootValue || "";
        // 지역을 실제로 바꾸지 않았다면 기존에 저장된 지역을 그대로 유지한다.
        regionIdHidden.value = selectedId || (regionSelectionChanged ? "" : initialRegionId);
    }

    function clearAfter(index) {
        for (let targetIndex = index + 1; targetIndex < selects.length; targetIndex++) {
            resetSelect(selects[targetIndex]);
        }
    }

    async function handleManualChange(index) {
        const requestGeneration = ++regionRequestGeneration;
        regionSelectionChanged = true;
        clearAfter(index);
        updateRegionId();
        const selectedId = selects[index].value;
        if (selectedId && index + 1 < selects.length) {
            await fetchRegions(selectedId, selects[index + 1], requestGeneration);
        }
    }

    function clearSelection() {
        ++regionRequestGeneration;
        selects.forEach(resetSelect);
        markMode("");
        updateRegionId();
    }

    /** 국내/해외 버튼: 이전 모드의 선택과 hidden regionId 를 남기지 않는다. */
    async function selectMode(mode) {
        if (mode === regionMode) return;
        const requestGeneration = ++regionRequestGeneration;
        regionSelectionChanged = true;
        markMode(mode);
        selects.forEach(resetSelect);
        updateRegionId();

        const loaded = await fetchRegions(undefined, continentSelect, requestGeneration);
        if (!loaded || requestGeneration !== regionRequestGeneration) return;
        filterRootOptions();

        if (mode !== "domestic") return;
        // 국내는 대한민국 아래 시/도부터 고르게 한다.
        const domesticOption = Array.from(continentSelect.options)
            .find(option => isDomesticRoot(option.value));
        if (!domesticOption) return;
        continentSelect.value = domesticOption.value;
        updateRegionId();
        await fetchRegions(domesticOption.value, countrySelect, requestGeneration);
    }

    async function applyRegionPath(path) {
        const requestGeneration = ++regionRequestGeneration;
        selects.forEach(resetSelect);
        updateRegionId();
        if (!Array.isArray(path) || path.length === 0 || path.length > selects.length) {
            void fetchRegions(undefined, continentSelect, requestGeneration);
            return false;
        }

        // 경로의 최상위로 국내/해외 모드를 함께 복원한다.
        markMode(isDomesticRoot(path[0].id) ? "domestic" : "overseas");

        let parentId;
        for (let index = 0; index < path.length; index++) {
            const loaded = await fetchRegions(parentId, selects[index], requestGeneration);
            if (!loaded || requestGeneration !== regionRequestGeneration) return false;
            if (index === 0) filterRootOptions();

            const region = path[index];
            const matchingOptions = Array.from(selects[index].options).filter(option =>
                option.value === String(region.id)
                && (!region.regionName || option.textContent === region.regionName));
            if (matchingOptions.length !== 1) {
                clearSelection();
                return false;
            }
            selects[index].value = matchingOptions[0].value;
            parentId = matchingOptions[0].value;
        }

        if (requestGeneration !== regionRequestGeneration) return false;
        updateRegionId();
        return true;
    }

    modeButtons.forEach(button =>
        button.addEventListener("click", () => void selectMode(button.dataset.regionModeButton)));

    continentSelect.addEventListener("change", () => void handleManualChange(0));
    countrySelect.addEventListener("change", () => void handleManualChange(1));
    citySelect.addEventListener("change", () => void handleManualChange(2));
    districtSelect.addEventListener("change", () => void handleManualChange(3));

    // 수정 화면이면 기존 지역 경로(+모드)를 복원하고, 신규 등록은 모드 선택부터 시작한다.
    markMode("");
    if (initialRegionPath.length > 0) {
        void applyRegionPath(initialRegionPath);
    }

    const form = regionIdHidden.closest("form");
    if (form) {
        form.addEventListener("submit", updateRegionId);
    }

    // 외부(TourAPI 자동선택)에서 호출하는 경우는 사용자가 지역을 바꾼 것으로 본다.
    window.TravelDiaryRegionSelector = {
        applyRegionPath: path => {
            regionSelectionChanged = true;
            return applyRegionPath(path);
        },
        clearSelection: () => {
            regionSelectionChanged = true;
            clearSelection();
        }
    };
});
