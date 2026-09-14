/*
 * "체험 여행일기를 내 것으로 저장하려고 로그인하러 간다" 는 표시.
 *
 * 인증 앞뒤를 잇기 위한 아주 작은 쪽지다. 체험 내용(제목/본문/사진/photoRef)은 여기에 담지 않는다.
 * 그 값은 계속 localStorage 의 draft 와 IndexedDB 에만 있고, 이 쪽지에는 어느 다이어리였는지와
 * 언제 눌렀는지만 남는다.
 *
 * 돌아올 자리는 고정된 내부 경로 하나뿐이다. 사용자가 준 값이나 draft 안의 값을
 * 돌아갈 주소로 쓰지 않으므로 바깥으로 새는 길이 생기지 않는다.
 *
 * 쪽지와 draft 의 수명은 다르다.
 *   쪽지  : 한 번 쓰고 버리는 값. 시간이 지나면 자동 이동 용도로는 무효다.
 *   draft : 사용자가 만든 내용. 쪽지가 사라져도 그대로 남는다.
 */
(function (global) {
    'use strict';

    const KEY = 'travelDiary.guestDiaryImportIntent.v1';
    /** 인증을 마치고 돌아올 자리. 고정 내부 경로다. */
    const IMPORT_PATH = '/diaries/import';
    /**
     * 쪽지의 뜻. "인증을 마치면 바로 저장해 달라" 는 한 가지뿐이다.
     *
     * 체험 여행일기가 브라우저에 남아 있다는 것과 저장하겠다는 뜻은 다르다.
     * 그래서 자동 저장 여부는 draft 가 아니라 이 쪽지로만 정한다.
     */
    const MODE_AUTO = 'AUTO_AFTER_AUTH';
    /** 쪽지를 자동 이동에 쓸 수 있는 시간. 로그인·가입·이메일 인증을 마칠 만큼만 둔다. */
    const MAX_AGE_MS = 60 * 60 * 1000;

    function storage() {
        try {
            return global.localStorage || null;
        } catch (error) {
            return null;
        }
    }

    /** 남겨 둔 쪽지. 깨졌거나 시간이 지났으면 없는 것으로 본다. */
    function read() {
        const store = storage();
        if (!store) return null;

        let saved;
        try {
            saved = store.getItem(KEY);
        } catch (error) {
            return null;
        }
        if (!saved) return null;

        let parsed;
        try {
            parsed = JSON.parse(saved);
        } catch (error) {
            // 손상된 쪽지는 되살리지 않는다. (draft 는 건드리지 않는다)
            clear();
            return null;
        }
        if (!parsed || typeof parsed.draftId !== 'string') {
            clear();
            return null;
        }

        const requestedAt = Date.parse(parsed.requestedAt);
        if (!Number.isFinite(requestedAt)
            || Date.now() - requestedAt > MAX_AGE_MS) {
            // 시간이 지난 쪽지는 자동 이동에 쓰지 않는다. draft 는 그대로 둔다.
            return null;
        }
        /*
          뜻이 적혀 있지 않은 쪽지는 예전 판이다. 이 쪽지는 처음부터 "저장하러 로그인한다" 는
          한 가지 뜻으로만 쓰였으므로 같은 뜻으로 읽는다.
        */
        return {
            draftId: parsed.draftId,
            requestedAt: parsed.requestedAt,
            mode: parsed.mode === MODE_AUTO || parsed.mode === undefined
                ? MODE_AUTO : String(parsed.mode)
        };
    }

    function remember(draftId) {
        const store = storage();
        if (!store || !draftId) return false;
        try {
            store.setItem(KEY, JSON.stringify({
                draftId: draftId,
                requestedAt: new Date().toISOString(),
                mode: MODE_AUTO
            }));
        } catch (error) {
            return false;
        }
        return true;
    }

    /** 쪽지만 지운다. 체험 내용(draft·사진)은 건드리지 않는다. */
    function clear() {
        const store = storage();
        if (!store) return;
        try {
            store.removeItem(KEY);
        } catch (error) {
            // 지우지 못해도 시간이 지나면 자동 이동에는 쓰이지 않는다.
        }
    }

    global.GuestDiaryImportIntent = {
        KEY: KEY,
        IMPORT_PATH: IMPORT_PATH,
        MAX_AGE_MS: MAX_AGE_MS,
        MODE_AUTO: MODE_AUTO,
        read: read,
        remember: remember,
        clear: clear,

        /**
         * 저장하려고 로그인하러 간다.
         *
         * <p>여기에서 로그인 주소를 직접 만들지 않고 돌아올 자리로 그냥 간다.
         * 그 자리는 로그인해야 열리는 경로라, 서버가 평소처럼 로그인 화면으로 보내면서
         * 원래 가려던 곳을 기억해 둔다. 인증이 끝나면 그 기억이 우리를 여기로 데려온다.
         * (로그인·소셜 로그인·소셜 가입이 모두 같은 성공 처리를 쓰므로 한 번에 이어진다)
         */
        startLogin(draftId) {
            remember(draftId);
            global.location.href = IMPORT_PATH;
        },

        /**
         * 로그인 화면에서 돌아갈 자리를 다시 붙여 준다.
         *
         * <p>회원가입 → 이메일 인증 → 로그인처럼 중간에 세션이 끊기면 서버가 기억해 둔
         * "원래 가려던 곳" 이 사라진다. 그때는 이 브라우저에 남은 쪽지를 보고 다시 채운다.
         * 채워 넣는 값은 고정 내부 경로라 바깥 주소가 들어올 자리가 없다.
         */
        applyToLoginForm() {
            const field = document.querySelector('#loginForm input[name="redirect"]');
            if (!field) return;
            // 서버가 이미 갈 곳을 정해 줬으면 그대로 둔다. (기본값 '/' 일 때만 채운다)
            if (field.value && field.value !== '/') return;
            if (!read()) return;
            field.value = IMPORT_PATH;
        }
    };
})(window);
