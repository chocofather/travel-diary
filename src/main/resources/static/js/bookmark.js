document.addEventListener('DOMContentLoaded', () => {
    // 공통 북마크 버튼 처리 (상세/리스트/추천/질문/코스 등)
    document.body.addEventListener('click', e => {
        const btn = e.target.closest('.bookmark-icon');
        if (!btn) return;

        e.preventDefault();
        e.stopPropagation();

        // 로그인 체크 (isLoggedIn 전역변수 또는 서버에서 window에 내려주기)
        if (typeof isLoggedIn !== 'undefined' && !isLoggedIn) {
            window.location.href = '/login?redirect=' + encodeURIComponent(location.pathname);
            return;
        }

        // 버튼 data 속성 읽기
        const targetId = btn.dataset.id || btn.dataset.targetId;
        if (!targetId) {
            console.warn('bookmark-icon에 data-id/target-id가 없습니다.', btn);
            return;
        }

        const csrfToken = document.querySelector('meta[name="_csrf"]')?.content;
        const csrfHeader = document.querySelector('meta[name="_csrf_header"]')?.content;
        if (!csrfToken || !csrfHeader) {
            console.error('CSRF 토큰을 확인할 수 없습니다.');
            return;
        }

        // destinationId만 서버로 보냄
        fetch('/bookmarks', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/x-www-form-urlencoded',
                [csrfHeader]: csrfToken
            },
            credentials: 'same-origin',
            body: `destinationId=${encodeURIComponent(targetId)}`
        })
            .then(res => {
                if (res.status === 401) {
                    window.location.href = '/login?redirect=' + encodeURIComponent(location.pathname);
                    throw new Error('Unauthorized');
                }
                return res.text();
            })
            .then(result => {
                const added = result === 'bookmarked';
                toggleBookmarkIcon(added, btn);

                // 카운트 엘리먼트가 있으면 갱신
                updateBookmarkCount(btn, targetId);
            })
            .catch(e => {
                if (e.message !== 'Unauthorized') console.error(e);
            });
    });

    // 진입 시 모든 북마크 아이콘, 카운트 초기화
    document.querySelectorAll('.bookmark-icon').forEach(btn => {
        const targetId = btn.dataset.id || btn.dataset.targetId;
        if (!targetId) {
            console.warn('bookmark-icon에 data-id/target-id가 없습니다.', btn);
            return;
        }

        // 찜 여부
        fetch(`/bookmarks/check?destinationId=${targetId}`)
            .then(res => res.json())
            .then(isBookmarked => {
                toggleBookmarkIcon(isBookmarked, btn);
                btn.dataset.bookmarked = isBookmarked;
            });

        // 카운트
        updateBookmarkCount(btn, targetId);
    });

    // 북마크 카운트 갱신 (리스트/상세/추천 등)
    function updateBookmarkCount(btn, id) {
        // 우선순위: 버튼 내부 → 상세 상단
        const countEl = btn.querySelector('.bookmark-count') || document.getElementById('bookmark-count');
        if (!countEl) return;

        fetch(`/bookmarks/count?destinationId=${id}`)
            .then(res => res.json())
            .then(count => { countEl.textContent = count; });
    }

    // 아이콘 토글 함수 (공통)
    function toggleBookmarkIcon(isBookmarked, buttonEl) {
        const img = buttonEl.querySelector('img');
        if (!img) return;
        img.src = isBookmarked
            ? '/uploads/icons/bookmark2.png'
            : '/uploads/icons/bookmark.png';
        buttonEl.dataset.bookmarked = isBookmarked;
    }
});
