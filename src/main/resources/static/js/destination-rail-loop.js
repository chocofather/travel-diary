/* 지역 rail 전용. native touch/wheel scrolling을 유지하고 같은 주기의 위치로 보정한다. */
(function () {
    const instances = new Map();
    const cloneSelector = '[data-rail-clone]';
    const reducedMotion = window.matchMedia('(prefers-reduced-motion: reduce)');

    function cleanDetached() {
        instances.forEach((instance, element) => {
            if (!element.isConnected) {
                instance.destroy();
                instances.delete(element);
            }
        });
    }

    function create(scroller, track, itemSelector, key) {
        cleanDetached();
        if (!scroller) return null;
        if (instances.has(scroller)) return instances.get(scroller);
        const originals = [...track.querySelectorAll(itemSelector)].filter(item => !item.hasAttribute('data-rail-clone'));
        if (!originals.length) return null;
        let start = 0, period = 0, frame = 0, remaining = 0, lastTime = 0, step = 0, settleTimer = 0;
        let rebuilding = false;
        const upperRail = scroller.matches('.region-buttons');
        const wholeItems = window.matchMedia('(min-width: 601px)');
        const complete = () => upperRail && wholeItems.matches;
        const keyOf = item => item.dataset[key];
        const clone = item => {
            const copy = item.cloneNode(true);
            copy.setAttribute('data-rail-clone', '');
            copy.setAttribute('aria-hidden', 'true');
            [copy, ...copy.querySelectorAll('*')].forEach(node => {
                node.removeAttribute('id');
                if (node.matches('button,a,input,select,textarea,[tabindex]')) node.tabIndex = -1;
            });
            return copy;
        };
        function stop() {
            cancelAnimationFrame(frame);
            frame = 0;
            remaining = 0;
        }
        function snap() {
            if (!complete() || rebuilding || frame || !step) return;
            scroller.scrollLeft = start + Math.round((scroller.scrollLeft - start) / step) * step;
            normalize();
        }
        function fitViewport(gap) {
            if (!upperRail) return;
            const shell = scroller.parentElement;
            scroller.classList.remove('region-complete-items');
            shell.style.removeProperty('--region-slot-width');
            shell.style.removeProperty('--region-rail-inset');
            scroller.style.width = '';
            if (!complete()) return;
            // 긴 label도 포함한 동일 slot. icon 크기와 기존 gap은 그대로 사용한다.
            const slot = Math.ceil(Math.max(...originals.map(item => item.getBoundingClientRect().width)));
            shell.style.setProperty('--region-slot-width', `${slot}px`);
            scroller.classList.add('region-complete-items');
            const style = getComputedStyle(shell);
            const available = shell.clientWidth - parseFloat(style.paddingLeft) - parseFloat(style.paddingRight);
            const count = Math.max(1, Math.floor((available + gap) / (slot + gap)));
            const viewport = count * slot + (count - 1) * gap;
            scroller.style.width = `${viewport}px`;
            // 고정 control 영역 사이에서 back item + viewport 전체를 중앙에 둔다.
            shell.style.setProperty('--region-rail-inset', `${Math.max(0, available - viewport) / 2}px`);
        }
        function normalize() {
            if (rebuilding || !period || originals.length < 2) return;
            const position = scroller.scrollLeft;
            if (position < start - 0.5 || position >= start + period) {
                scroller.scrollLeft = start + ((position - start) % period + period) % period;
            }
        }
        function rebuild() {
            const phase = period ? ((scroller.scrollLeft - start) % period + period) % period / period : null;
            rebuilding = true;
            stop();
            track.querySelectorAll(cloneSelector).forEach(item => item.remove());
            scroller.classList.remove('region-loop');
            const gap = parseFloat(getComputedStyle(track).columnGap) || 0;
            fitViewport(gap);
            step = originals[0].getBoundingClientRect().width + gap;
            period = originals.reduce((sum, item) => sum + item.getBoundingClientRect().width + gap, 0);
            if (period && scroller.clientWidth && originals.length > 1) {
                // 한 주기가 화면보다 짧아도 양쪽에 viewport만큼의 안전 여유를 확보한다.
                const copies = Math.max(1, Math.ceil(scroller.clientWidth / period));
                const leading = document.createDocumentFragment();
                const trailing = document.createDocumentFragment();
                for (let i = 0; i < copies; i++) {
                    originals.forEach(item => leading.append(clone(item)));
                    originals.forEach(item => trailing.append(clone(item)));
                }
                track.prepend(leading);
                track.append(trailing);
                scroller.classList.add('region-loop');
                start = originals[0].getBoundingClientRect().left - scroller.getBoundingClientRect().left + scroller.scrollLeft;
                const selected = originals.find(item => item.classList.contains('selected'));
                const offset = selected ? selected.getBoundingClientRect().left - originals[0].getBoundingClientRect().left : 0;
                scroller.scrollLeft = start + (phase === null ? Math.max(0, offset - (scroller.clientWidth - (selected?.getBoundingClientRect().width || 0)) / 2) : phase * period);
            }
            rebuilding = false;
            snap();
        }
        function animate(time) {
            const elapsed = Math.min(40, time - lastTime || 16);
            lastTime = time;
            const delta = Math.abs(remaining) < 0.5 ? remaining : remaining * (1 - Math.exp(-elapsed / 80));
            scroller.scrollLeft += delta;
            normalize();
            remaining -= delta;
            if (Math.abs(remaining) > 0.1) frame = requestAnimationFrame(animate);
            else { frame = 0; remaining = 0; snap(); }
        }
        function move(direction, distance) {
            const gap = parseFloat(getComputedStyle(track).columnGap) || 0;
            if (!frame) snap();
            const requested = distance || (originals[0].getBoundingClientRect().width + gap) * 2;
            const amount = direction * (complete() ? Math.max(1, Math.round(requested / step)) * step : requested);
            if (reducedMotion.matches) {
                scroller.scrollLeft += amount;
                normalize();
                snap();
            } else {
                remaining += amount;
                if (!frame) { lastTime = performance.now(); frame = requestAnimationFrame(animate); }
            }
        }
        function select(item) {
            const original = canonical(item);
            const value = keyOf(item);
            track.querySelectorAll(itemSelector).forEach(node => {
                const selected = keyOf(node) === value;
                node.classList.toggle('selected', selected);
                if (!node.hasAttribute('data-rail-clone')) node.setAttribute('aria-pressed', String(selected));
            });
            rebuild();
            return original;
        }
        function canonical(item) {
            const original = originals.find(node => keyOf(node) === keyOf(item)) || item;
            if (item.hasAttribute('data-rail-clone') && document.activeElement === item) original.focus({preventScroll: true});
            return original;
        }
        originals.forEach(item => {
            if (!item.matches('button,a')) {
                item.setAttribute('role', 'button');
                item.tabIndex = 0;
            }
            item.setAttribute('aria-pressed', String(item.classList.contains('selected')));
        });
        scroller.addEventListener('scroll', () => {
            normalize();
            if (complete()) {
                clearTimeout(settleTimer);
                settleTimer = setTimeout(snap, 160);
            }
        }, {passive: true});
        scroller.addEventListener('scrollend', snap);
        scroller.addEventListener('pointerdown', stop, {passive: true});
        scroller.addEventListener('wheel', stop, {passive: true});
        const observer = new ResizeObserver(rebuild);
        const instance = {move, select, original: canonical, rebuild, destroy() { stop(); clearTimeout(settleTimer); observer.disconnect(); }};
        instances.set(scroller, instance);
        rebuild();
        observer.observe(scroller);
        if (upperRail) observer.observe(scroller.parentElement);
        return instance;
    }
    window.DestinationRailLoop = {create, original(item) {
        const scroller = item.closest('.region-buttons,.subregion-scroll-container');
        return instances.get(scroller)?.original(item) || item;
    }, select(item) {
        const scroller = item.closest('.region-buttons,.subregion-scroll-container');
        return instances.get(scroller)?.select(item) || item;
    }};
    function refresh() {
        cleanDetached();
        instances.forEach(instance => instance.rebuild());
    }
    document.fonts?.ready.then(refresh);
    document.fonts?.addEventListener('loadingdone', refresh);
    document.addEventListener('keydown', event => {
        const item = event.target.closest('.region-btn[role="button"]');
        if (item && !item.hasAttribute('data-rail-clone') && (event.key === 'Enter' || event.key === ' ')) {
            event.preventDefault();
            item.click();
        }
    });
}());
