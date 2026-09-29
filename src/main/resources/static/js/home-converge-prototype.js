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

        // transform 은 레이아웃에 영향을 주지 않으므로 offsetLeft 는 늘 최종 자리다. (격자가 기준 요소)
        function measureStarts() {
            const gridRect = grid.getBoundingClientRect();
            const layout = cards.map(card => ({
                left: gridRect.left + card.offsetLeft,
                width: card.offsetWidth
            }));
            return tabletMedia.matches
                ? tabletStartOffsets(layout, gridRect.left + gridRect.width / 2)
                : fanStartOffsets(layout, doc.documentElement.clientWidth || win.innerWidth);
        }

        function targetProgress() {
            if (staticMedia.matches || cards.length === 0) return 1;
            return convergeProgress(grid.getBoundingClientRect().top, cards[0].offsetHeight, win.innerHeight);
        }

        function settle() {
            grid.classList.remove('is-converging');
            grid.style.removeProperty('--converge-progress');
            cards.forEach(card => {
                card.style.removeProperty('--converge-x');
                card.style.removeProperty('--converge-y');
            });
        }

        function render(progress) {
            rendered = progress;
            if (progress >= 1) {
                settle();
                return;
            }
            if (!starts) starts = measureStarts();
            const eased = easeProgress(progress);
            cards.forEach((card, index) => {
                const point = curveOffset(starts[index], eased);
                card.style.setProperty('--converge-x', point.x.toFixed(1) + 'px');
                card.style.setProperty('--converge-y', point.y.toFixed(1) + 'px');
            });
            grid.style.setProperty('--converge-progress', eased.toFixed(4));
            grid.classList.add('is-converging');
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
        curveOffset
    };
    root.TravelDiaryHomeConverge = api;
    if (typeof module !== 'undefined' && module.exports) module.exports = api;
    if (typeof document !== 'undefined' && typeof window !== 'undefined') init(document, window);
})(typeof window !== 'undefined' ? window : globalThis);
