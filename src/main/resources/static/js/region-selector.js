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
        const selectedId =
            districtSelect.value || citySelect.value || countrySelect.value || continentSelect.value || "";
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
        const requestGeneration = ++regionRequestGeneration;
        selects.forEach(resetSelect);
        updateRegionId();
        void fetchRegions(undefined, continentSelect, requestGeneration);
    }

    async function applyRegionPath(path) {
        const requestGeneration = ++regionRequestGeneration;
        selects.forEach(resetSelect);
        updateRegionId();
        if (!Array.isArray(path) || path.length === 0 || path.length > selects.length) {
            void fetchRegions(undefined, continentSelect, requestGeneration);
            return false;
        }

        let parentId;
        for (let index = 0; index < path.length; index++) {
            const loaded = await fetchRegions(parentId, selects[index], requestGeneration);
            if (!loaded || requestGeneration !== regionRequestGeneration) return false;

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

    continentSelect.addEventListener("change", () => void handleManualChange(0));
    countrySelect.addEventListener("change", () => void handleManualChange(1));
    citySelect.addEventListener("change", () => void handleManualChange(2));
    districtSelect.addEventListener("change", () => void handleManualChange(3));

    // 수정 화면이면 기존 지역 경로를 복원하고, 그 외에는 최상위 목록만 불러온다.
    if (initialRegionPath.length > 0) {
        void applyRegionPath(initialRegionPath);
    } else {
        const initialRequestGeneration = ++regionRequestGeneration;
        void fetchRegions(undefined, continentSelect, initialRequestGeneration);
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
