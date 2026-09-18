package com.example.travlediary.service.file;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 개인 다이어리 사진을 실제로 내보내도 되는지 정하는 자리의 계약.
 *
 * <p>여기서 막지 못하면 소유권 확인을 통과한 요청 하나로 저장소 밖의 파일까지 나갈 수 있다.
 * 그래서 저장 키 형태, 루트 밖으로 나가는 경로, 심볼릭 링크, 내용과 확장자의 불일치를 모두 본다.
 */
class DiaryPrivatePhotoStorageTest {

    private static final String KEY =
            "/uploads/diary-pages/123e4567-e89b-12d3-a456-426614174000.jpg";

    @TempDir
    Path root;

    /** 사진은 private 루트에서 먼저 찾는다. */
    @Test
    void readsFromThePrivateRootFirst() throws Exception {
        Path priv = privateRoot().resolve("diary-pages/123e4567-e89b-12d3-a456-426614174000.jpg");
        Files.createDirectories(priv.getParent());
        Files.write(priv, jpeg());
        Path legacy = publicRoot().resolve("diary-pages/123e4567-e89b-12d3-a456-426614174000.jpg");
        Files.createDirectories(legacy.getParent());
        Files.write(legacy, png());

        DiaryPrivatePhotoStorage.StoredPhotoFile file = storage().resolveForRead(KEY);

        assertThat(file.path()).isEqualTo(priv.toRealPath());
        assertThat(file.contentType()).isEqualTo("image/jpeg");
        assertThat(file.contentLength()).isEqualTo(jpeg().length);
    }

    /**
     * 아직 옮기지 않은 예전 파일은 공개 루트에서 읽어 준다.
     * (이 fallback 은 파일 이동이 끝나면 없어진다 — 그래도 브라우저가 예전 주소로 직접 열지는 못한다)
     */
    @Test
    void fallsBackToTheLegacyPublicRootWhenTheFileHasNotMovedYet() throws Exception {
        Path legacy = publicRoot().resolve("diary-pages/123e4567-e89b-12d3-a456-426614174000.jpg");
        Files.createDirectories(legacy.getParent());
        Files.write(legacy, jpeg());

        assertThat(storage().resolveForRead(KEY).path()).isEqualTo(legacy.toRealPath());
    }

