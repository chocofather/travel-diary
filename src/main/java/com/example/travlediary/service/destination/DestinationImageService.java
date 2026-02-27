package com.example.travlediary.service.destination;

import com.example.travlediary.model.DestinationImage;
import com.example.travlediary.repository.destination.DestinationMapper;
import com.example.travlediary.service.file.FileUploadService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.util.Arrays;
import java.util.List;

@RequiredArgsConstructor
@Service
public class DestinationImageService {

    private final DestinationMapper destinationMapper;
    private final FileUploadService fileUploadService;

    @Value("${custom.upload-path}")
    private String uploadDir;

    @Transactional
    public void saveImages(Long destId,
                           MultipartFile[] files,
                           Integer mainIdx,
                           Integer[] slideIdx) {
        int idx = 0;

        for (MultipartFile file : files) {
            if (file.isEmpty()) continue;

            // ✅ 실제 저장 및 URL 경로 반환
            String imageUrl = fileUploadService.saveFile(file, "destinations");

            DestinationImage img = new DestinationImage();
            img.setDestinationId(destId);
            img.setImageUrl(imageUrl);
            img.setIsMain(mainIdx != null && mainIdx == idx);

            int finalIdx = idx;
            img.setIsSlide(slideIdx != null &&
                    Arrays.stream(slideIdx).anyMatch(i -> i == finalIdx));

            img.setOrderIndex(idx++);
            destinationMapper.insertImage(img);
        }
    }

    public List<DestinationImage> getImages(Long destId) {
        return destinationMapper.findImagesByDestinationId(destId);
    }

    @Transactional
    public void deleteImageById(Long imageId) {
        DestinationImage image = destinationMapper.findImageById(imageId);
        if (image != null) {
            String relativePath = image.getImageUrl().replaceFirst("/uploads/", "");
            String fullPath = uploadDir + File.separator + relativePath;

            File file = new File(fullPath);
            if (file.exists()) file.delete();

            destinationMapper.deleteImageById(imageId);
        }
    }
}
