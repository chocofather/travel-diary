package com.example.travlediary.service.file;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.util.UUID;

@Service
public class FileUploadService {

    private final String uploadDir;

    public FileUploadService(@Value("${custom.upload-path}") String uploadDir) {
        this.uploadDir = uploadDir;
    }

    /**
     * 기본 저장 - 상위 upload 폴더에 저장
     */
    public String saveFile(MultipartFile file) {
        return saveFile(file, "");
    }

    /**
     * 하위 디렉토리 포함 저장
     * @param file MultipartFile
     * @param subDir "events", "destinations" 등 하위 폴더 이름
     * @return 저장된 파일의 웹 URL 경로 (ex: /uploads/events/uuid.jpg)
     */
    public String saveFile(MultipartFile file, String subDir) {
        if (file == null || file.isEmpty()) return null;

        // 하위 폴더 지정
        File dir = subDir.isEmpty()
                ? new File(uploadDir)
                : new File(uploadDir + File.separator + subDir);

        if (!dir.exists()) dir.mkdirs();

        String original = file.getOriginalFilename();
        String ext = "";

        int dotIdx = original.lastIndexOf('.');
        if (dotIdx != -1) {
            ext = original.substring(dotIdx);
        }

        String savedName = UUID.randomUUID().toString() + ext;
        File dest = new File(dir, savedName);

        try {
            file.transferTo(dest);
            System.out.println("✅ 저장 완료: " + dest.getAbsolutePath());
        } catch (IOException e) {
            System.err.println("❌ 저장 실패: " + e.getMessage());
            throw new RuntimeException("파일 저장 실패", e);
        }

        // 웹 경로 반환
        return subDir.isEmpty()
                ? "/uploads/" + savedName
                : "/uploads/" + subDir + "/" + savedName;
    }
}
