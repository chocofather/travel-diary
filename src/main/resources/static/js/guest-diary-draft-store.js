/*
 * 비회원 다이어리 체험용 draft 보관소.
 *
 * 서버에는 아무것도 저장하지 않는다. 이 파일은 브라우저 localStorage 만 읽고 쓴다.
 * 회원 다이어리(diaries / diary_pages / diary_elements / diary_cover_designs)는
 * 기존 Controller → Service → Mapper 경로가 그대로 맡고, 여기에서는 그 경로를 부르지 않는다.
 *
 * 필드 이름은 나중에 로그인 후 DB 로 옮기기 쉽도록 실제 컬럼과 1:1 로 맞춰 두었다.
 * 다만 식별자는 DB 의 AUTO_INCREMENT 를 흉내 내지 않는다. 브라우저에서만 통하는
 * 임시 UUID 문자열(gd_/gp_/ge_ 접두)이며 서버가 신뢰하는 값이 아니다.
 * 로그인 후 옮길 때는 서버가 값을 다시 검증하고 자기 id 를 새로 발급해야 한다.
 *
 * 저장하지 않는 것: 계정정보/토큰/이메일, 그리고 사진 원본(base64). 사진 바이너리는
 * 다음 단계에서 IndexedDB 가 맡고 여기에는 참조 키만 들어온다.
 */
