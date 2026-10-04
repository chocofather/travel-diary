package com.tripbora.repository;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static java.util.Map.entry;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * 브라우저 저장소 이름을 Travel Diary 에서 TripBora 로 옮기는 호환 코드의 약속.
 *
 * <p>사용자 브라우저에 남은 체험 초안·사진·가져오기 쪽지·최근 이모지/스티커가 사라지면 안 되므로,
 * 이름만 바꾸지 않고 처음 쓸 때 옛 이름의 값을 새 이름으로 옮긴다.
 * 옛 이름은 각 모듈의 LEGACY_* 상수 한 줄에만 두고, P10b 에서 LEGACY_* 와 옮기는 코드를 함께 지운다.
 *
 * <p>이 저장소에는 JS 실행 환경이 없어 동작 대신 코드 구조를 고정한다.
 * localStorage 를 옮기는 함수는 네 모듈이 글자 그대로 같은 본문을 쓰고, 그 본문이 규칙을 지키는지 본다.
 */
class TripBoraBrowserStorageMigrationContractTest {

    private static final Path JS_ROOT = Path.of("src/main/resources/static/js");
    private static final Path TEMPLATE_ROOT = Path.of("src/main/resources/templates");

    /** 파일 → {새 키 상수, 새 키, 옛 키 상수, 옛 키}. */
    private static final Map<String, List<String>> LOCAL_STORAGE_KEYS = Map.ofEntries(
            entry("guest-diary-draft-store.js", List.of(
                    "STORAGE_KEY", "tripbora.guestDiaryDraft.v1",
                    "LEGACY_STORAGE_KEY", "travelDiary.guestDiaryDraft.v1")),
            entry("guest-diary-import-intent.js", List.of(
                    "KEY", "tripbora.guestDiaryImportIntent.v1",
                    "LEGACY_KEY", "travelDiary.guestDiaryImportIntent.v1")),
            entry("diary-editor.js", List.of(
                    "RECENT_EMOJI_KEY", "tripbora.recentEmojis",
                    "LEGACY_RECENT_EMOJI_KEY", "travelDiaryRecentEmojis")),
            entry("diary-sticker-picker.js", List.of(
                    "RECENT_KEY", "tripbora.recentStickers",
                    "LEGACY_RECENT_KEY", "travelDiaryRecentStickers")));

    private static final String PHOTO_STORE = "guest-diary-photo-store.js";
    private static final String LEGACY_DB_NAME = "travelDiaryGuest";
    private static final List<String> LEGACY_NAMES = List.of(
            "travelDiary.guestDiaryDraft.v1", "travelDiary.guestDiaryImportIntent.v1",
            "travelDiaryRecentEmojis", "travelDiaryRecentStickers", LEGACY_DB_NAME);

    /** 새 이름이 정식 키이고, 옛 이름은 LEGACY_* 상수로만 선언된다. */
    @Test
    void newNamesAreCanonicalAndLegacyNamesAreOnlyLegacyConstants() throws IOException {
        for (Map.Entry<String, List<String>> module : LOCAL_STORAGE_KEYS.entrySet()) {
            String js = script(module.getKey());
            List<String> names = module.getValue();
            assertThat(js).as(module.getKey())
                    .containsPattern("const " + names.get(0) + " = ['\"]" + quote(names.get(1)) + "['\"];")
                    .containsPattern("const " + names.get(2) + " = ['\"]" + quote(names.get(3)) + "['\"];");
        }

        String photoStore = script(PHOTO_STORE);
        assertThat(photoStore)
                .contains("const DB_NAME = 'tripboraGuest';")
                .contains("const LEGACY_DB_NAME = '" + LEGACY_DB_NAME + "';")
                // 새 보관소의 구조는 그대로다.
                .contains("const STORE = 'photos';")
                .contains("global.indexedDB.open(DB_NAME, DB_VERSION)")
                .contains("db.createObjectStore(STORE, {keyPath: 'photoRef'})")
                .contains("store.createIndex(DRAFT_INDEX, 'draftId', {unique: false})");
    }

