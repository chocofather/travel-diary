/**
 * 라벨 / 떡메모지에 글쓰기.
 *
 * 붙인 직후와 두 번 눌렀을 때만 고칠 수 있고, 그때만 contenteditable 이 붙는다.
 * 평소에는 읽기 화면과 똑같이 글자만 놓여 있다.
 *
 * 라벨은 한 줄이라 Enter 로 끝내고, 떡메모지는 여러 줄이라 Enter 로 줄을 바꾸고
 * Ctrl/Cmd+Enter 로 끝낸다. Esc 는 쓰던 것을 버리고 마지막으로 저장된 글로 되돌린다.
 * 밖을 누르면(blur) 그대로 저장한다.
 *
 * 저장되는 것은 글자뿐이다. 붙여넣기도 글자만 들어온다(HTML 은 들어오지 않는다).
 * 저장에 실패하면 마지막으로 서버가 알려 준 글로 되돌린다 — 화면만 앞서가지 않게 한다.
 */
document.addEventListener('DOMContentLoaded', () => {
    // 읽기 모드에서는 고칠 일이 없다. 아예 붙이지 않는다.
    if (!document.querySelector('.diary-detail-page.is-edit-mode')) return;

    /** 라벨은 한 줄, 떡메모지는 여러 줄. 어느 쪽인지는 저장된 디자인 code 로 안다. */
    const isLabel = (item) => (item.dataset.noteStyle || '').endsWith('_LABEL');

    /** 지금 고쳐 쓰고 있는 라벨. 한 번에 하나만 연다. */
    let editing = null;

    /*
      두 번 누르기는 아는 사람만 쓰는 지름길이다.
      고른 라벨 아래 줄의 "글 편집" 이 눈에 보이는 길이고, 손가락으로도 이쪽이 확실하다.
    */
    document.addEventListener('click', (event) => {
        const action = event.target.closest('[data-note-edit]');
        if (!action) return;
        const item = action.closest('.diary-note[data-text-url]');
        if (item) begin(item);
    });

    document.addEventListener('dblclick', (event) => {
        const item = event.target.closest('.diary-note[data-text-url]');
        if (item) begin(item);
    });

    /**
     * 고쳐 쓰기 시작.
     * 이미 열려 있던 다른 라벨은 먼저 저장하고 닫는다.
     */
    function begin(item) {
        if (editing === item) return;
        if (editing) commit(editing);

        const text = item.querySelector('.diary-note-text');
        if (!text) return;

        editing = item;
        // 마지막으로 서버가 알고 있는 글. 되돌릴 때와 저장이 필요한지 볼 때 쓴다.
        item.dataset.savedText = text.textContent;
        item.classList.add('is-editing');
        /*
          plaintext-only 는 붙여넣기까지 글자만 들어오게 한다.
          받아 주지 않는 브라우저에서는 true 로 두고 아래 paste 에서 직접 막는다.
        */
        text.setAttribute('contenteditable', 'plaintext-only');
        if (text.contentEditable !== 'plaintext-only') {
            text.setAttribute('contenteditable', 'true');
        }
        text.focus();
        caretToEnd(text);
    }

    /** 글 끝에 커서를 둔다. 고쳐 쓰기는 대개 뒤에 이어 적는 일이다. */
    function caretToEnd(text) {
        const range = document.createRange();
        range.selectNodeContents(text);
        range.collapse(false);
        const selection = window.getSelection();
        selection.removeAllRanges();
        selection.addRange(range);
    }

    /**
     * 고쳐 쓰기를 끝낸다. 바뀐 것이 있을 때만 저장한다.
     *
     * <p>보낼 글을 정하자마자 "마지막으로 아는 글" 을 그 값으로 옮겨 둔다.
     * 편집을 닫으면 focusout 이 뒤따라오는데, 그때 다시 들어와도 바뀐 것이 없어
     * 같은 글을 두 번 보내지 않는다. (Enter 한 번에 요청도 한 번이다)
     */
    function commit(item) {
        const text = item.querySelector('.diary-note-text');
        if (!text) return;

        const next = text.textContent;
        const previous = item.dataset.savedText || '';
        close(item, text);
        if (next === previous) return;

        item.dataset.savedText = next;
        save(item, text, next, previous);
    }

    /** 쓰던 것을 버리고 마지막으로 저장된 글로 되돌린다. */
    function cancel(item) {
        const text = item.querySelector('.diary-note-text');
        if (!text) return;
        text.textContent = item.dataset.savedText || '';
        close(item, text);
    }

    /**
     * 편집 상태를 먼저 내려놓고 나서 contenteditable 을 뗀다.
     *
     * <p>순서가 중요하다. 편집 중이던 글자가 편집 대상에서 풀리는 순간 브라우저는
     * focusout 을 그 자리에서 바로 띄운다. 그때까지 editing 이 남아 있으면
     * 저장이 한 번 더 시작되고, 두 요청이 엇갈리면 화면이 빈 글로 덮인다.
     */
    function close(item, text) {
        if (editing === item) editing = null;
        item.classList.remove('is-editing');
        text.removeAttribute('contenteditable');
    }

    document.addEventListener('keydown', (event) => {
        const item = editing;
        if (!item || !item.contains(event.target)) return;

        /*
          한글을 조합하는 동안의 Enter 는 글자를 확정하는 Enter 다.
          여기서 저장으로 받으면 "제주" 를 치다가 창이 닫힌다.
        */
        if (event.isComposing || event.keyCode === 229) return;

        if (event.key === 'Escape') {
            event.preventDefault();
            cancel(item);
            return;
        }
        if (event.key !== 'Enter') return;

        if (isLabel(item)) {
            // 라벨은 한 줄이다. 줄을 바꾸는 대신 여기서 끝낸다.
            event.preventDefault();
            commit(item);
            return;
        }
        // 떡메모지는 Enter 로 줄을 바꾸고, Ctrl/Cmd 를 함께 눌렀을 때만 끝낸다.
        if (event.ctrlKey || event.metaKey) {
            event.preventDefault();
            commit(item);
        }
    });

    /*
      밖을 누르면 그대로 저장한다.

      글자 칸이 focus 를 잃었을 때만 본다. 같은 라벨 안의 버튼(글 편집)을 눌러
      focus 가 옮겨 다니는 것까지 끝내는 것으로 보면, 방금 연 편집이 곧바로 닫힌다.
    */
    document.addEventListener('focusout', (event) => {
        const item = editing;
        if (!item || event.target !== item.querySelector('.diary-note-text')) return;
        commit(item);
    });

    /*
      붙여넣기는 글자만 받는다.
      plaintext-only 를 아는 브라우저에서는 이미 글자만 들어오지만,
      그렇지 않은 곳에서는 여기서 서식과 태그를 걷어 낸다.
    */
    document.addEventListener('paste', (event) => {
        const item = editing;
        if (!item || !item.contains(event.target)) return;

        event.preventDefault();
        const plain = (event.clipboardData || window.clipboardData).getData('text/plain');
        // 라벨은 한 줄이라 여러 줄을 붙여 넣어도 한 줄로 이어 붙인다. (서버도 같은 규칙)
        const text = isLabel(item) ? plain.replace(/[\r\n]+/g, ' ') : plain;
        document.execCommand('insertText', false, text);
    });

    /**
     * 저장. 서버가 다듬어 돌려준 글을 화면에도 그대로 반영한다.
     * 실패하면 이번에 쓴 것을 버리고 바로 앞의 글로 되돌린다.
     */
    async function save(item, text, next, previous) {
        const url = item.dataset.textUrl;
        const csrfToken = document.querySelector('meta[name="_csrf"]')?.content;
        const csrfHeader = document.querySelector('meta[name="_csrf_header"]')?.content;
        if (!url || !csrfToken || !csrfHeader) {
            restore(item, text, previous);
            return;
        }

        try {
            const response = await fetch(url, {
                method: 'POST',
                credentials: 'same-origin',
                headers: {
                    'Accept': 'application/json',
                    'Content-Type': 'application/x-www-form-urlencoded;charset=UTF-8',
                    [csrfHeader]: csrfToken
                },
                body: new URLSearchParams({text: next})
            });
            if (!response.ok) throw new Error('저장하지 못했습니다.');

            const payload = await response.json();
            // 길이를 넘겨 다듬였거나 줄이 합쳐졌어도 새로고침 전후가 같아진다.
            text.textContent = payload.textContent;
            item.dataset.savedText = payload.textContent;
        } catch (error) {
            restore(item, text, previous);
        }
    }

    /** 저장하지 못했다. 화면만 앞서가지 않게 서버가 아는 마지막 글로 되돌린다. */
    function restore(item, text, previous) {
        const last = previous || '';
        text.textContent = last;
        item.dataset.savedText = last;
    }

    /*
      붙인 직후 바로 쓸 수 있게 열어 둔다.
      (라벨/메모지 고르는 자리가 새 요소를 캔버스에 올린 뒤 이 함수를 부른다)
    */
    window.diaryNoteText = {
        begin
    };
});
