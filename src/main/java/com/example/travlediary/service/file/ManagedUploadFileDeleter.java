package com.example.travlediary.service.file;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 업로드 폴더 안의 관리 파일 하나를 지운다.
 *
 * <p>여행일기 표지/페이지 사진처럼 업로드 폴더에 남는 파일은 지금까지 화면 코드가 직접 지웠고
 * 재사용할 수 있는 자리가 없었다. 최종 파기 worker 가 Controller 에 기대지 않도록 여기로 모은다.
 *
 * <p>어떤 폴더를 지워도 되는지는 부르는 쪽의 정책이다. 이 클래스는 업로드 루트 밖으로 나가는
 * 경로를 막는 일만 한다. 이미 없는 파일은 목표 상태가 이미 이뤄진 것으로 본다.
 */
@Service
public class ManagedUploadFileDeleter {

    private static final String UPLOAD_URL_PREFIX = "/uploads/";

    private final Path uploadRoot;

    public ManagedUploadFileDeleter(@Value("${custom.upload-path}") String uploadPath) {
        this.uploadRoot = Paths.get(uploadPath).toAbsolutePath().normalize();
    }

    /**
     * @param imageUrl {@code /uploads/...} 형태의 서비스 표시 경로
     * @throws IOException 파일이 있는데 지우지 못했을 때. 재시도 대상이다.
     */
    public DeletionOutcome delete(String imageUrl) throws IOException {
        Path target = resolveManagedFile(imageUrl);
        if (target == null) {
            return DeletionOutcome.REJECTED;
        }
        return Files.deleteIfExists(target)
                ? DeletionOutcome.DELETED
                : DeletionOutcome.ALREADY_ABSENT;
    }

    /**
     * 업로드 루트 안의 실제 파일 경로. 루트 밖으로 계산되면 null 이다.
     *
     * <p>문자열을 잇지 않고 normalize 뒤 startsWith 로 확인하고, 심볼릭 링크로 루트를 벗어나는
     * 경우를 막기 위해 담긴 디렉터리의 실제 경로까지 다시 본다. 파일 자체가 심볼릭 링크라면
     * {@link Files#deleteIfExists}가 링크만 지우므로 가리키는 대상은 건드리지 않는다.
     */
    private Path resolveManagedFile(String imageUrl) throws IOException {
        if (imageUrl == null || !imageUrl.startsWith(UPLOAD_URL_PREFIX)) {
            return null;
        }
        String relativePath = imageUrl.substring(UPLOAD_URL_PREFIX.length());
        if (relativePath.isBlank() || relativePath.contains("\\")) {
            return null;
        }

        Path target;
        try {
            target = uploadRoot.resolve(relativePath).normalize();
        } catch (InvalidPathException exception) {
            return null;
        }
        if (!target.startsWith(uploadRoot) || target.equals(uploadRoot)) {
            return null;
        }
        if (Files.notExists(uploadRoot)) {
            // 업로드 폴더 자체가 없으면 지울 파일도 없다.
            return null;
        }

        Path realUploadRoot = uploadRoot.toRealPath();
        Path parent = target.getParent();
        if (parent == null || Files.notExists(parent)) {
            return null;
        }
        Path realParent = parent.toRealPath();
        if (!realParent.startsWith(realUploadRoot)) {
            return null;
        }
        return realParent.resolve(target.getFileName());
    }

    public enum DeletionOutcome {
        /** 실제로 지웠다. */
        DELETED,
        /** 이미 없었다. 목표 상태는 이뤄져 있다. */
        ALREADY_ABSENT,
        /** 업로드 루트 밖을 가리키는 등 지울 수 없는 경로다. */
        REJECTED
    }
}
