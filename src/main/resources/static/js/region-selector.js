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

    // ---- 단계별 보조 UI (첫 단계 버튼 grid / 하위 단계 검색) -------------------
    const steps = selects.map((select, index) => regionField
        ? regionField.querySelector(`[data-region-step="${index}"]`)
        : null);
    const chipBoxes = selects.map((select, index) => regionField
        ? regionField.querySelector(`[data-region-chips="${index}"]`)
        : null);
    const searchInputs = selects.map((select, index) => regionField
        ? regionField.querySelector(`[data-region-search="${index}"]`)
        : null);
    // 검색으로 걸러내도 원래 목록을 잃지 않도록 단계별 전체 옵션을 들고 있는다.
    const loadedOptions = selects.map(() => []);

    /** 모드별 첫 단계: 국내는 시/도(1), 해외는 대륙(0) */
    function firstStepIndex() {
        return regionMode === "domestic" ? 1 : 0;
    }

    function clearSearch(index) {
        const input = searchInputs[index];
        if (input) input.value = "";
    }

    function snapshotOptions(index) {
        loadedOptions[index] = Array.from(selects[index].options)
            .filter(option => option.value !== "")
            .map(option => ({value: option.value, label: option.textContent}));
    }

    /** 현재 검색어에 맞는 옵션만 남긴다. 선택된 값은 숨겨져도 유지한다. */
    function renderOptions(index) {
        const select = selects[index];
        const keyword = (searchInputs[index]?.value ?? "").trim().toLocaleLowerCase();
        const selectedValue = select.value;

        const visible = loadedOptions[index].filter(option =>
            keyword === "" || option.label.toLocaleLowerCase().includes(keyword)
                || option.value === selectedValue);

        select.replaceChildren(new Option("선택", ""));
        visible.forEach(option => select.appendChild(new Option(option.label, option.value)));
        select.value = selectedValue;
    }

    function renderChips(index) {
        const chipBox = chipBoxes[index];
        if (!chipBox) return;
        const select = selects[index];

        chipBox.replaceChildren();
        Array.from(select.options).forEach(option => {
            if (!option.value) return;
            const chip = document.createElement("button");
            chip.type = "button";
            chip.className = "admin-region-chip";
            chip.textContent = option.textContent;
            chip.dataset.regionChipValue = option.value;
            const pressed = select.value === option.value;
            chip.setAttribute("aria-pressed", String(pressed));
            chip.classList.toggle("active", pressed);
            chip.addEventListener("click", () => {
                // 버튼은 보조 UI 일 뿐이고 실제 상태는 기존 select 가 갖는다.
                select.value = option.value;
                void handleManualChange(index);
            });
            chipBox.appendChild(chip);
        });
    }

    /** 단계별 표시 방식(버튼 / 검색+select)과 빈 단계 숨김을 정한다. */
    function updateStepViews() {
        steps.forEach((step, index) => {
            if (!step) return;
            const hasOptions = loadedOptions[index].length > 0;
            const isFirstStep = index === firstStepIndex();

            if (regionMode === "") {
                step.hidden = true;
                return;
            }
            if (regionMode === "domestic" && index === 0) {
                step.hidden = true;
                return;
            }
            step.hidden = !hasOptions;
            step.dataset.regionView = isFirstStep && chipBoxes[index] ? "chips" : "select";
        });
    }

    function refreshStep(index) {
        snapshotOptions(index);
        renderOptions(index);
        renderChips(index);
        updateStepViews();
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
            // 부모가 바뀌면 하위 단계의 검색어와 버튼도 함께 비운다.
            clearSearch(targetIndex);
            loadedOptions[targetIndex] = [];
            chipBoxes[targetIndex]?.replaceChildren();
        }
    }

    async function handleManualChange(index) {
        const requestGeneration = ++regionRequestGeneration;
        regionSelectionChanged = true;
        clearAfter(index);
        updateRegionId();
        renderChips(index);
        updateStepViews();
        const selectedId = selects[index].value;
        if (selectedId && index + 1 < selects.length) {
            const loaded = await fetchRegions(selectedId, selects[index + 1], requestGeneration);
            if (loaded && requestGeneration === regionRequestGeneration) {
                refreshStep(index + 1);
            }
        }
    }

    function clearSelection() {
        ++regionRequestGeneration;
        selects.forEach(resetSelect);
        selects.forEach((select, index) => {
            clearSearch(index);
            loadedOptions[index] = [];
            chipBoxes[index]?.replaceChildren();
        });
        markMode("");
        updateRegionId();
        updateStepViews();
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
        refreshStep(0);

        if (mode !== "domestic") return;
        // 국내는 대한민국 아래 시/도부터 고르게 한다.
        const domesticOption = Array.from(continentSelect.options)
            .find(option => isDomesticRoot(option.value));
        if (!domesticOption) return;
        continentSelect.value = domesticOption.value;
        updateRegionId();
        const childrenLoaded =
            await fetchRegions(domesticOption.value, countrySelect, requestGeneration);
        if (childrenLoaded && requestGeneration === regionRequestGeneration) {
            refreshStep(1);
        }
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
            // 버튼 grid / 검색 목록도 복원된 선택에 맞춘다.
            refreshStep(index);
        }

        if (requestGeneration !== regionRequestGeneration) return false;
        updateRegionId();
        updateStepViews();
        return true;
    }

    modeButtons.forEach(button =>
        button.addEventListener("click", () => void selectMode(button.dataset.regionModeButton)));

    continentSelect.addEventListener("change", () => void handleManualChange(0));
    countrySelect.addEventListener("change", () => void handleManualChange(1));
    citySelect.addEventListener("change", () => void handleManualChange(2));
    districtSelect.addEventListener("change", () => void handleManualChange(3));

    // 하위 단계 검색: 이름 부분검색으로 옵션만 걸러내고 선택값은 유지한다.
    searchInputs.forEach((input, index) => {
        if (!input) return;
        input.addEventListener("input", () => renderOptions(index));
        input.addEventListener("keydown", event => {
            if (event.key === "Enter") event.preventDefault();
        });
    });

    // 수정 화면이면 기존 지역 경로(+모드)를 복원하고, 신규 등록은 모드 선택부터 시작한다.
    markMode("");
    updateStepViews();
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
