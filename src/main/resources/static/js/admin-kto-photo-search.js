// 서버 KtoSelectedPhotoRequestParser.MAX_SELECTED_PHOTOS 와 같은 값
const MAX_KTO_SELECTED_PHOTOS = 30;
const KTO_SELECTION_LIMIT_MESSAGE = "KTO 사진은 최대 30장까지 선택할 수 있습니다.";

function ktoPhotoRelevanceRank(item, keyword) {
    const normalizedKeyword = String(keyword ?? "").trim();
    const title = String(item.title ?? "").trim();
    const searchKeyword = String(item.searchKeyword ?? "").trim();

    if (title === normalizedKeyword) return 0;
    if (title.includes(normalizedKeyword)) return 1;
    if (searchKeyword.includes(normalizedKeyword)) return 2;
    return 3;
}

// 관광사진 API galPhotographyMonth(YYYYMM)만 촬영월로 본다. TourAPI 사진은 촬영일이 없어 null 이다
function ktoPhotoPhotographyMonthKey(item) {
    const month = String(item?.photographyMonth ?? "").trim();
    return /^\d{6}$/.test(month) ? Number(month) : null;
}

// 촬영월 최신순, 촬영월이 없으면 뒤로 보낸다
function compareKtoPhotoPhotographyMonth(left, right) {
    if (left === right) return 0;
    if (left === null) return 1;
    if (right === null) return -1;
    return right - left;
}

// 서버가 정한 licenseType 으로만 나눈다. 제3유형은 숨기지 않고 제1유형 뒤에 두며, 그 밖의 값은 맨 뒤로 보낸다
function ktoPhotoLicenseRank(item) {
    const licenseType = String(item?.licenseType ?? "").trim();
    if (licenseType === "KOGL_TYPE_1") return 0;
    if (licenseType === "KOGL_TYPE_3") return 1;
    return 2;
}

// 1단계(관광사진 페이지를 넘기는 동안)에는 촬영월이 있는 제1유형만 바로 보여준다.
// 촬영월 없는 제1유형(TourAPI 등)과 제3유형 등은 제1유형 페이지를 모두 본 뒤에 보여주려고 따로 보관한다
function isImmediateKtoPhoto(item) {
    return ktoPhotoLicenseRank(item) === 0 && ktoPhotoPhotographyMonthKey(item) !== null;
}

function isTrailingLicenseKtoPhoto(item) {
    return ktoPhotoLicenseRank(item) !== 0;
}

// 유형 → 촬영월 최신순(없으면 그 유형 뒤) → 검색어 관련도 → 받은 순서. 관련도는 유형·촬영월을 뒤집지 않는다
function stablySortKtoPhotos(items, keyword) {
    return items
        .map((item, originalIndex) => ({
            item,
            originalIndex,
            rank: ktoPhotoRelevanceRank(item, keyword),
            licenseRank: ktoPhotoLicenseRank(item),
            photographyMonth: ktoPhotoPhotographyMonthKey(item)
        }))
        .sort((left, right) =>
            left.licenseRank - right.licenseRank
            || compareKtoPhotoPhotographyMonth(left.photographyMonth, right.photographyMonth)
            || left.rank - right.rank
            || left.originalIndex - right.originalIndex
        )
        .map(entry => entry.item);
}

function ktoPhotoSelectionKey(item) {
    const externalContentId = String(item?.externalContentId ?? "").trim();
    const imageUrl = String(item?.imageUrl ?? "").trim();
    // 서버 parser 가 두 값을 모두 필수로 요구하므로 하나라도 없으면 선택 대상에서 제외한다
    if (!externalContentId || !imageUrl) return null;
    return JSON.stringify([externalContentId, imageUrl]);
}

// 이미 불러온 사진은 자리를 그대로 두고, 새 결과 중 겹치는 사진만 뺀다. 새 결과의 순서는 유지한다
function excludeLoadedKtoPhotos(items, loadedItems) {
    const seenKeys = new Set(loadedItems.map(ktoPhotoSelectionKey).filter(key => key !== null));
    return items.filter(item => {
        const key = ktoPhotoSelectionKey(item);
        if (key === null) return true;
        if (seenKeys.has(key)) return false;
        seenKeys.add(key);
        return true;
    });
}

