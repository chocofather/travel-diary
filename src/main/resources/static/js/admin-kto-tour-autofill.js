document.addEventListener("DOMContentLoaded", () => {
    const nameInput = document.querySelector("[data-destination-korean-name]");
    const searchButton = document.querySelector("[data-kto-tour-search-button]");
    const status = document.querySelector("[data-kto-tour-status]");
    const results = document.querySelector("[data-kto-tour-results]");
    const destinationType = document.querySelector("[data-kto-tour-destination-type]");
    const englishNameInput = document.querySelector("[data-kto-tour-english-name]");
    const englishOverviewInput = document.querySelector("[data-kto-tour-english-overview]");
    const englishStatus = document.querySelector("[data-kto-tour-english-status]");
    let lastSelectedContentId = null;
    let englishRequestGeneration = 0;
    let koreanDetailRequestGeneration = 0;

    if (!nameInput || !searchButton || !status || !results) return;

    searchButton.addEventListener("click", searchTours);

    async function searchTours() {
        const keyword = nameInput.value.trim();
        results.replaceChildren();
        if (!keyword) {
            setStatus("여행지명을 입력해 주세요.", true);
            return;
        }

        setLoading(true, "TourAPI에서 검색하고 있습니다.");
        try {
            // 선택한 여행지 유형을 그대로 넘기고, TourAPI contentTypeId 변환은 서버가 한다.
            const params = new URLSearchParams({
                keyword,
                pageNo: "1",
                numOfRows: "10",
                destinationType: destinationType ? destinationType.value : ""
            });
            const response = await fetch(`/admin/api/kto/tour/search?${params.toString()}`, {
                headers: {Accept: "application/json"}
            });
            const payload = await response.json();
            if (!response.ok) throw new Error(payload.message || "관광정보를 불러오지 못했습니다.");
            renderCandidates(Array.isArray(payload.items) ? payload.items : []);
        } catch (error) {
            setStatus(error.message || "관광정보를 불러오지 못했습니다.", true);
        } finally {
            searchButton.disabled = false;
        }
    }

    function renderCandidates(items) {
        results.replaceChildren();
        if (!items.length) {
            setStatus("검색 결과가 없습니다.");
            return;
        }
        setStatus(`${items.length}개의 여행지 후보가 검색되었습니다.`);
        items.forEach(item => {
            const button = document.createElement("button");
            button.type = "button";
            button.className = "admin-kto-tour-candidate";

            const title = document.createElement("strong");
            title.textContent = item.title || "이름 없음";
            const meta = document.createElement("span");
            meta.textContent = [item.address, item.contentTypeName || item.contentTypeId]
                .filter(Boolean).join(" · ");
            button.append(title, meta);
            button.addEventListener("click", () => loadDetail(item));
            results.append(button);
        });
    }

    async function loadDetail(item) {
        const contentChanged = lastSelectedContentId !== null
            && lastSelectedContentId !== item.contentId;
        const shouldApplyRegion = lastSelectedContentId === null || contentChanged;
        const requestGeneration = ++englishRequestGeneration;
        const loadingGeneration = ++koreanDetailRequestGeneration;
        if (contentChanged) {
            clearTourApiManagedFields();
            clearEnglishAutofill();
            window.TravelDiaryRegionSelector?.clearSelection();
        }
        lastSelectedContentId = item.contentId;
        setLoading(true, `${item.title || "선택한 장소"} 정보를 불러오고 있습니다.`);
        try {
            const params = new URLSearchParams({
                contentId: item.contentId,
                contentTypeId: item.contentTypeId
            });
            const response = await fetch(`/admin/api/kto/tour/detail?${params.toString()}`, {
                headers: {Accept: "application/json"}
            });
            const payload = await response.json();
            if (!response.ok) throw new Error(payload.message || "관광정보를 불러오지 못했습니다.");
            if (requestGeneration !== englishRequestGeneration) return;
            applyAutofill(payload);
            setStatus(`${payload.title || item.title || "선택한 장소"} 정보를 빈 항목에 입력했습니다.`);
            if (shouldApplyRegion) {
                void applyRegionMatch(payload.regionMatch, requestGeneration);
            }
            void matchEnglishTour(
                payload.title || item.title,
                payload.longitude || item.longitude,
                payload.latitude || item.latitude,
                requestGeneration
            );
        } catch (error) {
            if (requestGeneration !== englishRequestGeneration) return;
            setStatus(error.message || "관광정보를 불러오지 못했습니다.", true);
        } finally {
            if (loadingGeneration === koreanDetailRequestGeneration) {
                searchButton.disabled = false;
            }
        }
    }

    async function applyRegionMatch(regionMatch, requestGeneration) {
        if (requestGeneration !== englishRequestGeneration) return;
        const regionSelector = window.TravelDiaryRegionSelector;
        if (!regionSelector) return;
        if (!regionMatch?.matched || !Array.isArray(regionMatch.path)) {
            setStatus("지역을 자동으로 찾지 못했습니다. 직접 선택해 주세요.");
            return;
        }

        const applied = await regionSelector.applyRegionPath(regionMatch.path);
        if (requestGeneration !== englishRequestGeneration) return;
        if (!applied) {
            setStatus("지역을 자동으로 찾지 못했습니다. 직접 선택해 주세요.");
        }
    }

    function applyAutofill(detail) {
        fillIfEmpty(nameInput, detail.title);
        fillIfEmpty(document.querySelector("[data-kto-tour-overview]"), detail.overview);
        fillIfEmpty(document.querySelector("[data-kto-tour-longitude]"), detail.longitude);
        fillIfEmpty(document.querySelector("[data-kto-tour-latitude]"), detail.latitude);

        const currentType = destinationType ? destinationType.value : "";
        fillTypeField("closedDays", currentType, detail.closedDays);
        fillTypeField("openingHours", currentType, detail.openingHours);
        fillTypeField("admissionFee", currentType, detail.admissionFee);
        fillTypeField("contactNumber", currentType, detail.contactNumber);
        fillTypeField("homepageUrl", currentType, detail.homepageUrl);
        fillTypeField("guide", currentType, detail.guide);
        fillTypeField("mainMenu", currentType, detail.mainMenu);
        fillTypeField("checkinTime", currentType, detail.checkinTime);
        fillTypeField("checkoutTime", currentType, detail.checkoutTime);
        fillTypeField("roomCount", currentType, detail.roomCount);
        fillTypeField("roomType", currentType, detail.roomType);
        // 문자열 판정은 서버에서 끝났으므로 여기서는 Boolean 값만 그대로 반영한다.
        fillTypeField("parkingAvailable", currentType, detail.parkingAvailable);
        fillTypeField("takeoutAvailable", currentType, detail.takeoutAvailable);
        fillTypeField("reservation", currentType, detail.reservation);
    }

    async function matchEnglishTour(koreanTitle, mapX, mapY, requestGeneration) {
        if (!englishNameInput || !englishOverviewInput || !englishStatus) return;
        if (!hasValue(mapX) || !hasValue(mapY)) {
            setEnglishStatus("좌표가 없어 영문 관광정보를 자동으로 찾지 않았습니다.");
            return;
        }

        setEnglishStatus("주변 영문 관광정보를 찾고 있습니다.");
        try {
            const params = new URLSearchParams({
                title: koreanTitle,
                mapX: String(mapX),
                mapY: String(mapY)
            });
            const response = await fetch(`/admin/api/kto/tour/english-match?${params.toString()}`, {
                headers: {Accept: "application/json"}
            });
            const payload = await response.json();
            if (requestGeneration !== englishRequestGeneration) return;
            if (!response.ok) throw new Error(payload.message || "영문 관광정보를 불러오지 못했습니다.");

            if (payload.status === "MATCHED" && payload.matched) {
                await loadEnglishDetail(payload.matched, requestGeneration);
                return;
            }
            setEnglishStatus("일치하는 영문 관광정보가 없습니다.");
        } catch (error) {
            if (requestGeneration !== englishRequestGeneration) return;
            setEnglishStatus(error.message || "영문 관광정보를 불러오지 못했습니다.", true);
        }
    }

    async function loadEnglishDetail(candidate, requestGeneration) {
        if (!candidate || !candidate.contentId) return;
        setEnglishStatus(`${candidate.title || "선택한 장소"} 영문 정보를 불러오고 있습니다.`);
        try {
            const params = new URLSearchParams({contentId: candidate.contentId});
            const response = await fetch(`/admin/api/kto/tour/english-detail?${params.toString()}`, {
                headers: {Accept: "application/json"}
            });
            const payload = await response.json();
            if (requestGeneration !== englishRequestGeneration) return;
            if (!response.ok) throw new Error(payload.message || "영문 관광정보를 불러오지 못했습니다.");
            fillIfEmpty(englishNameInput, payload.title);
            fillIfEmpty(englishOverviewInput, payload.overview);
            setEnglishStatus(`${payload.title || candidate.title || "선택한 장소"} 영문 정보를 빈 항목에 입력했습니다.`);
        } catch (error) {
            if (requestGeneration !== englishRequestGeneration) return;
            setEnglishStatus(error.message || "영문 관광정보를 불러오지 못했습니다.", true);
        }
    }

    function hasValue(value) {
        return value !== null && value !== undefined && String(value).trim() !== "";
    }

    function fillTypeField(fieldName, type, value) {
        if (!type) return;
        // 음식점/카페처럼 같은 입력칸을 공유하는 유형은 data-kto-tour-type에 공백으로 나열한다.
        const element = Array.from(document.querySelectorAll(`[data-kto-tour-field="${fieldName}"]`))
            .find(candidate => (candidate.dataset.ktoTourType || "").split(/\s+/).includes(type));
        if (element && element.type === "checkbox") {
            setCheckboxIfKnown(element, value);
            return;
        }
        fillIfEmpty(element, value);
    }

    // 서버가 판별한 nullable Boolean 만 반영한다. null/undefined 면 기존 체크 상태를 건드리지 않는다.
    function setCheckboxIfKnown(element, value) {
        if (value !== true && value !== false) return;
        if (element.checked === value) return;
        element.checked = value;
        element.dispatchEvent(new Event("change", {bubbles: true}));
    }

    function fillIfEmpty(element, value) {
        if (!element || element.value.trim()) return;
        if (value === null || value === undefined || String(value).trim() === "") return;
        setFieldValue(element, String(value));
    }

    function setFieldValue(element, value) {
        element.value = value;
        element.dispatchEvent(new Event("change", {bubbles: true}));
    }

    function clearTourApiManagedFields() {
        const managedFields = new Set([
            nameInput,
            document.querySelector("[data-kto-tour-overview]"),
            document.querySelector("[data-kto-tour-longitude]"),
            document.querySelector("[data-kto-tour-latitude]"),
            englishNameInput,
            englishOverviewInput,
            ...document.querySelectorAll("[data-kto-tour-field]")
        ]);
        for (const element of managedFields) {
            if (!element) continue;
            // 체크박스는 value 가 제출값이므로 비우지 않고 체크만 해제한다.
            if (element.type === "checkbox") {
                element.checked = false;
            } else {
                element.value = "";
            }
            element.dispatchEvent(new Event("change", {bubbles: true}));
        }
    }

    function clearEnglishAutofill() {
        setEnglishStatus("새 장소 기준으로 영문 관광정보를 다시 찾습니다.");
    }

    function setLoading(loading, message) {
        searchButton.disabled = loading;
        setStatus(message);
    }

    function setStatus(message, isError = false) {
        status.textContent = message;
        status.classList.toggle("is-error", isError);
    }

    function setEnglishStatus(message, isError = false) {
        if (!englishStatus) return;
        englishStatus.textContent = message;
        englishStatus.classList.toggle("is-error", isError);
    }
});
