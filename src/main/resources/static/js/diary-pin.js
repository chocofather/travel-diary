/**
 * 4자리 PIN 판.
 *
 * 여는 자리는 넷이다 — 잠긴 다이어리 열기 / PIN 걸기 / 바꾸기 / 풀기.
 * 넷 다 같은 판을 쓰고, 무엇을 묻는지(제목·안내)와 다 채웠을 때 할 일만 다르다.
 *
 * 입력은 키보드와 화면 숫자판 두 갈래로 들어오지만 담기는 곳은 하나다.
 * 두 갈래 모두 push(숫자) / pop() 한 쌍만 부르고, 화면은 그 값의 길이만 보고 칸을 칠한다.
 * 그래서 섞어 써도 어긋나지 않는다.
 *
 * 값은 이 스크립트 안에서만 들고 있다가 판을 닫을 때 지운다.
 * DOM 이나 hidden 칸에 남기지 않고, 주소나 세션에도 싣지 않는다.
 * (맞는지 아닌지는 언제나 서버가 정한다 — 화면은 묻고 결과만 받는다)
 */
document.addEventListener('DOMContentLoaded', () => {
    const backdrop = document.getElementById('diary-pin-backdrop');
    if (!backdrop) return;

    const modal = backdrop.querySelector('[data-pin-modal]');
    const title = backdrop.querySelector('[data-pin-title]');
    const guide = backdrop.querySelector('[data-pin-guide]');
    const slots = Array.from(backdrop.querySelectorAll('.diary-pin-slot'));
    const error = backdrop.querySelector('[data-pin-error]');
    const submitButton = backdrop.querySelector('[data-pin-submit]');
    const pad = backdrop.querySelector('[data-pin-pad]');

    const LENGTH = 4;
    /** 지금 입력 중인 숫자. 판을 닫으면 지운다. */
    let digits = '';
    /** 지금 단계에서 다 채웠을 때 할 일 */
    let onComplete = null;
    /** 판을 닫은 뒤 되돌아갈 자리 (열기 전에 눌렀던 버튼) */
    let opener = null;
    let busy = false;

    backdrop.querySelectorAll('[data-pin-key]').forEach((key) => {
        key.addEventListener('click', () => push(key.dataset.pinKey));
    });
    backdrop.querySelector('[data-pin-backspace]')
        ?.addEventListener('click', () => pop());
    submitButton?.addEventListener('click', () => complete());
    backdrop.querySelector('[data-pin-cancel]')?.addEventListener('click', () => close());

    // 바깥을 눌러도 닫는다. (다이어리의 다른 판들과 같은 규칙)
    backdrop.addEventListener('mousedown', (event) => {
        if (event.target === backdrop) close();
    });

    document.addEventListener('keydown', (event) => {
        if (backdrop.hidden) return;

        if (event.key === 'Escape') {
            close();
            return;
        }
        if (event.key === 'Enter') {
            event.preventDefault();
            complete();
            return;
        }
        if (event.key === 'Backspace') {
            event.preventDefault();
            pop();
            return;
        }
        // 키보드로 들어온 숫자도 숫자판과 같은 길로 담는다.
        if (/^[0-9]$/.test(event.key)) {
            event.preventDefault();
            push(event.key);
        }
    });

    // 판이 열려 있는 동안에는 초점이 바깥으로 새지 않게 한다.
    backdrop.addEventListener('focusout', (event) => {
        if (backdrop.hidden) return;
        if (!modal.contains(event.relatedTarget)) {
            window.setTimeout(() => modal.focus(), 0);
        }
    });

    function push(digit) {
        if (busy || digits.length >= LENGTH) return;
        digits += digit;
        paint();
        // 네 자리를 채우면 바로 확인한다. (확인 버튼을 또 누르지 않아도 된다)
        if (digits.length === LENGTH) complete();
    }

    function pop() {
        if (busy || !digits) return;
        digits = digits.slice(0, -1);
        paint();
    }

    /** 채워진 칸만 칠한다. 숫자는 어디에도 그리지 않는다. */
    function paint() {
        slots.forEach((slot, index) => {
            slot.classList.toggle('is-filled', index < digits.length);
        });
    }

    function reset() {
        digits = '';
        paint();
        showError('');
        setBusy(false);
    }

    function showError(message) {
        if (!error) return;
        error.textContent = message || '';
        error.hidden = !message;
    }

    /** 서버가 잠시 쉬어 가라고 하면 숫자판을 잠깐 닫아 둔다. */
    function setBusy(value) {
        busy = value;
        backdrop.querySelectorAll('.diary-pin-key').forEach((key) => {
            key.disabled = value;
        });
    }

    async function complete() {
        if (busy || digits.length !== LENGTH || !onComplete) return;
        const entered = digits;
        // 다음 단계로 넘어가든 다시 묻든 칸은 늘 비우고 시작한다.
        digits = '';
        paint();

        setBusy(true);
        try {
            await onComplete(entered);
        } finally {
            if (!backdrop.hidden) setBusy(false);
        }
    }

    /**
     * 한 단계를 연다. (묻는 말과 다 채웠을 때 할 일만 갈아 끼운다)
     * step.onComplete 는 다음 단계를 다시 열거나, 서버에 물어보고 판을 닫는다.
     */
    function open(step) {
        title.textContent = step.title;
        guide.textContent = step.guide;
        onComplete = step.onComplete;
        opener = step.opener || opener;
        digits = '';
        paint();
        showError(step.error || '');
        setBusy(false);

        if (backdrop.hidden) {
            backdrop.hidden = false;
            document.body.classList.add('is-pin-open');
        }
        modal.focus();
    }

    function close() {
        // 남은 숫자는 여기서 지운다. 닫힌 판에 값이 남지 않는다.
        digits = '';
        onComplete = null;
        paint();
        showError('');
        setBusy(false);
        backdrop.hidden = true;
        document.body.classList.remove('is-pin-open');
        if (opener) {
            opener.focus();
            opener = null;
        }
    }

    /** CSRF 토큰은 layout 의 meta 값을 그대로 쓴다. (다른 다이어리 요청과 같은 방식) */
    async function send(url, body) {
        const csrfToken = document.querySelector('meta[name="_csrf"]')?.content;
        const csrfHeader = document.querySelector('meta[name="_csrf_header"]')?.content;
        if (!csrfToken || !csrfHeader) {
            return {ok: false, message: '보안 토큰을 확인할 수 없어 처리하지 못했습니다.'};
        }

        const response = await fetch(url, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/x-www-form-urlencoded; charset=UTF-8',
                'X-Requested-With': 'XMLHttpRequest',
                [csrfHeader]: csrfToken
            },
            body: new URLSearchParams(body)
        });

        if (response.status === 401) {
            window.location.href = '/login';
            return {ok: false, message: '로그인이 필요합니다.'};
        }
        let payload = {};
        if ((response.headers.get('Content-Type') || '').includes('application/json')) {
            payload = await response.json();
        }
        return {
            ok: response.ok,
            status: response.status,
            payload,
            message: payload.message || 'PIN을 처리하지 못했습니다.'
        };
    }

    /*
      바깥에서 부르는 길.
      화면(책장 / 수정 화면)은 어떤 일을 하고 싶은지만 말하고,
      묻는 절차와 입력 처리는 모두 이 안에서 끝난다.
    */
    window.diaryPin = {
        /** 잠긴 다이어리 열기. 맞으면 원래 가려던 자리로 이어서 간다. */
        unlock(diaryId, target, opener) {
            open({
                title: '이 여행일기는 잠겨 있어요',
                guide: '4자리 PIN을 입력해 주세요.',
                opener,
                onComplete: async (pin) => {
                    const result = await send(`/diaries/${diaryId}/pin/unlock`, {pin});
                    if (result.ok && result.payload.unlocked) {
                        window.location.href = target || `/diaries/${diaryId}`;
                        return;
                    }
                    if (result.status === 429) {
                        // 서버가 정한 제한이다. 화면은 안내만 하고 잠시 숫자판을 닫아 둔다.
                        showError(result.message);
                        setBusy(true);
                        return;
                    }
                    showError(result.message || 'PIN 번호가 올바르지 않습니다.');
                }
            });
        },

        /** PIN 걸기. 잘못 걸지 않도록 두 번 물어본다. */
        set(diaryId, onDone, opener) {
            open({
                title: 'PIN 잠금 설정',
                guide: '사용할 4자리 PIN을 입력해 주세요.',
                opener,
                onComplete: async (first) => confirmAndSet(diaryId, first, onDone)
            });
        },

        /** PIN 바꾸기. 지금 PIN → 새 PIN → 새 PIN 확인 순서다. */
        change(diaryId, onDone, opener) {
            open({
                title: 'PIN 변경',
                guide: '현재 PIN을 입력해 주세요.',
                opener,
                onComplete: async (currentPin) => open({
                    title: 'PIN 변경',
                    guide: '새 4자리 PIN을 입력해 주세요.',
                    onComplete: async (newPin) => open({
                        title: 'PIN 변경',
                        guide: '새 PIN을 한 번 더 입력해 주세요.',
                        onComplete: async (again) => {
                            if (newPin !== again) {
                                showError('새 PIN 번호가 일치하지 않습니다.');
                                return;
                            }
                            const result = await send(`/diaries/${diaryId}/pin/change`,
                                {currentPin, newPin});
                            if (!result.ok) {
                                showError(result.message);
                                return;
                            }
                            close();
                            if (onDone) onDone('PIN이 변경되었습니다.');
                        }
                    })
                })
            });
        },

        /** PIN 잠금 자체를 없앤다. (한 번 여는 것과 다르다) */
        remove(diaryId, onDone, opener) {
            open({
                title: 'PIN 잠금 해제',
                guide: '잠금을 해제하려면 현재 PIN을 입력해 주세요.',
                opener,
                onComplete: async (currentPin) => {
                    const result = await send(`/diaries/${diaryId}/pin/remove`, {currentPin});
                    if (!result.ok) {
                        showError(result.message);
                        return;
                    }
                    close();
                    if (onDone) onDone('PIN 잠금이 해제되었습니다.');
                }
            });
        }
    };

    /** 걸기의 두 번째 단계. 두 번 넣은 값이 같을 때만 서버로 보낸다. */
    function confirmAndSet(diaryId, first, onDone) {
        open({
            title: 'PIN 잠금 설정',
            guide: 'PIN을 한 번 더 입력해 주세요.',
            onComplete: async (second) => {
                if (first !== second) {
                    showError('PIN 번호가 일치하지 않습니다.');
                    return;
                }
                const result = await send(`/diaries/${diaryId}/pin`, {newPin: first});
                if (!result.ok) {
                    showError(result.message);
                    return;
                }
                close();
                if (onDone) onDone('PIN 잠금이 설정되었습니다.');
            }
        });
    }
});
