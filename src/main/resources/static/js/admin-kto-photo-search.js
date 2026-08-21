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

function stablySortKtoPhotos(items, keyword) {
    return items
        .map((item, originalIndex) => ({
            item,
            originalIndex,
            rank: ktoPhotoRelevanceRank(item, keyword)
        }))
        .sort((left, right) =>
            left.rank - right.rank || left.originalIndex - right.originalIndex
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
        const destinationNameInput = document.querySelector("[data-destination-korean-name]");

        if (!keywordInput || !searchButton || !status || !results || !moreButton
            || !selectedArea || !selectedCount || !mainStatus || !selectedList
            || !selectedPhotosJson) return;

        let currentKeyword = "";
        let currentPage = 0;
        let totalCount = 0;
        let displayedCount = 0;
        let loading = false;
        let loadedItems = [];
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

            const photoDetails = [];
            const location = String(item.photographyLocation ?? "").trim();
            const month = formatPhotographyMonth(item.photographyMonth);
            if (location) photoDetails.push(`촬영지 ${location}`);
            if (month) photoDetails.push(`촬영월 ${month}`);
            body.append(textElement(
                "p",
                "admin-kto-photo-details",
                photoDetails.length > 0 ? photoDetails.join(" · ") : "촬영 정보 없음"
            ));
            body.append(textElement("p", "admin-kto-photo-source", attribution(item)));
            body.append(textElement(
                "p",
                "admin-kto-photo-license",
                String(item.licenseLabel ?? "공공누리 제1유형")
            ));

            card.append(preview, body);
            return card;
        }

        function createSelectedPhoto({ key, item, isMain }) {
            const selectedPhoto = document.createElement("article");
            selectedPhoto.className = "admin-kto-photo-selected-item";
            selectedPhoto.classList.toggle("is-main", isMain);

            const thumbnail = document.createElement("div");
            thumbnail.className = "admin-kto-photo-selected-thumbnail";
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

        function renderLoadedPhotos() {
            const fragment = document.createDocumentFragment();
            stablySortKtoPhotos(loadedItems, currentKeyword)
                .forEach(item => fragment.append(createCard(item)));
            results.replaceChildren(fragment);
        }

        function renderSelectedPhotos() {
            const selections = selectionState.entries();
            const fragment = document.createDocumentFragment();
            selections.forEach(selection => fragment.append(createSelectedPhoto(selection)));
            selectedList.replaceChildren(fragment);
            selectedPhotosJson.value = JSON.stringify(serializeKtoSelectedPhotos(selectionState.entries()));

            selectedCount.textContent = `${selectionState.count()}장`;
            selectedArea.hidden = selections.length === 0;
            const mainItem = selectionState.mainItem();
            mainStatus.textContent = mainItem
                ? `대표사진: ${String(mainItem.title ?? "제목 없음")}`
                : "대표사진 없음";
        }

        function updateMoreButton(receivedCount) {
            moreButton.hidden = receivedCount === 0 || displayedCount >= totalCount;
        }

        async function loadPhotos(append) {
            if (loading) return;

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
                displayedCount = 0;
                loadedItems = [];
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
                loadedItems.push(...payload.items);
                displayedCount = loadedItems.length;
                renderLoadedPhotos();

                if (displayedCount === 0) {
                    setStatus("검색 결과가 없습니다.", "empty");
                } else {
                    setStatus(`총 ${totalCount.toLocaleString("ko-KR")}장의 관광사진`, "success");
                }
                updateMoreButton(payload.items.length);
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

        searchButton.addEventListener("click", () => loadPhotos(false));
        moreButton.addEventListener("click", () => loadPhotos(true));
        keywordInput.addEventListener("keydown", event => {
            if (event.key !== "Enter") return;
            event.preventDefault();
            loadPhotos(false);
        });
    });
});