function createKtoPhotoSelectionState() {
    const selectedItems = new Map();
    let mainSelectionKey = null;

    return {
        toggle(item) {
            const key = ktoPhotoSelectionKey(item);
            if (!key) return null;

            if (selectedItems.has(key)) {
                selectedItems.delete(key);
                if (mainSelectionKey === key) mainSelectionKey = null;
                return false;
            }

            // 해제는 항상 허용하고, 새로 추가할 때만 서버와 같은 상한을 적용한다
            if (selectedItems.size >= MAX_KTO_SELECTED_PHOTOS) return "limit";

            selectedItems.set(key, item);
            return true;
        },
        remove(key) {
            if (!selectedItems.delete(key)) return false;
            if (mainSelectionKey === key) mainSelectionKey = null;
            return true;
        },
        setMain(key) {
            if (!selectedItems.has(key)) return false;
            mainSelectionKey = key;
            return true;
        },
        isSelected(item) {
            const key = ktoPhotoSelectionKey(item);
            return key !== null && selectedItems.has(key);
        },
        isMain(item) {
            const key = ktoPhotoSelectionKey(item);
            return key !== null && key === mainSelectionKey;
        },
        entries() {
            return Array.from(selectedItems, ([key, item]) => ({
                key,
                item,
                isMain: key === mainSelectionKey
            }));
        },
        count() {
            return selectedItems.size;
        },
        mainItem() {
            return mainSelectionKey === null ? null : selectedItems.get(mainSelectionKey) ?? null;
        }
    };
}

// 공공누리 제3유형은 변경금지라 미리보기도 잘라 보이지 않게 원본 비율로 그린다
function isKtoPhotoNoDerivatives(item) {
    return String(item?.licenseType ?? "").trim() === "KOGL_TYPE_3";
}

function serializeKtoSelectedPhotos(entries) {
    return entries.map(({ item, isMain }) => ({
        externalContentId: String(item.externalContentId ?? "").trim(),
        imageUrl: String(item.imageUrl ?? "").trim(),
        title: String(item.title ?? "").trim(),
        photographer: String(item.photographer ?? "").trim(),
        isMain: Boolean(isMain)
    }));
}

