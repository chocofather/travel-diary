const assert = require("node:assert/strict");
const test = require("node:test");
const {planBulkSource, validateBulkSourceValues, applyUploadPlan,
    normalizeSourceUrl, normalizeSourceUrlInput} =
    require("../../main/resources/static/js/admin-destination-image-bulk-source.js");

test("accepts an absolute HTTP(S) common page and blank optional fields", () => {
    assert.equal(validateBulkSourceValues({
        sourceName: "  공공기관  ", photographer: "   ", licenseType: "",
        licenseDetail: null, commonSourceUrl: " https://example.com/collection?lang=ko "
    }), null);
    assert.equal(validateBulkSourceValues({
        sourceName: "공공기관", photographer: "", licenseType: "",
        licenseDetail: "", commonSourceUrl: "   "
    }), null);
});

test("adds HTTPS to bare source pages and preserves existing schemes", () => {
    assert.equal(normalizeSourceUrl(" tripbora.com "), "https://tripbora.com");
    assert.equal(normalizeSourceUrl("example.com/photos/123"), "https://example.com/photos/123");
    assert.equal(normalizeSourceUrl("http://example.com"), "http://example.com");
    assert.equal(normalizeSourceUrl("https://example.com"), "https://example.com");
    assert.equal(normalizeSourceUrl("https://https://example.com"), "https://https://example.com");
    assert.equal(validateBulkSourceValues({commonSourceUrl: "tripbora.com"}), null);
});

test("does not turn malformed addresses into valid pages", () => {
    assert.equal(normalizeSourceUrl("bad address"), "bad address");
    assert.equal(normalizeSourceUrl("ftp://example.com"), "ftp://example.com");
    assert.deepEqual(validateBulkSourceValues({commonSourceUrl: "bad address"}), {
        field: "commonSourceUrl", message: "공통 출처 URL은 http:// 또는 https://로 시작해야 합니다."
    });
});

test("identifies malformed absolute URLs", () => {
    assert.deepEqual(validateBulkSourceValues({commonSourceUrl: "https://"}), {
        field: "commonSourceUrl",
        message: "공통 출처 URL 형식이 올바르지 않습니다."
    });
});

test("identifies the exact field exceeding its limit", () => {
    assert.deepEqual(validateBulkSourceValues({sourceName: "가".repeat(101)}), {
        field: "sourceName", message: "제공기관은 100자를 넘을 수 없습니다."
    });
    assert.deepEqual(validateBulkSourceValues({commonSourceUrl: "https://example.com/" + "a".repeat(2000)}), {
        field: "commonSourceUrl", message: "공통 출처 URL은 2000자를 넘을 수 없습니다."
    });
});

test("shows the completed URL in common and work page inputs before submission", () => {
    for (const [kind, value] of [["common", " tripbora.com "],
        ["work", " example.com/photos/123 "]]) {
        const input = {value, dataset: {imageSourceUrl: kind}, maxLength: 2000,
            setCustomValidity(message) { this.error = message; }};
        normalizeSourceUrlInput(input);
        assert.equal(input.value, kind === "common"
            ? "https://tripbora.com" : "https://example.com/photos/123");
        assert.equal(input.error, "");
    }
});

test("reports malformed and overlong work page URLs on that field", () => {
    const invalid = {value: "not a page", dataset: {imageSourceUrl: "work"}, maxLength: 2000,
        setCustomValidity(message) { this.error = message; }};
    normalizeSourceUrlInput(invalid);
    assert.equal(invalid.value, "not a page");
    assert.equal(invalid.error, "사진별 원본 페이지 URL은 http:// 또는 https://로 시작해야 합니다.");
    invalid.value = "example.com/" + "a".repeat(2000);
    normalizeSourceUrlInput(invalid);
    assert.equal(invalid.error, "사진별 원본 페이지 URL은 2000자를 넘을 수 없습니다.");
});

test("fills blank fields while preserving photographer and work-specific values", () => {
    const plan = planBulkSource([
        {id: 1, name: "a.jpg", values: {sourceName: "", photographer: "개별 촬영자", licenseType: ""}},
        {id: 2, name: "b.jpg", values: {sourceName: "", photographer: "", licenseType: ""}}
    ], {sourceName: "공공기관", photographer: "공통 촬영자", licenseType: "KOGL_TYPE_1"}, new Set());
    assert.equal(plan.appliedPhotos, 2);
    assert.equal(plan.skipped.length, 1);
    assert.deepEqual(plan.manualPhotos, ["a.jpg"]);
    assert.equal(plan.changes.some(item => item.photo === "a.jpg" && item.field === "photographer"), false);
});

test("overwrites only the explicitly selected field", () => {
    const plan = planBulkSource([{id: 1, name: "a.jpg", values: {
        sourceName: "기존 기관", photographer: "개별 촬영자"
    }}], {sourceName: "새 기관", photographer: "공통 촬영자"}, new Set(["sourceName"]));
    assert.deepEqual(plan.changes.map(item => item.field), ["sourceName"]);
    assert.deepEqual(plan.skipped.map(item => item.field), ["photographer"]);
});

test("counts two selected files separately even when their names match", () => {
    const plan = planBulkSource([
        {id: 0, name: "photo.jpg", values: {}},
        {id: 1, name: "photo.jpg", values: {}}
    ], {sourceName: "공공기관"}, new Set());
    assert.equal(plan.appliedPhotos, 2);
});

test("applies the previewed fields to three upload cards and keeps individual edits", () => {
    const cards = Array.from({length: 3}, (_, index) => {
        const controls = Object.fromEntries(["sourceName", "photographer", "commonSourceUrl"]
            .map(field => [field, {value: "", name: `image${field}`, changes: 0,
                dispatchEvent() { this.changes++; }}]));
        if (index === 1) controls.photographer.value = "개별 촬영자";
        return {controls, querySelector(selector) {
            return controls[selector.match(/data-image-metadata-field="([^"]+)"/)?.[1]];
        }};
    });
    const photos = cards.map((card, id) => ({id, name: `${id}.jpg`, values: Object.fromEntries(
        Object.entries(card.controls).map(([field, control]) => [field, control.value]))}));
    const plan = planBulkSource(photos, {
        sourceName: "공통 제공기관", photographer: "공통 촬영자",
        commonSourceUrl: "https://example.org/source"
    }, new Set());

    const first = applyUploadPlan(plan, cards);

    assert.equal(first, cards[0].controls.sourceName);
    assert.deepEqual(cards.map(card => card.controls.sourceName.value),
        ["공통 제공기관", "공통 제공기관", "공통 제공기관"]);
    assert.equal(cards[1].controls.photographer.value, "개별 촬영자");
    assert.equal(cards[0].controls.commonSourceUrl.value, "https://example.org/source");
    assert.equal(cards[0].controls.sourceName.changes, 1);
    cards[2].controls.photographer.value = "사진별 수정";
    assert.equal(cards[2].controls.photographer.value, "사진별 수정");
});
