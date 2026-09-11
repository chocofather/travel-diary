document.addEventListener("DOMContentLoaded", () => {
    const root = document.querySelector("[data-kto-import]");
    if (!root) return;

    const regionSelect = root.querySelector("[data-kto-import-region]");
    const subRegionSelect = root.querySelector("[data-kto-import-sub-region]");
    const contentTypeSelect = root.querySelector("[data-kto-import-content-type]");
    const searchButton = root.querySelector("[data-kto-import-search]");
    const rows = root.querySelector("[data-kto-import-rows]");
    const status = root.querySelector("[data-kto-import-status]");
    const selectedCount = root.querySelector("[data-kto-import-selected-count]");
    const submitButton = root.querySelector("[data-kto-import-submit]");
    const selectAllButton = root.querySelector("[data-kto-import-select-all]");
    const clearButton = root.querySelector("[data-kto-import-clear]");
    const filterButtons = Array.from(root.querySelectorAll("[data-kto-import-filter]"));
    const previousButton = root.querySelector("[data-kto-import-prev]");
    const nextButton = root.querySelector("[data-kto-import-next]");
    const pageLabel = root.querySelector("[data-kto-import-page]");
    const resultSection = root.querySelector("[data-kto-import-result]");
    const resultSummary = root.querySelector("[data-kto-import-result-summary]");
    const resultList = root.querySelector("[data-kto-import-result-list]");

    const PAGE_SIZE = 20;
    // 후보 구성 → 등록여부 판정 → 등록상태 필터 → 페이징은 모두 서버가 한다.
    // 화면은 서버가 준 한 페이지만 그리고, 건수도 서버 값을 그대로 쓴다.
    let pageItems = [];
    let counts = {ALL: 0, NEW: 0, REGISTERED: 0};
    let registrationFilter = "NEW";
    let pageNo = 1;
    let totalCount = 0;
    let searched = false;
    // 페이지를 넘겨도 유지되는 선택. contentId -> {contentId, contentTypeId, title}
    const selected = new Map();

    void loadAreas();

    regionSelect.addEventListener("change", () => {
        void loadSubRegions(regionSelect.value);
    });
    searchButton.addEventListener("click", () => {
        // 조회 조건이 바뀌면 이전 선택은 의미가 없으므로 여기서만 비운다.
        selected.clear();
        pageNo = 1;
        void loadCandidates();
    });
    previousButton.addEventListener("click", () => {
        if (pageNo <= 1) return;
        pageNo -= 1;
        void loadCandidates();
    });
    nextButton.addEventListener("click", () => {
        pageNo += 1;
        void loadCandidates();
    });
    filterButtons.forEach(button => button.addEventListener("click", () => {
        registrationFilter = button.dataset.ktoImportFilter;
        applyActiveFilterButton();
        if (!searched) return;
        pageNo = 1;
        void loadCandidates();
    }));
    selectAllButton.addEventListener("click", () => {
        // 등록완료 항목은 전체선택 대상이 아니다.
        pageItems.filter(item => !item.registered).forEach(remember);
        renderRows();
    });
    clearButton.addEventListener("click", () => {
        selected.clear();
        renderRows();
    });
    submitButton.addEventListener("click", () => {
        void importSelected();
    });

    async function loadAreas() {
        try {
            fillOptions(regionSelect, await requestJson("/admin/api/kto/tour/bulk/areas"),
                "- 지역 선택 -");
        } catch (error) {
            setStatus(error.message, true);
        }
    }

    async function loadSubRegions(regionCode) {
        fillOptions(subRegionSelect, [], "전체");
        subRegionSelect.disabled = !regionCode;
        if (!regionCode) return;
        try {
            const areas = await requestJson(
                `/admin/api/kto/tour/bulk/areas?regionCode=${encodeURIComponent(regionCode)}`);
            fillOptions(subRegionSelect, areas, "전체");
        } catch (error) {
            setStatus(error.message, true);
        }
    }

    async function loadCandidates() {
        if (!regionSelect.value) {
            setStatus("지역을 선택해 주세요.", true);
            return;
        }
        setBusy(true, "TourAPI에서 후보를 조회하고 있습니다.");
        try {
            const params = new URLSearchParams({
                regionCode: regionSelect.value,
                registrationFilter,
                pageNo: String(pageNo),
                numOfRows: String(PAGE_SIZE)
            });
            if (subRegionSelect.value) params.set("subRegionCode", subRegionSelect.value);
            if (contentTypeSelect.value) params.set("contentTypeId", contentTypeSelect.value);

            const payload = await requestJson(`/admin/api/kto/tour/bulk/candidates?${params}`);
            searched = true;
            pageItems = Array.isArray(payload.items) ? payload.items : [];
            totalCount = Number(payload.totalCount) || 0;
            counts = {
                ALL: Number(payload.allCount) || 0,
                NEW: Number(payload.newCount) || 0,
                REGISTERED: Number(payload.registeredCount) || 0
            };
            renderRows();
            setStatus(totalCount
                ? `${filterLabel()} ${totalCount}건 중 ${describeRange()} 표시 중`
                : `${filterLabel()} 조건에 해당하는 후보가 없습니다.`);
        } catch (error) {
            pageItems = [];
            totalCount = 0;
            renderRows();
            setStatus(error.message, true);
        } finally {
            setBusy(false);
        }
    }

    function filterLabel() {
        if (registrationFilter === "NEW") return "미등록";
        if (registrationFilter === "REGISTERED") return "등록완료";
        return "전체";
    }

    function describeRange() {
        const from = (pageNo - 1) * PAGE_SIZE + 1;
        return `${from}~${from + pageItems.length - 1}번`;
    }

    function remember(item) {
        selected.set(item.contentId, {
            contentId: item.contentId,
            contentTypeId: item.contentTypeId,
            title: item.title
        });
    }

    function renderRows() {
        rows.replaceChildren();
        pageItems.forEach(item => rows.append(buildRow(item)));
        updateCounts();
        updateSelection();
        updatePaging();
    }

    function buildRow(item) {
        const row = document.createElement("tr");
        row.classList.toggle("is-registered", item.registered);

        const checkCell = document.createElement("td");
        checkCell.className = "is-check";
        const checkbox = document.createElement("input");
        checkbox.type = "checkbox";
        // 이미 등록된 항목은 고를 수 없다.
        checkbox.disabled = item.registered;
        checkbox.checked = selected.has(item.contentId);
        checkbox.setAttribute("aria-label", `${item.title} 선택`);
        checkbox.addEventListener("change", () => {
            if (checkbox.checked) remember(item);
            else selected.delete(item.contentId);
            updateSelection();
        });
        checkCell.append(checkbox);

        const thumbCell = document.createElement("td");
        thumbCell.className = "is-thumb";
        if (item.thumbnailUrl) {
            const image = document.createElement("img");
            image.className = "admin-kto-import-thumb";
            image.src = item.thumbnailUrl;
            image.alt = "";
            image.loading = "lazy";
            thumbCell.append(image);
        } else {
            const empty = document.createElement("span");
            empty.className = "admin-kto-import-thumb is-empty";
            empty.textContent = "없음";
            thumbCell.append(empty);
        }

        const badge = document.createElement("span");
        badge.className = `admin-kto-import-badge ${item.registered ? "is-registered" : "is-new"}`;
        badge.textContent = item.registered ? "등록완료" : "미등록";

        row.append(
            checkCell,
            thumbCell,
            textCell(item.title),
            textCell(item.address),
            textCell(item.contentTypeName),
            textCell(item.contentId),
            cellWith(badge));
        return row;
    }

    function textCell(value) {
        const cell = document.createElement("td");
        cell.textContent = value || "-";
        return cell;
    }

    function cellWith(element) {
        const cell = document.createElement("td");
        cell.append(element);
        return cell;
    }

    function applyActiveFilterButton() {
        filterButtons.forEach(button => button.classList.toggle(
            "active", button.dataset.ktoImportFilter === registrationFilter));
    }

    function updateCounts() {
        filterButtons.forEach(button => {
            const label = button.querySelector("[data-kto-import-count]");
            if (label) label.textContent = String(counts[button.dataset.ktoImportFilter] ?? 0);
        });
    }

    /** 선택 건수는 현재 페이지가 아니라 전체 선택 기준이다. */
    function updateSelection() {
        selectedCount.textContent = `선택 ${selected.size}건`;
        submitButton.disabled = selected.size === 0;
    }

    function updatePaging() {
        pageLabel.textContent = String(pageNo);
        previousButton.disabled = pageNo <= 1;
        nextButton.disabled = pageNo * PAGE_SIZE >= totalCount;
    }

    async function importSelected() {
        const items = Array.from(selected.values())
            .map(item => ({contentId: item.contentId, contentTypeId: item.contentTypeId}));
        if (!items.length) return;

        setBusy(true, `선택한 ${items.length}건을 등록하고 있습니다.`);
        try {
            const payload = await requestJson("/admin/api/kto/tour/bulk/import", {
                method: "POST",
                headers: {"Content-Type": "application/json", Accept: "application/json"},
                body: JSON.stringify({items})
            });
            renderResult(payload);
            // 등록되었거나 중복으로 확인된 항목만 선택에서 뺀다. 실패 항목은 그대로 남긴다.
            (payload.results || [])
                .filter(result => result.status === "SUCCESS" || result.status === "DUPLICATE")
                .forEach(result => selected.delete(result.contentId));
            // 등록 여부와 건수를 서버 기준으로 다시 받는다 (같은 조건이면 TourAPI 재호출은 없다).
            await loadCandidates();
            setStatus(`성공 ${payload.successCount}건, 중복으로 건너뜀 ${payload.duplicateCount}건,`
                + ` 실패 ${payload.failureCount}건`);
        } catch (error) {
            setStatus(error.message, true);
        } finally {
            setBusy(false);
        }
    }

    function renderResult(payload) {
        resultSection.hidden = false;
        resultSummary.textContent = `성공 ${payload.successCount}건 · 중복으로 건너뜀`
            + ` ${payload.duplicateCount}건 · 실패 ${payload.failureCount}건`;
        resultList.replaceChildren();
        (payload.results || [])
            .filter(result => result.status !== "SUCCESS")
            .forEach(result => {
                const entry = document.createElement("li");
                entry.classList.toggle("is-failed", result.status === "FAILED");
                const label = result.status === "FAILED" ? "실패" : "중복";
                entry.textContent = `[${label}] ${result.title || "이름 없음"}`
                    + ` (contentId ${result.contentId})`
                    + (result.message ? ` - ${result.message}` : "");
                resultList.append(entry);
            });
    }

    function fillOptions(select, areas, placeholder) {
        select.replaceChildren();
        const empty = document.createElement("option");
        empty.value = "";
        empty.textContent = placeholder;
        select.append(empty);
        (areas || []).forEach(area => {
            const option = document.createElement("option");
            option.value = area.code;
            option.textContent = area.name;
            select.append(option);
        });
    }

    async function requestJson(url, options) {
        const response = await fetch(url, options || {headers: {Accept: "application/json"}});
        let payload = null;
        try {
            payload = await response.json();
        } catch (error) {
            payload = null;
        }
        if (!response.ok) {
            throw new Error((payload && payload.message) || "요청을 처리하지 못했습니다.");
        }
        return payload;
    }

    function setBusy(busy, message) {
        searchButton.disabled = busy;
        submitButton.disabled = busy || selected.size === 0;
        if (message) setStatus(message);
    }

    function setStatus(message, isError = false) {
        status.textContent = message;
        status.classList.toggle("is-error", isError);
    }
});