document.addEventListener("DOMContentLoaded", () => {
    const endpoint = "/admin/api/kto/photos/search";
    const pageSize = 12;
    const defaultErrorMessage = "관광사진 검색 서비스를 이용할 수 없습니다.";

    document.querySelectorAll("[data-kto-photo-search]").forEach(searchArea => {
        const keywordInput = searchArea.querySelector("[data-kto-photo-keyword]");
        const searchButton = searchArea.querySelector("[data-kto-photo-search-button]");
        const status = searchArea.querySelector("[data-kto-photo-status]");
        const results = searchArea.querySelector("[data-kto-photo-results]");
        const moreButton = searchArea.querySelector("[data-kto-photo-more]");
        const selectedArea = searchArea.querySelector("[data-kto-photo-selected-area]");
        const selectedCount = searchArea.querySelector("[data-kto-photo-selected-count]");
        const mainStatus = searchArea.querySelector("[data-kto-photo-main-status]");
        const selectedList = searchArea.querySelector("[data-kto-photo-selected-list]");
        const selectedPhotosJson = searchArea.querySelector("[data-kto-selected-photos-json]");
        const submitButton = searchArea.querySelector("[data-kto-photo-submit]");
        const destinationNameInput = document.querySelector("[data-destination-korean-name]");

        if (!keywordInput || !searchButton || !status || !results || !moreButton
            || !selectedArea || !selectedCount || !mainStatus || !selectedList
            || !selectedPhotosJson) return;

        let currentKeyword = "";
        let currentPage = 0;
        let totalCount = 0;
        // 첫 페이지 전체 수(관광사진 + 덧붙인 TourAPI 사진). 상태 문구에만 쓴다
        let resultTotalCount = 0;
        let loading = false;
        // 화면에 그린 사진. 맨 뒤에 붙이기만 하고 이미 그린 카드의 순서는 바꾸지 않는다
        let displayedItems = [];
        // 1단계 동안 보여주지 않고 보관하는 사진: 촬영월 없는 제1유형 / 제3유형 등
        let deferredUndatedPrimaryItems = [];
        let deferredTrailingItems = [];
        // 관광사진 페이지를 모두 받은 뒤(2단계) 보관분을 보여줄 순서대로 담은 대기열
        let galleryExhausted = false;
        let deferredQueue = null;
        let latestBatchStartItem = null;
        let firstTrailingItem = null;
        // 등록 요청을 보낸 뒤에는 화면을 떠날 때까지 다시 보내지 않는다(빠른 두 번 클릭으로 같은 사진이 두 번 저장되지 않게)
        let submitting = false;
        const submitLabel = submitButton?.textContent.trim() ?? "";
        const selectionState = createKtoPhotoSelectionState();

        function destinationName() {
            return destinationNameInput?.value.trim() ?? "";
        }

        function setStatus(message, state = "") {
            status.textContent = message;
            if (state) {
                status.dataset.state = state;
            } else {
                delete status.dataset.state;
            }
        }

        function clearSelectionLimitNotice() {
            if (status.textContent === KTO_SELECTION_LIMIT_MESSAGE) setStatus("");
        }

        function setLoading(nextLoading, append) {
            loading = nextLoading;
            searchArea.setAttribute("aria-busy", String(nextLoading));
            searchButton.disabled = nextLoading;
            moreButton.disabled = nextLoading;
            searchButton.textContent = nextLoading && !append ? "검색 중..." : "검색";
            moreButton.textContent = nextLoading && append ? "불러오는 중..." : "더보기";
        }

        function formatPhotographyMonth(value) {
            const month = String(value ?? "").trim();
            return /^\d{6}$/.test(month)
                ? `${month.slice(0, 4)}.${month.slice(4)}`
                : month;
        }

        function attribution(item) {
            const sourceName = String(item.sourceName ?? "한국관광공사").trim();
            const photographer = String(item.photographer ?? "").trim();
            if (!photographer) return sourceName;
            if (sourceName && photographer.includes(sourceName)) return photographer;
            return [sourceName, photographer].filter(Boolean).join(" · ");
        }

        function textElement(tagName, className, text) {
            const element = document.createElement(tagName);
            element.className = className;
            element.textContent = text;
            return element;
        }

        function createCard(item) {
            const card = document.createElement("article");
            card.className = "admin-kto-photo-card";

            const selectionKey = ktoPhotoSelectionKey(item);
            const isSelected = selectionState.isSelected(item);
            const isMain = selectionState.isMain(item);
            card.classList.toggle("is-selected", isSelected);
            card.classList.toggle("is-main", isMain);
            card.classList.toggle("is-no-derivatives", isKtoPhotoNoDerivatives(item));
            card.setAttribute("role", "button");
            card.setAttribute("aria-pressed", String(isSelected));
            card.setAttribute(
                "aria-label",
                selectionKey === null
                    ? "식별 정보가 없어 선택할 수 없는 관광사진"
                    : `${String(item.title ?? "관광사진")} ${isSelected ? "선택 해제" : "선택"}`
            );

            if (selectionKey === null) {
                card.setAttribute("aria-disabled", "true");
            } else {
                card.tabIndex = 0;

                const toggleCardSelection = () => {
                    const result = selectionState.toggle(item);
                    if (result === null) return;
                    if (result === "limit") {
                        setStatus(KTO_SELECTION_LIMIT_MESSAGE, "error");
                        return;
                    }

                    clearSelectionLimitNotice();
                    renderLoadedPhotos();
                    renderSelectedPhotos();
                };
                card.addEventListener("click", toggleCardSelection);
                card.addEventListener("keydown", event => {
                    if (event.key !== "Enter" && event.key !== " ") return;
                    event.preventDefault();
                    toggleCardSelection();
                });
            }

            const preview = document.createElement("div");
            preview.className = "admin-kto-photo-preview";

            const fallback = textElement("span", "admin-kto-photo-image-fallback", "이미지를 불러올 수 없습니다.");
            fallback.setAttribute("role", "img");
            fallback.hidden = true;

            const imageUrl = String(item.imageUrl ?? "").trim();
            if (imageUrl) {
                const image = document.createElement("img");
                image.src = imageUrl;
                image.alt = `${String(item.title ?? "관광사진")} 미리보기`;
                image.loading = "lazy";
                image.addEventListener("error", () => {
                    image.hidden = true;
                    fallback.hidden = false;
                });
                preview.append(image, fallback);
            } else {
                fallback.hidden = false;
                preview.append(fallback);
            }

            if (isSelected) {
                const selectedCheck = textElement("span", "admin-kto-photo-selected-check", "✓");
                selectedCheck.setAttribute("aria-hidden", "true");
                preview.append(selectedCheck);
            }

            if (isMain) {
                preview.append(textElement("span", "admin-kto-photo-main-badge", "대표"));
            }

            const body = document.createElement("div");
            body.className = "admin-kto-photo-card-body";
            body.append(textElement("h3", "admin-kto-photo-title", String(item.title ?? "제목 없음")));

            // 촬영 정보는 응답에 있을 때만 작은 보조정보로 보이고, 없으면 줄 자체를 생략한다
            const photoDetails = [];
            const location = String(item.photographyLocation ?? "").trim();
            const month = formatPhotographyMonth(item.photographyMonth);
            if (month) photoDetails.push(`${month} 촬영`);
            if (location) photoDetails.push(`촬영지 ${location}`);
            if (photoDetails.length > 0) {
                body.append(textElement("p", "admin-kto-photo-details", photoDetails.join(" · ")));
            }
            body.append(textElement("p", "admin-kto-photo-source", attribution(item)));
            // 라이선스 라벨은 서버가 출처별 근거로 정한 값만 쓴다. 없으면 유형을 가정하지 않는다
            const licenseLabel = String(item.licenseLabel ?? "").trim();
            if (licenseLabel) {
                body.append(textElement("p", "admin-kto-photo-license", licenseLabel));
            }

            card.append(preview, body);
            return card;
        }

        function createSelectedPhoto({ key, item, isMain }) {
            const selectedPhoto = document.createElement("article");
            selectedPhoto.className = "admin-kto-photo-selected-item";
            selectedPhoto.classList.toggle("is-main", isMain);

            const thumbnail = document.createElement("div");
            thumbnail.className = "admin-kto-photo-selected-thumbnail";
            thumbnail.classList.toggle("is-no-derivatives", isKtoPhotoNoDerivatives(item));
            const imageUrl = String(item.imageUrl ?? "").trim();
            if (imageUrl) {
                const image = document.createElement("img");
                image.src = imageUrl;
                image.alt = "";
                image.loading = "lazy";
                image.addEventListener("error", () => {
                    image.hidden = true;
                    thumbnail.classList.add("has-image-error");
                });
                thumbnail.append(image);
            } else {
                thumbnail.classList.add("has-image-error");
            }

            const summary = document.createElement("div");
            summary.className = "admin-kto-photo-selected-summary";
            summary.append(textElement(
                "h4",
                "admin-kto-photo-selected-title",
                String(item.title ?? "제목 없음")
            ));
            if (isMain) {
                summary.append(textElement("span", "admin-kto-photo-main-badge", "대표"));
            }

            const actions = document.createElement("div");
            actions.className = "admin-kto-photo-selected-actions";

            const mainButton = document.createElement("button");
            mainButton.type = "button";
            mainButton.className = "admin-kto-photo-compact-button";
            mainButton.setAttribute("aria-pressed", String(isMain));
            mainButton.disabled = isMain;
            mainButton.textContent = isMain ? "대표사진" : "대표 지정";
            mainButton.addEventListener("click", () => {
                selectionState.setMain(key);
                renderLoadedPhotos();
                renderSelectedPhotos();
            });

            const removeButton = document.createElement("button");
            removeButton.type = "button";
            removeButton.className = "admin-kto-photo-compact-button is-remove";
            removeButton.textContent = "선택 해제";
            removeButton.setAttribute("aria-label", `${String(item.title ?? "관광사진")} 선택 해제`);
            removeButton.addEventListener("click", () => {
                selectionState.remove(key);
                clearSelectionLimitNotice();
                renderLoadedPhotos();
                renderSelectedPhotos();
            });

            actions.append(mainButton, removeButton);
            selectedPhoto.append(thumbnail, summary, actions);
            return selectedPhoto;
        }

        function createNewBatchMarker() {
            const marker = textElement("p", "admin-kto-photo-new-batch", "새로 불러온 이미지");
            marker.setAttribute("data-kto-photo-new-batch", "");
            return marker;
        }

        function createLicenseDivider() {
            return textElement("p", "admin-kto-photo-license-divider", "공공누리 제3유형 사진");
        }

        // 화면에 그린 사진과 보관 중인 사진. 중복 제외 기준으로 쓴다
        function knownItems() {
            return displayedItems.concat(deferredUndatedPrimaryItems, deferredTrailingItems);
        }

        // 이번 더보기 묶음 앞에는 "새로 불러온 이미지", 첫 제3유형 앞에는 유형 구분 표시를 둔다
        function appendPhotoCard(fragment, item) {
            if (item === latestBatchStartItem) fragment.append(createNewBatchMarker());
            if (item === firstTrailingItem) fragment.append(createLicenseDivider());
            fragment.append(createCard(item));
        }

        // displayedItems 순서가 곧 화면 순서다. 선택 상태가 바뀌어 다시 그려도 위치는 바뀌지 않는다
        function renderLoadedPhotos() {
            const fragment = document.createDocumentFragment();
            displayedItems.forEach(item => appendPhotoCard(fragment, item));
            results.replaceChildren(fragment);
        }

        // 새 결과는 기존 카드를 다시 그리지 않고 항상 목록 맨 뒤에만 붙인다
        function appendLoadedPhotos(items) {
            results.querySelector("[data-kto-photo-new-batch]")?.remove();
            const fragment = document.createDocumentFragment();
            items.forEach(item => appendPhotoCard(fragment, item));
            results.append(fragment);
        }

        function showPhotos(items, markAsNewBatch) {
            if (items.length === 0) return;
            if (firstTrailingItem === null) {
                firstTrailingItem = items.find(isTrailingLicenseKtoPhoto) ?? null;
            }
            if (markAsNewBatch) latestBatchStartItem = items[0];
            displayedItems.push(...items);
            appendLoadedPhotos(items);
        }

        // 2단계 대기열: 촬영월 없는 제1유형 → 제3유형 등. 관광사진 페이지를 모두 받은 시점에 한 번만 만든다
        function startDeferredStage() {
            if (deferredQueue !== null) return;
            deferredQueue = stablySortKtoPhotos(
                deferredUndatedPrimaryItems.concat(deferredTrailingItems),
                currentKeyword
            );
        }

        // 한 번에 pageSize 장씩 꺼내되, 제1유형이 남아 있으면 제3유형을 같은 묶음에 섞지 않는다
        function takeDeferredBatch() {
            const trailingStart = deferredQueue.findIndex(isTrailingLicenseKtoPhoto);
            const groupEnd = trailingStart > 0 ? trailingStart : deferredQueue.length;
            return deferredQueue.splice(0, Math.min(pageSize, groupEnd));
        }

        function renderSelectedPhotos() {
            const selections = selectionState.entries();
            const fragment = document.createDocumentFragment();
            selections.forEach(selection => fragment.append(createSelectedPhoto(selection)));
            selectedList.replaceChildren(fragment);
            selectedPhotosJson.value = JSON.stringify(serializeKtoSelectedPhotos(selectionState.entries()));
            if (submitButton) submitButton.disabled = submitting || selections.length === 0;

            selectedCount.textContent = `${selectionState.count()}장`;
            selectedArea.hidden = selections.length === 0;
            const mainItem = selectionState.mainItem();
            mainStatus.textContent = mainItem
                ? `대표사진: ${String(mainItem.title ?? "제목 없음")}`
                : "대표사진 없음";
        }

        function updateMoreButton() {
            moreButton.hidden = galleryExhausted && (deferredQueue?.length ?? 0) === 0;
        }

        function updateResultStatus() {
            if (displayedItems.length === 0 && moreButton.hidden) {
                setStatus("검색 결과가 없습니다.", "empty");
            } else {
                setStatus(`총 ${resultTotalCount.toLocaleString("ko-KR")}장의 관광사진`, "success");
            }
        }

        async function loadPhotos(append) {
            if (loading) return;

            if (append && galleryExhausted) {
                // 2단계: 관광사진 페이지는 모두 받았으므로 보관해 둔 사진을 서버 요청 없이 맨 뒤에 붙인다
                if (deferredQueue === null) return;
                showPhotos(takeDeferredBatch(), true);
                updateMoreButton();
                updateResultStatus();
                return;
            }

            let requestKeyword = append ? currentKeyword : keywordInput.value.trim();
            if (!append && !requestKeyword) {
                requestKeyword = destinationName();
                keywordInput.value = requestKeyword;
            }
            if (!requestKeyword) {
                setStatus("검색어나 한국어 여행지명을 입력해 주세요.", "error");
                keywordInput.focus();
                return;
            }

            const requestPage = append ? currentPage + 1 : 1;
            if (!append) {
                currentKeyword = requestKeyword;
                currentPage = 0;
                totalCount = 0;
                resultTotalCount = 0;
                displayedItems = [];
                deferredUndatedPrimaryItems = [];
                deferredTrailingItems = [];
                galleryExhausted = false;
                deferredQueue = null;
                latestBatchStartItem = null;
                firstTrailingItem = null;
                results.replaceChildren();
                moreButton.hidden = true;
            }

            setLoading(true, append);
            setStatus(append ? "추가 관광사진을 불러오는 중입니다." : "관광사진을 검색하는 중입니다.", "loading");
            let errorMessage = defaultErrorMessage;

            try {
                const params = new URLSearchParams({
                    keyword: requestKeyword,
                    pageNo: String(requestPage),
                    numOfRows: String(pageSize)
                });
                const response = await fetch(`${endpoint}?${params.toString()}`, {
                    headers: { "Accept": "application/json" }
                });
                const payload = await response.json();

                if (!response.ok) {
                    if (typeof payload?.message === "string" && payload.message.trim()) {
                        errorMessage = payload.message.trim();
                    }
                    throw new Error("KTO_PHOTO_RESPONSE_ERROR");
                }
                if (!payload || !Array.isArray(payload.items)) {
                    throw new Error("KTO_PHOTO_RESPONSE_INVALID");
                }

                currentPage = requestPage;
                totalCount = Number.isFinite(Number(payload.totalCount)) ? Number(payload.totalCount) : 0;
                if (requestPage === 1) resultTotalCount = totalCount;

                // 1단계: 촬영월이 있는 제1유형만 이번 페이지 안에서 촬영월 최신순으로 정렬해 맨 뒤에 붙이고,
                // 촬영월 없는 제1유형과 제3유형 등은 화면에 그리지 않고 보관한다
                const newItems = excludeLoadedKtoPhotos(payload.items, knownItems());
                const immediateItems = stablySortKtoPhotos(newItems.filter(isImmediateKtoPhoto), currentKeyword);
                deferredUndatedPrimaryItems.push(...newItems.filter(item =>
                    !isImmediateKtoPhoto(item) && !isTrailingLicenseKtoPhoto(item)));
                deferredTrailingItems.push(...newItems.filter(isTrailingLicenseKtoPhoto));

                // 관광사진은 pageSize 장씩 넘긴다. 첫 페이지 전체 수에는 TourAPI 사진 수가 더해져 있어
                // 넉넉하게 판단되며, 그때는 다음 더보기에서 빈 페이지를 받아 소진을 확인한다
                galleryExhausted = payload.items.length === 0 || requestPage * pageSize >= totalCount;
                let shownItems = immediateItems;
                if (galleryExhausted) {
                    startDeferredStage();
                    // 이번 요청에서 붙일 제1유형이 없으면 같은 클릭에서 바로 2단계 첫 묶음을 붙인다
                    if (shownItems.length === 0) shownItems = takeDeferredBatch();
                }
                showPhotos(shownItems, append);
                updateMoreButton();
                updateResultStatus();
            } catch (error) {
                setStatus(errorMessage, "error");
                if (!append) results.replaceChildren();
                moreButton.hidden = true;
            } finally {
                setLoading(false, append);
            }
        }

        if (!keywordInput.value.trim()) {
            keywordInput.value = destinationName();
        }
        renderSelectedPhotos();

        const submitForm = submitButton?.form;
        if (submitForm) {
            submitForm.addEventListener("submit", event => {
                if (submitting || selectionState.count() === 0) {
                    event.preventDefault();
                    return;
                }
                // 첫 제출 즉시 잠근다. 결과(성공·실패 원인)는 서버가 돌려준 관리 화면에서 안내한다
                submitting = true;
                submitButton.disabled = true;
                submitButton.textContent = "등록 중...";
                searchArea.setAttribute("aria-busy", "true");
            });
            // 오류 화면에서 뒤로 돌아오면(페이지 캐시) 다시 등록할 수 있게 풀어 준다
            window.addEventListener("pageshow", event => {
                if (!event.persisted || !submitting) return;
                submitting = false;
                submitButton.textContent = submitLabel;
                searchArea.setAttribute("aria-busy", "false");
                renderSelectedPhotos();
            });
        }

        searchButton.addEventListener("click", () => loadPhotos(false));
        moreButton.addEventListener("click", () => loadPhotos(true));
        keywordInput.addEventListener("keydown", event => {
            if (event.key !== "Enter") return;
            event.preventDefault();
            loadPhotos(false);
        });
    });
});
