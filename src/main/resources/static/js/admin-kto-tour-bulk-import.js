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
    const pageClearButton = root.querySelector("[data-kto-import-page-clear]");
    const pageToggle = root.querySelector("[data-kto-import-page-toggle]");
    const pageCount = root.querySelector("[data-kto-import-page-count]");
    const {pageSelection, applyPageSelection} =
        window.TripBoraBulkPageSelection || window.TravelDiaryBulkPageSelection; // legacy fallback (P10a 제거)
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
    let counts = {ALL: 0, NEW: 0, POSSIBLE_DUPLICATE: 0, REGISTERED: 0};
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
    selectAllButton.addEventListener("click", () => selectCurrentPage(true));
    pageClearButton.addEventListener("click", () => selectCurrentPage(false));
    pageToggle.addEventListener("change", () => selectCurrentPage(pageToggle.checked));
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
                POSSIBLE_DUPLICATE: Number(payload.possibleDuplicateCount) || 0,
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
        if (registrationFilter === "POSSIBLE_DUPLICATE") return "중복 확인";
        if (registrationFilter === "REGISTERED") return "등록완료";
        return "전체";
    }

    /** 서버 공통 중복 판별 결과. REGISTERED · POSSIBLE_DUPLICATE · NOT_REGISTERED */
    function duplicateStatus(item) {
        return item.duplicate?.status || (item.registered ? "REGISTERED" : "NOT_REGISTERED");
    }

    function describeRange() {
        const from = (pageNo - 1) * PAGE_SIZE + 1;
        return `${from}~${from + pageItems.length - 1}번`;
    }

    function remember(item) {
        selected.set(item.contentId, {
            contentId: item.contentId,
            contentTypeId: item.contentTypeId,
            title: item.title,
            // 중복 확인 후보는 관리자가 하나씩 확인하고 고른 경우에만 서버가 저장한다.
            allowPossibleDuplicate: duplicateStatus(item) === "POSSIBLE_DUPLICATE"
        });
    }

    /**
     * 현재 페이지 기준 선택 상태. 등록완료 항목은 고를 수 없고,
     * 중복 확인 항목은 일괄 선택 대상에서 빼고 하나씩 확인해서만 고른다.
     */
    function currentPageSelection() {
        return pageSelection(pageItems, item => duplicateStatus(item) === "NOT_REGISTERED",
            item => selected.has(item.contentId));
    }

    /** 현재 페이지의 미등록 후보만 고르거나 푼다. 다른 페이지에서 고른 항목은 그대로 둔다. */
    function selectCurrentPage(select) {
        currentPageSelection().targets.forEach(item => {
            if (select) remember(item);
            else selected.delete(item.contentId);
        });
        renderRows();
    }

    function renderRows() {
        rows.replaceChildren();
        pageItems.forEach(item => rows.append(buildRow(item)));
        updateCounts();
        updateSelection();
        updatePaging();
    }

    function buildRow(item) {
        const status = duplicateStatus(item);
        const registered = status === "REGISTERED";
        const review = status === "POSSIBLE_DUPLICATE";
        const row = document.createElement("tr");
        row.classList.toggle("is-registered", registered);
        row.classList.toggle("is-review", review);

        const checkCell = document.createElement("td");
        checkCell.className = "is-check";
        const checkbox = document.createElement("input");
        checkbox.type = "checkbox";
        // 이미 등록된 항목은 고를 수 없다.
        checkbox.disabled = registered;
        checkbox.checked = !registered && selected.has(item.contentId);
        checkbox.setAttribute("aria-label", `${item.title} 선택`);
        checkbox.addEventListener("change", () => {
            if (checkbox.checked && review && !confirmPossibleDuplicate(item)) {
                checkbox.checked = false;
                return;
            }
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

        row.append(
            checkCell,
            thumbCell,
            textCell(item.title),
            textCell(item.address),
            textCell(item.contentTypeName),
            textCell(item.contentId),
            statusCell(item, status));
        return row;
    }

    /** 등록 여부 배지와, 같은 곳으로 본 기존 여행지 링크·근거. */
    function statusCell(item, status) {
        const cell = document.createElement("td");
        const badge = document.createElement("span");
        const [className, label] = status === "REGISTERED" ? ["is-registered", "등록완료"]
            : status === "POSSIBLE_DUPLICATE" ? ["is-review", "중복 확인"] : ["is-new", "미등록"];
        badge.className = `admin-kto-import-badge ${className}`;
        badge.textContent = label;
        cell.append(badge);
        const existingId = item.duplicate?.destinationId;
        if (existingId) {
            cell.append(existingLink(existingId, item.duplicate.destinationName));
            const reason = document.createElement("span");
            reason.className = "admin-kto-import-reason";
            reason.textContent = item.duplicate.message || "";
            cell.append(reason);
        }
        return cell;
    }

    function existingLink(destinationId, name) {
        const link = document.createElement("a");
        link.className = "admin-kto-import-existing";
        link.href = `/admin/destinations/edit/${encodeURIComponent(destinationId)}`;
        link.target = "_blank";
        link.rel = "noopener";
        link.textContent = `#${destinationId}${name ? ` ${name}` : ""}`;
        return link;
    }

    function confirmPossibleDuplicate(item) {
        const duplicate = item.duplicate || {};
        return window.confirm(`'${item.title}'은(는) 기존 여행지 #${duplicate.destinationId}`
            + ` ${duplicate.destinationName || ""}와 같은 곳일 수 있습니다 (${duplicate.message || "중복 확인"}).\n`
            + "다른 여행지가 맞다면 확인을 눌러 등록 대상에 넣어 주세요.");
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

    /** 전체 선택 건수는 페이지를 넘어 고른 항목 기준이고, 현재 페이지 건수는 따로 보여준다. */
    function updateSelection() {
        selectedCount.textContent = `전체 선택 ${selected.size}건`;
        submitButton.disabled = selected.size === 0;
        clearButton.disabled = selected.size === 0;
        applyPageSelection(currentPageSelection(), {
            toggle: pageToggle,
            selectButton: selectAllButton,
            clearButton: pageClearButton,
            count: pageCount
        });
    }

    function updatePaging() {
        pageLabel.textContent = String(pageNo);
        previousButton.disabled = pageNo <= 1;
        nextButton.disabled = pageNo * PAGE_SIZE >= totalCount;
    }

    async function importSelected() {
        const items = Array.from(selected.values())
            .map(item => ({
                contentId: item.contentId,
                contentTypeId: item.contentTypeId,
                allowPossibleDuplicate: item.allowPossibleDuplicate === true
            }));
        if (!items.length) return;

        setBusy(true, `선택한 ${items.length}건을 등록하고 있습니다.`);
        try {
            const payload = await requestJson("/admin/api/kto/tour/bulk/import", {
                method: "POST",
                headers: {"Content-Type": "application/json", Accept: "application/json"},
                body: JSON.stringify({items})
            });
            renderResult(payload);
            // 등록되었거나 중복으로 건너뛴 항목은 선택에서 뺀다. 실패 항목은 그대로 남긴다.
            // 저장 직전에 새로 중복 확인이 필요해진 항목도 빼서, 목록에서 근거를 보고 다시 고르게 한다.
            (payload.results || [])
                .filter(result => ["SUCCESS", "DUPLICATE", "POSSIBLE_DUPLICATE"].includes(result.status))
                .forEach(result => selected.delete(result.contentId));
            // 등록 여부와 건수를 서버 기준으로 다시 받는다 (같은 조건이면 TourAPI 재호출은 없다).
            await loadCandidates();
            setStatus(resultSummaryText(payload));
        } catch (error) {
            setStatus(error.message, true);
        } finally {
            setBusy(false);
        }
    }

    function resultSummaryText(payload) {
        return `성공 ${payload.successCount}건 · 중복으로 건너뜀 ${payload.duplicateCount}건`
            + (payload.possibleDuplicateCount ? ` · 중복 확인 필요 ${payload.possibleDuplicateCount}건` : "")
            + ` · 실패 ${payload.failureCount}건`;
    }

    function renderResult(payload) {
        resultSection.hidden = false;
        resultSummary.textContent = resultSummaryText(payload);
        resultList.replaceChildren();
        (payload.results || [])
            .filter(result => result.status !== "SUCCESS")
            .forEach(result => {
                const entry = document.createElement("li");
                entry.classList.toggle("is-failed", result.status === "FAILED");
                entry.classList.toggle("is-review", result.status === "POSSIBLE_DUPLICATE");
                const label = result.status === "FAILED" ? "실패"
                    : result.status === "POSSIBLE_DUPLICATE" ? "중복 확인" : "중복";
                entry.textContent = `[${label}] ${result.title || "이름 없음"}`
                    + ` (contentId ${result.contentId})`
                    + (result.message ? ` - ${result.message}` : "");
                if (result.destinationId && result.status !== "FAILED") {
                    entry.append(" ", existingLink(result.destinationId, null));
                }
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

    /** 상태를 바꾸는 요청에 CSRF 토큰을 싣는다. (layout 의 meta 값을 그대로 쓴다) */
    function csrfHeader(method) {
        const unsafe = !["GET", "HEAD", "OPTIONS", "TRACE"]
            .includes(String(method || "GET").toUpperCase());
        if (!unsafe) return {};
        const token = document.querySelector('meta[name="_csrf"]')?.content;
        const header = document.querySelector('meta[name="_csrf_header"]')?.content;
        return token && header ? {[header]: token} : {};
    }

    async function requestJson(url, options) {
        const settings = options || {headers: {Accept: "application/json"}};
        const response = await fetch(url, {
            ...settings,
            headers: {...(settings.headers || {}), ...csrfHeader(settings.method)}
        });
        let payload = null;
        try {
            payload = await response.json();
        } catch (error) {
            payload = null;
        }
        if (!response.ok) {
            if (response.status === 403) {
                throw new Error("요청 권한 또는 보안 토큰을 확인할 수 없습니다. 관리자 페이지를 새로고침한 뒤 다시 시도해 주세요.");
            }
            throw new Error((payload && payload.message)
                || `요청을 처리하지 못했습니다. (HTTP ${response.status})`);
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
