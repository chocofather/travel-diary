/* Mobile presentation shared by post/course comments; paging and CRUD stay in their modules. */
window.CommunityCommentMobile = (() => {
    const mobile = window.matchMedia('(max-width: 600px)');

    function prepareImages(group) {
        const images = Array.from(group.querySelectorAll('.comment-image'));
        const thumbnails = images.map(image => {
            const thumbnail = document.createElement('span');
            thumbnail.className = 'content-comment-thumbnail';
            image.replaceWith(thumbnail);
            thumbnail.append(image);
            const overflow = document.createElement('span');
            overflow.className = 'content-comment-image-overflow';
            overflow.setAttribute('aria-hidden', 'true');
            thumbnail.append(overflow);
            return thumbnail;
        });

        function update() {
            // Four 72px thumbnails plus gaps need 306px; replies usually show three.
            const capacity = group.getBoundingClientRect().width >= 306 ? 4 : 3;
            const visible = Math.min(images.length, capacity);
            group.style.setProperty('--comment-thumbnail-count', String(visible));
            thumbnails.forEach((thumbnail, index) => {
                thumbnail.classList.toggle('is-mobile-hidden', index >= visible);
                const extra = index === visible - 1 ? images.length - visible : 0;
                thumbnail.lastElementChild.textContent = extra > 0 ? `+${extra}` : '';
            });
        }
        new ResizeObserver((entries, observer) => {
            if (!group.isConnected) observer.unobserve(group);
            else update();
        }).observe(group);
        update();
    }

    function bindImageNavigation({modal, image, show, close}) {
        const parent = modal.parentNode;
        const nextSibling = modal.nextSibling;
        function syncViewport() {
            if (!parent) return;
            // Escape any content ancestor that establishes a fixed containing block.
            if (mobile.matches) document.body.append(modal);
            else if (modal.parentNode !== parent) parent.insertBefore(modal, nextSibling);
        }
        mobile.addEventListener('change', syncViewport);
        syncViewport();
        image.addEventListener('load', () => {
            if (image.naturalWidth && image.naturalHeight) {
                image.style.setProperty('--comment-image-ratio', String(image.naturalWidth / image.naturalHeight));
            }
        });
        let start = null;
        let suppressClick = false;
        image.addEventListener('pointerdown', event => {
            if (!mobile.matches) return;
            start = {x: event.clientX, y: event.clientY, id: event.pointerId};
            if (event.isTrusted) image.setPointerCapture?.(event.pointerId);
        });
        image.addEventListener('pointercancel', () => { start = null; });
        image.addEventListener('pointerup', event => {
            if (!mobile.matches || !start || start.id !== event.pointerId) return;
            const dx = event.clientX - start.x;
            const dy = event.clientY - start.y;
            start = null;
            if (Math.abs(dx) < 40 || Math.abs(dx) <= Math.abs(dy) * 1.2) return;
            show(dx < 0 ? 1 : -1);
            suppressClick = true;
            window.setTimeout(() => { suppressClick = false; }, 0);
        });

        return {
            handleClick(event) {
                if (!mobile.matches) return false;
                if (event.target.closest('.close-btn')) close();
                else if (event.target === image && !suppressClick) {
                    const bounds = image.getBoundingClientRect();
                    show(event.clientX < bounds.left + bounds.width / 2 ? -1 : 1);
                }
                // Mobile closes only through X (keyboard Escape remains available).
                return true;
            }
        };
    }

    return {prepareImages, bindImageNavigation};
})();
