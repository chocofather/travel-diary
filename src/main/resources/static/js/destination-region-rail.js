/*
  지역 선택 rail(대륙·국가·도시·국내 하위지역) 공통. 무한 순환하지 않는 일반 가로 스크롤이다.
  - 서버가 그린 지역 목록 한 벌만 쓴다. 항목을 복제하지 않는다.
  - 화살표는 항목 개수가 아니라 실제 넘침(scrollWidth > clientWidth)과 현재 위치로 보이고 숨는다.
  - 크기 변화(ResizeObserver)·글꼴 로딩·목록 교체 때마다 다시 계산한다.
  - 터치/휠 스크롤은 브라우저 기본 동작 그대로 둔다.
*/
(function () {
    const instances = new Map();
    const reducedMotion = window.matchMedia('(prefers-reduced-motion: reduce)');
    // 소수점 폭 반올림 때문에 끝에 닿았는데도 1px 남는 경우를 끝으로 본다.
    const EDGE = 1;

    function cleanDetached() {
        instances.forEach((instance, scroller) => {
            if (!scroller.isConnected) {
                instance.destroy();
                instances.delete(scroller);
            }
        });
    }

    function create(scroller, track, itemSelector, prevButtons, nextButtons) {
        cleanDetached();
        if (!scroller || !track) return null;
        if (instances.has(scroller)) return instances.get(scroller);

        const items = () => [...track.querySelectorAll(itemSelector)];
        const prev = [...(prevButtons || [])];
        const next = [...(nextButtons || [])];
        let frame = 0;

        function maxScroll() {
            return Math.max(0, scroller.scrollWidth - scroller.clientWidth);
        }

        function update() {
            frame = 0;
            const max = maxScroll();
            const overflowing = max > EDGE;
            const position = scroller.scrollLeft;
            prev.forEach(button => { button.hidden = !overflowing || position <= EDGE; });
            next.forEach(button => { button.hidden = !overflowing || position >= max - EDGE; });
            scroller.classList.toggle('is-overflowing', overflowing);
        }

        function scheduleUpdate() {
            if (!frame) frame = requestAnimationFrame(update);
        }

        // 항목의 왼쪽/오른쪽 끝을 scroller 의 스크롤 좌표로 바꾼다.
        function edges(item) {
            const box = scroller.getBoundingClientRect();
            const rect = item.getBoundingClientRect();
            const left = rect.left - box.left + scroller.scrollLeft;
            return {left, right: left + rect.width};
        }

        // CSS 가 scroll-behavior: smooth 라 'auto' 도 부드럽게 움직인다. 바로 옮길 때는 'instant' 를 쓴다.
        function scrollToPosition(left, smooth) {
            const target = Math.min(maxScroll(), Math.max(0, left));
            if (typeof scroller.scrollTo === 'function') {
                scroller.scrollTo({left: target, behavior: smooth && !reducedMotion.matches ? 'smooth' : 'instant'});
            } else {
                scroller.scrollLeft = target;
            }
            scheduleUpdate();
        }

        /*
          다음/이전 묶음으로 이동한다.
          앞으로: 오른쪽에서 잘린 첫 항목이 왼쪽 끝에 오게 한다.
          뒤로: 왼쪽에서 잘린 마지막 항목이 오른쪽 끝에 오게 한다.
          끝에서는 maxScroll 로 막혀 마지막 항목이 온전히 보인다.
        */
        function move(direction) {
            const start = scroller.scrollLeft;
            const end = start + scroller.clientWidth;
            const list = items();
            if (direction > 0) {
                const cut = list.map(edges).find(edge => edge.right > end + EDGE);
                scrollToPosition(cut && cut.left > start + EDGE ? cut.left : start + scroller.clientWidth, true);
            } else {
                const cut = list.map(edges).reverse().find(edge => edge.left < start - EDGE);
                scrollToPosition(cut && cut.right < end - EDGE ? cut.right - scroller.clientWidth : start - scroller.clientWidth, true);
            }
        }

        // 화면 밖에 있으면 가장 가까운 쪽으로만 들인다. 이미 보이면 움직이지 않는다.
        function reveal(item, smooth) {
            if (!item) return;
            const edge = edges(item);
            const start = scroller.scrollLeft;
            const end = start + scroller.clientWidth;
            if (edge.left < start - EDGE) scrollToPosition(edge.left, smooth);
            else if (edge.right > end + EDGE) scrollToPosition(edge.right - scroller.clientWidth, smooth);
            else scheduleUpdate();
        }

        function select(item) {
            items().forEach(node => {
                const selected = node === item;
                node.classList.toggle('selected', selected);
                node.setAttribute('aria-pressed', String(selected));
            });
            reveal(item, true);
            return item;
        }

        items().forEach(item => {
            if (!item.matches('button,a')) {
                item.setAttribute('role', 'button');
                item.tabIndex = 0;
            }
            item.setAttribute('aria-pressed', String(item.classList.contains('selected')));
        });
        prev.forEach(button => button.addEventListener('click', () => move(-1)));
        next.forEach(button => button.addEventListener('click', () => move(1)));
        scroller.addEventListener('scroll', scheduleUpdate, {passive: true});

        // scroller 자체와 각 항목의 크기 변화(창 크기, 이미지·글꼴 로딩)를 모두 본다.
        const observer = typeof ResizeObserver === 'function' ? new ResizeObserver(scheduleUpdate) : null;
        if (observer) {
            observer.observe(scroller);
            items().forEach(item => observer.observe(item));
        }

        const instance = {
            move,
            select,
            update,
            reveal,
            destroy() {
                cancelAnimationFrame(frame);
                frame = 0;
                observer?.disconnect();
            }
        };
        instances.set(scroller, instance);
        // 처음 그릴 때는 선택된 지역이 보이도록 애니메이션 없이 맞춘다.
        reveal(items().find(item => item.classList.contains('selected')), false);
        update();
        return instance;
    }

    function instanceOf(item) {
        const scroller = item?.closest('.region-buttons,.subregion-scroll-container');
        return scroller ? instances.get(scroller) : null;
    }

    window.DestinationRegionRail = {
        create,
        select(item) {
            return instanceOf(item)?.select(item) || item;
        }
    };

    function refresh() {
        cleanDetached();
        instances.forEach(instance => instance.update());
    }
    window.addEventListener('resize', refresh, {passive: true});
    document.fonts?.ready.then(refresh);
    document.fonts?.addEventListener?.('loadingdone', refresh);
    document.addEventListener('keydown', event => {
        const item = event.target.closest?.('.region-btn[role="button"]');
        if (item && (event.key === 'Enter' || event.key === ' ')) {
            event.preventDefault();
            item.click();
        }
    });
}());
