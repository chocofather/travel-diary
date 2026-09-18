package com.example.travlediary.service.file;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * 회원 개인 다이어리 사진의 저장소.
 *
 * <p>대표 이미지·페이지 사진·적용 표지 사진·내 표지 디자인 사진은 주소만 알면 누구나 열 수 있는
 * {@code /uploads/**} 정적 매핑 아래에 있어서는 안 된다. 그래서 실제 파일은 공개 업로드 루트
 * 바깥의 private 루트에 둔다. 표지 라이브러리 공유 사진({@link DiaryCoverLibraryPhotoStorage})과
 * 같은 방식이고, 수명 주기가 달라 루트만 따로 쓴다.
 *
 * <p>DB 의 {@code image_url} 값은 그대로 두고 여기에서만 해석한다. 값은 예전과 같은
 * {@code /uploads/diary-pages/{uuid}.jpg} 형태지만 이것은 <b>논리적인 저장 키</b>일 뿐이고
 * 실제 파일 위치가 아니다. 부르는 쪽은 경로를 조립하지 않고 이 저장 키만 주고받는다.
 *
 * <p>아직 옮기지 않은 예전 파일은 공개 업로드 루트에 남아 있으므로, 읽을 때만 한시적으로
 * private → legacy 순서로 찾는다. 이 fallback 은 {@link #LEGACY_READ_FALLBACK} 한 곳에서만
 * 켜고 끄며, 파일 이동이 끝나면 그 상수를 {@code false} 로 두는 것으로 없앨 수 있다.
 */
@Service
public class DiaryPrivatePhotoStorage {

    /** 다이어리 대표 이미지 */
    public static final String COVER_DIRECTORY = "diary-covers";
    /** 페이지에 붙인 사진 */
    public static final String PAGE_DIRECTORY = "diary-pages";
    /** 다이어리에 실제로 적용된 표지의 사진 */
    public static final String COVER_ELEMENT_DIRECTORY = "diary-cover-elements";
    /** 보관함의 "내 표지 디자인" 사진 */
    public static final String COVER_DESIGN_DIRECTORY = "diary-cover-designs";

    /** 이 저장소가 다루는 폴더 전부. 여기 없는 폴더는 저장도 조회도 하지 않는다. */
    public static final List<String> MANAGED_DIRECTORIES = List.of(
            COVER_DIRECTORY, PAGE_DIRECTORY, COVER_ELEMENT_DIRECTORY, COVER_DESIGN_DIRECTORY);

    /**
     * 예전 파일을 공개 업로드 루트에서 읽어 주는 한시 조치.
     *
     * <p>실제 파일 이동이 끝나면 {@code false} 로 바꾸는 것만으로 사라진다.
     * 이 fallback 은 <b>서버 안에서 파일을 읽을 때만</b> 쓴다 — 공개 ResourceHandler 는
     * 이 폴더들을 더 이상 매핑하지 않으므로 브라우저가 예전 주소로 직접 열 수는 없다.
     */
    static final boolean LEGACY_READ_FALLBACK = true;

    /** DB 에 저장되는 논리 저장 키의 앞머리. 값 형식을 바꾸지 않으려고 예전 그대로 쓴다. */
    private static final String STORAGE_KEY_PREFIX = "/uploads/";

    /**
     * 다룰 수 있는 저장 키. 관리 폴더 하나 + UUID 파일명 + 아는 확장자만 받는다.
     * 경로 조각('..', 하위 폴더)은 형태 자체가 맞지 않아 들어오지 못한다.
     */
    private static final Pattern MANAGED_STORAGE_KEY = Pattern.compile(
            "^/uploads/(?:diary-covers|diary-pages|diary-cover-elements|diary-cover-designs)/"
                    + "[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}"
                    + "\\.(?:jpg|jpeg|png|gif|webp)$",
            Pattern.CASE_INSENSITIVE);

    private final FileUploadService fileUploadService;
    private final Path privateRoot;
    private final Path legacyPublicRoot;

    public DiaryPrivatePhotoStorage(FileUploadService fileUploadService,
                                    @Value("${custom.upload-path}") String publicUploadPath,
                                    @Value("${custom.diary-private-path}") String privateStoragePath) {
        this.fileUploadService = fileUploadService;
        this.legacyPublicRoot = Paths.get(publicUploadPath).toAbsolutePath().normalize();
        this.privateRoot = Paths.get(privateStoragePath).toAbsolutePath().normalize();
        requireOutsidePublicRoot();
    }

    /**
     * private 루트가 공개 업로드 루트 안에 있으면 기동 자체를 막는다.
     *
     * <p>설정 문자열만 보면 {@code ../} 로 빠져나온 것처럼 보여도 심볼릭 링크를 타면 다시
     * 공개 루트 안일 수 있다. 그래서 정규화한 경로와 실제 경로를 모두 확인한다.
     * (표지 라이브러리 저장소와 같은 기준이다)
     */
    private void requireOutsidePublicRoot() {
        ensureOutside(privateRoot, legacyPublicRoot);
        for (String directory : MANAGED_DIRECTORIES) {
            ensureOutside(privateRoot.resolve(directory).normalize(), legacyPublicRoot);
        }
        try {
            if (Files.exists(privateRoot) && Files.exists(legacyPublicRoot)) {
                ensureOutside(privateRoot.toRealPath(), legacyPublicRoot.toRealPath());
            }
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "개인 다이어리 사진 저장 경로를 확인하지 못했습니다.", exception);
        }
    }

    private void ensureOutside(Path candidate, Path publicRoot) {
        if (candidate.startsWith(publicRoot)) {
            throw new IllegalStateException(
                    "개인 다이어리 사진 저장 경로는 공개 upload 경로 밖이어야 합니다.");
        }
    }

    /**
     * 새로 올린 사진 한 장을 private 저장소에 둔다.
     *
     * <p>형식 검증은 공개 업로드와 같은 정책을 그대로 쓴다. 저장 확장자도 파일 내용으로
     * 서버가 정하며 원본 파일명은 쓰지 않는다.
     *
     * @return DB 에 넣을 논리 저장 키 ({@code /uploads/diary-pages/{uuid}.jpg})
     */
    public String save(MultipartFile file, String directory) {
        if (file == null || file.isEmpty()) {
            return null;
        }
        String extension = fileUploadService.validatedGeneralImageExtension(file);
        return store(file, directory, extension);
    }

    /**
     * 비회원 체험에서 가져온 사진 한 장.
     * 체험은 GIF 까지 받아 왔으므로 그 검증을 그대로 쓴다. (일반 업로드와 허용 형식이 다르다)
     */
    public String saveImportedPhoto(MultipartFile file, String directory) {
        String extension = fileUploadService.validatedImportedDiaryPhotoExtension(file);
        return store(file, directory, extension);
    }

    private String store(MultipartFile file, String directory, String extension) {
        Path targetDirectory = resolveManagedDirectory(directory);
        String storageKey = STORAGE_KEY_PREFIX + directory + "/"
                + UUID.randomUUID() + "." + extension;
        Path destination = targetDirectory.resolve(fileNameOf(storageKey)).normalize();
        ensureContained(targetDirectory, destination);

        try (InputStream input = file.getInputStream()) {
            Files.createDirectories(targetDirectory);
            Files.copy(input, destination);
        } catch (IOException exception) {
            deleteQuietly(destination);
            // 서버 경로가 메시지로 새어 나가지 않게 원인만 붙여 둔다.
            throw new RuntimeException("사진 저장 실패", exception);
        }
        return storageKey;
    }

    /**
     * 이미 저장된 사진을 관리 폴더 사이에서 복사한다.
     *
     * <p>표지 디자인을 여행일기에 적용할 때처럼 원본과 적용본이 한 파일을 나눠 쓰면
     * 한쪽을 지웠을 때 다른 쪽이 깨진다. 그래서 값만 옮기지 않고 파일도 새로 만든다.
     * 원본은 건드리지 않는다. 아직 옮기지 않은 예전 원본도 그대로 복사해 온다.
     *
     * @return 복사본의 저장 키. 원본이 관리 대상이 아니거나 없으면 null.
     */
    public String copyManaged(String sourceStorageKey, String targetDirectory) {
        Path source;
        try {
            source = resolveExistingFile(sourceStorageKey);
        } catch (IllegalArgumentException exception) {
            return null;
        }

        Path directory = resolveManagedDirectory(targetDirectory);
        String extension = extensionOf(sourceStorageKey);
        String storageKey = STORAGE_KEY_PREFIX + targetDirectory + "/"
                + UUID.randomUUID() + "." + extension;
        Path destination = directory.resolve(fileNameOf(storageKey)).normalize();
        ensureContained(directory, destination);

        try {
            Files.createDirectories(directory);
            Files.copy(source, destination);
        } catch (IOException exception) {
            deleteQuietly(destination);
            throw new RuntimeException("사진 복사 실패", exception);
        }
        return storageKey;
    }

    /**
     * 통제된 응답이 내려보낼 파일 하나.
     *
     * <p>소유권 확인은 부르는 쪽이 이미 마쳤다. 여기서는 저장 키가 관리 대상인지,
     * 실제 파일이 루트 안의 보통 파일인지, 내용이 확장자와 같은 형식인지만 본다.
     *
     * @throws IllegalArgumentException 어떤 이유로든 내보낼 수 없을 때. 이유는 구분하지 않는다.
     */
    public StoredPhotoFile resolveForRead(String storageKey) {
        Path target = resolveExistingFile(storageKey);
        StoredImageFormat format = detectFormat(target);
        if (!format.matchesStorageKey(storageKey)) {
            throw new IllegalArgumentException("사진 파일 형식이 올바르지 않습니다.");
        }
        long size;
        try {
            size = Files.size(target);
        } catch (IOException exception) {
            throw new IllegalArgumentException("사진 파일을 찾을 수 없습니다.", exception);
        }
        if (size <= 0) {
            throw new IllegalArgumentException("사진 파일이 비어 있습니다.");
        }
        return new StoredPhotoFile(target, format.contentType(), size);
    }

    /**
     * 표지 라이브러리 공유 등록이 원본으로 쓸 파일.
     *
     * <p>공유 등록은 사용자가 넘긴 경로가 아니라 <b>검증된 관리 저장 키</b>만 받아야 한다.
     * {@link #resolveForRead(String)} 와 같은 검사를 거친 실제 경로만 내준다.
     */
    public Path resolveManagedSource(String storageKey) {
        return resolveForRead(storageKey).path();
    }

    /**
     * 사진 한 장을 지운다. private 저장본과 아직 옮기지 않은 예전 파일을 모두 정리한다.
     *
     * @return 한 곳이라도 실제로 지웠으면 true
     */
    public boolean delete(String storageKey) {
        if (!isManagedKey(storageKey)) {
            return false;
        }
        boolean deleted = deleteIfExists(privateRoot, storageKey);
        if (LEGACY_READ_FALLBACK) {
            deleted |= deleteIfExists(legacyPublicRoot, storageKey);
        }
        return deleted;
    }

    /** 이 저장소가 다루는 저장 키인지. (공용 스티커 같은 다른 경로를 걸러낼 때 쓴다) */
    public boolean isManagedKey(String storageKey) {
        return storageKey != null && MANAGED_STORAGE_KEY.matcher(storageKey).matches();
    }

    /**
     * private 루트에서 먼저 찾고, 없으면 예전 공개 업로드 루트에서 찾는다.
     * 어느 쪽이든 심볼릭 링크와 루트 밖 경로는 받지 않는다.
     */
    private Path resolveExistingFile(String storageKey) {
        if (!isManagedKey(storageKey)) {
            throw new IllegalArgumentException("올바르지 않은 사진 저장 키입니다.");
        }
        Optional<Path> found = readableFile(privateRoot, storageKey);
        if (found.isEmpty() && LEGACY_READ_FALLBACK) {
            found = readableFile(legacyPublicRoot, storageKey);
        }
        return found.orElseThrow(() -> new IllegalArgumentException("사진 파일을 찾을 수 없습니다."));
    }

    /**
     * 그 루트 아래의 실제 파일. 보통 파일이 아니거나 링크를 타고 루트를 벗어나면 비어 있다.
     */
    private Optional<Path> readableFile(Path root, String storageKey) {
        Path target;
        try {
            target = root.resolve(relativeOf(storageKey)).normalize();
        } catch (InvalidPathException exception) {
            return Optional.empty();
        }
        if (!target.startsWith(root)) {
            return Optional.empty();
        }
        if (Files.isSymbolicLink(target) || !Files.isRegularFile(target)) {
            return Optional.empty();
        }
        try {
            Path realRoot = root.toRealPath();
            Path realTarget = target.toRealPath();
            return realTarget.startsWith(realRoot) ? Optional.of(realTarget) : Optional.empty();
        } catch (IOException exception) {
            return Optional.empty();
        }
    }

    private boolean deleteIfExists(Path root, String storageKey) {
        Optional<Path> target = readableFile(root, storageKey);
        if (target.isEmpty()) {
            return false;
        }
        try {
            return Files.deleteIfExists(target.get());
        } catch (IOException exception) {
            // 정리 실패가 원래 요청을 깨뜨리지 않게 한다. 남은 파일은 다음 파기에서 다시 만난다.
            return false;
        }
    }

    private StoredImageFormat detectFormat(Path source) {
        Optional<StoredImageFormat> format;
        try {
            format = StoredImageFormat.detect(source);
        } catch (IOException exception) {
            throw new IllegalArgumentException("사진 파일을 읽지 못했습니다.", exception);
        }
        return format.orElseThrow(() ->
                new IllegalArgumentException("사진 파일 형식이 올바르지 않습니다."));
    }

    private Path resolveManagedDirectory(String directory) {
        if (!MANAGED_DIRECTORIES.contains(directory)) {
            throw new IllegalArgumentException("개인 다이어리 사진 폴더가 아닙니다.");
        }
        Path resolved = privateRoot.resolve(directory).normalize();
        ensureContained(privateRoot, resolved);
        return resolved;
    }

    /** 저장 키에서 루트 기준 상대 경로 ({@code diary-pages/{uuid}.jpg}) */
    private String relativeOf(String storageKey) {
        return storageKey.substring(STORAGE_KEY_PREFIX.length());
    }

    private String fileNameOf(String storageKey) {
        String relative = relativeOf(storageKey);
        return relative.substring(relative.indexOf('/') + 1);
    }

    private String extensionOf(String storageKey) {
        return storageKey.substring(storageKey.lastIndexOf('.') + 1);
    }

    private void ensureContained(Path root, Path candidate) {
        if (!candidate.startsWith(root)) {
            throw new IllegalArgumentException("허용된 저장 경로를 벗어날 수 없습니다.");
        }
    }

    private void deleteQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
            // 원래 저장 실패를 우선 전달한다.
        }
    }

    /** 통제된 응답이 그대로 쓰는 파일 정보. */
    public record StoredPhotoFile(Path path, String contentType, long contentLength) {
    }
}