    /** 옛 이름 문자열은 LEGACY_* 선언 한 줄 말고는 정적 JS·템플릿 어디에도 없다. */
    @Test
    void legacyNamesDoNotLeakOutsideTheMigrationConstants() throws IOException {
        for (Path file : files(JS_ROOT, ".js")) {
            List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
            for (String legacy : LEGACY_NAMES) {
                List<String> hits = lines.stream()
                        .filter(line -> line.contains("'" + legacy + "'") || line.contains("\"" + legacy + "\""))
                        .toList();
                assertThat(hits).as("%s 의 %s", file.getFileName(), legacy)
                        .allSatisfy(line -> assertThat(line.strip()).startsWith("const LEGACY_"));
                assertThat(hits).as("%s 의 %s", file.getFileName(), legacy).hasSizeLessThanOrEqualTo(1);
            }
        }
        for (Path template : files(TEMPLATE_ROOT, ".html")) {
            assertThat(read(template)).as(template.toString()).doesNotContain(LEGACY_NAMES.toArray(String[]::new));
        }
    }

    /**
     * 네 모듈이 같은 옮기기 함수를 쓰고, 그 함수가 규칙을 지킨다.
     * - 옛 키가 없으면 아무것도 하지 않는다.
     * - 새 키가 이미 있으면 덮어쓰지 않는다. (새 키 우선)
     * - 옛 값은 가공하지 않고 문자열 그대로 새 키에 넣는다.
     * - 새 키 저장이 성공한 뒤에만 옛 키를 지운다. 저장이 실패하면 catch 로 빠져 옛 키를 남기고 false 를 준다.
     */
    @Test
    void localStorageModulesShareOneSafeMigrationFunction() throws IOException {
        String canonical = null;
        for (String module : LOCAL_STORAGE_KEYS.keySet()) {
            String body = normalize(functionSource(script(module), "migrateLegacyKey(store, legacyKey, key)"));
            if (canonical == null) {
                canonical = body;
            }
            assertThat(body).as(module + " 의 migrateLegacyKey 는 다른 모듈과 같다").isEqualTo(canonical);
        }

        assertThat(canonical)
                .contains("const legacyValue = store.getItem(legacyKey);")
                .contains("if (legacyValue === null) { return true; }")
                .contains("if (store.getItem(key) === null) { store.setItem(key, legacyValue); }")
                .contains("} catch (error) { return false; }")
                .doesNotContain("JSON.");
        assertThat(canonical.indexOf("store.setItem(key, legacyValue);"))
                .as("옛 키는 새 키 저장 뒤에만 지운다")
                .isLessThan(canonical.indexOf("store.removeItem(legacyKey);"));
        assertThat(canonical.indexOf("store.removeItem(legacyKey);"))
                .isLessThan(canonical.indexOf("return true; } catch"));
    }

    /** 처음 저장소에 손댈 때 한 번만 옮기고, 옮기지 못한 화면에서는 옛 값을 읽기 전용으로만 보여 준다. */
    @Test
    void eachModuleMigratesOnceBeforeReadingAndOnlyFallsBackWhilePending() throws IOException {
        String draft = script("guest-diary-draft-store.js");
        assertThat(draft)
                .contains("let legacyKeyPending = null;")
                .contains("if (store && legacyKeyPending === null) {")
                .contains("legacyKeyPending = !migrateLegacyKey(store, LEGACY_STORAGE_KEY, STORAGE_KEY);")
                .contains("if (saved === null && legacyKeyPending) {")
                .contains("saved = store.getItem(LEGACY_STORAGE_KEY);")
                .contains("store.setItem(STORAGE_KEY, JSON.stringify(saved));")
                // 지울 때는 남은 옛 값도 함께 지워 다음 방문에 되살아나지 않는다.
                .contains("store.removeItem(STORAGE_KEY);")
                .contains("store.removeItem(LEGACY_STORAGE_KEY);")
                .doesNotContain("setItem(LEGACY_STORAGE_KEY");

        String intent = script("guest-diary-import-intent.js");
        assertThat(intent)
                .contains("legacyKeyPending = !migrateLegacyKey(store, LEGACY_KEY, KEY);")
                .contains("saved = store.getItem(LEGACY_KEY);")
                .contains("store.removeItem(KEY);")
                .contains("store.removeItem(LEGACY_KEY);")
                .doesNotContain("setItem(LEGACY_KEY");

        String editor = script("diary-editor.js");
        assertThat(editor)
                .contains("let recentEmojiLegacyPending = null;")
                .contains("recentEmojiLegacyPending = !migrateLegacyKey(store, LEGACY_RECENT_EMOJI_KEY, RECENT_EMOJI_KEY);")
                .contains("(recentEmojiLegacyPending ? store.getItem(LEGACY_RECENT_EMOJI_KEY) : null)")
                .contains("recentEmojiStorage().setItem(RECENT_EMOJI_KEY, JSON.stringify(next));")
                .doesNotContain("setItem(LEGACY_RECENT_EMOJI_KEY");

        String sticker = script("diary-sticker-picker.js");
        assertThat(sticker)
                .contains("let recentLegacyPending = null;")
                .contains("recentLegacyPending = !migrateLegacyKey(store, LEGACY_RECENT_KEY, RECENT_KEY);")
                .contains("(recentLegacyPending ? store.getItem(LEGACY_RECENT_KEY) : null)")
                .contains("recentStorage().setItem(RECENT_KEY, JSON.stringify(ids));")
                .doesNotContain("setItem(LEGACY_RECENT_KEY");

        // 최근 목록 저장소는 이제 옮기기를 거친 접근자로만 쓴다.
        assertThat(editor).doesNotContain("window.localStorage.getItem(RECENT_EMOJI_KEY)");
        assertThat(sticker).doesNotContain("window.localStorage.getItem(RECENT_KEY)");
    }

