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

    const DB_NAME = 'tripboraGuest';
    const DB_VERSION = 1;
    const STORE = 'photos';
    /** 체험 다이어리 한 권의 사진을 한 번에 지우기 위한 색인. */
    const DRAFT_INDEX = 'byDraft';

    /*
      Travel Diary 시절 보관소. 처음 열 때 사진을 DB_NAME 으로 옮긴다.
      P10b 에서 LEGACY_* 와 아래 "옛 보관소 옮기기" 묶음을 함께 지운다.
    */
    const LEGACY_DB_NAME = 'travelDiaryGuest';
    /** 옛 보관소의 사진을 새 보관소에 다 옮겼다는 표식(localStorage). 이 값이 있으면 옛 보관소를 다시 읽지 않는다. */
    const LEGACY_MIGRATED_MARKER = 'tripbora.guestPhotoDb.migrated.v1';
    /** 여러 탭이 동시에 옮기지 않도록 거는 잠금 이름. (Web Locks 를 지원할 때만) */
    const LEGACY_MIGRATION_LOCK = 'tripbora.guestPhotoDb.migration';
    const COPY = {DONE: 'DONE', MISSING: 'MISSING', FAILED: 'FAILED'};

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
     *
     * 모든 읽기·쓰기가 이 한 곳을 거치므로, 옛 보관소 사진을 옮기는 일은 여기서 한 번만 하고 끝난 뒤에 연다.
     * 그래야 draft 가 가리키는 photoRef 를 읽기 전에 사진이 새 보관소에 들어와 있다.
     */
    function open() {
        if (opening) return opening;
        opening = migrateLegacyDatabase().then(openDatabase, openDatabase);
        return opening;
    }

    function openDatabase() {
        return new Promise((resolve) => {
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
    }

    /* ---- 옛 보관소 옮기기 (P10b 에서 이 묶음 전체를 지운다) ---- */

    /**
     * 옛 보관소(LEGACY_DB_NAME)의 사진을 새 보관소로 옮긴다. 어떤 경우에도 예외를 밖으로 던지지 않는다.
     *
     * 1) 표식이 없으면: 옛 보관소가 실제로 있을 때만 열어 사진을 모두 읽고 새 보관소에 put 한다.
     *    새 보관소 트랜잭션이 commit 된 뒤에만 표식을 남긴다. 실패하면 표식도 남기지 않고 옛 보관소도 지우지 않는다.
     * 2) 표식이 있으면: 옛 보관소를 다시 읽지 않는다. (새 보관소에서 지운 사진이 되살아나지 않게)
     *    옛 보관소가 남아 있으면 지우기만 다시 시도한다. 다른 탭 때문에 막히면 다음 방문에 다시 시도한다.
     *
     * 표식은 localStorage 에 둔다. 표식을 읽거나 남길 수 없는 환경이면 옮기지 않는다. (옛 사진은 그대로 남는다)
     */
    async function migrateLegacyDatabase() {
        const indexedDb = global.indexedDB;
        const markers = markerStorage();
        if (!indexedDb || !markers) return;
        await withMigrationLock(async () => {
            try {
                const migrated = hasMigrationMarker(markers);
                if (migrated === null) return;
                if (!migrated) {
                    const copied = await copyLegacyPhotos(indexedDb);
                    if (copied !== COPY.DONE) return;
                    if (!writeMigrationMarker(markers)) return;
                }
                await deleteLegacyDatabase(indexedDb);
            } catch (error) {
                // 표식을 남기기 전에 실패했다면 옛 보관소는 그대로다. 다음 방문에 다시 시도한다.
            }
        });
    }

    /**
     * 다른 탭과 겹치지 않게 한 번에 하나만 옮긴다. 먼저 잠금을 잡은 탭이 표식을 남기면 뒤 탭은 다시 읽지 않는다.
     * Web Locks 를 못 쓰는 브라우저에서는 잠금 없이 실행한다. (task 는 예외를 던지지 않는다)
     */
    async function withMigrationLock(task) {
        const locks = global.navigator && global.navigator.locks;
        if (locks && typeof locks.request === 'function') {
            try {
                await locks.request(LEGACY_MIGRATION_LOCK, task);
                return;
            } catch (error) {
                // 잠금 자체를 쓸 수 없는 환경이다. 아래에서 잠금 없이 실행한다.
            }
        }
        await task();
    }

    function markerStorage() {
        try {
            return global.localStorage || null;
        } catch (error) {
            return null;
        }
    }

    /** true/false. 표식을 읽을 수 없으면 null — 그때는 옮기지도 지우지도 않는다. */
    function hasMigrationMarker(store) {
        try {
            return store.getItem(LEGACY_MIGRATED_MARKER) !== null;
        } catch (error) {
            return null;
        }
    }

    function writeMigrationMarker(store) {
        try {
            store.setItem(LEGACY_MIGRATED_MARKER, new Date().toISOString());
            return true;
        } catch (error) {
            return false;
        }
    }

    /** indexedDB.databases() 로 옛 보관소가 있는지 본다. 지원하지 않거나 알 수 없으면 null. */
    async function legacyDatabaseExists(indexedDb) {
        if (typeof indexedDb.databases !== 'function') return null;
        try {
            const databases = await indexedDb.databases();
            return databases.some((database) => database.name === LEGACY_DB_NAME);
        } catch (error) {
            return null;
        }
    }

    /** 옛 보관소의 사진을 새 보관소로 복사한다. 새 보관소 트랜잭션이 commit 되었을 때만 DONE 이다. */
    async function copyLegacyPhotos(indexedDb) {
        if (await legacyDatabaseExists(indexedDb) === false) return COPY.MISSING;
        const legacy = await openLegacyDatabase(indexedDb);
        if (legacy.status !== COPY.DONE) return legacy.status;
        const records = await readAllPhotos(legacy.db);
        legacy.db.close();
        if (!records) return COPY.FAILED;

        const target = await openDatabase();
        if (!target) return COPY.FAILED;
        const committed = await putAllPhotos(target, records);
        target.close();
        return committed ? COPY.DONE : COPY.FAILED;
    }

    /**
     * 옛 보관소를 연다. 버전을 주지 않고 열어 기존 구조를 건드리지 않는다.
     * 원래 없던 보관소라면(oldVersion 0) 이 open 때문에 빈 보관소가 생기지 않도록 되돌린다.
     */
    function openLegacyDatabase(indexedDb) {
        return new Promise((resolve) => {
            let request;
            let missing = false;
            try {
                request = indexedDb.open(LEGACY_DB_NAME);
            } catch (error) {
                resolve({status: COPY.FAILED});
                return;
            }
            request.onupgradeneeded = (event) => {
                if (event.oldVersion === 0) {
                    missing = true;
                    request.transaction.abort();
                }
            };
            request.onsuccess = () => resolve({status: COPY.DONE, db: request.result});
            request.onerror = () => resolve({status: missing ? COPY.MISSING : COPY.FAILED});
            request.onblocked = () => resolve({status: COPY.FAILED});
        });
    }

    /** 옛 보관소의 사진 레코드를 그대로 읽는다. photoRef 와 레코드 모양은 바꾸지 않는다. 실패하면 null. */
    function readAllPhotos(db) {
        if (!db.objectStoreNames.contains(STORE)) {
            return Promise.resolve([]);
        }
        return new Promise((resolve) => {
            try {
                const transaction = db.transaction(STORE, 'readonly');
                const request = transaction.objectStore(STORE).getAll();
                transaction.oncomplete = () => resolve(request.result || []);
                transaction.onerror = () => resolve(null);
                transaction.onabort = () => resolve(null);
            } catch (error) {
                resolve(null);
            }
        });
    }

    /** 새 보관소에 한 트랜잭션으로 넣는다. 같은 photoRef 는 덮어써서 여러 번 실행돼도 결과가 같다. */
    function putAllPhotos(db, records) {
        return new Promise((resolve) => {
            let transaction;
            try {
                transaction = db.transaction(STORE, 'readwrite');
                transaction.oncomplete = () => resolve(true);
                transaction.onerror = () => resolve(false);
                transaction.onabort = () => resolve(false);
                const store = transaction.objectStore(STORE);
                records.forEach((record) => store.put(record));
            } catch (error) {
                try {
                    if (transaction) transaction.abort();
                } catch (ignored) {
                    // 이미 끝난 트랜잭션이면 그대로 둔다.
                }
                resolve(false);
            }
        });
    }

    /**
     * 옛 보관소를 지운다. 표식을 남긴 뒤에만 부른다.
     * 다른 탭이 옛 보관소를 열고 있으면 blocked 가 되는데, 기다리지 않는다. 요청은 그 탭이 닫히면 이어지고,
     * 그 전에 이 화면이 닫혀도 다음 방문에 다시 시도한다. 사진은 이미 새 보관소에 있다.
     */
    async function deleteLegacyDatabase(indexedDb) {
        if (await legacyDatabaseExists(indexedDb) === false) return;
        await new Promise((resolve) => {
            try {
                const request = indexedDb.deleteDatabase(LEGACY_DB_NAME);
                request.onsuccess = () => resolve();
                request.onerror = () => resolve();
                request.onblocked = () => resolve();
            } catch (error) {
                resolve();
            }
        });
    }

    /* ---- 옛 보관소 옮기기 끝 ---- */

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

    global.TripBoraGuestPhotoStore = {
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
    global.TravelDiaryGuestPhotoStore = global.TripBoraGuestPhotoStore; // legacy alias (P10a 제거)
})(window);
