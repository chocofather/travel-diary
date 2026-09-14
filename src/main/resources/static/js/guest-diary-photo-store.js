/*
 * 비회원 체험 사진 보관소.
 *
 * 사진 원본(Blob)은 여기(IndexedDB)에만 둔다. 서버로 올리지 않고, localStorage 에도 넣지 않는다.
 * 체험 다이어리(GuestDiaryDraft)에는 이 보관소를 가리키는 photoRef 와 자리/크기 같은
 * 메타데이터만 남는다. 그래야 localStorage 가 사진 용량으로 터지지 않는다.
 *
 * photoRef 는 브라우저에서만 통하는 임시 UUID 다. 서버의 이미지 id 가 아니며,
 * 로그인 후 옮길 때는 서버가 Blob 을 다시 받아 자기 경로를 새로 발급해야 한다.
 *
 * 페이지 사진과 표지 사진이 이 한 보관소를 함께 쓴다. (scope 로만 갈린다)
 */
(function (global) {
    'use strict';

    const DB_NAME = 'travelDiaryGuest';
    const DB_VERSION = 1;
    const STORE = 'photos';
    /** 체험 다이어리 한 권의 사진을 한 번에 지우기 위한 색인. */
    const DRAFT_INDEX = 'byDraft';

    /** 호출한 쪽이 안내 문구를 고를 수 있도록 실패 사유를 문자열로 돌려준다. */
    const REASON = {
        UNAVAILABLE: 'UNAVAILABLE',
        QUOTA_EXCEEDED: 'QUOTA_EXCEEDED',
        FAILED: 'FAILED',
        NOT_FOUND: 'NOT_FOUND'
    };

    let opening = null;

    function newRef() {
        const random = global.crypto;
        if (random && typeof random.randomUUID === 'function') {
            return 'gph_' + random.randomUUID();
        }
        if (random && typeof random.getRandomValues === 'function') {
            const bytes = random.getRandomValues(new Uint8Array(16));
            return 'gph_' + Array.from(bytes, (b) => b.toString(16).padStart(2, '0')).join('');
        }
        return 'gph_' + Date.now().toString(16) + Math.random().toString(16).slice(2);
    }

    /** 용량 초과는 따로 알려 준다. 다른 실패와 안내 문구가 달라야 하기 때문이다. */
    function reasonOf(error) {
        return error && error.name === 'QuotaExceededError'
            ? REASON.QUOTA_EXCEEDED : REASON.FAILED;
    }

    /**
     * 보관소 열기. 시크릿 모드처럼 IndexedDB 를 쓸 수 없는 환경에서는 예외를 밖으로 던지지 않고
     * 비어 있는 값을 준다. 사진만 못 쓰고 나머지 체험은 그대로 이어진다.
     */
    function open() {
        if (opening) return opening;
        opening = new Promise((resolve) => {
            let request;
            try {
                request = global.indexedDB.open(DB_NAME, DB_VERSION);
            } catch (error) {
                resolve(null);
                return;
            }
            if (!request) {
                resolve(null);
                return;
            }

            request.onupgradeneeded = () => {
                const db = request.result;
                if (!db.objectStoreNames.contains(STORE)) {
                    const store = db.createObjectStore(STORE, {keyPath: 'photoRef'});
                    // 체험 다이어리를 버릴 때 그 한 권의 사진만 골라 지우기 위한 색인이다.
                    store.createIndex(DRAFT_INDEX, 'draftId', {unique: false});
                }
            };
            request.onsuccess = () => resolve(request.result);
            request.onerror = () => resolve(null);
            request.onblocked = () => resolve(null);
        });
        return opening;
    }

    /** 트랜잭션 하나를 열어 실행한다. 실패는 예외가 아니라 사유로 돌려준다. */
    async function run(mode, work) {
        const db = await open();
        if (!db) {
            return {ok: false, reason: REASON.UNAVAILABLE};
        }
        return new Promise((resolve) => {
            let transaction;
            try {
                transaction = db.transaction(STORE, mode);
            } catch (error) {
                resolve({ok: false, reason: reasonOf(error)});
                return;
            }

            let value;
            transaction.oncomplete = () => resolve({ok: true, value: value});
            transaction.onerror = () => resolve({ok: false, reason: reasonOf(transaction.error)});
            transaction.onabort = () => resolve({ok: false, reason: reasonOf(transaction.error)});

            try {
                work(transaction.objectStore(STORE), (result) => {
                    value = result;
                });
            } catch (error) {
                try {
                    transaction.abort();
                } catch (ignored) {
                    // 이미 끝난 트랜잭션이면 그대로 둔다. 위 onerror/onabort 가 결과를 준다.
                }
                resolve({ok: false, reason: reasonOf(error)});
            }
        });
    }

    /**
     * 사진 한 장 보관.
     *
     * @param photo {draftId, scope, pageId, file, width, height}
     * @return {ok:true, photoRef} 또는 {ok:false, reason}
     */
    async function savePhoto(photo) {
        const photoRef = newRef();
        const record = {
            photoRef: photoRef,
            draftId: photo.draftId,
            // 'PAGE' 인지 'COVER' 인지. 지울 때 훑는 범위를 좁히는 데 쓴다.
            scope: photo.scope,
            pageId: photo.pageId || null,
            fileName: photo.file.name || '',
            mimeType: photo.file.type || '',
            size: photo.file.size || 0,
            width: photo.width || 0,
            height: photo.height || 0,
            // 원본은 Blob 그대로 둔다. base64 로 바꾸지 않는다.
            blob: photo.file,
            createdAt: new Date().toISOString()
        };

        const result = await run('readwrite', (store) => {
            store.add(record);
        });
        return result.ok ? {ok: true, photoRef: photoRef} : result;
    }

    /** 보관해 둔 사진 한 장. 없으면 NOT_FOUND 로 알린다. */
    async function getPhoto(photoRef) {
        if (!photoRef) {
            return {ok: false, reason: REASON.NOT_FOUND};
        }
        const result = await run('readonly', (store, done) => {
            const request = store.get(photoRef);
            request.onsuccess = () => done(request.result || null);
        });
        if (!result.ok) return result;
        return result.value
            ? {ok: true, photo: result.value}
            : {ok: false, reason: REASON.NOT_FOUND};
    }

    async function hasPhoto(photoRef) {
        const result = await getPhoto(photoRef);
        return result.ok;
    }

    async function deletePhoto(photoRef) {
        if (!photoRef) {
            return {ok: true};
        }
        return run('readwrite', (store) => {
            store.delete(photoRef);
        });
    }

    /** 체험 다이어리 한 권이 가진 사진의 photoRef 목록. 언제나 draftId 안으로만 훑는다. */
    async function listPhotoRefsForDraft(draftId) {
        if (!draftId) {
            return {ok: true, photoRefs: []};
        }
        const result = await run('readonly', (store, done) => {
            const request = store.index(DRAFT_INDEX).getAllKeys(draftId);
            request.onsuccess = () => done(request.result || []);
        });
        return result.ok
            ? {ok: true, photoRefs: Array.from(result.value || [])}
            : result;
    }

    /**
     * 체험 다이어리 한 권의 사진을 통째로 지운다.
     * 색인으로 draftId 안만 훑으므로 다른 다이어리의 사진은 건드리지 않는다.
     */
    async function deletePhotosForDraft(draftId) {
        if (!draftId) {
            return {ok: true};
        }
        return run('readwrite', (store) => {
            const request = store.index(DRAFT_INDEX).getAllKeys(draftId);
            request.onsuccess = () => {
                (request.result || []).forEach((key) => store.delete(key));
            };
        });
    }

    /**
     * 참조가 끊긴 사진 정리.
     *
     * <p>브라우저가 도중에 닫히면 draft 에서는 빠졌는데 Blob 만 남을 수 있다.
     * 지금 draft 가 쓰는 photoRef 목록을 받아 그 밖의 것만 지운다.
     * 훑는 범위는 언제나 이 draftId 안이다.
     */
    async function cleanupOrphans(draftId, validPhotoRefs) {
        if (!draftId) {
            return {ok: true, removed: 0};
        }
        const keep = new Set(validPhotoRefs || []);
        const result = await run('readwrite', (store, done) => {
            const request = store.index(DRAFT_INDEX).getAllKeys(draftId);
            request.onsuccess = () => {
                const removed = (request.result || []).filter((key) => !keep.has(key));
                removed.forEach((key) => store.delete(key));
                done(removed.length);
            };
        });
        return result.ok ? {ok: true, removed: result.value || 0} : result;
    }

    global.TravelDiaryGuestPhotoStore = {
        DB_NAME: DB_NAME,
        STORE_NAME: STORE,
        DRAFT_INDEX: DRAFT_INDEX,
        REASON: REASON,
        open: open,
        savePhoto: savePhoto,
        getPhoto: getPhoto,
        hasPhoto: hasPhoto,
        deletePhoto: deletePhoto,
        listPhotoRefsForDraft: listPhotoRefsForDraft,
        deletePhotosForDraft: deletePhotosForDraft,
        cleanupOrphans: cleanupOrphans
    };
})(window);