    /** 사진은 모든 접근이 지나는 open() 에서, 새 보관소를 열기 전에 한 번만 옮긴다. */
    @Test
    void photosAreMigratedOnceBeforeTheNewStoreOpens() throws IOException {
        String open = normalize(functionSource(script(PHOTO_STORE), "open()"));
        assertThat(open)
                .contains("if (opening) return opening;")
                .contains("opening = migrateLegacyDatabase().then(openDatabase, openDatabase);");
    }

    /** 옛 보관소는 실제로 있을 때만 열고, 없던 보관소를 open 때문에 새로 만들지 않는다. */
    @Test
    void theLegacyDatabaseIsOnlyOpenedWhenItExists() throws IOException {
        String js = script(PHOTO_STORE);
        String exists = normalize(functionSource(js, "legacyDatabaseExists(indexedDb)"));
        assertThat(exists)
                .contains("if (typeof indexedDb.databases !== 'function') return null;")
                .contains("databases.some((database) => database.name === LEGACY_DB_NAME)");

        String copy = normalize(functionSource(js, "copyLegacyPhotos(indexedDb)"));
        assertThat(copy).startsWith("{ if (await legacyDatabaseExists(indexedDb) === false) return COPY.MISSING;");

        String openLegacy = normalize(functionSource(js, "openLegacyDatabase(indexedDb)"));
        assertThat(openLegacy)
                // 버전을 주지 않아 기존 구조를 바꾸지 않는다.
                .contains("request = indexedDb.open(LEGACY_DB_NAME);")
                .contains("if (event.oldVersion === 0) { missing = true; request.transaction.abort(); }")
                .contains("request.onerror = () => resolve({status: missing ? COPY.MISSING : COPY.FAILED});");
        // 옛 보관소를 버전과 함께 열거나 구조를 만들지 않는다.
        assertThat(js).doesNotContain("open(LEGACY_DB_NAME, ");
    }

