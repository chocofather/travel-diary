/** 선택 사진의 공통 출처를 미리 보고, 신규 업로드 입력칸 또는 등록된 사진의 저장 요청에 적용한다. */
(function () {
    "use strict";

    const fields = ["sourceName", "photographer", "licenseType", "licenseDetail", "commonSourceUrl"];
    const labels = {
        sourceName: "제공기관", photographer: "저작자", licenseType: "라이선스 유형",
        licenseDetail: "라이선스 상세", commonSourceUrl: "공통 제공기관 페이지 URL"
    };
    const limits = {
        sourceName: 100, photographer: 100, licenseType: 50,
        licenseDetail: 255, commonSourceUrl: 2000
    };
    const domainPattern = /^(?:[a-z0-9](?:[a-z0-9-]*[a-z0-9])?\.)+[a-z0-9](?:[a-z0-9-]*[a-z0-9])?$/i;

    function normalizeSourceUrl(raw) {
        const value = String(raw ?? "").trim();
        if (!value || /^[a-z][a-z\d+.-]*:\/\//i.test(value)
            || value.startsWith("//") || /[\s@]/.test(value)) return value;
        try {
            const url = new URL(`https://${value}`);
            if (domainPattern.test(url.hostname) || url.hostname === "localhost") {
                return `https://${value}`;
            }
        } catch (_) {
            // 유효하지 않은 주소는 자동으로 고치지 않고 입력값 그대로 검증한다.
        }
        return value;
    }

    function sourceUrlError(value, label, maxLength) {
        if (!value) return "";
        if (value.length > maxLength) return `${label}은 ${maxLength}자를 넘을 수 없습니다.`;
        if (!/^https?:\/\//i.test(value)) return `${label}은 http:// 또는 https://로 시작해야 합니다.`;
        try {
            const url = new URL(value);
            const authority = value.match(/^https?:\/\/([^/]*)/i)?.[1] || "";
            if (!url.hostname || authority.includes("@") || /\s/.test(value)) {
                return `${label} 형식이 올바르지 않습니다.`;
            }
        } catch (_) {
            return `${label} 형식이 올바르지 않습니다.`;
        }
        return "";
    }

    function normalizeSourceUrlInput(input) {
        input.value = normalizeSourceUrl(input.value);
        const label = input.dataset.imageSourceUrl === "work"
            ? "사진별 원본 페이지 URL" : "공통 출처 URL";
        input.setCustomValidity(sourceUrlError(input.value, label, input.maxLength || 2000));
        return input.value;
    }

    function validateBulkSourceValues(values) {
        for (const field of fields) {
            const value = field === "commonSourceUrl"
                ? normalizeSourceUrl(values[field]) : String(values[field] ?? "").trim();
            // 모든 항목은 선택 입력이다. 값이 있는 항목만 사진에 적용하고 검사한다.
            if (!value) continue;
            if (value.length > limits[field]) {
                const label = field === "commonSourceUrl" ? "공통 출처 URL" : labels[field];
                return {field, message: `${label}은 ${limits[field]}자를 넘을 수 없습니다.`};
            }
            if (field !== "commonSourceUrl") continue;
            const error = sourceUrlError(value, "공통 출처 URL", limits.commonSourceUrl);
            if (error) return {field, message: error};
        }
        return null;
    }

    function planBulkSource(photos, values, overwrite) {
        const changes = [];
        const skipped = [];
        photos.forEach(photo => {
            fields.forEach(field => {
                const next = (values[field] || "").trim();
                if (!next) return;
                const previous = (photo.values[field] || "").trim();
                if (previous === next) return;
                if (previous && !overwrite.has(field)) {
                    skipped.push({id: photo.id, photo: photo.name, field, previous});
                } else {
                    changes.push({id: photo.id, photo: photo.name, field, previous, next});
                }
            });
        });
        return {
            changes, skipped,
            appliedPhotos: new Set(changes.map(item => item.id)).size,
            manualPhotos: [...new Map(skipped.map(item => [item.id, item.photo])).values()]
        };
    }

    function applyUploadPlan(plan, cards) {
        const updates = plan.changes.map(change => ({
            card: cards[change.id],
            control: cards[change.id]?.querySelector(
                `[data-image-metadata-field="${change.field}"]`),
            value: change.next
        }));
        if (updates.some(update => !update.control)) {
            throw new Error("사진별 입력칸을 찾지 못했습니다.");
        }
        updates.forEach(({card, control, value}) => {
            control.value = value;
            control.dispatchEvent(new Event("change", {bubbles: true}));
            card.classList?.add("is-bulk-applied");
        });
        return updates[0]?.control ?? null;
    }

    if (typeof module !== "undefined" && module.exports) {
        module.exports = {planBulkSource, validateBulkSourceValues, applyUploadPlan,
            normalizeSourceUrl, normalizeSourceUrlInput};
    }
    if (typeof document === "undefined") return;

    document.addEventListener("input", event => {
        if (event.target.matches?.("[data-image-source-url]")) {
            event.target.setCustomValidity("");
        }
    });
    document.addEventListener("change", event => {
        if (event.target.matches?.("[data-image-source-url]")) normalizeSourceUrlInput(event.target);
    });
    document.addEventListener("focusout", event => {
        if (event.target.matches?.("[data-image-source-url]")) normalizeSourceUrlInput(event.target);
    });
    document.addEventListener("keydown", event => {
        if (event.key === "Enter" && event.target.matches?.("[data-image-source-url]")) {
            normalizeSourceUrlInput(event.target);
        }
    });
    document.addEventListener("click", event => {
        const submit = event.target.closest?.("button[type='submit'], input[type='submit']");
        submit?.form?.querySelectorAll("[data-image-source-url]")
            .forEach(normalizeSourceUrlInput);
    }, true);

    function cardField(card, field, mode) {
        return mode === "upload"
            ? card.querySelector(`[data-image-metadata-field="${field}"]`)
            : card.querySelector(`[name="${field}"]`);
    }

    function cardsFor(panel) {
        const mode = panel.dataset.imageBulk;
        const grid = mode === "upload"
            ? panel.closest("[data-destination-upload-preview]")?.querySelector("[data-destination-upload-preview-grid]")
            : document.querySelector(".admin-destination-image-grid");
        return [...(grid?.children || [])];
    }

    function choice(card) {
        return card.querySelector("[data-image-bulk-select]");
    }

    function hasUnsavedMetadata(card) {
        return [...card.querySelectorAll(".admin-destination-image-metadata-form input:not([type='hidden']), .admin-destination-image-metadata-form select")]
            .some(control => {
                if (control.tagName === "SELECT") {
                    const initial = [...control.options].find(option => option.defaultSelected)?.value
                        ?? control.options[0]?.value;
                    return control.value !== initial;
                }
                return control.value !== control.defaultValue;
            });
    }

    function addLine(parent, text) {
        const line = document.createElement("p");
        line.textContent = text;
        parent.append(line);
    }

    function setUp(panel) {
        const mode = panel.dataset.imageBulk;
        const preview = panel.querySelector("[data-bulk-preview]");
        const apply = panel.querySelector("[data-bulk-apply]");
        const count = panel.querySelector("[data-bulk-count]");
        const result = panel.querySelector("[data-bulk-result]");
        let applyingUpload = false;

        /** Commons 사진은 출처를 고칠 수 없어 공통 선택에 있어도 출처 일괄 적용 대상이 아니다. */
        function isCommons(card) {
            return choice(card)?.dataset.imageCommons === "true";
        }
        /** 적용 대상. 나눠 올리기로 이미 등록된 사진은 여기서 더 고칠 수 없으므로 뺀다. */
        function selectedCards() {
            return cardsFor(panel).filter(card => choice(card)?.checked
                && !card.classList.contains("is-upload-done") && !isCommons(card));
        }
        function updateCount() {
            const selected = selectedCards().length;
            const commons = cardsFor(panel).filter(card => choice(card)?.checked && isCommons(card)).length;
            count.textContent = `선택한 사진 ${selected}장`
                + (commons ? ` (Commons 사진 ${commons}장은 출처를 바꿀 수 없어 제외)` : "");
            // 미리보기를 거치지 않아도 선택한 사진이 있으면 바로 적용할 수 있다.
            apply.disabled = selected === 0;
        }
        /** 미리보기·결과·오류 표시만 지운다. 공통 출처 입력값과 덮어쓰기 체크는 그대로 둔다. */
        function invalidate() {
            preview.hidden = true;
            result.hidden = true;
            panel.querySelectorAll("[data-bulk-field-error]").forEach(message => message.remove());
            panel.querySelectorAll("[data-bulk-field][aria-invalid]")
                .forEach(input => input.removeAttribute("aria-invalid"));
            updateCount();
        }
        function showFieldError(error) {
            const input = panel.querySelector(`[data-bulk-field="${error.field}"]`);
            input.setAttribute("aria-invalid", "true");
            const fieldMessage = document.createElement("span");
            fieldMessage.className = "admin-image-bulk-field-error";
            fieldMessage.dataset.bulkFieldError = error.field;
            fieldMessage.textContent = error.message;
            input.after(fieldMessage);
            addLine(preview, error.message);
            preview.hidden = false;
            input.focus();
        }
        function values() {
            return Object.fromEntries(fields.map(field => {
                const input = panel.querySelector(`[data-bulk-field="${field}"]`);
                return [field, field === "commonSourceUrl"
                    ? normalizeSourceUrlInput(input) : input.value.trim()];
            }));
        }
        function overwrite() {
            return new Set([...panel.querySelectorAll("[data-bulk-overwrite]:checked")]
                .map(input => input.value));
        }
        function photo(card) {
            const id = mode === "upload" ? cardsFor(panel).indexOf(card) : card.dataset.imageId;
            const name = mode === "upload"
                ? `${card.querySelector(".admin-upload-preview-name")?.textContent} (${id + 1}번)`
                : `사진 #${id}`;
            return {id, name, values: Object.fromEntries(fields.map(field =>
                [field, cardField(card, field, mode)?.value || ""]))};
        }
        function stop(message) {
            addLine(preview, message);
            preview.hidden = false;
            return null;
        }
        /**
         * 지금 입력값·선택으로 적용 계획을 세운다. 미리보기와 적용이 같이 쓴다(미리보기를 먼저 누를 필요가 없다).
         * 막히는 이유가 있으면 미리보기 영역에 적고 null 을 돌려준다.
         */
        function prepare() {
            invalidate();
            preview.replaceChildren();
            const selected = selectedCards();
            if (!selected.length) return stop("적용할 사진을 선택해 주세요.");
            if (mode === "existing" && selected.some(hasUnsavedMetadata)) {
                return stop("선택한 사진에 저장되지 않은 개별 수정이 있습니다. 사진별 출처 정보를 먼저 저장해 주세요.");
            }
            const inputValues = values();
            if (Object.values(inputValues).every(value => !value)) return stop("공통 출처 정보를 입력해 주세요.");
            const valueError = validateBulkSourceValues(inputValues);
            if (valueError) { showFieldError(valueError); return null; }
            for (const field of fields) {
                if (!inputValues[field]) continue;
                const input = panel.querySelector(`[data-bulk-field="${field}"]`);
                if (!input.checkValidity()) {
                    showFieldError({field, message: field === "commonSourceUrl"
                        ? "공통 출처 URL 형식이 올바르지 않습니다."
                        : `${labels[field]} 입력값을 확인해 주세요.`});
                    return null;
                }
            }
            if ((inputValues.licenseType || inputValues.licenseDetail)
                && !panel.querySelector("[data-bulk-license-confirm]").checked) {
                return stop("선택한 모든 사진에 동일한 라이선스가 적용되는지 확인해 주세요.");
            }
            const overwriteFields = overwrite();
            return {selected, inputValues, overwriteFields,
                plan: planBulkSource(selected.map(photo), inputValues, overwriteFields)};
        }
        function skippedText(plan) {
            return plan.skipped.length
                ? ` 기존 값이 있어 건너뛴 항목 ${plan.skipped.length}개(사진 ${plan.manualPhotos.length}장)`
                    + " — 바꾸려면 '기존 값을 덮어쓸 항목'을 체크하세요."
                : "";
        }
        /** 적용 결과를 미리 확인하는 선택 기능. 입력값·선택은 바꾸지 않는다. */
        function showPreview() {
            const prepared = prepare();
            if (!prepared) return;
            const {plan} = prepared;
            addLine(preview, plan.changes.length
                ? `변경될 사진 ${plan.appliedPhotos}장 · 기존 값으로 건너뛸 항목 ${plan.skipped.length}개`
                : "바뀔 값이 없습니다. 선택한 사진에 이미 같은 값이 있거나 기존 값이 있어 건너뜁니다.");
            plan.changes.forEach(item => addLine(preview,
                `${item.photo} · ${labels[item.field]}: ${item.previous || "(비어 있음)"} → ${item.next}`));
            plan.skipped.forEach(item => addLine(preview,
                `${item.photo} · ${labels[item.field]}: 기존 값 유지 (${item.previous})`));
            if (plan.manualPhotos.length) addLine(preview, `개별 확인 필요: ${plan.manualPhotos.join(", ")}`);
            preview.hidden = false;
        }
        function showResult(text) {
            result.textContent = text;
            result.hidden = false;
        }

        // 등록된 사진(existing)은 머리말의 공통 선택 관리가 전체 선택·해제를 맡아 이 버튼이 없다.
        panel.querySelector("[data-bulk-select-all]")?.addEventListener("click", () => {
            cardsFor(panel).forEach(card => { if (choice(card)) choice(card).checked = true; }); invalidate();
        });
        panel.querySelector("[data-bulk-clear-all]")?.addEventListener("click", () => {
            cardsFor(panel).forEach(card => { if (choice(card)) choice(card).checked = false; }); invalidate();
        });
        panel.querySelector("[data-bulk-preview-button]").addEventListener("click", showPreview);
        panel.addEventListener("input", invalidate);
        panel.addEventListener("change", invalidate);
        function invalidateForCardEdit(event) {
            if (applyingUpload) return;
            const card = event.target.closest?.(".admin-upload-preview-card, .admin-destination-image-card");
            if (!card) return;
            if (mode === "upload" && panel.closest("[data-destination-upload-preview]")?.contains(card)) invalidate();
            if (mode === "existing" && card.classList.contains("admin-destination-image-card")) invalidate();
        }
        document.addEventListener("input", invalidateForCardEdit);
        document.addEventListener("change", invalidateForCardEdit);
        document.addEventListener("destination-images-changed", event => {
            if (mode === "upload" && event.target.contains(panel)) invalidate();
        });

        apply.addEventListener("click", () => {
            // 미리보기를 먼저 누르지 않아도 지금 입력값으로 계획을 세워 바로 적용한다.
            const prepared = prepare();
            if (!prepared) return;
            const {selected, inputValues, overwriteFields, plan} = prepared;
            if (!plan.changes.length) {
                showResult("바뀔 값이 없어 적용하지 않았습니다. 선택한 사진에 이미 같은 값이 있습니다." + skippedText(plan));
                return;
            }
            const overwritten = plan.changes.filter(item => item.previous).length;
            if (overwritten && !window.confirm(
                `선택한 사진의 기존 값 ${overwritten}개를 공통 출처로 덮어씁니다. 계속할까요?`)) return;
            if (mode === "upload") {
                let firstUpdated;
                applyingUpload = true;
                try {
                    firstUpdated = applyUploadPlan(plan, cardsFor(panel));
                } catch (error) {
                    showResult(error.message);
                    return;
                } finally {
                    applyingUpload = false;
                }
                showResult(`${plan.appliedPhotos}장의 사진에 입력값을 적용했습니다.` + skippedText(plan)
                    + " 이미지 업로드를 누르면 사진별 출처와 함께 저장됩니다.");
                firstUpdated?.focus({preventScroll: true});
                return;
            }
            const hidden = panel.querySelector("[data-bulk-hidden]");
            hidden.replaceChildren();
            const append = (name, value) => {
                const input = document.createElement("input");
                input.type = "hidden"; input.name = name; input.value = value; hidden.append(input);
            };
            selected.forEach(card => append("imageIds", card.dataset.imageId));
            fields.forEach(field => { if (inputValues[field]) append(field, inputValues[field]); });
            overwriteFields.forEach(field => append("overwriteFields", field));
            append("licenseConfirmed", panel.querySelector("[data-bulk-license-confirm]").checked);
            append("overwriteConfirmed", true);
            panel.requestSubmit();
        });
        updateCount();
    }

    document.addEventListener("DOMContentLoaded", () =>
        document.querySelectorAll("[data-image-bulk]").forEach(setUp));
}());
