(function (root) {
    /*
      메인 랜드마크 아치 카드의 스크롤 수렴. 프로토타입으로 시작해 파일 이름에 prototype 이 남아 있다.
      랜드마크가 한 곳도 없으면 섹션이 없으므로 아무것도 하지 않는다. 카드 수가 6보다 적어도 같은 식으로 모인다.
      격자가 화면 아래에서 올라오는 동안의 진행률(0→1)에 맞춰 카드를 최종 자리로 모은다.
      - 시작 위치: 데스크톱은 왼쪽 세 장 / 오른쪽 세 장이 화면 양 끝에서 완만한 부채꼴로 겹쳐 선다.
        태블릿은 양옆 열만 조금 벌어진다.
      - 궤적: 시작점 → 제어점 → 최종 자리(0,0)의 2차 베지어 곡선. 옆으로 먼저 다가오고 끝에서 떠오르듯 줄에 앉는다.
      - 속도: 스크롤 위치가 정한 목표 진행률을 매 프레임 조금씩 따라가(lerp) 스크롤이 튀어도 카드는 부드럽게 움직인다.
      JS 는 카드마다 --converge-x / --converge-y 와 격자의 --converge-progress 만 쓰고,
      투명도·크기·겹침 순서는 css/home-converge-prototype.css 가 정한다.
      섹션을 고정(pin)하지 않으므로 스크롤은 평소처럼 지나간다.
      모바일 폭과 prefers-reduced-motion 에서는 진행률을 쓰지 않아 최종 배열이 그대로 보인다.

      수렴은 페이지를 연 뒤 한 번뿐이다. 다 모인 뒤에는 위로 다시 스크롤해도 흩뜨리지 않는다. (새로고침하면 다시)

      수동 레일: 카드 목록(ul)은 원래 가로 스크롤 레일이다. 수렴하는 동안(is-converging)만 CSS 가 레일을 멈추고
      첫 화면 밖 카드(is-offstage)를 숨긴다. 카드 폭·자리는 두 상태가 같아 전환할 때 튀지 않는다.
      자동으로 넘기지 않고 드래그(마우스)·가로 스크롤(트랙패드)·스와이프·화살표 키·이전/다음 버튼으로만 움직인다.

      끝없이 돌기(순환): 원본 카드가 한 화면을 넘으면, 원본 앞뒤에 같은 순서의 복제본을 한 벌씩 둔다.
        [복제 1..N][원본 1..N][복제 1..N]
      레일은 늘 원본 구간에서 쉬고, 복제 구간으로 넘어가면 멈춘 순간 같은 모습의 원본 자리로 즉시(애니메이션 없이) 옮긴다.
      복제본은 보이기만 하는 카드다. 읽기 도구에서 숨기고(aria-hidden) 링크는 Tab 대상에서 뺀다(tabindex=-1).
     */

    // 격자 윗변이 화면 높이의 93% 에 들어오면 0, 첫 카드 줄의 가운데가 화면 52% 에 오면 1(수렴 끝).
    const START_RATIO = 0.93;
    const END_RATIO = 0.52;

    // 60fps 한 프레임에 남은 거리의 12% 를 따라간다. 스크롤을 멈추면 약 0.4초 안에 제자리에 닿는다.
    const FOLLOW_PER_FRAME = 0.12;
    const FRAME_MS = 1000 / 60;
    const SNAP_DISTANCE = 0.001;

    // 부채꼴 시작 모양(카드 폭 기준 비율). 바깥 → 가운데 순서.
    const FAN_SPREAD = 0.62;          // 같은 쪽 카드끼리의 가로 간격 (1 보다 작아 살짝 겹친다)
    const FAN_EDGE_BLEED = 0.2;       // 바깥 카드가 화면 밖으로 걸치는 정도
    const FAN_DROP = [0.5, 0.26, 0.06]; // 바깥일수록 아래에서 출발한다
    const CURVE_CONTROL_X = 0.4;      // 제어점 X: 시작 X 의 40% 지점 → 가로 이동이 세로보다 앞선다
    const CURVE_SAG = 0.1;            // 제어점 Y 를 카드 폭의 10% 만큼 더 내려 완만히 휘게 한다

    // 태블릿(가운데 열은 제자리): 양옆 열만 짧게 모인다.
    const TABLET_SHIFT = 48;
    const TABLET_DROP = 0.08;

    const STATIC_QUERY = '(max-width: 680px), (prefers-reduced-motion: reduce)';
    const TABLET_QUERY = '(max-width: 960px)';
    const REDUCED_MOTION_QUERY = '(prefers-reduced-motion: reduce)';

    // 마우스를 이보다 적게 움직이고 놓으면 드래그가 아니라 클릭(상세 이동)이다.
    const DRAG_THRESHOLD = 6;
    // 스크롤 이벤트가 이만큼 없으면 레일이 멈춘 것으로 본다. (scrollend 를 모르는 브라우저용)
    const SETTLE_IDLE_MS = 120;

    /*
      스크롤 위치 → 목표 진행률(선형, 0~1).
      시작: 격자 윗변이 START_RATIO 지점. 끝: 첫 줄 가운데(윗변 + 줄 높이/2)가 END_RATIO 지점.
      같은 위치면 스크롤 방향과 관계없이 같은 값이다.
     */
    function convergeProgress(gridTop, rowHeight, viewportHeight) {
        if (!(viewportHeight > 0)) return 1;
        const startTop = viewportHeight * START_RATIO;
        const endTop = viewportHeight * END_RATIO - (rowHeight > 0 ? rowHeight : 0) / 2;
        if (!(startTop > endTop)) return 1;
        return Math.min(1, Math.max(0, (startTop - gridTop) / (startTop - endTop)));
    }

    // 출발과 도착이 급하지 않도록 화면에 그릴 때만 smoothstep 을 건다.
    function easeProgress(progress) {
        return progress * progress * (3 - 2 * progress);
    }

    // 현재 진행률이 목표를 따라가는 한 걸음. 프레임 간격이 달라도 같은 속도가 되도록 보정한다.
    function followProgress(current, target, elapsedMs) {
        const frames = Math.max(0, elapsedMs) / FRAME_MS;
        const next = current + (target - current) * (1 - Math.pow(1 - FOLLOW_PER_FRAME, frames));
        return Math.abs(target - next) < SNAP_DISTANCE ? target : next;
    }

    /*
      데스크톱 부채꼴 시작 위치.
      cards 는 최종 배치의 화면 기준 left·width 를 순서대로 준다(한 줄).
      앞 절반은 왼쪽 끝, 뒤 절반은 오른쪽 끝에서 대칭으로 출발한다.
      돌려주는 값은 최종 자리 기준 이동량 {x, y, sag} 이다.
     */
    function fanStartOffsets(cards, viewportWidth) {
        const half = Math.ceil(cards.length / 2);
        return cards.map((card, index) => {
            const fromLeft = index < half;
            const rank = Math.min(fromLeft ? index : cards.length - 1 - index, FAN_DROP.length - 1);
            const edge = -card.width * FAN_EDGE_BLEED;
            const reach = rank * card.width * FAN_SPREAD;
            const startLeft = fromLeft
                ? edge + reach
                : viewportWidth - edge - card.width - reach;
            return {
                x: startLeft - card.left,
                y: card.width * FAN_DROP[rank],
                sag: card.width * CURVE_SAG
            };
        });
    }

    // 태블릿: 격자 가운데보다 왼쪽 열은 왼쪽에서, 오른쪽 열은 오른쪽에서, 가운데 열은 아래에서만 온다.
    function tabletStartOffsets(cards, gridCenter) {
        return cards.map(card => {
            const offset = card.left + card.width / 2 - gridCenter;
            const side = Math.abs(offset) < 1 ? 0 : Math.sign(offset);
            return {
                x: side * TABLET_SHIFT,
                y: Math.abs(side) * card.width * TABLET_DROP,
                sag: 0
            };
        });
    }

    /*
      2차 베지어: P0 = 시작, P1 = 제어점, P2 = 최종 자리(0,0).
      B(t) = (1-t)²·P0 + 2(1-t)t·P1 + t²·P2  (P2 가 0 이라 마지막 항은 빠진다)
     */
    function curveOffset(start, t) {
        const rest = 1 - t;
        const controlX = start.x * CURVE_CONTROL_X;
        const controlY = start.y + start.sag;
        return {
            x: rest * rest * start.x + 2 * rest * t * controlX,
            y: rest * rest * start.y + 2 * rest * t * controlY
        };
    }

    // 여러 자리(scrollLeft) 중 지금 위치에 가장 가까운 것의 번호. 같으면 앞쪽이다.
    function nearestSnap(positions, scrollLeft) {
        let nearest = 0;
        positions.forEach((position, index) => {
            if (Math.abs(position - scrollLeft) < Math.abs(positions[nearest] - scrollLeft)) nearest = index;
        });
        return nearest;
    }

    function isDrag(distance) {
        return Math.abs(distance) >= DRAG_THRESHOLD;
    }

    /*
      순환 레일의 칸 번호는 [복제 N][원본 N][복제 N] 전체 목록 기준이다. 원본 구간은 count ~ 2·count-1.
      loopShift: 칸이 원본 구간 밖이면 같은 모습의 원본 쪽 칸으로 옮길 만큼(±count). 안이면 0.
     */
    function loopShift(index, count) {
        if (!(count > 0)) return 0;
        if (index < count) return count;
        if (index >= 2 * count) return -count;
        return 0;
    }

    // 한 칸 이동. 목표가 원본 구간을 벗어나면 먼저 옮길 양(shift)과 옮긴 뒤의 목표를 함께 돌려준다.
    function loopStep(base, direction, count) {
        const target = base + direction;
        const shift = loopShift(target, count);
        return { shift, target: target + shift };
    }

    // 전체 목록 칸 번호 → 원본 몇 번째(0부터)
    function logicalIndex(index, count) {
        return ((index - count) % count + count) % count;
    }

    // 보이기만 하는 복제 카드. 읽기 도구·Tab 대상에서 빼고, 같은 id 가 두 번 생기지 않게 id 를 지운다.
    function cloneCard(card) {
        const clone = card.cloneNode(true);
        clone.classList.add('is-clone');
        clone.classList.remove('is-offstage', 'is-first', 'is-last');
        clone.setAttribute('aria-hidden', 'true');
        clone.removeAttribute('id');
        clone.querySelectorAll('[id]').forEach(element => element.removeAttribute('id'));
        clone.querySelectorAll('a, button, input, select, textarea, [tabindex]')
            .forEach(element => element.setAttribute('tabindex', '-1'));
        return clone;
    }

    /*
      수렴이 끝난 뒤의 수동 레일. isReady() 가 false 인 동안(수렴 중)에는 어떤 조작도 받지 않는다.
      originals 는 서버가 그린 원본 카드(li)다. onUse 는 사용자가 레일을 움직일 때마다 부른다.
     */
    function initRail(win, rail, grid, originals, isReady, onUse) {
        const prev = rail.querySelector('[data-home-converge-prev]');
        const next = rail.querySelector('[data-home-converge-next]');
        const reducedMotion = win.matchMedia(REDUCED_MOTION_QUERY);
        const count = originals.length;
        let allCards = null;   // [복제][원본][복제]. 처음 순환할 때 한 번 만든다
        let looping = false;
        let logical = 0;       // 레일 왼쪽 끝에 놓인 원본 카드 번호(0부터). 폭이 바뀌어도 이 카드를 지킨다
        let pending = null;    // 버튼·키보드로 가는 중인 목표 칸. 연달아 눌러도 이 값에서 이어 센다
        let refreshFrame = 0;
        let settleTimer = 0;
        let drag = null;
        let suppressClick = false;
        let touching = false;
        let touchScroll = false;

        function items() {
            return looping ? allCards : originals;
        }

        function inset() {
            return parseFloat(win.getComputedStyle(grid).paddingLeft) || 0;
        }

        function maxScroll() {
            return Math.max(0, grid.scrollWidth - grid.clientWidth);
        }

        // 칸의 카드 왼쪽 끝이 레일 안쪽 여백에 맞는 scrollLeft
        function positionOf(index) {
            const list = items();
            const card = list[Math.min(list.length - 1, Math.max(0, index))];
            return Math.min(Math.max(0, card.offsetLeft - inset()), maxScroll());
        }

        function nearestIndex() {
            return nearestSnap(items().map((card, index) => positionOf(index)), grid.scrollLeft);
        }

        function firstIndex() {
            return looping ? count : 0;
        }

        function visibleCount() {
            const list = items();
            const pitch = list.length > 1 ? list[1].offsetLeft - list[0].offsetLeft : list[0].offsetWidth;
            const gap = pitch - list[0].offsetWidth;
            return Math.max(1, Math.floor((grid.clientWidth - 2 * inset() + gap) / pitch + 0.01));
        }

        // 원본 카드가 레일 한 화면을 넘을 때만 돈다. 넘지 않으면 넘길 것이 없다.
        function needsLoop() {
            if (count < 2) return false;
            const last = originals[count - 1];
            return last.offsetLeft + last.offsetWidth - originals[0].offsetLeft
                > grid.clientWidth - 2 * inset() + 1;
        }

        function setLooping(on) {
            if (on === looping) return;
            if (on && !allCards) {
                const before = originals.map(cloneCard);
                const after = originals.map(cloneCard);
                before.forEach(clone => grid.insertBefore(clone, originals[0]));
                after.forEach(clone => grid.appendChild(clone));
                allCards = before.concat(originals, after);
            }
            looping = on;
            grid.classList.toggle('is-looping', on);
        }

        // 같은 모습의 한 벌 옆 자리로 즉시 옮긴다. 복제본이 원본과 똑같아 화면은 그대로다.
        function jump(shift) {
            if (!shift || !looping) return;
            const delta = Math.sign(shift) * (allCards[count].offsetLeft - allCards[0].offsetLeft);
            grid.scrollLeft += delta;
            if (drag) drag.left += delta;
        }

        function scrollToLeft(left) {
            grid.scrollTo({ left, behavior: reducedMotion.matches ? 'auto' : 'smooth' });
        }

        function updateNav() {
            const scrollable = isReady() && (looping || maxScroll() > 1);
            rail.classList.toggle('is-scrollable', scrollable);
            [prev, next].forEach(button => {
                if (button) button.hidden = !scrollable;
            });
            // 도는 레일에는 끝이 없다. 돌지 않는 레일만 처음·끝에서 막는다.
            if (prev) prev.disabled = !looping && grid.scrollLeft <= 1;
            if (next) next.disabled = !looping && grid.scrollLeft >= maxScroll() - 1;
        }

        // 수렴이 끝났을 때와 폭이 바뀔 때: 돌지 여부를 다시 정하고, 보던 원본 카드를 왼쪽 끝에 즉시 맞춘다.
        function refresh() {
            refreshFrame = 0;
            if (isReady() && !drag) {
                setLooping(needsLoop());
                pending = null;
                const list = items();
                logical = Math.min(logical, list.length - 1);
                grid.scrollLeft = positionOf(firstIndex() + logical);
            }
            updateNav();
        }

        function requestRefresh() {
            if (!refreshFrame) refreshFrame = win.requestAnimationFrame(refresh);
        }

        function scheduleSettle() {
            if (settleTimer) win.clearTimeout(settleTimer);
            settleTimer = win.setTimeout(() => settle(true), SETTLE_IDLE_MS);
        }

        /*
          레일이 멈춘 뒤: 복제 구간이면 같은 모습의 원본 자리로 옮기고, 스냅을 다시 켠다.
          버튼으로 가는 중인데 scrollend 가 먼저 오면(연달아 눌러 앞 이동이 끊긴 경우) 목표에 닿을 때까지 기다린다.
          스크롤이 한동안 없으면(idle) 목표에 못 닿았어도 지금 자리에서 마무리한다.
         */
        function settle(idle) {
            if (!idle && pending !== null && Math.abs(grid.scrollLeft - positionOf(pending)) > 1) return;
            if (settleTimer) win.clearTimeout(settleTimer);
            settleTimer = 0;
            if (drag || touching || !isReady()) return;
            pending = null;
            touchScroll = false;
            if (looping) jump(loopShift(nearestIndex(), count));
            const index = nearestIndex();
            logical = looping ? logicalIndex(index, count) : index;
            grid.classList.remove('is-free');
            updateNav();
        }

        // 버튼·키보드: 목표 칸까지 부드럽게 간다. 가는 동안은 스냅을 꺼 두어 옮겨 붙이기가 튀지 않게 한다.
        function moveTo(target) {
            pending = target;
            logical = looping ? logicalIndex(target, count) : Math.min(target, originals.length - 1);
            scrollToLeft(positionOf(target));
            scheduleSettle();
        }

        function step(direction) {
            if (!isReady() || !(looping || maxScroll() > 1)) return;
            onUse();
            grid.classList.add('is-free');
            const base = pending !== null ? pending : nearestIndex();
            if (looping) {
                const move = loopStep(base, direction, count);
                jump(move.shift);
                moveTo(move.target);
            } else {
                moveTo(Math.min(originals.length - 1, Math.max(0, base + direction)));
            }
        }

        if (prev) prev.addEventListener('click', () => step(-1));
        if (next) next.addEventListener('click', () => step(1));

        /*
          좌우 화살표: 옆 원본 카드로 초점을 옮긴다(끝에서는 반대편 끝으로). 초점은 늘 원본에만 가므로,
          가려져 있으면 그 원본이 보이는 가장 가까운 자리로 넘긴다.
         */
        grid.addEventListener('keydown', event => {
            if (!isReady() || (event.key !== 'ArrowLeft' && event.key !== 'ArrowRight')) return;
            const card = event.target.closest && event.target.closest('.home-converge-card');
            const index = originals.indexOf(card);
            if (index < 0) return;
            let targetIndex = index + (event.key === 'ArrowRight' ? 1 : -1);
            if (looping) targetIndex = (targetIndex + count) % count;
            const target = originals[targetIndex];
            if (!target) return;
            event.preventDefault();
            onUse();
            const link = target.querySelector('.home-converge-link');
            if (link) link.focus({ preventScroll: true });

            const cardIndex = firstIndex() + targetIndex;
            const start = pending !== null ? pending : nearestIndex();
            const shown = visibleCount();
            let windowStart = start;
            if (cardIndex < start) windowStart = cardIndex;
            else if (cardIndex > start + shown - 1) windowStart = cardIndex - shown + 1;
            if (windowStart === start) return;
            grid.classList.add('is-free');
            moveTo(windowStart);
        });

        // 마우스 드래그. 터치·펜은 브라우저 기본 스와이프(scroll-snap)를 그대로 쓴다.
        function onPointerMove(event) {
            if (!drag) return;
            const distance = event.clientX - drag.x;
            if (!drag.moved) {
                if (!isDrag(distance)) return;
                drag.moved = true;
                grid.classList.add('is-dragging', 'is-free');
                onUse();
            }
            event.preventDefault();
            grid.scrollLeft = drag.left - distance;
            // 손을 따라가는 동안에도 원본 구간을 벗어나면 같은 모습의 자리로 옮겨 끝에 닿지 않게 한다.
            if (looping) jump(loopShift(nearestIndex(), count));
        }

        function endDrag() {
            win.removeEventListener('pointermove', onPointerMove);
            win.removeEventListener('pointerup', endDrag);
            win.removeEventListener('pointercancel', endDrag);
            const finished = drag;
            drag = null;
            if (!finished || !finished.moved) return;

            // 곧이어 오는 click 은 드래그의 끝이라 링크로 보내지 않는다. click 이 안 오면 다음 차례에 푼다.
            suppressClick = true;
            win.setTimeout(() => { suppressClick = false; }, 0);

            // 드래그 중에는 스냅을 꺼 두었다. 가까운 카드 자리로 맞추고, 멈추면(settle) 다시 켠다.
            grid.classList.remove('is-dragging');
            moveTo(nearestIndex());
        }

        grid.addEventListener('pointerdown', event => {
            if (event.pointerType !== 'mouse' || event.button !== 0 || !isReady()
                || !(looping || maxScroll() > 1)) return;
            pending = null;
            drag = { x: event.clientX, left: grid.scrollLeft, moved: false };
            win.addEventListener('pointermove', onPointerMove);
            win.addEventListener('pointerup', endDrag);
            win.addEventListener('pointercancel', endDrag);
        });

        grid.addEventListener('click', event => {
            if (!suppressClick) return;
            suppressClick = false;
            event.preventDefault();
            event.stopPropagation();
        }, true);

        // 손가락이 닿아 있는 동안과 놓은 뒤 관성으로 흐르는 동안에는 옮겨 붙이지 않는다. 멈추면 settle 이 맞춘다.
        grid.addEventListener('touchstart', () => {
            touching = true;
            touchScroll = true;
            pending = null;
            grid.classList.remove('is-free');
        }, { passive: true });
        const endTouch = () => {
            touching = false;
            scheduleSettle();
        };
        grid.addEventListener('touchend', endTouch, { passive: true });
        grid.addEventListener('touchcancel', endTouch, { passive: true });

        grid.addEventListener('scroll', () => {
            if (!isReady()) return;
            scheduleSettle();
            // 트랙패드로 멈추지 않고 멀리 밀어 복제본 끝에 닿기 직전이면, 멈추기 전이라도 같은 모습의 자리로 옮긴다.
            if (looping && !drag && !touchScroll && pending === null) {
                const index = nearestIndex();
                if (index < 1 || index > allCards.length - visibleCount() - 1) jump(loopShift(index, count));
            }
        }, { passive: true });
        if ('onscrollend' in grid) grid.addEventListener('scrollend', () => settle(false));

        // 링크·사진의 기본 끌어 놓기가 드래그를 가로채지 않게 한다.
        grid.addEventListener('dragstart', event => event.preventDefault());
        win.addEventListener('resize', requestRefresh);

        return {
            refresh,
            logicalIndex: () => logical
        };
    }

    function init(doc, win) {
        const grid = doc.querySelector('[data-home-converge]');
        if (!grid || typeof win.matchMedia !== 'function') return;

        const cards = Array.from(grid.querySelectorAll('.home-converge-card'));
        const staticMedia = win.matchMedia(STATIC_QUERY);
        const tabletMedia = win.matchMedia(TABLET_QUERY);
        let frame = 0;
        let lastTime = 0;
        let current = null;   // 화면에 그리고 있는 진행률 (null 이면 다음 프레임에 목표로 바로 맞춘다)
        let rendered = null;
        let starts = null;
        let converged = false; // 한 번 다 모이면 그 뒤로는 다시 흩뜨리지 않는다

        const rail = grid.closest('.home-converge-rail');
        const railControl = rail
            ? initRail(win, rail, grid, cards,
                () => !grid.classList.contains('is-converging'),
                () => { converged = true; })
            : null;

        /*
          transform 은 레이아웃에 영향을 주지 않으므로 offsetLeft 는 늘 최종 자리다. (격자가 기준 요소)
          첫 화면(레일 안)에 온전히 보이는 카드만 모이고, 그 밖의 카드는 수렴하는 동안 숨긴다(is-offstage).
          복제본은 수렴이 끝난 뒤에 생기므로 여기서는 원본만 있다.
         */
        function measureStarts() {
            const gridRect = grid.getBoundingClientRect();
            const visible = [];
            cards.forEach(card => {
                const offstage = card.offsetLeft + card.offsetWidth > grid.clientWidth + 1;
                card.classList.toggle('is-offstage', offstage);
                if (!offstage) visible.push(card);
            });
            const layout = visible.map(card => ({
                left: gridRect.left + card.offsetLeft,
                width: card.offsetWidth
            }));
            const offsets = tabletMedia.matches
                ? tabletStartOffsets(layout, gridRect.left + gridRect.width / 2)
                : fanStartOffsets(layout, doc.documentElement.clientWidth || win.innerWidth);
            return cards.map(card => {
                const index = visible.indexOf(card);
                return index < 0 ? null : offsets[index];
            });
        }

        function targetProgress() {
            if (converged || staticMedia.matches || cards.length === 0) return 1;
            return convergeProgress(grid.getBoundingClientRect().top, cards[0].offsetHeight, win.innerHeight);
        }

        // 수렴이 끝났다: transform 을 걷고 같은 프레임에 레일(순환)을 켠다. 카드 자리는 그대로라 튀지 않는다.
        function settle() {
            grid.classList.remove('is-converging');
            grid.style.removeProperty('--converge-progress');
            cards.forEach(card => {
                card.style.removeProperty('--converge-x');
                card.style.removeProperty('--converge-y');
            });
            if (railControl) railControl.refresh();
        }

        function render(progress) {
            rendered = progress;
            if (progress >= 1 || converged) {
                converged = true;
                settle();
                return;
            }
            if (!starts) starts = measureStarts();
            const eased = easeProgress(progress);
            cards.forEach((card, index) => {
                if (!starts[index]) return;
                const point = curveOffset(starts[index], eased);
                card.style.setProperty('--converge-x', point.x.toFixed(1) + 'px');
                card.style.setProperty('--converge-y', point.y.toFixed(1) + 'px');
            });
            grid.style.setProperty('--converge-progress', eased.toFixed(4));
            if (!grid.classList.contains('is-converging')) {
                grid.classList.add('is-converging');
                if (railControl) railControl.refresh();
            }
        }

        // 목표에 닿을 때까지만 프레임을 이어 가고, 닿으면 멈춰 스크롤이 없을 때는 아무 일도 하지 않는다.
        function tick(now) {
            frame = 0;
            const target = targetProgress();
            current = current === null
                ? target
                : followProgress(current, target, lastTime ? Math.min(now - lastTime, 64) : FRAME_MS);
            lastTime = now;

            if (current !== rendered) render(current);
            if (current !== target) {
                frame = win.requestAnimationFrame(tick);
            } else {
                lastTime = 0;
            }
        }

        function requestUpdate() {
            if (!frame) frame = win.requestAnimationFrame(tick);
        }

        // 폭이 바뀌면 최종 자리와 화면 끝이 달라지므로 시작 위치를 다시 재고, 따라가기 없이 바로 맞춘다.
        function remeasure() {
            if (converged) return;
            starts = null;
            current = null;
            rendered = null;
            requestUpdate();
        }

        function onMediaChange(media) {
            if (typeof media.addEventListener === 'function') {
                media.addEventListener('change', remeasure);
            } else if (typeof media.addListener === 'function') {
                media.addListener(remeasure);
            }
        }

        win.addEventListener('scroll', requestUpdate, { passive: true });
        win.addEventListener('resize', remeasure);
        onMediaChange(staticMedia);
        onMediaChange(tabletMedia);
        requestUpdate();
    }

    const api = {
        convergeProgress,
        easeProgress,
        followProgress,
        fanStartOffsets,
        tabletStartOffsets,
        curveOffset,
        nearestSnap,
        isDrag,
        loopShift,
        loopStep,
        logicalIndex,
        initRail
    };
    root.TravelDiaryHomeConverge = api;
    if (typeof module !== 'undefined' && module.exports) module.exports = api;
    if (typeof document !== 'undefined' && typeof window !== 'undefined') init(document, window);
})(typeof window !== 'undefined' ? window : globalThis);
