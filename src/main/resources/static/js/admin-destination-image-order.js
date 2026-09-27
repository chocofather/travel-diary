(function (root) {
    /*
      관리자 여행지 이미지 관리 - 등록된 사진 순서 편집.

      순서를 바꾸는 동안에는 화면에서만 바뀌고, '순서 저장'을 누를 때 사진 번호 목록을 한 번에 보낸다.
      '취소'는 마지막으로 저장한 순서로 되돌린다. 대표 이미지·슬라이드 지정은 순서와 상관없다.
      - 마우스: 사진을 끌어서 옮긴다.
      - 터치: 사진 오른쪽 위 손잡이(⠿)를 끌어서 옮긴다(화면 스크롤은 그대로 된다).
      - 키보드·모바일: '위치 이동'에서 번호를 고르거나 ◀ ▶ 로 한 칸씩 옮긴다.
     */

    /** from 번째 항목을 to 번째 자리로 옮긴 새 배열. 나머지는 차례대로 밀린다. */
    function moveItem(items, from, to) {
        const next = items.slice();
        if (from < 0 || from >= next.length) return next;
        const target = Math.max(0, Math.min(to, next.length - 1));
        const [item] = next.splice(from, 1);
        next.splice(target, 0, item);
        return next;
    }

    /** 저장한 순서와 비교해 자리가 바뀐 사진 수. */
    function changedCount(saved, current) {
        return current.filter((id, index) => saved[index] !== id).length;
    }

    /**
     * 격자에서 포인터(페이지 좌표)가 가리키는 "끼워 넣을 자리"와 표시선 위치.
     * 포인터 높이의 줄(없으면 가장 가까운 줄)에서, 칸의 가운데보다 왼쪽이면 그 칸 앞, 줄 끝을 넘으면 그 줄 마지막 칸 뒤다.
     *
     * @param rects 사진 칸의 페이지 좌표 {left, right, top, bottom}. 끌기 시작할 때 한 번만 잰다.
     * @param gap   칸 사이 간격(표시선을 간격 한가운데 둔다)
     * @returns {{index: number, x: number, top: number, bottom: number}} index 는 "그 앞에 넣을 칸"(0..N)
     */
    function insertionPoint(rects, x, y, gap = 10) {
        if (!rects.length) return {index: 0, x: 0, top: 0, bottom: 0};
        let rowTop = rects[0].top;
        let nearest = Infinity;
        for (const rect of rects) {
            const distance = y < rect.top ? rect.top - y : y > rect.bottom ? y - rect.bottom : 0;
            if (distance < nearest) {
                nearest = distance;
                rowTop = rect.top;
            }
        }
        let last = -1;
        for (let index = 0; index < rects.length; index++) {
            const rect = rects[index];
            if (Math.abs(rect.top - rowTop) > 1) continue;
            if (x < (rect.left + rect.right) / 2) {
                return {index, x: rect.left - gap / 2, top: rect.top, bottom: rect.bottom};
            }
            last = index;
        }
        const end = rects[last];
        return {index: last + 1, x: end.right + gap / 2, top: end.top, bottom: end.bottom};
    }

    /** from 번째 사진을 "insertBefore 번째 칸 앞"에 넣었을 때의 새 위치(0부터). */
    function finalIndex(from, insertBefore) {
        return insertBefore > from ? insertBefore - 1 : insertBefore;
    }

    const api = {moveItem, changedCount, insertionPoint, finalIndex};
    root.TravelDiaryImageOrder = api;
    if (typeof module !== "undefined" && module.exports) module.exports = api;
    if (typeof document === "undefined") return;

    document.addEventListener("DOMContentLoaded", () => {
        const editor = document.querySelector("[data-image-order-editor]");
        if (!editor) return;
        const list = editor.querySelector("[data-image-order-list]");
        const saveButton = editor.querySelector("[data-image-order-save]");
        const cancelButton = editor.querySelector("[data-image-order-cancel]");
        const status = editor.querySelector("[data-image-order-status]");
        const openButton = document.querySelector("[data-image-order-open]");
        // 격자 위 머리말(총 N장·순서 편집). 편집 중에는 편집 도구 막대가 그 자리를 대신한다.
        const listBar = document.querySelector("[data-image-order-bar]");
        const result = document.querySelector("[data-image-order-result]");
        const cardGrid = document.querySelector(".admin-destination-image-grid");
        const bulkPanel = document.querySelector("[data-image-bulk='existing']");
        const orderUrl = editor.dataset.orderUrl;

        const items = () => Array.from(list.children);
        const idOf = item => Number(item.dataset.imageId);
        const currentIds = () => items().map(idOf);
        let savedIds = currentIds();
        let saving = false;

        // 모든 사진에 같은 '위치 이동' 목록(1번 ~ N번)을 만든다. 번호를 직접 입력하지 않는다.
        items().forEach(item => {
            const select = item.querySelector("[data-image-order-move]");
            select.append(new Option("위치 이동", ""));
            items().forEach((_, index) => select.append(new Option(`${index + 1}번으로`, String(index + 1))));
        });

        function setStatus(message, isError = false) {
            status.textContent = message;
            status.classList.toggle("is-error", isError);
            status.setAttribute("role", isError ? "alert" : "status");
        }

        function renumber() {
            const all = items();
            all.forEach((item, index) => {
                item.querySelector("[data-image-order-number]").textContent = String(index + 1);
                const select = item.querySelector("[data-image-order-move]");
                select.value = "";
                select.setAttribute("aria-label", `${index + 1}번 사진을 옮길 위치`);
                item.querySelector("[data-image-order-step='-1']").disabled = index === 0;
                item.querySelector("[data-image-order-step='1']").disabled = index === all.length - 1;
            });
            const changed = changedCount(savedIds, currentIds());
            saveButton.disabled = saving || changed === 0;
            if (!saving) {
                setStatus(changed ? `자리가 바뀐 사진 ${changed}장 · '순서 저장'을 눌러야 반영됩니다.` : "");
            }
        }

        /** 사진 한 장만 옮기고 번호를 다시 매긴다(나머지 사진 DOM 은 그대로 둔다). */
        function moveTo(item, index) {
            const all = items();
            const from = all.indexOf(item);
            const to = Math.max(0, Math.min(index, all.length - 1));
            if (from !== to) list.insertBefore(item, all[to > from ? to + 1 : to] || null);
            renumber();
        }

        function applyOrder(ids) {
            ids.forEach(id => list.append(list.querySelector(`[data-image-id='${id}']`)));
            renumber();
        }

        function setEditing(editing) {
            editor.hidden = !editing;
            if (cardGrid) cardGrid.hidden = editing;
            if (bulkPanel) bulkPanel.hidden = editing;
            if (openButton) openButton.hidden = editing;
            if (listBar) listBar.hidden = editing;
            if (result && editing) result.hidden = true;
        }

        openButton?.addEventListener("click", () => {
            setEditing(true);
            renumber();
            editor.scrollIntoView({block: "start"});
            list.querySelector("[data-image-order-move]")?.focus({preventScroll: true});
        });

        cancelButton.addEventListener("click", () => {
            if (saving) return;
            applyOrder(savedIds);
            setEditing(false);
            openButton?.focus();
        });

        // 키보드·모바일: 원하는 번호로 옮기거나 한 칸씩 옮긴다. 옮긴 사진의 같은 조작에 포커스를 둔다.
        list.addEventListener("change", event => {
            const select = event.target.closest("[data-image-order-move]");
            if (!select || !select.value) return;
            const item = select.closest("[data-image-id]");
            const from = items().indexOf(item) + 1;
            const to = Number(select.value);
            moveTo(item, to - 1);
            select.focus();
            if (from !== to) setStatus(`${from}번 사진을 ${to}번으로 옮겼습니다. ` + status.textContent);
        });
        list.addEventListener("click", event => {
            const button = event.target.closest("[data-image-order-step]");
            if (!button) return;
            const item = button.closest("[data-image-id]");
            moveTo(item, items().indexOf(item) + Number(button.dataset.imageOrderStep));
            (button.disabled ? item.querySelector("[data-image-order-move]") : button).focus();
        });

        /*
          끌어서 옮기기.
          끄는 동안에는 사진 DOM 을 옮기지 않는다(예전에는 포인터가 움직일 때마다 사진을 옮기고 38장 번호를 다시 쓰고
          그때마다 화면 전체를 다시 그려, 큰 사진과 겹치면 몇 초씩 밀렸다).
          시작할 때 칸 위치를 한 번만 재 두고, 포인터가 움직이면 다음 화면 갱신(requestAnimationFrame)에서 한 번만
          "놓일 자리"를 계산해 표시선과 떠 있는 미리보기만 옮긴다(transform). 놓는 순간 한 장만 옮기고 번호를 다시 매긴다.
        */
        const EDGE_ZONE = 80;
        const MAX_SCROLL_STEP = 22;
        const indicator = document.createElement("div");
        indicator.className = "admin-image-order-indicator";
        indicator.hidden = true;
        indicator.setAttribute("aria-hidden", "true");
        document.body.append(indicator);
        let pending = null;
        let drag = null;

        function pageRects() {
            return items().map(item => {
                const rect = item.getBoundingClientRect();
                return {left: rect.left + window.scrollX, right: rect.right + window.scrollX,
                    top: rect.top + window.scrollY, bottom: rect.bottom + window.scrollY};
            });
        }

        function startDrag(start) {
            const {item} = start;
            const rect = item.getBoundingClientRect();
            const ghost = document.createElement("div");
            ghost.className = "admin-image-order-ghost";
            ghost.setAttribute("aria-hidden", "true");
            const image = new Image();
            image.src = item.querySelector("img")?.currentSrc || "";
            image.alt = "";
            const label = document.createElement("span");
            ghost.append(image, label);
            ghost.style.width = `${rect.width}px`;
            document.body.append(ghost);
            const toolbar = editor.querySelector(".admin-image-order-toolbar");
            drag = {
                item, ghost, label,
                from: items().indexOf(item),
                rects: pageRects(),
                offsetX: start.x - rect.left,
                offsetY: start.y - rect.top,
                x: start.x, y: start.y,
                index: -1,
                frame: 0,
                // 위쪽 자동 스크롤은 화면 위에 붙은 도구 막대 아래에서부터 센다.
                topEdge: Math.max(0, toolbar ? toolbar.getBoundingClientRect().bottom : 0)
            };
            item.classList.add("is-dragging");
            list.classList.add("is-sorting");
            scheduleFrame();
        }

        function scheduleFrame() {
            if (drag && !drag.frame) drag.frame = requestAnimationFrame(updateDrag);
        }

        /** 한 화면 갱신에 한 번: 가장자리 자동 스크롤 → 미리보기 이동 → 놓일 자리가 바뀌었을 때만 표시선·안내 갱신. */
        function updateDrag() {
            drag.frame = 0;
            let step = 0;
            if (drag.y < drag.topEdge + EDGE_ZONE) {
                step = -Math.ceil(Math.min(1, (drag.topEdge + EDGE_ZONE - drag.y) / EDGE_ZONE) * MAX_SCROLL_STEP);
            } else if (drag.y > window.innerHeight - EDGE_ZONE) {
                step = Math.ceil(Math.min(1, (drag.y - (window.innerHeight - EDGE_ZONE)) / EDGE_ZONE) * MAX_SCROLL_STEP);
            }
            if (step) window.scrollBy({top: step, behavior: "instant"});
            drag.ghost.style.transform = `translate3d(${drag.x - drag.offsetX}px, ${drag.y - drag.offsetY}px, 0)`;
            // 칸 위치는 페이지 좌표로 재 두었으므로 스크롤해도 다시 잴 필요가 없다.
            const target = insertionPoint(drag.rects, drag.x + window.scrollX, drag.y + window.scrollY);
            if (target.index !== drag.index) {
                drag.index = target.index;
                const to = finalIndex(drag.from, target.index);
                indicator.hidden = false;
                indicator.classList.toggle("is-same", to === drag.from);
                indicator.style.height = `${target.bottom - target.top}px`;
                indicator.style.transform = `translate3d(${target.x - 2}px, ${target.top}px, 0)`;
                drag.label.textContent = to === drag.from ? "제자리" : `${to + 1}번 자리`;
                setStatus(to === drag.from
                    ? `${drag.from + 1}번 사진을 끌고 있습니다. 놓을 자리로 옮기세요.`
                    : `${drag.from + 1}번 사진 → ${to + 1}번 자리에 놓입니다.`);
            }
            // 가장자리에 머무는 동안에는 포인터가 멈춰 있어도 계속 스크롤한다.
            if (step) scheduleFrame();
        }

        function finishDrag(commit) {
            pending = null;
            if (!drag) return;
            cancelAnimationFrame(drag.frame);
            const {item, from, ghost} = drag;
            const to = commit
                ? finalIndex(from, insertionPoint(drag.rects, drag.x + window.scrollX, drag.y + window.scrollY).index)
                : from;
            ghost.remove();
            indicator.hidden = true;
            item.classList.remove("is-dragging");
            list.classList.remove("is-sorting");
            drag = null;
            moveTo(item, to);
            if (to !== from) setStatus(`${from + 1}번 사진을 ${to + 1}번으로 옮겼습니다. ` + status.textContent);
        }

        list.addEventListener("pointerdown", event => {
            const item = event.target.closest(".admin-image-order-item");
            if (!item || saving || drag || event.target.closest("select, button")) return;
            if (event.pointerType === "mouse" ? event.button !== 0 : !event.target.closest("[data-image-order-handle]")) return;
            event.preventDefault();
            list.setPointerCapture?.(event.pointerId);
            pending = {item, x: event.clientX, y: event.clientY};
        });
        list.addEventListener("pointermove", event => {
            if (pending && !drag) {
                // 살짝 누른 것(클릭)과 끌기를 가른다. 몇 px 만 움직여도 바로 끌기가 시작된다.
                if (Math.hypot(event.clientX - pending.x, event.clientY - pending.y) < 4) return;
                startDrag(pending);
                pending = null;
            }
            if (!drag) return;
            drag.x = event.clientX;
            drag.y = event.clientY;
            scheduleFrame();
        });
        list.addEventListener("pointerup", () => finishDrag(true));
        list.addEventListener("pointercancel", () => finishDrag(false));
        document.addEventListener("keydown", event => {
            if (event.key === "Escape" && drag) finishDrag(false);
        });

        function csrfHeaders() {
            const token = document.querySelector("meta[name='_csrf']")?.content;
            const header = document.querySelector("meta[name='_csrf_header']")?.content;
            return token && header ? {[header]: token} : {};
        }

        /** 저장한 순서를 일반 관리 카드에도 그대로 옮기고 번호를 다시 매긴다(새로고침 없이). */
        function applyToCards(ids) {
            if (!cardGrid) return;
            ids.forEach((id, index) => {
                const card = document.getElementById(`image-${id}`);
                if (!card) return;
                cardGrid.append(card);
                const badge = card.querySelector(".admin-image-order-badge");
                if (badge) {
                    badge.textContent = String(index + 1);
                    badge.setAttribute("aria-label", `순서 ${index + 1}번째`);
                }
            });
        }

        saveButton.addEventListener("click", async () => {
            const ids = currentIds();
            if (saving || changedCount(savedIds, ids) === 0) return;
            saving = true;
            saveButton.disabled = true;
            cancelButton.disabled = true;
            setStatus("순서를 저장하는 중입니다.");
            try {
                const response = await fetch(orderUrl, {
                    method: "POST",
                    headers: {"Content-Type": "application/json", Accept: "application/json", ...csrfHeaders()},
                    credentials: "same-origin",
                    body: JSON.stringify({imageIds: ids})
                });
                const isJson = (response.headers.get("Content-Type") || "").includes("application/json");
                const payload = isJson ? await response.json().catch(() => null) : null;
                if (!response.ok || response.redirected || !payload || payload.message) {
                    throw new Error(payload?.message || (response.status === 403 || response.redirected
                        ? "로그인이 만료되었거나 보안 토큰이 바뀌었습니다. 페이지를 새로고침한 뒤 다시 순서를 정해 주세요."
                        : `순서를 저장하지 못했습니다. (HTTP ${response.status})`));
                }
                savedIds = ids;
                applyToCards(ids);
                saving = false;
                cancelButton.disabled = false;
                setEditing(false);
                if (result) {
                    result.textContent = `사진 ${ids.length}장의 순서를 저장했습니다. 대표 이미지와 슬라이드 지정은 그대로입니다.`;
                    result.hidden = false;
                }
                openButton?.focus();
            } catch (error) {
                // 바꾼 순서는 화면에 그대로 두고 다시 저장할 수 있게 한다.
                saving = false;
                cancelButton.disabled = false;
                saveButton.disabled = false;
                setStatus(error instanceof TypeError
                    ? "네트워크 오류로 저장하지 못했습니다. 바꾼 순서는 그대로 있으니 다시 저장해 주세요."
                    : `${error.message} 바꾼 순서는 그대로 있으니 다시 저장해 주세요.`, true);
            }
        });

        window.addEventListener("beforeunload", event => {
            if (!editor.hidden && changedCount(savedIds, currentIds()) > 0) event.preventDefault();
        });
    });
})(typeof window !== "undefined" ? window : globalThis);