(function (global) {
    "use strict";

    const STORAGE_KEY = "travelDiary.guestDiaryDraft.v1";
    const SCHEMA_VERSION = 1;
    /** 비회원 체험 한도. UI 뿐 아니라 addPage() 자체가 이 수를 넘기지 않는다. */
    const MAX_PAGES = 3;

    /** 호출한 쪽이 로그인 유도 등으로 이어갈 수 있도록 실패 사유를 문자열로 돌려준다. */
    const REASON = {
        NO_DRAFT: "NO_DRAFT",
        PAGE_LIMIT_REACHED: "PAGE_LIMIT_REACHED",
        PAGE_NOT_FOUND: "PAGE_NOT_FOUND",
        ELEMENT_NOT_FOUND: "ELEMENT_NOT_FOUND",
        STORAGE_UNAVAILABLE: "STORAGE_UNAVAILABLE",
        INVALID_BASICS: "INVALID_BASICS",
        INVALID_PAGE_DATE: "INVALID_PAGE_DATE"
    };

    /** diaries.title 과 같은 길이. 회원 화면/서버와 같은 값을 쓴다. */
    const MAX_TITLE_LENGTH = 150;

    /* diary_pages / diary_elements 기본값. DB DEFAULT 와 같은 값을 쓴다. */
    const DEFAULT_BACKGROUND_TYPE = "PLAIN";
    const DEFAULT_HEADER_FONT = "DEFAULT";
    const DEFAULT_COVER_STYLE = "DEFAULT";
    const DEFAULT_NOTEBOOK_TYPE = "CLASSIC";
    const ELEMENT_TYPES = ["TEXT", "PHOTO", "STICKER", "NOTE"];

    function storage() {
        try {
            return global.localStorage || null;
        } catch (error) {
            // 시크릿 모드나 저장소 차단. 체험을 못 할 뿐 화면이 죽지는 않는다.
            return null;
        }
    }

    function newId(prefix) {
        const random = global.crypto;
        if (random && typeof random.randomUUID === "function") {
            return prefix + random.randomUUID();
        }
        if (random && typeof random.getRandomValues === "function") {
            const bytes = random.getRandomValues(new Uint8Array(16));
            return prefix + Array.from(bytes, (b) => b.toString(16).padStart(2, "0")).join("");
        }
        return prefix + Date.now().toString(16) + Math.random().toString(16).slice(2);
    }

    function nowIso() {
        return new Date().toISOString();
    }

    function isObject(value) {
        return value !== null && typeof value === "object" && !Array.isArray(value);
    }

    function text(value, fallback) {
        return typeof value === "string" ? value : (fallback === undefined ? null : fallback);
    }

    function number(value, fallback) {
        return typeof value === "number" && Number.isFinite(value) ? value : fallback;
    }

    /** 달력 입력이 주는 모양(YYYY-MM-DD)이고 실제로 있는 날인 값만 날짜로 본다. */
    function isDateValue(value) {
        if (typeof value !== "string" || !/^\d{4}-\d{2}-\d{2}$/.test(value)) {
            return false;
        }
        const parsed = new Date(value + "T00:00:00Z");
        return !Number.isNaN(parsed.getTime())
            && parsed.toISOString().slice(0, 10) === value;
    }

    /**
     * 여행일기 기본정보 규칙. 회원 다이어리(DiaryServiceImpl.validated)와 같은 기준이다.
     * 제목이 있어야 하고, 여행 기간이 있어야 하고, 종료일이 시작일보다 빠르면 안 된다.
     *
     * 화면이 막지 못했더라도 이 규칙에 걸리는 값은 draft 에 들어가지 않는다.
     * 나중에 로그인 후 옮겨 담을 때 서버가 거절할 값을 미리 만들지 않기 위해서다.
     *
     * @return 어긋난 이유 한 줄. 문제가 없으면 null
     */
    function basicsError(basics) {
        const input = isObject(basics) ? basics : {};
        const title = text(input.title, "").trim();
        if (title === "") {
            return "여행일기 제목을 입력해 주세요.";
        }
        if (title.length > MAX_TITLE_LENGTH) {
            return "여행일기 제목이 너무 깁니다.";
        }
        if (!isDateValue(input.startDate)) {
            return "여행 시작일을 입력해 주세요.";
        }
        if (!isDateValue(input.endDate)) {
            return "여행 종료일을 입력해 주세요.";
        }
        // 같은 모양의 날짜 문자열이라 글자 비교로 앞뒤를 가릴 수 있다.
        if (input.endDate < input.startDate) {
            return "여행 종료일이 시작일보다 빠릅니다.";
        }
        return null;
    }

    /**
     * 기본정보가 다 갖춰진 draft 인지.
     *
     * 예전 체험 다이어리는 제목/기간 없이 만들어졌을 수 있다. 그 값을 지우지 않고
     * "아직 덜 채워졌다" 는 것만 알려, 화면이 채울 자리로 안내하게 한다.
     */
    function isComplete(draft) {
        return isObject(draft) && basicsError(draft) === null;
    }

    /**
     * 페이지 날짜 규칙. 회원 페이지(DiaryPageServiceImpl.validated)와 같은 기준이다.
     * 날짜가 있어야 하고, 그 날이 이 여행일기의 기간 안이어야 한다.
     *
     * 같은 날짜에 여러 장은 회원과 마찬가지로 막지 않는다. 자리번호(pageOrder)와
     * 날짜(pageDate)는 서로 다른 것이라서다.
     *
     * @return 어긋난 이유 한 줄. 문제가 없으면 null
     */
    function pageDateError(draft, pageDate) {
        if (!isDateValue(draft.startDate) || !isDateValue(draft.endDate)) {
            return "여행 기간을 먼저 입력해 주세요.";
        }
        if (!isDateValue(pageDate)) {
            return "페이지 날짜를 선택해 주세요.";
        }
        if (pageDate < draft.startDate || pageDate > draft.endDate) {
            return "페이지 날짜는 여행 기간(" + draft.startDate + " ~ " + draft.endDate
                + ") 안에서 선택해 주세요.";
        }
        return null;
    }

    /**
     * 날짜가 아직 없는 장.
     *
     * 예전 체험 여행일기는 날짜 없이 장을 늘릴 수 있었다. 그 장을 지우지 않고
     * 어느 장을 채워야 하는지만 알려 준다.
     */
    function pagesMissingDate(draft) {
        if (!isObject(draft)) {
            return [];
        }
        return draft.pages.filter((page) => !isDateValue(page.pageDate));
    }

    /*
     * 사진 원본은 이 저장소에 들어오지 않는다. base64(data:) 는 용량도 크고
     * localStorage 에 남길 값도 아니라 읽는 순간 버린다. 다음 단계의 IndexedDB 참조 키만 남긴다.
     */
    function safeImageUrl(value) {
        const url = text(value, null);
        if (url === null || url.slice(0, 5).toLowerCase() === "data:") {
            return null;
        }
        return url;
    }

    function normalizeElement(raw) {
        if (!isObject(raw)) {
            return null;
        }
        const elementType = text(raw.elementType, null);
        if (ELEMENT_TYPES.indexOf(elementType) === -1) {
            return null;
        }
        return {
            elementId: text(raw.elementId, null) || newId("ge_"),
            elementType: elementType,
            textContent: text(raw.textContent, null),
            // 스티커/라벨 등 카탈로그 경로만 들어온다. 사진 원본은 저장하지 않는다.
            imageUrl: safeImageUrl(raw.imageUrl),
            // 사진 바이너리는 다음 단계에서 IndexedDB 에 두고 여기에는 그 키만 남긴다.
            photoRef: text(raw.photoRef, null),
            styleType: text(raw.styleType, null),
            colorType: text(raw.colorType, null),
            photoStyle: text(raw.photoStyle, null),
            textFont: text(raw.textFont, null),
            textColor: text(raw.textColor, null),
            positionX: number(raw.positionX, 0),
            positionY: number(raw.positionY, 0),
            width: number(raw.width, 0.3),
            height: number(raw.height, 0.3),
            rotation: number(raw.rotation, 0),
            zIndex: number(raw.zIndex, 0)
        };
    }

    function normalizePage(raw, order) {
        const page = isObject(raw) ? raw : {};
        const elements = Array.isArray(page.elements) ? page.elements : [];
        return {
            pageId: text(page.pageId, null) || newId("gp_"),
            // diary_pages.page_order 와 같은 1부터 시작하는 자리번호
            pageOrder: order,
            pageDate: text(page.pageDate, null),
            backgroundType: text(page.backgroundType, DEFAULT_BACKGROUND_TYPE),
            paperColor: text(page.paperColor, null),
            pageHeader: text(page.pageHeader, null),
            pageHeaderFont: text(page.pageHeaderFont, DEFAULT_HEADER_FONT),
            pageHeaderBold: page.pageHeaderBold === true,
            content: text(page.content, ""),
            elements: elements.map(normalizeElement).filter(Boolean)
        };
    }

    /*
     * diary_cover_designs + diary_cover_design_elements 를 그대로 옮길 수 있는 모양.
     * 이번 단계에서는 표지 편집기를 연결하지 않으므로 값이 들어오지 않지만,
     * 나중에 편집기가 이 자리에 그대로 써 넣을 수 있게 형태만 맞춰 둔다.
     */
    function normalizeCoverDesign(raw) {
        if (!isObject(raw)) {
            return null;
        }
        const elements = Array.isArray(raw.elements) ? raw.elements : [];
        return {
            name: text(raw.name, null),
            baseCoverStyle: text(raw.baseCoverStyle, DEFAULT_COVER_STYLE),
            backgroundColor: text(raw.backgroundColor, null),
            elements: elements.map(normalizeElement).filter(Boolean)
        };
    }

    /*
     * 어떤 값이 들어와도 쓸 수 있는 draft 로 만든다.
     * 구버전/손상된 값이 섞여 있어도 여기에서 걸러지므로 화면이 죽지 않는다.
     * 페이지가 한도보다 많으면 앞에서부터 MAX_PAGES 개만 남긴다.
     */
    function normalizeDraft(raw) {
        if (!isObject(raw)) {
            return null;
        }
        const pages = Array.isArray(raw.pages) ? raw.pages : [];
        return {
            schemaVersion: SCHEMA_VERSION,
            draftId: text(raw.draftId, null) || newId("gd_"),
            createdAt: text(raw.createdAt, null) || nowIso(),
            updatedAt: text(raw.updatedAt, null) || nowIso(),
            title: text(raw.title, ""),
            startDate: text(raw.startDate, null),
            endDate: text(raw.endDate, null),
            notebookType: text(raw.notebookType, DEFAULT_NOTEBOOK_TYPE),
            // 기본 표지(DEFAULT) 인지 내가 꾸민 표지(CUSTOM) 인지
            coverType: text(raw.coverType, DEFAULT_COVER_STYLE),
            coverStyle: text(raw.coverStyle, DEFAULT_COVER_STYLE),
            coverDesign: normalizeCoverDesign(raw.coverDesign),
            // 새로고침 뒤 보고 있던 장으로 돌아가기 위한 화면 상태. 별도 key 를 만들지 않는다.
            currentPageId: text(raw.currentPageId, null),
            pages: pages.slice(0, MAX_PAGES)
                .map((page, index) => normalizePage(page, index + 1))
        };
    }

    /** 읽기. 깨진 JSON 이나 알아볼 수 없는 모양이면 조용히 비우고 null 을 준다. */
    function read() {
        const store = storage();
        if (!store) {
            return null;
        }
        let saved;
        try {
            saved = store.getItem(STORAGE_KEY);
        } catch (error) {
            return null;
        }
        if (!saved) {
            return null;
        }
        let parsed;
        try {
            parsed = JSON.parse(saved);
        } catch (error) {
            // 손상된 값은 되살리려 하지 않는다. 지우고 처음부터 시작할 수 있게 한다.
            clear();
            return null;
        }
        const draft = normalizeDraft(parsed);
        if (!draft) {
            clear();
            return null;
        }
        return draft;
    }

    function write(draft) {
        const store = storage();
        if (!store) {
            return {ok: false, reason: REASON.STORAGE_UNAVAILABLE};
        }
        const saved = Object.assign({}, draft, {updatedAt: nowIso()});
        try {
            store.setItem(STORAGE_KEY, JSON.stringify(saved));
        } catch (error) {
            // 용량 초과 등. 기존 값을 건드리지 않고 실패만 알린다.
            return {ok: false, reason: REASON.STORAGE_UNAVAILABLE};
        }
        return {ok: true, draft: saved};
    }

    function clear() {
        const store = storage();
        if (!store) {
            return false;
        }
        try {
            store.removeItem(STORAGE_KEY);
        } catch (error) {
            return false;
        }
        return true;
    }

    /**
     * 새 체험 draft 를 만든다. 체험 다이어리는 언제나 한 개라서
     * 기존 draft 가 있으면 남겨 두지 않고 이 값으로 덮어쓴다.
     *
     * 기본정보(제목/여행 기간)가 갖춰지지 않았으면 아무것도 만들지 않는다.
     * 회원 다이어리도 그 값 없이는 만들어지지 않기 때문이다.
     */
    function createDraft(basics) {
        const input = isObject(basics) ? basics : {};
        const invalid = basicsError(input);
        if (invalid) {
            return {ok: false, reason: REASON.INVALID_BASICS, message: invalid};
        }
        const draft = normalizeDraft({
            draftId: newId("gd_"),
            createdAt: nowIso(),
            title: input.title.trim(),
            startDate: input.startDate,
            endDate: input.endDate,
            notebookType: input.notebookType,
            coverType: input.coverType,
            coverStyle: input.coverStyle,
            coverDesign: input.coverDesign,
            pages: []
        });
        return write(draft);
    }

    function getDraft() {
        return read();
    }

    function hasDraft() {
        return read() !== null;
    }

    /**
     * 제목/기간/표지 같은 기본정보 수정.
     * 페이지 배열과 표지 요소는 여기에서 건드리지 않는다. (덜 채워진 예전 draft 를
     * 보완할 때도 이미 만들어 둔 표지·페이지·사진은 그대로 남는다)
     *
     * 제목이나 기간을 고치는 요청이면 고쳐진 뒤의 값으로 기본정보 규칙을 다시 본다.
     */
    function updateDraft(patch) {
        const draft = read();
        if (!draft) {
            return {ok: false, reason: REASON.NO_DRAFT};
        }
        const input = isObject(patch) ? patch : {};
        const next = Object.assign({}, draft, {
            title: input.title === undefined ? draft.title : text(input.title, draft.title).trim(),
            startDate: input.startDate === undefined ? draft.startDate : text(input.startDate, null),
            endDate: input.endDate === undefined ? draft.endDate : text(input.endDate, null),
            notebookType: input.notebookType === undefined
                ? draft.notebookType : text(input.notebookType, draft.notebookType),
            coverType: input.coverType === undefined
                ? draft.coverType : text(input.coverType, draft.coverType),
            coverStyle: input.coverStyle === undefined
                ? draft.coverStyle : text(input.coverStyle, draft.coverStyle),
            coverDesign: input.coverDesign === undefined
                ? draft.coverDesign : normalizeCoverDesign(input.coverDesign),
            pages: draft.pages
        });
        const editsBasics = input.title !== undefined
            || input.startDate !== undefined
            || input.endDate !== undefined;
        const invalid = editsBasics ? basicsError(next) : null;
        if (invalid) {
            return {ok: false, reason: REASON.INVALID_BASICS, message: invalid};
        }
        return write(next);
    }

    /**
     * 페이지 추가. 비회원 한도와 페이지 날짜는 여기에서 지킨다.
     * 화면이 버튼을 막지 못했더라도 4번째 페이지나 날짜 없는 장은 만들어지지 않는다.
     */
    function addPage(page) {
        const draft = read();
        if (!draft) {
            return {ok: false, reason: REASON.NO_DRAFT};
        }
        if (draft.pages.length >= MAX_PAGES) {
            // 다음 단계에서 이 사유를 받아 로그인 유도로 이어간다.
            return {ok: false, reason: REASON.PAGE_LIMIT_REACHED, limit: MAX_PAGES};
        }
        const invalid = pageDateError(draft, isObject(page) ? page.pageDate : null);
        if (invalid) {
            return {ok: false, reason: REASON.INVALID_PAGE_DATE, message: invalid};
        }
        const added = normalizePage(page, draft.pages.length + 1);
        const next = Object.assign({}, draft, {pages: draft.pages.concat([added])});
        const result = write(next);
        return result.ok ? {ok: true, page: added, draft: result.draft} : result;
    }

    function getPage(pageId) {
        const draft = read();
        if (!draft) {
            return null;
        }
        return draft.pages.find((page) => page.pageId === pageId) || null;
    }

    /**
     * 한 장의 설정 수정.
     *
     * 날짜를 고치는 요청이면 추가할 때와 같은 규칙을 다시 본다. 본문·머리말 저장처럼
     * 날짜를 담지 않은 요청은 그대로 지나간다. (날짜가 아직 없는 예전 장에서도 글은 써진다)
     */
    function updatePage(pageId, patch) {
        const draft = read();
        if (!draft) {
            return {ok: false, reason: REASON.NO_DRAFT};
        }
        const index = draft.pages.findIndex((page) => page.pageId === pageId);
        if (index === -1) {
            return {ok: false, reason: REASON.PAGE_NOT_FOUND};
        }
        const input = isObject(patch) ? patch : {};
        if (input.pageDate !== undefined) {
            const invalid = pageDateError(draft, input.pageDate);
            if (invalid) {
                return {ok: false, reason: REASON.INVALID_PAGE_DATE, message: invalid};
            }
        }
        const merged = Object.assign({}, draft.pages[index], isObject(patch) ? patch : {});
        // 자리번호와 id 는 patch 로 바꾸지 못한다.
        merged.pageId = draft.pages[index].pageId;
        const pages = draft.pages.slice();
        pages[index] = normalizePage(merged, index + 1);
        const result = write(Object.assign({}, draft, {pages: pages}));
        return result.ok ? {ok: true, page: pages[index], draft: result.draft} : result;
    }

    /** 페이지 삭제. 남은 페이지의 자리번호는 1부터 다시 이어 붙인다. */
    function removePage(pageId) {
        const draft = read();
        if (!draft) {
            return {ok: false, reason: REASON.NO_DRAFT};
        }
        const pages = draft.pages.filter((page) => page.pageId !== pageId);
        if (pages.length === draft.pages.length) {
            return {ok: false, reason: REASON.PAGE_NOT_FOUND};
        }
        const reordered = pages.map((page, index) => normalizePage(page, index + 1));
        const result = write(Object.assign({}, draft, {pages: reordered}));
        return result.ok ? {ok: true, draft: result.draft} : result;
    }

    /** 보고 있던 장. 새로고침 뒤 같은 장을 다시 열기 위한 화면 상태다. */
    function setCurrentPageId(pageId) {
        const draft = read();
        if (!draft) {
            return {ok: false, reason: REASON.NO_DRAFT};
        }
        return write(Object.assign({}, draft, {currentPageId: text(pageId, null)}));
    }

    /** 한 장에 꾸미기 요소 하나 추가. 겹침 순서는 이미 놓인 것들보다 위로 올린다. */
    function addElement(pageId, element) {
        const draft = read();
        if (!draft) {
            return {ok: false, reason: REASON.NO_DRAFT};
        }
        const index = draft.pages.findIndex((page) => page.pageId === pageId);
        if (index === -1) {
            return {ok: false, reason: REASON.PAGE_NOT_FOUND};
        }
        const added = normalizeElement(Object.assign({}, isObject(element) ? element : {}, {
            elementId: newId("ge_")
        }));
        if (!added) {
            return {ok: false, reason: REASON.ELEMENT_NOT_FOUND};
        }
        const placed = draft.pages[index].elements;
        if (!isObject(element) || typeof element.zIndex !== "number") {
            added.zIndex = placed.reduce((top, item) => Math.max(top, item.zIndex), 0) + 1;
        }

        const pages = draft.pages.slice();
        pages[index] = Object.assign({}, draft.pages[index], {
            elements: placed.concat([added])
        });
        const result = write(Object.assign({}, draft, {pages: pages}));
        return result.ok ? {ok: true, element: added, draft: result.draft} : result;
    }

    /** 자리/크기/회전/겹침이나 글 내용 수정. 없는 값은 그대로 둔다. */
    function updateElement(pageId, elementId, patch) {
        const draft = read();
        if (!draft) {
            return {ok: false, reason: REASON.NO_DRAFT};
        }
        const pageIndex = draft.pages.findIndex((page) => page.pageId === pageId);
        if (pageIndex === -1) {
            return {ok: false, reason: REASON.PAGE_NOT_FOUND};
        }
        const elements = draft.pages[pageIndex].elements.slice();
        const elementIndex = elements.findIndex((item) => item.elementId === elementId);
        if (elementIndex === -1) {
            return {ok: false, reason: REASON.ELEMENT_NOT_FOUND};
        }

        const merged = Object.assign({}, elements[elementIndex], isObject(patch) ? patch : {});
        // 식별자와 종류는 patch 로 바꾸지 못한다.
        merged.elementId = elements[elementIndex].elementId;
        merged.elementType = elements[elementIndex].elementType;
        elements[elementIndex] = normalizeElement(merged);

        const pages = draft.pages.slice();
        pages[pageIndex] = Object.assign({}, draft.pages[pageIndex], {elements: elements});
        const result = write(Object.assign({}, draft, {pages: pages}));
        return result.ok
            ? {ok: true, element: elements[elementIndex], draft: result.draft}
            : result;
    }

    function removeElement(pageId, elementId) {
        const draft = read();
        if (!draft) {
            return {ok: false, reason: REASON.NO_DRAFT};
        }
        const pageIndex = draft.pages.findIndex((page) => page.pageId === pageId);
        if (pageIndex === -1) {
            return {ok: false, reason: REASON.PAGE_NOT_FOUND};
        }
        const kept = draft.pages[pageIndex].elements
            .filter((item) => item.elementId !== elementId);
        if (kept.length === draft.pages[pageIndex].elements.length) {
            return {ok: false, reason: REASON.ELEMENT_NOT_FOUND};
        }

        const pages = draft.pages.slice();
        pages[pageIndex] = Object.assign({}, draft.pages[pageIndex], {elements: kept});
        const result = write(Object.assign({}, draft, {pages: pages}));
        return result.ok ? {ok: true, draft: result.draft} : result;
    }

    /*
     * ===== 체험 표지 =====
     * 회원의 diary_cover_designs(+ elements)에 해당하는 자리다. 체험 다이어리가 하나뿐이라
     * 표지 디자인도 하나뿐이고, 목록이나 이름 관리는 두지 않는다.
     */

    function getCoverDesign() {
        const draft = read();
        return draft ? draft.coverDesign : null;
    }

    /** 아직 표지를 만들지 않았으면 기본 표지로 한 장 연다. */
    function ensureCoverDesign(draft) {
        return draft.coverDesign || normalizeCoverDesign({elements: []});
    }

    /** 재질(base_cover_style)과 배경색(background_color) 수정. 요소는 건드리지 않는다. */
    function updateCoverDesign(patch) {
        const draft = read();
        if (!draft) {
            return {ok: false, reason: REASON.NO_DRAFT};
        }
        const cover = ensureCoverDesign(draft);
        const input = isObject(patch) ? patch : {};
        const next = Object.assign({}, cover, {
            name: input.name === undefined ? cover.name : text(input.name, cover.name),
            baseCoverStyle: input.baseCoverStyle === undefined
                ? cover.baseCoverStyle : text(input.baseCoverStyle, DEFAULT_COVER_STYLE),
            backgroundColor: input.backgroundColor === undefined
                ? cover.backgroundColor : text(input.backgroundColor, null),
            elements: cover.elements
        });
        const result = write(Object.assign({}, draft, {
            coverDesign: next,
            // 꾸민 표지를 쓰는 다이어리라는 표시. 목록/편집 화면이 이 값을 본다.
            coverType: "CUSTOM"
        }));
        return result.ok ? {ok: true, coverDesign: next, draft: result.draft} : result;
    }

    /** 표지에 요소 하나 추가. 겹침 순서는 이미 놓인 것들보다 위로 올린다. */
    function addCoverElement(element) {
        const draft = read();
        if (!draft) {
            return {ok: false, reason: REASON.NO_DRAFT};
        }
        const cover = ensureCoverDesign(draft);
        const added = normalizeElement(Object.assign({}, isObject(element) ? element : {}, {
            elementId: newId("gc_")
        }));
        if (!added) {
            return {ok: false, reason: REASON.ELEMENT_NOT_FOUND};
        }
        if (!isObject(element) || typeof element.zIndex !== "number") {
            added.zIndex = cover.elements
                .reduce((top, item) => Math.max(top, item.zIndex), 0) + 1;
        }

        const next = Object.assign({}, cover, {elements: cover.elements.concat([added])});
        const result = write(Object.assign({}, draft, {
            coverDesign: next,
            coverType: "CUSTOM"
        }));
        return result.ok ? {ok: true, element: added, draft: result.draft} : result;
    }

    function updateCoverElement(elementId, patch) {
        const draft = read();
        if (!draft) {
            return {ok: false, reason: REASON.NO_DRAFT};
        }
        const cover = ensureCoverDesign(draft);
        const elements = cover.elements.slice();
        const index = elements.findIndex((item) => item.elementId === elementId);
        if (index === -1) {
            return {ok: false, reason: REASON.ELEMENT_NOT_FOUND};
        }

        const merged = Object.assign({}, elements[index], isObject(patch) ? patch : {});
        // 식별자와 종류는 patch 로 바꾸지 못한다.
        merged.elementId = elements[index].elementId;
        merged.elementType = elements[index].elementType;
        elements[index] = normalizeElement(merged);

        const next = Object.assign({}, cover, {elements: elements});
        const result = write(Object.assign({}, draft, {coverDesign: next}));
        return result.ok
            ? {ok: true, element: elements[index], draft: result.draft}
            : result;
    }

    function removeCoverElement(elementId) {
        const draft = read();
        if (!draft) {
            return {ok: false, reason: REASON.NO_DRAFT};
        }
        const cover = ensureCoverDesign(draft);
        const kept = cover.elements.filter((item) => item.elementId !== elementId);
        if (kept.length === cover.elements.length) {
            return {ok: false, reason: REASON.ELEMENT_NOT_FOUND};
        }

        const next = Object.assign({}, cover, {elements: kept});
        const result = write(Object.assign({}, draft, {coverDesign: next}));
        return result.ok ? {ok: true, draft: result.draft} : result;
    }

    global.TravelDiaryGuestDraftStore = {
        STORAGE_KEY: STORAGE_KEY,
        SCHEMA_VERSION: SCHEMA_VERSION,
        MAX_PAGES: MAX_PAGES,
        REASON: REASON,
        MAX_TITLE_LENGTH: MAX_TITLE_LENGTH,
        basicsError: basicsError,
        isComplete: isComplete,
        pageDateError: pageDateError,
        pagesMissingDate: pagesMissingDate,
        createDraft: createDraft,
        getDraft: getDraft,
        hasDraft: hasDraft,
        updateDraft: updateDraft,
        addPage: addPage,
        getPage: getPage,
        updatePage: updatePage,
        removePage: removePage,
        setCurrentPageId: setCurrentPageId,
        addElement: addElement,
        updateElement: updateElement,
        removeElement: removeElement,
        getCoverDesign: getCoverDesign,
        updateCoverDesign: updateCoverDesign,
        addCoverElement: addCoverElement,
        updateCoverElement: updateCoverElement,
        removeCoverElement: removeCoverElement,
        clear: clear
    };
})(window);