    /**
     * 복사는 새 보관소 트랜잭션이 commit 된 뒤에만 성공이고, 표식은 그 뒤에만 남는다.
     * 표식이 있으면 옛 보관소를 다시 읽지 않는다. 옛 보관소 삭제는 표식이 있을 때만 한다.
     */
    @Test
    void theMarkerIsWrittenOnlyAfterCommitAndPreventsReimport() throws IOException {
        String js = script(PHOTO_STORE);
        assertThat(js)
                .contains("const LEGACY_MIGRATED_MARKER = 'tripbora.guestPhotoDb.migrated.v1';")
                .contains("const LEGACY_MIGRATION_LOCK = 'tripbora.guestPhotoDb.migration';");

        String put = normalize(functionSource(js, "putAllPhotos(db, records)"));
        assertThat(put)
                .contains("transaction = db.transaction(STORE, 'readwrite');")
                .contains("transaction.oncomplete = () => resolve(true);")
                .contains("transaction.onerror = () => resolve(false);")
                .contains("transaction.onabort = () => resolve(false);")
                .contains("records.forEach((record) => store.put(record));");

        String copy = normalize(functionSource(js, "copyLegacyPhotos(indexedDb)"));
        assertThat(copy)
                .contains("if (!records) return COPY.FAILED;")
                .contains("return committed ? COPY.DONE : COPY.FAILED;");

        String migrate = normalize(functionSource(js, "migrateLegacyDatabase()"));
        assertThat(migrate)
                .contains("if (migrated === null) return;")
                .contains("if (!migrated) { const copied = await copyLegacyPhotos(indexedDb); "
                        + "if (copied !== COPY.DONE) return; "
                        + "if (!writeMigrationMarker(markers)) return; } "
                        + "await deleteLegacyDatabase(indexedDb);")
                .contains("withMigrationLock(");
        // 옮기기와 지우기는 이 함수를 거쳐서만 일어난다.
        assertThat(count(js, "copyLegacyPhotos(indexedDb)")).isEqualTo(2);
        assertThat(count(js, "deleteLegacyDatabase(indexedDb)")).isEqualTo(2);
        assertThat(count(js, "writeMigrationMarker(")).isEqualTo(2);
    }

    /** 옛 보관소 삭제가 막히거나 실패해도 기다리거나 다시 복사하지 않고, 새 보관소는 지우지 않는다. */
    @Test
    void blockedOrFailedDeletionKeepsDataAndRetriesNextVisit() throws IOException {
        String js = script(PHOTO_STORE);
        String delete = normalize(functionSource(js, "deleteLegacyDatabase(indexedDb)"));
        assertThat(delete)
                .contains("indexedDb.deleteDatabase(LEGACY_DB_NAME)")
                .contains("request.onblocked = () => resolve();")
                .contains("request.onerror = () => resolve();");
        assertThat(count(js, "deleteDatabase(")).isEqualTo(1);
        assertThat(js).doesNotContain("deleteDatabase(DB_NAME");

        String lock = normalize(functionSource(js, "withMigrationLock(task)"));
        assertThat(lock)
                .contains("global.navigator && global.navigator.locks")
                .contains("await locks.request(LEGACY_MIGRATION_LOCK, task);");
    }

    private static String script(String name) throws IOException {
        return read(JS_ROOT.resolve(name));
    }

    private static String read(Path path) throws IOException {
        return Files.readString(path, StandardCharsets.UTF_8);
    }

    private static List<Path> files(Path root, String suffix) throws IOException {
        try (Stream<Path> files = Files.walk(root)) {
            return files.filter(path -> path.toString().endsWith(suffix)).sorted().toList();
        }
    }

    /** "function 이름(인자)" 의 중괄호 본문. 이 파일들의 대상 함수에는 문자열 속 중괄호가 없다. */
    private static String functionSource(String js, String signature) {
        int start = js.indexOf("function " + signature);
        assertThat(start).as("function " + signature).isGreaterThanOrEqualTo(0);
        int open = js.indexOf('{', start);
        int depth = 0;
        for (int i = open; i < js.length(); i++) {
            char c = js.charAt(i);
            if (c == '{') {
                depth++;
            } else if (c == '}' && --depth == 0) {
                return js.substring(open, i + 1);
            }
        }
        throw new AssertionError("닫히지 않은 함수: " + signature);
    }

    /** 줄바꿈·들여쓰기·주석 줄 차이를 없앤다. */
    private static String normalize(String source) {
        return source.lines()
                .map(String::strip)
                .filter(line -> !line.startsWith("//"))
                .reduce((a, b) -> a + " " + b)
                .orElse("")
                .replaceAll("\\s+", " ");
    }

    private static String quote(String value) {
        return java.util.regex.Pattern.quote(value);
    }

    private static int count(String text, String token) {
        int count = 0;
        for (int index = text.indexOf(token); index >= 0; index = text.indexOf(token, index + token.length())) {
            count++;
        }
        return count;
    }
}
