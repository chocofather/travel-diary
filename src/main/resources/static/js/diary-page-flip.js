/**
 * 다이어리 페이지 넘김 연출.
 * 기존 ?spread=N 이동 구조는 그대로 두고, 링크로 이동하기 전에 종이 한 면이 넘어가는 효과만 보여준다.
 * 실제 페이지 요소(TEXT/PHOTO)는 건드리지 않고 임시로 만든 빈 종이 면에만 transform 을 준다.
 */
document.addEventListener('DOMContentLoaded', () => {
    const spread = document.querySelector('.diary-book-spread');
    const links = Array.from(document.querySelectorAll('.diary-spread-button[data-flip]'));
    if (!spread || links.length === 0) return;

    const reducedMotion = window.matchMedia('(prefers-reduced-motion: reduce)');
    const compactLayout = window.matchMedia('(max-width: 860px)');
    const FLIP_DURATION = 560;
    const SLIDE_DURATION = 440;

    let flipping = false;

    links.forEach((link) => {
        link.addEventListener('click', (event) => {
            // 모션을 줄이는 설정이면 연출 없이 링크 그대로 이동한다.
            if (reducedMotion.matches) return;
            if (flipping) {
                // 넘어가는 동안의 연속 클릭은 무시한다.
                event.preventDefault();
                return;
            }

            event.preventDefault();
            flipping = true;
            startFlip(link.dataset.flip === 'next', () => {
                window.location.href = link.href;
            });
        });
    });

    function startFlip(isNext, done) {
        // 넘어가는 동안에는 편집(드래그/resize/rotate)과 액션이 시작되지 않게 막는다.
        spread.classList.add('is-flipping');

        let paper = null;
        const stateClass = compactLayout.matches
            ? (isNext ? 'is-slide-next' : 'is-slide-previous')
            : (isNext ? 'is-flipping-next' : 'is-flipping-previous');
        spread.classList.add(stateClass);

        if (!compactLayout.matches) {
            paper = document.createElement('div');
            paper.className = 'diary-flip-paper ' + (isNext ? 'is-flip-next' : 'is-flip-previous');
            paper.setAttribute('aria-hidden', 'true');
            // 넘어가는 면의 종이 배경을 그대로 따라간다.
            const source = spread.querySelector(
                isNext ? '.diary-sheet-right' : '.diary-sheet-left');
            const background = Array.from(source ? source.classList : [])
                .find(name => name.startsWith('diary-sheet-bg-'));
            if (background) paper.classList.add(background);
            spread.append(paper);
        }

        window.setTimeout(() => {
            if (paper) paper.remove();
            spread.classList.remove('is-flipping', stateClass);
            done();
        }, paper ? FLIP_DURATION : SLIDE_DURATION);
    }
});
