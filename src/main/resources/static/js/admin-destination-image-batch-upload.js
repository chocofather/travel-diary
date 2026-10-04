(function (root) {
    /*
      관리자 여행지 이미지 관리 - 직접 업로드를 사진 한 장씩 차례로 보낸다.

      예전에는 선택한 사진 전부를 요청 하나에 담아, 합계가 요청 한도(10MB)를 넘으면 통째로 413 으로 거절됐다.
      (1.2MB 사진이면 8장까지는 되고 9장부터 막혔다) 사진마다 요청을 나누면 한도는 사진 한 장에만 걸리고,
      한 장이 실패해도 앞서 저장한 사진은 그대로 남는다. 실패한 사진만 같은 업로드 키로 다시 보내므로
      이미 저장된 사진이 두 번 등록되지 않는다.
     */

    function formatBytes(bytes) {
        const MB = 1024 * 1024;
        // 한도처럼 딱 떨어지는 값은 20MB 로, 사진 크기는 10.89MB 처럼 소수 둘째 자리까지 보인다.
        if (bytes >= MB && bytes % MB === 0) return `${bytes / MB}MB`;
        if (bytes >= MB) return `${(bytes / MB).toFixed(2)}MB`;
        return `${Math.max(1, Math.round(bytes / 1024))}KB`;
    }

    /** 선택한 파일을 올릴 순서대로 준비한다. 한도를 넘는 사진은 보내지 않고 이유와 함께 실패로 둔다. */
    function planUpload(files, maxBytes, newKey) {
        return Array.from(files).map((file, index) => {
            const entry = {index, file, name: file.name, size: file.size, uploadKey: newKey(), status: "pending", message: ""};
            if (maxBytes > 0 && file.size > maxBytes) {
                entry.status = "failed";
                entry.retryable = false;
                entry.message = `사진 한 장은 ${formatBytes(maxBytes)}까지 올릴 수 있습니다. (이 사진 ${formatBytes(file.size)})`;
            }
            return entry;
        });
    }

    function summarize(entries) {
        const count = {total: entries.length, bytes: 0, done: 0, success: 0, failed: 0, pending: 0, uploading: 0};
        for (const entry of entries) {
            count.bytes += entry.size;
            count[entry.status]++;
            if (entry.status === "success" || entry.status === "failed") count.done++;
        }
        return count;
    }

    /**
     * 서버 응답을 사진별 결과로 바꾼다.
     *
     * @param maxBytes 사진 한 장의 한도(화면이 받은 서버 설정값). 413 안내에 같은 기준을 쓴다.
     */
    function describeResponse(status, payload, redirected, maxBytes) {
        if (status === 200 && !redirected && payload && payload.imageId != null) {
            return {ok: true, imageId: payload.imageId, alreadySaved: Boolean(payload.alreadySaved)};
        }
        if (redirected || status === 401 || status === 403 || (status === 200 && !payload)) {
            return {ok: false, message: "로그인이 만료되었거나 보안 토큰이 바뀌었습니다. 페이지를 새로고침한 뒤 이 사진만 다시 올려 주세요."};
        }
        if (status === 413) {
            return {ok: false, message: maxBytes
                ? `사진이 서버 용량 한도를 넘었습니다. 사진 한 장은 ${formatBytes(maxBytes)}까지 올릴 수 있습니다.`
                : "사진이 서버 용량 한도를 넘었습니다."};
        }
        return {ok: false, message: (payload && payload.message) || `사진을 저장하지 못했습니다. (HTTP ${status})`};
    }

    /**
     * 보낼 차례인 사진(pending)을 한 장씩 차례로 올린다. 순서대로 보내야 등록 순서가 선택 순서와 같다.
     * 성공한 사진은 다시 보내지 않는다.
     *
     * @param send     entry → Promise<{ok, imageId?, message?}>
     * @param onChange 상태가 바뀔 때마다 부른다
     */
    async function runUploadQueue(entries, send, onChange = () => {}) {
        for (const entry of entries) {
            if (entry.status !== "pending") continue;
            entry.status = "uploading";
            entry.message = "";
            onChange(entry);
            let result;
            try {
                result = await send(entry);
            } catch (error) {
                result = {ok: false, message: error && error.name === "AbortError"
                    ? "응답이 너무 늦어 중단했습니다. 이 사진만 다시 올려 주세요."
                    : "네트워크 오류로 보내지 못했습니다. 이 사진만 다시 올려 주세요."};
            }
            if (result.ok) {
                entry.status = "success";
                entry.imageId = result.imageId;
            } else {
                entry.status = "failed";
                entry.retryable = true;
                entry.message = result.message;
            }
            onChange(entry);
        }
    }

    /** 다시 보낼 수 있는 실패 사진만 대기로 되돌린다. 같은 업로드 키를 그대로 쓴다. */
    function markRetry(entries) {
        let count = 0;
        for (const entry of entries) {
            if (entry.status === "failed" && entry.retryable) {
                entry.status = "pending";
                entry.message = "";
                count++;
            }
        }
        return count;
    }

    const api = {planUpload, summarize, describeResponse, runUploadQueue, markRetry, formatBytes};
    root.TripBoraImageBatchUpload = api;
    root.TravelDiaryImageBatchUpload = root.TripBoraImageBatchUpload; // legacy alias (P10a 제거)
    if (typeof module !== "undefined" && module.exports) module.exports = api;
    if (typeof document === "undefined") return;

    document.addEventListener("DOMContentLoaded", () => {
        const form = document.querySelector("[data-image-batch-upload]");
        if (!form || typeof window.fetch !== "function" || typeof FormData === "undefined") return;

        const input = form.querySelector("input[type='file'][name='files']");
        const preview = form.querySelector("[data-destination-upload-preview]");
        const submitButton = form.querySelector("button[type='submit']");
        const selection = form.querySelector("[data-image-upload-selection]");
        const progress = form.querySelector("[data-image-upload-progress]");
        const summary = form.querySelector("[data-image-upload-summary]");
        const bar = form.querySelector("[data-image-upload-bar]");
        const failures = form.querySelector("[data-image-upload-failures]");
        const retryButton = form.querySelector("[data-image-upload-retry]");
        const reloadLink = form.querySelector("[data-image-upload-reload]");
        const uploadUrl = form.dataset.uploadUrl;
        const maxBytes = Number(form.dataset.uploadMaxBytes) || 0;
        let entries = [];
        let running = false;

        const newKey = () => (window.crypto && typeof window.crypto.randomUUID === "function")
            ? window.crypto.randomUUID()
            : `k${Date.now().toString(36)}${Math.random().toString(36).slice(2, 12)}`;

        function cardFor(entry) {
            return preview?.querySelector(`.admin-upload-preview-card[data-file-index='${entry.index}']`) || null;
        }

        /** 사진 카드에 적힌 출처 입력값. 보내기 직전에 읽어 공통 출처 적용 결과까지 담는다. */
        function metadataOf(entry) {
            const values = {};
            cardFor(entry)?.querySelectorAll("[data-image-metadata-field]").forEach(control => {
                values[control.dataset.imageMetadataField] = control.value;
            });
            return values;
        }

        function csrfHeaders() {
            const token = document.querySelector("meta[name='_csrf']")?.content;
            const header = document.querySelector("meta[name='_csrf_header']")?.content;
            return token && header ? {[header]: token} : {};
        }

        async function send(entry) {
            const body = new FormData();
            body.append("file", entry.file, entry.name);
            body.append("uploadKey", entry.uploadKey);
            Object.entries(metadataOf(entry)).forEach(([name, value]) => body.append(name, value));
            const controller = new AbortController();
            const timer = setTimeout(() => controller.abort(), 120000);
            try {
                const response = await fetch(uploadUrl, {
                    method: "POST", body, headers: {Accept: "application/json", ...csrfHeaders()},
                    credentials: "same-origin", signal: controller.signal
                });
                const isJson = (response.headers.get("Content-Type") || "").includes("application/json");
                const payload = isJson ? await response.json().catch(() => null) : null;
                return describeResponse(response.status, payload, response.redirected, maxBytes);
            } finally {
                clearTimeout(timer);
            }
        }

        function setBadge(entry) {
            const card = cardFor(entry);
            if (!card) return;
            let badge = card.querySelector("[data-image-upload-state]");
            if (!badge) {
                badge = document.createElement("p");
                badge.dataset.imageUploadState = "";
                card.append(badge);
            }
            const labels = {pending: "대기", uploading: "업로드 중", success: "등록됨", failed: "실패"};
            badge.className = `admin-image-upload-state is-${entry.status}`;
            badge.textContent = entry.status === "failed" ? `실패 · ${entry.message}` : labels[entry.status];
            card.classList.toggle("is-upload-failed", entry.status === "failed");
            card.classList.toggle("is-upload-done", entry.status === "success");
            // 저장된 사진의 출처는 여기서 더 고칠 수 없다(아래 관리 카드에서 고친다).
            if (entry.status === "success") {
                card.querySelectorAll("input, select").forEach(control => { control.disabled = true; });
            }
        }

        function render() {
            const count = summarize(entries);
            const sent = count.success + count.failed;
            summary.textContent = running
                ? `업로드 중 ${Math.min(sent + 1, count.total)} / ${count.total}장 · 성공 ${count.success} · 실패 ${count.failed}`
                : `업로드 결과: 전체 ${count.total}장 중 성공 ${count.success}장 · 실패 ${count.failed}장`;
            bar.style.width = `${count.total ? Math.round(sent / count.total * 100) : 0}%`;
            const failed = entries.filter(entry => entry.status === "failed");
            failures.replaceChildren(...failed.map(entry => {
                const item = document.createElement("li");
                item.textContent = `${entry.name} — ${entry.message}`;
                return item;
            }));
            failures.hidden = running || failed.length === 0;
            retryButton.hidden = running || !failed.some(entry => entry.retryable);
            reloadLink.hidden = running || count.success === 0;
        }

        function showSelection() {
            entries = planUpload(input.files || [], maxBytes, newKey);
            progress.hidden = true;
            if (!entries.length) {
                selection.textContent = "";
                return;
            }
            const count = summarize(entries);
            const tooLarge = entries.filter(entry => entry.status === "failed").length;
            selection.textContent = `선택 ${count.total}장 · 전체 ${formatBytes(count.bytes)} · 한 장씩 차례로 올립니다`
                + (maxBytes ? ` (사진당 최대 ${formatBytes(maxBytes)})` : "")
                + (tooLarge ? ` · 용량 초과 ${tooLarge}장은 올리지 않습니다` : "");
            entries.filter(entry => entry.status === "failed").forEach(setBadge);
        }

        async function upload() {
            if (running) return;
            running = true;
            input.disabled = true;
            submitButton.disabled = true;
            progress.hidden = false;
            render();
            await runUploadQueue(entries, send, entry => { setBadge(entry); render(); });
            running = false;
            input.disabled = false;
            // 남은 일은 '실패한 사진만 다시 업로드'뿐이다. 새로 고르면 다시 켠다.
            submitButton.disabled = true;
            render();
            const count = summarize(entries);
            if (count.failed === 0 && count.success > 0) {
                summary.textContent = `${count.success}장을 모두 등록했습니다. 목록을 새로 불러옵니다.`;
                window.location.assign(reloadLink.href);
            }
        }

        input.addEventListener("change", () => {
            // 미리보기 카드가 만들어진 뒤에 계획을 세운다(카드별 표시를 붙이기 위해).
            setTimeout(showSelection, 0);
            submitButton.disabled = false;
        });

        form.addEventListener("submit", event => {
            event.preventDefault();
            if (!entries.length) showSelection();
            if (!entries.some(entry => entry.status === "pending")) return;
            void upload();
        });

        retryButton.addEventListener("click", () => {
            if (running || !markRetry(entries)) return;
            entries.filter(entry => entry.status === "pending").forEach(setBadge);
            void upload();
        });

        window.addEventListener("beforeunload", event => {
            if (running) event.preventDefault();
        });
    });
})(typeof window !== "undefined" ? window : globalThis);
