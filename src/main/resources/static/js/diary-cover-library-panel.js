/**
 * 나의 여행일기(/diaries) 위에 여는 "표지 디자인" 패널. (위: 표지 라이브러리, 아래: 내 보유 디자인)
 *
 * 패널 화면은 따로 만들지 않는다. 기존 주소(/diaries/cover-library, /mine)를
 * 그대로 불러와 그 안의 [data-cover-library-view] 만 패널에 끼워 넣는다.
 * 그래서 정렬·쪽 이동·받기·신고·공개 상태 변경은 서버 동작이 전과 같다.
 * 상세 화면은 없다. 받기와 신고는 라이브러리 목록 카드에서 바로 한다.
 *
 * 패널을 열면 주소를 라이브러리 주소로 한 번만 쌓고, 정렬·탭·쪽 이동은 그 기록을 바꾸기만 한다.
 * 그래서 브라우저 뒤로 가기 한 번이면 패널이 닫힌다. 패널 주소로 새로 고침하거나 예전 표지 디자인
 * 주소로 들어오면 서버가 /diaries?coverLibrary=... (또는 ?coverDesigns=open) 로 보내고, 여기서 다시 연다.
 * 내 보유 디자인의 편집·공유·삭제와 새 디자인 만들기는 기존처럼 해당 페이지로 이동한다.
 */
