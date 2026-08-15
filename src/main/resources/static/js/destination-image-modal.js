/**
 * 여행지 상세 이미지 확대 모달.
 * 캐러셀 이미지와 대표 이미지(no-image 케이스) 모두 클릭하면 원본 비율로 크게 보여준다.
 *
 * - 리스너는 로드 즉시 document 에 캡처 단계로 걸고, 모달 요소는 클릭 시점에 찾는다.
 * - 열 때는 항상 showModal() 로 top layer 에 올린다.
 * - 상세 페이지의 이미지 목록을 그대로 써서 이전/다음 이동을 지원한다.
 */
(function () {
    const MODAL_ID = 'destination-image-modal';
    const IMAGE_SELECTOR = '.carousel .slide img, .carousel.no-image img';

    // 현재 모달이 보여주는 이미지 목록과 위치
    let images = [];
    let currentIndex = 0;

    function getModal() {
        return document.getElementById(MODAL_ID);
    }

    function isOpen(modal) {
        return !!modal && modal.classList.contains('is-open');
    }

    function collectImages() {
        return Array.from(document.querySelectorAll(IMAGE_SELECTOR));
    }

    function render(modal) {
        const modalImage = modal.querySelector('.image-modal-img');
        const source = images[currentIndex];
        if (!modalImage || !source) return;

        modalImage.src = source.currentSrc || source.src;
        modalImage.alt = source.alt || '여행지 이미지';
        // 이미지가 한 장뿐이면 좌/우 버튼을 숨긴다.
        modal.classList.toggle('is-single', images.length <= 1);
    }

    /** 순환 이동. 첫 장에서 이전 -> 마지막, 마지막에서 다음 -> 첫 장. */
    function move(step) {
        const modal = getModal();
        if (!modal || images.length <= 1) return;
        currentIndex = (currentIndex + step + images.length) % images.length;
        render(modal);
    }

    /** <dialog> 를 못 쓰는 환경에서만 쓰는 대체 경로. */
    function openWithoutDialogSupport(modal) {
        modal.classList.add('is-fallback');
        modal.setAttribute('open', '');
    }

    function openModal(image) {
        const modal = getModal();
        if (!modal) return;

        images = collectImages();
        currentIndex = Math.max(images.indexOf(image), 0);
        render(modal);

        // showModal() 은 open 상태에서 호출하면 예외가 나고,
        // open 속성만 붙은 dialog 는 top layer 밖(비모달)으로 렌더링돼 화면에 보이지 않는다.
        // 그래서 남아 있는 open 상태를 먼저 정리한 뒤 항상 showModal() 로 연다.
        if (typeof modal.showModal === 'function') {
            try {
                if (modal.open) modal.close();
                modal.classList.remove('is-fallback');
                modal.showModal();
            } catch (error) {
                openWithoutDialogSupport(modal);
            }
        } else {
            openWithoutDialogSupport(modal);
        }
        modal.classList.add('is-open');
        modal.removeAttribute('aria-hidden');
        document.body.style.overflow = 'hidden';
    }

    function closeModal() {
        const modal = getModal();
        if (!modal) return;

        if (typeof modal.close === 'function' && modal.open) {
            modal.close();
        } else {
            modal.removeAttribute('open');
        }
        modal.classList.remove('is-open', 'is-fallback');
        modal.setAttribute('aria-hidden', 'true');
        document.body.style.overflow = '';

        const modalImage = modal.querySelector('.image-modal-img');
        if (modalImage) {
            modalImage.removeAttribute('src');
        }
    }

    // 캡처 단계로 걸어 다른 스크립트가 클릭을 가로채도 확대가 동작하게 한다.
    document.addEventListener('click', (event) => {
        const target = event.target;
        if (!target || typeof target.closest !== 'function') return;

        if (target.closest('.image-modal-close')) {
            event.preventDefault();
            closeModal();
            return;
        }

        // 좌/우 이동. 닫기(이미지·배경 클릭)로 이어지지 않도록 여기서 끊는다.
        const nav = target.closest('.image-modal-nav');
        if (nav) {
            event.preventDefault();
            event.stopPropagation();
            move(nav.classList.contains('prev') ? -1 : 1);
            return;
        }

        // 확대된 이미지를 다시 누르면 닫힌다.
        if (target.closest('.image-modal-img')) {
            event.preventDefault();
            closeModal();
            return;
        }

        // 모달 바깥(백드롭) 클릭
        const modal = getModal();
        if (modal && target === modal) {
            closeModal();
            return;
        }

        const image = target.closest(IMAGE_SELECTOR);
        if (!image) return;
        event.preventDefault();
        openModal(image);
    }, true);

    // 모달이 열려 있을 때만 키보드에 반응한다.
    document.addEventListener('keydown', (event) => {
        const modal = getModal();
        if (!isOpen(modal)) return;

        if (event.key === 'Escape') {
            closeModal();
            return;
        }
        if (event.key === 'ArrowLeft') {
            event.preventDefault();
            move(-1);
            return;
        }
        if (event.key === 'ArrowRight') {
            event.preventDefault();
            move(1);
        }
    });

    // ESC 등으로 dialog 가 스스로 닫힐 때 상태를 맞춘다.
    document.addEventListener('close', (event) => {
        if (event.target && event.target.id === MODAL_ID) {
            event.target.classList.remove('is-open');
            document.body.style.overflow = '';
        }
    }, true);
})();
