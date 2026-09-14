/*
 * 다이어리 편집 저장의 단일 통로.
 *
 * 편집기 각 모듈(본문/한 줄 메모/드래그·크기·회전·겹침/스티커·라벨·메모지)은
 * 예전부터 같은 모양의 fetch 를 각자 갖고 있었다. 그 호출을 여기 한 곳으로 모은다.
 *
 * 기본 구현은 예전과 똑같은 서버 POST 다. 회원 편집기의 동작은 달라지지 않는다.
 * 비회원 체험 화면만 install() 로 다른 구현을 끼워 브라우저 저장소에 쓴다.
 * 그래서 "회원이냐 비회원이냐" 는 편집 이벤트마다 갈리지 않고 이 경계 한 곳에서만 갈린다.
 */
(function (global) {
    'use strict';

    /** 끼워 넣은 구현. 비회원 체험 화면에서만 채워진다. */
    let handler = null;

    /** 예전 각 모듈에 있던 것과 같은 서버 POST. (CSRF 토큰은 layout 의 meta 값) */
    async function postToServer(url, fields, options) {
        const defaultMessage = options.defaultMessage || '저장하지 못했습니다';
        const csrfToken = document.querySelector('meta[name="_csrf"]')?.content;
        const csrfHeader = document.querySelector('meta[name="_csrf_header"]')?.content;
        if (!csrfToken || !csrfHeader) {
            throw new Error(`보안 토큰을 확인할 수 없어 ${defaultMessage}`);
        }

        const response = await fetch(url, {
            method: 'POST',
            credentials: 'same-origin',
            headers: {
                'Accept': 'application/json',
                'Content-Type': 'application/x-www-form-urlencoded;charset=UTF-8',
                [csrfHeader]: csrfToken
            },
            body: new URLSearchParams(fields || {})
        });

        if (response.status === 401) {
            const redirect = global.location.pathname + global.location.search;
            global.location.href = `/login?redirect=${encodeURIComponent(redirect)}`;
            throw new Error('로그인이 필요합니다');
        }
        if (!response.ok) {
            let message = defaultMessage;
            if ((response.headers.get('Content-Type') || '').includes('application/json')) {
                const payload = await response.json();
                message = payload.message || message;
            }
            throw new Error(message);
        }

        // 위치/크기/회전은 204(본문 없음)로 답하고, 스티커 생성 등만 JSON 을 돌려준다.
        if (response.status === 204) return null;
        const contentType = response.headers.get('Content-Type') || '';
        return contentType.includes('application/json') ? response.json() : null;
    }

    global.DiarySaveTransport = {
        /**
         * 저장 구현 바꿔 끼우기. 비회원 체험 화면이 화면을 그리기 전에 한 번 부른다.
         * @param {function(string, object, object): Promise} implementation
         */
        install(implementation) {
            handler = typeof implementation === 'function' ? implementation : null;
        },

        /** 지금 서버로 보내는 상태인지. (회원 편집기는 언제나 true) */
        isServerBacked() {
            return handler === null;
        },

        /**
         * @param url 저장 주소. 회원은 실제 endpoint, 비회원은 저장소를 가리키는 이름표다.
         * @param fields 보낼 값
         * @param options defaultMessage: 실패 문구
         * @return 돌려줄 값이 있는 요청이면 그 값, 아니면 null
         */
        post(url, fields, options) {
            const settings = options || {};
            if (handler) {
                // 비회원 체험: 네트워크로 나가지 않고 브라우저 저장소에서 끝난다.
                return Promise.resolve(handler(url, fields || {}, settings));
            }
            return postToServer(url, fields, settings);
        }
    };
})(window);
