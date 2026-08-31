(function () {
    function initializeGallery() {
        const gallery = document.querySelector('[data-festival-gallery]');
        if (!gallery) return;

        const slides = Array.from(gallery.querySelectorAll('[data-festival-gallery-slide]'));
        if (slides.length === 0) return;

        const modal = document.getElementById('festival-image-modal');
        const modalImage = modal ? modal.querySelector('.festival-image-modal-image') : null;
        const attribution = document.querySelector('[data-festival-gallery-attribution]');
        const source = attribution ? attribution.querySelector('[data-festival-gallery-source]') : null;
        const separator = attribution ? attribution.querySelector('[data-festival-gallery-separator]') : null;
        const license = attribution ? attribution.querySelector('[data-festival-gallery-license]') : null;
        const currentCounter = gallery.querySelector('[data-festival-gallery-current]');
        let currentIndex = Math.max(slides.findIndex(slide => slide.classList.contains('is-active')), 0);

        function isModalOpen() {
            return !!modal && modal.classList.contains('is-open');
        }

        function updateCounter() {
            if (currentCounter) currentCounter.textContent = String(currentIndex + 1);
        }

        function updateAttribution() {
            if (!attribution) return;
            const slide = slides[currentIndex];
            const sourceName = slide.dataset.sourceName || '';
            const licenseLabel = slide.dataset.licenseLabel || '';
            attribution.hidden = !sourceName && !licenseLabel;
            if (source) {
                source.textContent = sourceName;
                source.hidden = !sourceName;
            }
            if (license) {
                license.textContent = licenseLabel;
                license.hidden = !licenseLabel;
            }
            if (separator) separator.hidden = !sourceName || !licenseLabel;
        }

        function updateModalImage() {
            if (!modalImage) return;
            const image = slides[currentIndex].querySelector('img');
            if (!image) return;
            modalImage.src = image.currentSrc || image.src;
            modalImage.alt = image.alt || '축제 이미지';
        }

        function render() {
            slides.forEach((slide, index) => {
                const active = index === currentIndex;
                slide.classList.toggle('is-active', active);
                slide.setAttribute('aria-hidden', String(!active));
            });
            updateCounter();
            updateAttribution();
            if (isModalOpen()) updateModalImage();
        }

        function move(step) {
            if (slides.length <= 1) return;
            currentIndex = (currentIndex + step + slides.length) % slides.length;
            render();
        }

        function openWithoutDialogSupport() {
            modal.classList.add('is-fallback');
            modal.setAttribute('open', '');
        }

        function openModal(index) {
            if (!modal || !modalImage) return;
            currentIndex = index;
            render();
            updateModalImage();
            if (typeof modal.showModal === 'function') {
                try {
                    if (modal.open) modal.close();
                    modal.classList.remove('is-fallback');
                    modal.showModal();
                } catch (error) {
                    openWithoutDialogSupport();
                }
            } else {
                openWithoutDialogSupport();
            }
            modal.classList.add('is-open');
            document.body.style.overflow = 'hidden';
        }

        function closeModal() {
            if (!modal) return;
            if (typeof modal.close === 'function' && modal.open) {
                modal.close();
            } else {
                modal.removeAttribute('open');
            }
            modal.classList.remove('is-open', 'is-fallback');
            document.body.style.overflow = '';
            if (modalImage) modalImage.removeAttribute('src');
        }

        gallery.querySelector('[data-festival-gallery-prev]')
                ?.addEventListener('click', () => move(-1));
        gallery.querySelector('[data-festival-gallery-next]')
                ?.addEventListener('click', () => move(1));
        slides.forEach((slide, index) => {
            slide.querySelector('[data-festival-gallery-open]')
                    ?.addEventListener('click', () => openModal(index));
        });

        modal?.querySelector('[data-festival-modal-prev]')
                ?.addEventListener('click', () => move(-1));
        modal?.querySelector('[data-festival-modal-next]')
                ?.addEventListener('click', () => move(1));
        modal?.querySelector('[data-festival-modal-close]')
                ?.addEventListener('click', closeModal);
        modal?.addEventListener('click', event => {
            if (event.target === modal) closeModal();
        });
        modal?.addEventListener('cancel', event => {
            event.preventDefault();
            closeModal();
        });
        modal?.addEventListener('close', () => {
            modal.classList.remove('is-open', 'is-fallback');
            document.body.style.overflow = '';
        });

        document.addEventListener('keydown', event => {
            if (!isModalOpen()) return;
            if (event.key === 'Escape') {
                event.preventDefault();
                closeModal();
            } else if (event.key === 'ArrowLeft') {
                event.preventDefault();
                move(-1);
            } else if (event.key === 'ArrowRight') {
                event.preventDefault();
                move(1);
            }
        });

        render();
    }

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', initializeGallery);
    } else {
        initializeGallery();
    }
})();
