document.addEventListener('DOMContentLoaded', () => {
    const destinationId = window.location.pathname.split('/').pop();
    const commentForm = document.getElementById('comment-form');
    const commentList = document.getElementById('comment-list');

    const photoModal = document.getElementById('photo-modal');
    const mainPhoto = document.getElementById('main-photo');
    const sidebarThumbnails = document.getElementById('sidebar-thumbnails');
    const closeModalBtn = document.querySelector('.photo-close');
    const prevBtn = document.querySelector('.photo-prev');
    const nextBtn = document.querySelector('.photo-next');

    const guestBox = document.getElementById('guest-comment-box');
    if (guestBox) {
        guestBox.addEventListener('click', () => {
            const currentPath = window.location.pathname;
            window.location.href = '/login?redirect=' + encodeURIComponent(currentPath);
        });
    }

    let photoList = [];
    let currentIndex = 0;

    // ✅ 댓글 등록
    commentForm?.addEventListener('submit', e => {
        e.preventDefault();
        const formData = new FormData(commentForm);
        fetch(`/comments?destinationId=${destinationId}`, {
            method: 'POST',
            body: formData
        })
            .then(res => res.ok ? res.json() : Promise.reject(res))
            .then(() => {
                commentForm.reset();
                loadComments();
                loadPhotoThumbnails();
            })
            .catch(err => console.error('댓글 등록 실패:', err));
    });

        // ✅ 댓글 불러오기
        function loadComments() {
            fetch(`/comments/list?destinationId=${destinationId}`)
                .then(res => res.json())
                .then(data => {
                    commentList.innerHTML = '';
                    const grouped = groupByParent(data);
                    grouped.null?.forEach(parent => renderComment(parent, grouped));
                });
        }
    //


    function renderComment(comment, grouped, depth = 0) {
        const div = document.createElement('div');
        div.className = 'comment-item' + (depth > 0 ? ' reply' : '');
        div.dataset.id = comment.id;

        // ✅ 작성자 정보
        const profileUrl = comment.writer?.profileImage || '/images/default-profile.png';
        const nickname = comment.writer?.nickname || '알 수 없음';

        div.innerHTML = `
            <div class="comment-header">
                 <img src="${profileUrl}" class="comment-profile" alt="프로필 이미지">
                <span class="comment-nickname">${nickname}</span>
                ${comment.updatedAt !== comment.createdAt
            ? '<span class="edited-tag"><img src="/uploads/icons/note.png" alt="수정됨 아이콘"> 수정됨</span>'
            : ''}            
            </div>
            
            <p class="comment-content">${comment.content}</p>
            
            ${comment.imageUrl ? `<img src="${comment.imageUrl}" class="comment-image" alt="댓글 이미지">` : ''}
            
           <div class="comment-actions">
            <button class="like-btn" data-id="${comment.id}">
                <img src="${comment.likedByMe ? '/uploads/icons/like2.png' : '/uploads/icons/like.png'}" alt="좋아요" class="likeicon">
                <span>${comment.likes}</span>
            </button>
        
             ${comment.myComment || comment.admin ? `
                <button class="edit-btn">수정</button>
                <button class="delete-btn">삭제</button>
            ` : ''}
        
                <button class="reply-btn">답글달기</button>
        </div>
        `;
        commentList.appendChild(div);
        grouped[comment.id]?.forEach(child => renderComment(child, grouped, depth + 1));
    }
    ///



    function groupByParent(comments) {
        return comments.reduce((acc, c) => {
            const key = c.parentCommentId ?? null;
            acc[key] = acc[key] || [];
            acc[key].push(c);
            return acc;
        }, {});
    }

    // ✅ 댓글 내부 이벤트 위임
    commentList.addEventListener('click', e => {
        const commentDiv = e.target.closest('.comment-item');
        const commentId = commentDiv?.dataset.id;

        const likeBtn = e.target.closest('.like-btn');
        if (likeBtn) {
            const img = likeBtn.querySelector('img');

            fetch(`/comments/${commentId}/like-toggle`, { method: 'POST' })
                .then(res => res.text())
                .then(result => {
                    if (result === 'liked') {
                        img.src = '/uploads/icons/like2.png'; // 좋아요 상태 이미지
                    } else if (result === 'unliked') {
                        img.src = '/uploads/icons/like.png'; // 좋아요 취소 이미지
                    }

                    const span = likeBtn.querySelector('span');
                    let count = parseInt(span.innerText);
                    span.innerText = result === 'liked' ? count + 1 : count - 1;

                })
                .catch(err => console.error('좋아요 실패:', err));
            return;
        } else if (e.target.classList.contains('delete-btn')) {
            if (confirm('댓글을 삭제하시겠습니까?')) {
                fetch(`/comments/${commentId}`, { method: 'DELETE' })
                    .then(res => res.ok ? (loadComments(), loadPhotoThumbnails()) : alert('삭제 권한 없음'));
            }

        } else if (e.target.classList.contains('edit-btn')) {
            const replyForm = commentDiv.querySelector('.reply-form');
            if (replyForm) replyForm.remove();

            const contentEl = commentDiv.querySelector('.comment-content');
            const original = contentEl.innerText.replace('(수정됨)', '').trim();
            const imageEl = commentDiv.querySelector('.comment-image'); // 기존 이미지

            const editBox = document.createElement('form');
            const inputId = `edit-image-${commentId}`; // 고유 ID
            editBox.className = 'edit-comment-form comment-write-form';

            editBox.innerHTML = `
              <div class="textarea-wrapper">
                <textarea name="content" class="edit-text" required>${original}</textarea>
              </div>
              <div class="comment-controls">
                <input type="file" id="${inputId}" name="image" accept="image/*" hidden>
                <label for="${inputId}" class="image-upload-label">
                  <img src="/uploads/icons/camera.png" alt="사진"> 새 이미지 선택
                </label>
                <button type="submit" class="submit-btn">저장</button>
                <button type="button" class="cancel-edit-btn">취소</button>
              </div>
            `;
            contentEl.replaceWith(editBox);
            commentDiv.classList.add('editing');
        } else if (e.target.classList.contains('save-edit-btn')) {
            const newContent = commentDiv.querySelector('.edit-text').value;
            fetch(`/comments/${commentId}`, {
                method: 'PUT',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ content: newContent })
            }).then(res => res.ok ? loadComments() : alert('수정 실패'));

        } else if (e.target.classList.contains('cancel-edit-btn')) {
            commentDiv.classList.remove('editing');
            loadComments();

        } else if (e.target.classList.contains('reply-btn')) {
            const editForm = commentDiv.querySelector('.edit-comment-form');

            if (editForm) {
                commentDiv.classList.remove('editing');
                editForm.remove();
            }

            if (commentDiv.querySelector('.reply-form')) {
                commentDiv.querySelector('.reply-form').remove();
                return;
            }

            const uniqueId = `nested-image-${commentId}`; // ID 충돌 방지

            const form = document.createElement('div');
            form.className = 'reply-form';
            form.innerHTML = `
               <form class="nested-reply-form comment-write-form" enctype="multipart/form-data">
                <div class="textarea-wrapper">
                    <textarea name="content" placeholder="답글을 입력하세요" required></textarea>
                </div>
                <div class="comment-controls">
                    <input type="file" id="${uniqueId}" name="image" accept="image/*" hidden>
                    <label for="${uniqueId}" class="image-upload-label">
                        <img src="/uploads/icons/camera.png" alt="사진"> 사진
                    </label>
                    <button type="submit" class="submit-btn">등록</button>
                    <button type="button" class="cancel-reply">취소</button>
                </div>
               </form>
            `;
            commentDiv.appendChild(form);

        } else if (e.target.classList.contains('cancel-reply')) {
            e.target.closest('.reply-form')?.remove();
        }
    });

        ///
    commentList.addEventListener('submit', function (e) {
        const form = e.target.closest('.nested-reply-form');
        if (!form) return;

        e.preventDefault();
        const parentCommentId = e.target.closest('.comment-item')?.dataset.id;
        const formData = new FormData(form);
        formData.append('parentCommentId', parentCommentId);

        fetch(`/comments?destinationId=${destinationId}`, {
            method: 'POST',
            body: formData
        }).then(res => res.ok ? res.json() : Promise.reject(res))
            .then(loadComments)
            .catch(err => console.error('대댓글 등록 실패:', err));
    });

    // ✅ 썸네일 로딩
    function loadPhotoThumbnails() {
        fetch(`/comments/images?destinationId=${destinationId}`)
            .then(res => res.json())
            .then(data => {
                const container = document.querySelector('.photo-review-list');
                if (!container) return;
                container.innerHTML = '';
                data.forEach(comment => {
                    const img = document.createElement('img');
                    img.src = comment.imageUrl;
                    img.className = 'photo-thumbnail';
                    img.alt = '사진 후기';

                    // 전체보기 모달 열기 기능 추가
                    img.addEventListener('click', () => {
                        photoList = data.map(c => c.imageUrl);
                        currentIndex = photoList.indexOf(comment.imageUrl);
                        renderSidebar();
                        showMainPhoto(currentIndex);
                        photoModal.style.display = 'flex';
                    });

                    container.appendChild(img);
                });
            });
    }

    // ✅ 전체보기 모달 열기
    document.addEventListener('click', e => {
        if (e.target.classList.contains('view-all')) {
            e.preventDefault();
            fetch(`/comments/images?destinationId=${destinationId}`)
                .then(res => res.json())
                .then(data => {
                    if (!data || data.length === 0) return;
                    photoList = data.map(c => c.imageUrl);
                    currentIndex = 0;
                    renderSidebar();
                    showMainPhoto(currentIndex);
                    photoModal.style.display = 'flex';
                });
        }

        if (e.target.classList.contains('photo-close')) {
            photoModal.style.display = 'none';
        }

        if (e.target.classList.contains('comment-image')) {
            const modal = document.getElementById('image-modal');
            const modalImg = document.getElementById('modal-img');
            modalImg.src = e.target.src;
            modal.style.display = 'flex';
        }

        if (e.target.classList.contains('close-btn')) {
            document.getElementById('image-modal').style.display = 'none';
        }

    });

    function renderSidebar() {
        sidebarThumbnails.innerHTML = '';
        photoList.forEach((url, idx) => {
            const img = document.createElement('img');
            img.src = url;
            img.className = 'thumbnail-item';
            if (idx === currentIndex) img.classList.add('active');
            img.addEventListener('click', () => {
                currentIndex = idx;
                showMainPhoto(idx);
            });
            sidebarThumbnails.appendChild(img);
        });
    }

    function showMainPhoto(index) {
        if (index < 0 || index >= photoList.length) return;
        mainPhoto.src = photoList[index];
        Array.from(sidebarThumbnails.children).forEach((el, i) => {
            el.classList.toggle('active', i === index);
        });
    }

    prevBtn?.addEventListener('click', () => {
        if (currentIndex > 0) {
            currentIndex--;
            showMainPhoto(currentIndex);
        }
    });

    nextBtn?.addEventListener('click', () => {
        if (currentIndex < photoList.length - 1) {
            currentIndex++;
            showMainPhoto(currentIndex);
        }
    });

    // ✅ ESC 키로 닫기
    document.addEventListener('keydown', e => {
        if (e.key === 'Escape') {
            photoModal.style.display = 'none';
        }
    });

    // ✅ 초기 실행
    loadComments();
    loadPhotoThumbnails();
});