    /** 파일이 어디에도 없으면 다른 실패와 같은 오류다. (있는지 없는지 구분되지 않는다) */
    @Test
    void aMissingFileFailsLikeEveryOtherRejection() {
        assertThatThrownBy(() -> storage().resolveForRead(KEY))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /** 관리 폴더와 UUID 파일명이 아닌 저장 키는 받지 않는다. */
    @Test
    void rejectsKeysOutsideTheManagedPrefixes() {
        DiaryPrivatePhotoStorage storage = storage();

        for (String key : new String[]{
                null,
                "",
                // 공개 폴더는 이 저장소가 다루지 않는다
                "/uploads/profiles/123e4567-e89b-12d3-a456-426614174000.jpg",
                "/uploads/posts/123e4567-e89b-12d3-a456-426614174000.jpg",
                // 정적 asset 경로
                "/images/diary/stickers/travel/plane.svg",
                // UUID 가 아닌 이름
                "/uploads/diary-pages/a.jpg",
                // 하위 폴더나 상위로 올라가는 조각
                "/uploads/diary-pages/sub/123e4567-e89b-12d3-a456-426614174000.jpg",
                "/uploads/diary-pages/../../secret.jpg",
                "/uploads/../diary-pages/123e4567-e89b-12d3-a456-426614174000.jpg",
                // 아는 형식이 아닌 확장자
                "/uploads/diary-pages/123e4567-e89b-12d3-a456-426614174000.svg",
                "/uploads/diary-pages/123e4567-e89b-12d3-a456-426614174000.html"}) {
            assertThat(storage.isManagedKey(key)).as(String.valueOf(key)).isFalse();
            assertThatThrownBy(() -> storage.resolveForRead(key))
                    .as(String.valueOf(key))
                    .isInstanceOf(IllegalArgumentException.class);
            // 관리 대상이 아니면 지우지도 않는다.
            assertThat(storage.delete(key)).as(String.valueOf(key)).isFalse();
        }
    }

    /** 저장소 밖을 가리키는 심볼릭 링크로는 아무것도 나가지 않는다. */
    @Test
    void rejectsSymbolicLinksPointingOutsideTheStorageRoot() throws Exception {
        Path outside = root.resolve("outside.jpg");
        Files.write(outside, jpeg());
        Path linked = privateRoot().resolve("diary-pages/123e4567-e89b-12d3-a456-426614174000.jpg");
        Files.createDirectories(linked.getParent());
        try {
            Files.createSymbolicLink(linked, outside);
        } catch (UnsupportedOperationException | java.io.IOException unsupported) {
            return; // 심볼릭 링크를 못 만드는 환경에서는 검증을 건너뛴다
        }

        assertThatThrownBy(() -> storage().resolveForRead(KEY))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(outside).exists();
    }

    /** 확장자는 사진인데 내용이 다르면 내보내지 않는다. (Content-Type 을 지어내지 않는다) */
    @Test
    void rejectsContentThatDoesNotMatchTheStoredExtension() throws Exception {
        Path stored = privateRoot().resolve("diary-pages/123e4567-e89b-12d3-a456-426614174000.jpg");
        Files.createDirectories(stored.getParent());
        Files.write(stored, png()); // 확장자는 jpg 인데 실제로는 PNG 다

        assertThatThrownBy(() -> storage().resolveForRead(KEY))
                .isInstanceOf(IllegalArgumentException.class);

        Files.write(stored, "<svg xmlns=\"http://www.w3.org/2000/svg\"></svg>".getBytes());
        assertThatThrownBy(() -> storage().resolveForRead(KEY))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /** 지울 때는 private 저장본과 아직 옮기지 않은 예전 파일을 함께 본다. */
    @Test
    void deleteRemovesBothThePrivateCopyAndTheLegacyFile() throws Exception {
        Path priv = privateRoot().resolve("diary-pages/123e4567-e89b-12d3-a456-426614174000.jpg");
        Path legacy = publicRoot().resolve("diary-pages/123e4567-e89b-12d3-a456-426614174000.jpg");
        Files.createDirectories(priv.getParent());
        Files.createDirectories(legacy.getParent());
        Files.write(priv, jpeg());
        Files.write(legacy, jpeg());

        assertThat(storage().delete(KEY)).isTrue();
        assertThat(priv).doesNotExist();
        assertThat(legacy).doesNotExist();
        // 이미 없으면 더 지울 것이 없다.
        assertThat(storage().delete(KEY)).isFalse();
    }

    /** 복사본은 원본을 건드리지 않고 관리 폴더 안에 새 저장 키로 생긴다. */
    @Test
    void copyManagedCreatesANewFileAndLeavesTheSourceAlone() throws Exception {
        Path source = privateRoot()
                .resolve("diary-cover-designs/123e4567-e89b-12d3-a456-426614174000.jpg");
        Files.createDirectories(source.getParent());
        Files.write(source, jpeg());

        String copied = storage().copyManaged(
                "/uploads/diary-cover-designs/123e4567-e89b-12d3-a456-426614174000.jpg",
                DiaryPrivatePhotoStorage.COVER_ELEMENT_DIRECTORY);

        assertThat(copied).startsWith("/uploads/diary-cover-elements/").endsWith(".jpg");
        assertThat(storage().isManagedKey(copied)).isTrue();
        assertThat(privateRoot().resolve(copied.substring("/uploads/".length())))
                .hasBinaryContent(jpeg());
        assertThat(source).isRegularFile();
    }

    /** 관리 대상이 아닌 원본은 복사하지 않는다. (공용 스티커 경로가 들어와도 마찬가지다) */
    @Test
    void copyManagedRefusesSourcesItDoesNotManage() {
        assertThat(storage().copyManaged("/images/diary/stickers/travel/plane.svg",
                DiaryPrivatePhotoStorage.COVER_ELEMENT_DIRECTORY)).isNull();
        assertThat(storage().copyManaged(KEY,
                DiaryPrivatePhotoStorage.COVER_ELEMENT_DIRECTORY)).isNull();
    }

    /** private 루트가 공개 업로드 루트 안이면 기동 자체를 막는다. */
    @Test
    void refusesAPrivateRootInsideThePublicUploadRoot() {
        Path publicRoot = root.resolve("uploads");
        Path inside = publicRoot.resolve("diary-private");

        assertThatThrownBy(() -> new DiaryPrivatePhotoStorage(
                new FileUploadService(publicRoot.toString()),
                publicRoot.toString(), inside.toString()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("공개 upload 경로 밖");
    }

    /* ===== 도우미 ===== */

    private Path publicRoot() {
        return root.resolve("uploads");
    }

    private Path privateRoot() {
        return root.resolve("diary-private");
    }

    private DiaryPrivatePhotoStorage storage() {
        return new DiaryPrivatePhotoStorage(
                new FileUploadService(publicRoot().toString()),
                publicRoot().toString(), privateRoot().toString());
    }

    private byte[] jpeg() {
        return new byte[]{(byte) 0xff, (byte) 0xd8, (byte) 0xff, (byte) 0xe0, 0, 16};
    }

    private byte[] png() {
        return new byte[]{(byte) 0x89, 'P', 'N', 'G', 0x0d, 0x0a, 0x1a, 0x0a, 0, 0, 0, 13};
    }
}
