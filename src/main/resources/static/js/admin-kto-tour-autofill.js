document.addEventListener("DOMContentLoaded", () => {
    const nameInput = document.querySelector("[data-destination-korean-name]");
    const searchButton = document.querySelector("[data-kto-tour-search-button]");
    const status = document.querySelector("[data-kto-tour-status]");
    const results = document.querySelector("[data-kto-tour-results]");
    const destinationType = document.querySelector("[data-kto-tour-destination-type]");
    // 번역 탭과 같은 canonical 코드. 서버도 이 값만 받는다.
    const FOREIGN_LANGUAGES = [
        {code: "en", label: "영어"},
        {code: "ja", label: "일본어"},
        {code: "zh-CN", label: "간체"},
        {code: "zh-TW", label: "번체"}
    ];
    // 유형별 상세에서 번역칸으로 옮기는 값. 어느 유형의 값인지는 서버가 판단한다.
    const SUBTYPE_FIELDS = [
        "closedDays", "openingHours", "admissionFee", "mainMenu", "roomType", "mainProducts"
    ];
    const foreignStatus = document.querySelector("[data-kto-tour-foreign-status]");
    let lastSelectedContentId = null;
    let foreignRequestGeneration = 0;
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
        const requestGeneration = ++foreignRequestGeneration;
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
            if (requestGeneration !== foreignRequestGeneration) return;
            applyAutofill(payload);
            setStatus(`${payload.title || item.title || "선택한 장소"} 정보를 빈 항목에 입력했습니다.`);
            if (shouldApplyRegion) {
                void applyRegionMatch(payload.regionMatch, requestGeneration);
            }
            void matchForeignTours(
                payload.title || item.title,
                payload.longitude || item.longitude,
                payload.latitude || item.latitude,
                requestGeneration
            );
        } catch (error) {
            if (requestGeneration !== foreignRequestGeneration) return;
            setStatus(error.message || "관광정보를 불러오지 못했습니다.", true);
        } finally {
            if (loadingGeneration === koreanDetailRequestGeneration) {
                searchButton.disabled = false;
            }
        }
    }

    async function applyRegionMatch(regionMatch, requestGeneration) {
        if (requestGeneration !== foreignRequestGeneration) return;
        const regionSelector = window.TravelDiaryRegionSelector;
        if (!regionSelector) return;
        if (!regionMatch?.matched || !Array.isArray(regionMatch.path)) {
            setStatus("지역을 자동으로 찾지 못했습니다. 직접 선택해 주세요.");
            return;
        }

        const applied = await regionSelector.applyRegionPath(regionMatch.path);
        if (requestGeneration !== foreignRequestGeneration) return;
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

    /**
     * 네 언어를 각각 찾아 빈 번역칸만 채운다.
     * 한 언어가 없거나 실패해도 나머지 언어는 그대로 진행한다.
     */
    async function matchForeignTours(koreanTitle, mapX, mapY, requestGeneration) {
        if (!foreignStatus) return;
        if (!hasValue(mapX) || !hasValue(mapY)) {
            setForeignStatus("좌표가 없어 외국어 관광정보를 자동으로 찾지 않았습니다.");
            return;
        }

        setForeignStatus("주변 외국어 관광정보를 찾고 있습니다.");
        const settled = await Promise.allSettled(FOREIGN_LANGUAGES.map(
            language => fillForeignLanguage(language, koreanTitle, mapX, mapY, requestGeneration)));
        if (requestGeneration !== foreignRequestGeneration) return;

        const filled = [];
        const missing = [];
        const failed = [];
        settled.forEach((result, index) => {
            const label = FOREIGN_LANGUAGES[index].label;
            // 한 언어의 예외가 다른 언어 결과를 지우지 않는다.
            const state = result.status === "fulfilled" ? result.value : "error";
            if (state === "filled") filled.push(label);
            else if (state === "none") missing.push(label);
            else failed.push(label);
        });
        setForeignStatus(summarize(filled, missing, failed), filled.length === 0);
    }

    function summarize(filled, missing, failed) {
        const parts = [];
        if (filled.length) parts.push(`${filled.join("·")} 정보를 빈 항목에 입력했습니다.`);
        if (missing.length) parts.push(`${missing.join("·")}는 일치하는 관광정보가 없습니다.`);
        if (failed.length) parts.push(`${failed.join("·")}는 불러오지 못했습니다.`);
        return parts.join(" ");
    }

    /** 한 언어의 매칭 + 상세 조회. 채웠으면 filled, 대응 장소가 없으면 none 을 준다. */
    async function fillForeignLanguage(language, koreanTitle, mapX, mapY, requestGeneration) {
        const nameInputForLanguage = foreignInput("name", language.code);
        const overviewInputForLanguage = foreignInput("overview", language.code);
        if (!nameInputForLanguage && !overviewInputForLanguage) return "none";

        const matchParams = new URLSearchParams({
            language: language.code,
            title: koreanTitle,
            mapX: String(mapX),
            mapY: String(mapY)
        });
        const matched = await requestJson(`/admin/api/kto/tour/foreign-match?${matchParams}`);
        if (requestGeneration !== foreignRequestGeneration) return "none";
        if (matched.status !== "MATCHED" || !matched.matched || !matched.matched.contentId) {
            return "none";
        }

        // 외국어 유형 코드는 국문과 다르므로 매칭 결과 값을 그대로 넘긴다.
        const detailParams = new URLSearchParams({
            language: language.code,
            contentId: matched.matched.contentId
        });
        if (hasValue(matched.matched.contentTypeId)) {
            detailParams.set("contentTypeId", matched.matched.contentTypeId);
        }
        const detail = await requestJson(`/admin/api/kto/tour/foreign-detail?${detailParams}`);
        if (requestGeneration !== foreignRequestGeneration) return "none";

        // 현지어 제목이 국문 원문과 같으면 번역이 아니므로 넣지 않는다.
        const localizedTitle = sameAsKoreanTitle(detail.title, koreanTitle) ? null : detail.title;
        fillIfEmpty(nameInputForLanguage, localizedTitle);
        fillIfEmpty(overviewInputForLanguage, detail.overview);
        // 간단 설명은 대응하는 값이 없어 비워 둔다.
        // 유형별 상세. 서버가 유형에 맞는 값만 채워 주므로 여기서는 유형을 따지지 않는다.
        // 화면에 없는 칸(다른 유형)이나 값이 없는 칸은 그대로 둔다.
        const subtypeFilled = SUBTYPE_FIELDS
            .map(field => fillSubtypeField(field, language.code, detail[field]))
            .some(Boolean);
        return hasValue(localizedTitle) || hasValue(detail.overview) || subtypeFilled
            ? "filled" : "none";
    }

    /** 해당 언어 탭의 유형별 상세 입력칸. 슬롯 번호가 아니라 언어 코드로 찾는다. */
    function fillSubtypeField(fieldName, languageCode, value) {
        const element = document.querySelector(
            `[data-kto-tour-foreign-field="${fieldName}"]`
            + `[data-kto-tour-foreign-language="${languageCode}"]`);
        if (!element || !hasValue(value) || element.value.trim()) return false;
        fillIfEmpty(element, value);
        return true;
    }

    function sameAsKoreanTitle(foreignTitle, koreanTitle) {
        if (!hasValue(foreignTitle) || !hasValue(koreanTitle)) return false;
        return String(foreignTitle).trim().replace(/\s+/g, " ")
            === String(koreanTitle).trim().replace(/\s+/g, " ");
    }

    function foreignInput(field, languageCode) {
        return document.querySelector(`[data-kto-tour-foreign-${field}="${languageCode}"]`);
    }

    async function requestJson(url) {
        const response = await fetch(url, {headers: {Accept: "application/json"}});
        const payload = await response.json();
        if (!response.ok) {
            throw new Error(payload.message || "외국어 관광정보를 불러오지 못했습니다.");
        }
        return payload;
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
            ...document.querySelectorAll("[data-kto-tour-foreign-name]"),
            ...document.querySelectorAll("[data-kto-tour-foreign-overview]"),
            ...document.querySelectorAll("[data-kto-tour-foreign-field]"),
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
        setForeignStatus("새 장소 기준으로 외국어 관광정보를 다시 찾습니다.");
    }

    function setLoading(loading, message) {
        searchButton.disabled = loading;
        setStatus(message);
    }

    function setStatus(message, isError = false) {
        status.textContent = message;
        status.classList.toggle("is-error", isError);
    }

    function setForeignStatus(message, isError = false) {
        if (!foreignStatus) return;
        foreignStatus.textContent = message;
        foreignStatus.classList.toggle("is-error", isError);
    }
});