document.addEventListener('DOMContentLoaded', () => {
    const overlay = document.querySelector('[data-cover-library-overlay]');
    const sheet = overlay?.querySelector('[data-cover-library-sheet]');
    const content = overlay?.querySelector('[data-cover-library-content]');
    if (!overlay || !sheet || !content || !window.fetch || !window.DOMParser) return;

    const LIBRARY_PATH = '/diaries/cover-library';
    const DIARY_LIST_PATH = '/diaries';
    let opener = null;
    let loading = 0;
    // 예전 주소를 거쳐 오며 넘어온 안내 문구. 패널이 처음 그려질 때 한 번만 보여 준다.
    let pendingNotice = overlay.querySelector('[data-cover-library-notice]');
    // 받기·신고 결과를 알리는 작은 안내를 닫는 타이머.
    let toastTimer = null;
    // 표지 라이브러리 영역을 접었는지. 새로 열면 접힌 채로 시작해 내 보유 디자인이 먼저 보이고,
    // 펼치면 패널이 열려 있는 동안 그 상태를 기억한다.
    let libraryCollapsed = true;

    document.addEventListener('click', event => {
        const trigger = event.target.closest('[data-cover-library-open]');
        if (!trigger || event.defaultPrevented || content.contains(trigger)
                || !isPlainClick(event)) return;
        event.preventDefault();
        opener = trigger;
        show();
        load(trigger.href, {push: true});
    });

    // 표지 라이브러리 접기/펼치기. 버튼이나 머리 줄 어디를 눌러도 된다. (탭 링크는 제외)
    content.addEventListener('click', event => {
        const header = event.target.closest('[data-library-section-header]');
        if (!header || !content.contains(header) || event.target.closest('a')) return;
        libraryCollapsed = !libraryCollapsed;
        applyLibraryCollapse();
    });

    content.addEventListener('click', event => {
        const link = event.target.closest('a[href]');
        if (!link || !isPlainClick(event) || link.target) return;

        if (link.matches('[data-cover-library-close]')) {
            event.preventDefault();
            close();
            return;
        }

        const url = new URL(link.href, location.href);
        if (url.origin !== location.origin) return;
        if (url.pathname === DIARY_LIST_PATH && !url.search) {
            event.preventDefault();
            close();
            return;
        }
        // 정렬·쪽·내 공유 디자인 탭. 지금 기록을 바꾸기만 한다.
        if (isLibraryView(url)) {
            event.preventDefault();
            load(url.href, {push: false});
        }
    });

    /*
      받기·신고·공개중지·재공개·삭제는 기존 POST 그대로 보낸다.
      - 받기: 아래 내 보유 디자인 영역만 새로 그린다.
      - 신고: 신고 창을 닫고 결과를 작은 안내로만 알린다. (보던 목록은 그대로 둔다)
      - 내 공유 디자인의 상태 변경: 되돌아온 화면(안내 문구 포함)을 그대로 그린다.
    */
    content.addEventListener('submit', event => {
        const form = event.target;
        if (event.defaultPrevented) return;
        const action = new URL(form.action, location.href);
        if (action.origin !== location.origin || !action.pathname.startsWith(LIBRARY_PATH + '/')
                || form.method.toLowerCase() !== 'post') return;

        event.preventDefault();
        if (form.matches('[data-cover-library-quick-download]')) {
            quickDownload(form, action);
            return;
        }
        if (form.matches('[data-cover-report-form]')) {
            submitReport(form, action);
            return;
        }
        form.querySelectorAll('button[type="submit"]').forEach(button => { button.disabled = true; });
        const token = ++loading;
        sheet.setAttribute('aria-busy', 'true');
        post(form, action)
            .then(result => {
                if (token !== loading) return;
                render(result.view);
                history.replaceState({coverLibrary: true}, '', result.url);
            })
            .catch(error => {
                // 요청은 이미 보냈을 수 있으므로 다시 보내지 않는다.
                // 로그인이 풀렸으면 그 화면으로, 아니면 지금 주소를 다시 열어 결과를 확인하게 한다.
                location.assign(error.redirectTo || location.href);
            });
    });

    document.addEventListener('keydown', event => {
        if (event.key !== 'Escape' || overlay.hidden || event.defaultPrevented) return;
        event.preventDefault();
        close();
    });

    overlay.addEventListener('click', event => {
        if (event.target === overlay) close();
    });

    window.addEventListener('popstate', event => {
        if (event.state?.coverLibrary) {
            show();
            load(location.href, {push: false});
        } else if (!overlay.hidden) {
            hide();
        }
    });

    openFromAddress();

    /*
      목록에서 바로 받기. 기존 다운로드 POST(같은 다운로드 서비스)를 보내고,
      서버가 돌려준 화면에서 "내 보유 디자인" 영역만 가져와 지금 영역과 바꾼다.
      보던 라이브러리 목록(정렬·쪽·스크롤)은 그대로 두고, 작은 안내로 결과만 알린다.
    */
    function quickDownload(form, action) {
        const button = form.querySelector('button[type="submit"]');
        if (!button || button.disabled) return;
        const card = form.closest('[data-library-item]');
        button.disabled = true;
        button.classList.add('is-busy');

        post(form, action)
            .then(result => {
                // 이미 보유 중이라 거부된 경우에도 서버가 본 상태(보유중)로 카드를 맞춘다.
                syncLibraryCard(card, result.view);
                const owned = result.view.querySelector('[data-cover-library-owned]');
                if (!owned?.querySelector('.is-just-added')) {
                    // 받지 못했으면 서버가 목록 위에 남긴 안내 문구를 그대로 보여 준다.
                    throw new Error(libraryNotice(result.view, '.diary-form-error'));
                }
                // 안내는 작은 알림으로 대신하므로 영역 안 문구는 뺀다.
                owned.querySelector('.diary-flash-message')?.remove();
                const fresh = document.importNode(owned, true);
                const current = content.querySelector('[data-cover-library-owned]');
                if (current) {
                    current.replaceWith(fresh);
                    fresh.querySelectorAll('.diary-sticker[data-tape-center]')
                        .forEach(item => window.diaryTape?.render(item));
                    window.diaryBookMenu?.refresh();
                }
                showToast('내 디자인에 추가했습니다.', current
                    ? fresh.querySelector('.is-just-added') : null);
            })
            .catch(error => {
                if (error.redirectTo) {
                    location.assign(error.redirectTo);
                    return;
                }
                showToast(error.message
                    || '표지 디자인을 내 디자인에 추가하지 못했습니다. 잠시 후 다시 시도해 주세요.',
                    null, true);
            })
            .finally(() => {
                // 보유중으로 바뀌었다면 버튼은 이미 빠졌다. 남아 있으면(실패) 다시 누를 수 있게 한다.
                if (!button.isConnected) return;
                button.disabled = false;
                button.classList.remove('is-busy');
            });
    }

    /*
      받기 결과를 라이브러리 카드 하나에 반영한다.
      - 다운로드 수: 서버가 반영한 최종 값(같은 회원이 다시 받아 늘지 않았으면 그대로)만 쓴다.
      - 보유중: 서버가 돌려준 목록에서 이 표지가 보유중이거나, 방금 받기에 성공했으면 받기(+)를 보유중으로 바꾼다.
    */
    function syncLibraryCard(card, view) {
        if (!card) return;
        const itemId = card.dataset.libraryItem;
        const result = view.querySelector('[data-cover-library-download-result]');
        const downloaded = result?.dataset.itemId === itemId;
        if (downloaded) {
            const count = card.querySelector('[data-library-download-count]');
            if (count) count.textContent = '다운로드 ' + result.dataset.downloadCount;
        }
        const serverCard = view.querySelector('[data-library-item="' + itemId + '"]');
        const ownedOnServer = serverCard?.querySelector('.diary-library-owned-badge');
        if (!downloaded && !ownedOnServer) return;

        const form = card.querySelector('[data-cover-library-quick-download]');
        const badge = content.querySelector('template[data-cover-library-owned-badge]');
        if (form && badge) {
            const hadFocus = form.contains(document.activeElement);
            form.replaceWith(badge.content.cloneNode(true));
            // 누른 버튼이 사라지므로 초점은 같은 줄의 신고 아이콘으로 옮긴다.
            if (hadFocus) card.querySelector('[data-cover-report-open]')?.focus();
        }
    }

    /*
      목록 카드 ⋯ 메뉴에서 연 신고. 기존 신고 POST 를 보내고, 서버가 목록 위에 남긴
      안내 문구(접수됨/이미 신고함 등)만 읽어 알린다. 목록은 다시 그리지 않는다.
    */
    function submitReport(form, action) {
        const modal = form.closest('[data-cover-report-modal]');
        const submit = form.querySelector('[data-cover-report-submit]');

        post(form, action)
            .then(result => {
                const error = libraryNotice(result.view, '.diary-form-error');
                if (error) throw new Error(error);
                modal?.diaryCoverReport?.close();
                showToast(libraryNotice(result.view, '.diary-flash-message') || '신고가 접수되었습니다.');
            })
            .catch(error => {
                if (error.redirectTo) {
                    location.assign(error.redirectTo);
                    return;
                }
                // 신고 창은 열어 둔 채 다시 보낼 수 있게 한다.
                if (submit) submit.disabled = false;
                showToast(error.message
                    || '신고를 접수하지 못했습니다. 잠시 후 다시 시도해 주세요.', null, true);
            });
    }

    function post(form, action) {
        return fetch(action.href, {
            method: 'POST',
            credentials: 'same-origin',
            body: new URLSearchParams(new FormData(form))
        }).then(response => readView(response));
    }

    /** 되돌아온 화면의 라이브러리 영역에 서버가 남긴 안내 문구. */
    function libraryNotice(view, selector) {
        return view.querySelector('.diary-library-section.is-library ' + selector)
            ?.textContent.trim() || '';
    }

    /** 패널 아래쪽에 잠깐 떴다 사라지는 안내. target 이 있으면 "보기"로 그 표지까지 내려간다. */
    function showToast(message, target, isError = false) {
        const toast = overlay.querySelector('[data-cover-library-toast]');
        const text = toast?.querySelector('[data-cover-library-toast-text]');
        const action = toast?.querySelector('[data-cover-library-toast-action]');
        if (!toast || !text || !action) return;

        text.textContent = message;
        toast.classList.toggle('is-error', isError);
        action.hidden = !target;
        action.onclick = target
            ? () => {
                target.scrollIntoView({behavior: 'smooth', block: 'center'});
                toast.hidden = true;
            }
            : null;
        toast.hidden = false;
        clearTimeout(toastTimer);
        toastTimer = setTimeout(() => { toast.hidden = true; }, 4000);
    }

    /*
      /diaries?coverDesigns=open 또는 /diaries?coverLibrary=<라이브러리 주소> 로 들어온 경우.
      나의 여행일기 주소는 깨끗하게 되돌려 두고(닫으면 여기로 돌아온다) 그 위에 패널을 연다.
      넘어온 값은 라이브러리 주소일 때만 쓴다.
    */
    function openFromAddress() {
        const params = new URLSearchParams(location.search);
        if (!params.has('coverDesigns') && !params.has('coverLibrary')) return;

        let target = LIBRARY_PATH;
        const requested = params.get('coverLibrary');
        if (requested) {
            const url = new URL(requested, location.href);
            if (url.origin === location.origin && isLibraryView(url)) {
                target = url.pathname + url.search;
                // 정렬·쪽·내 공유 디자인처럼 라이브러리 안의 자리로 들어왔다면 펼친 채로 연다.
                if (target !== LIBRARY_PATH) libraryCollapsed = false;
            }
        }
        params.delete('coverDesigns');
        params.delete('coverLibrary');
        const rest = params.toString();
        history.replaceState(null, '', location.pathname + (rest ? '?' + rest : '') + location.hash);

        show();
        load(target, {push: true});
    }

    function load(href, {push}) {
        const token = ++loading;
        sheet.setAttribute('aria-busy', 'true');
        fetch(href, {credentials: 'same-origin'})
            .then(response => readView(response))
            .then(result => {
                if (token !== loading) return;
                render(result.view);
                const state = {coverLibrary: true};
                if (push) {
                    history.pushState(state, '', result.url);
                } else {
                    history.replaceState(state, '', result.url);
                }
            })
            .catch(error => {
                if (token !== loading) return;
                // 로그인이 풀렸으면 로그인 화면으로 보낸다.
                if (error.redirectTo) {
                    location.assign(error.redirectTo);
                    return;
                }
                // 그 밖에는 패널 주소로 다시 이동하면 같은 자리로 되돌아오므로 패널만 닫는다.
                sheet.removeAttribute('aria-busy');
                close();
            });
    }

    function readView(response) {
        const url = new URL(response.url, location.href);
        if (response.ok && !isLibraryView(url)) {
            const error = new Error('left the library');
            error.redirectTo = url.href;
            return Promise.reject(error);
        }
        if (!response.ok) {
            return Promise.reject(new Error(''));
        }
        return response.text().then(html => {
            const doc = new DOMParser().parseFromString(html, 'text/html');
            const view = doc.querySelector('[data-cover-library-view]');
            if (!view) throw new Error('');
            return {view, url: url.pathname + url.search};
        });
    }

    function render(view) {
        const scrollTop = sheet.scrollTop;
        const sameView = content.firstElementChild?.dataset.coverLibraryView === view.dataset.coverLibraryView;
        content.replaceChildren(document.importNode(view, true));
        showPendingNotice();
        sheet.removeAttribute('aria-busy');
        // 같은 화면 안에서 바뀐 경우(공개 상태 변경 등)는 보던 자리를 지킨다.
        sheet.scrollTop = sameView ? scrollTop : 0;
        content.querySelectorAll('.diary-sticker[data-tape-center]')
            .forEach(item => window.diaryTape?.render(item));
        window.diaryCoverLibraryReport?.init(content);
        // 정렬·탭을 바꾸거나 다시 그려도 접어 둔 상태는 그대로 둔다.
        applyLibraryCollapse();
        // 라이브러리 카드와 내 보유 디자인의 ⋯ 메뉴를 새로 들어온 항목에도 연결한다.
        window.diaryBookMenu?.refresh();
        content.querySelector('[data-cover-library-title]')?.focus({preventScroll: true});
    }

    /** 지금 상태대로 표지 라이브러리 본문(탭·갤러리)을 숨기거나 보이고, 토글 버튼의 접근성 값을 맞춘다. */
    function applyLibraryCollapse() {
        const section = content.querySelector('[data-library-section]');
        const body = section?.querySelector('[data-library-section-body]');
        const toggle = section?.querySelector('[data-library-section-toggle]');
        const tabs = section?.querySelector('[data-library-section-tabs]');
        if (!section || !body || !toggle) return;
        toggle.hidden = false;
        body.hidden = libraryCollapsed;
        // 접으면 탭 대신 공개 디자인 개수만 머리에 남는다. (개수 표시는 CSS 가 접힌 상태에서만 보인다)
        if (tabs) tabs.hidden = libraryCollapsed;
        section.classList.toggle('is-collapsed', libraryCollapsed);
        toggle.setAttribute('aria-expanded', String(!libraryCollapsed));
        toggle.setAttribute('aria-label', libraryCollapsed ? '표지 라이브러리 펼치기' : '표지 라이브러리 접기');
    }

    function showPendingNotice() {
        if (!pendingNotice) return;
        const notice = pendingNotice.content.cloneNode(true);
        pendingNotice.remove();
        pendingNotice = null;
        if (!notice.querySelector('p')) return;
        content.querySelector('.diary-library-head')?.after(notice);
    }

    function show() {
        if (!overlay.hidden) return;
        overlay.hidden = false;
        document.documentElement.classList.add('is-cover-library-open');
    }

    function hide() {
        loading++;
        overlay.hidden = true;
        clearTimeout(toastTimer);
        overlay.querySelector('[data-cover-library-toast]')?.setAttribute('hidden', '');
        document.documentElement.classList.remove('is-cover-library-open');
        content.replaceChildren();
        libraryCollapsed = true;
        sheet.removeAttribute('aria-busy');
        opener?.focus();
    }

    // 패널에서 쌓은 주소 하나를 되돌려 나의 여행일기 주소로 돌아간다. (popstate 에서 닫힌다)
    function close() {
        if (history.state?.coverLibrary) {
            history.back();
        } else {
            hide();
        }
    }

    function isLibraryView(url) {
        return url.pathname === LIBRARY_PATH || url.pathname === LIBRARY_PATH + '/mine';
    }

    function isPlainClick(event) {
        return event.button === 0 && !event.metaKey && !event.ctrlKey
            && !event.shiftKey && !event.altKey;
    }
});
