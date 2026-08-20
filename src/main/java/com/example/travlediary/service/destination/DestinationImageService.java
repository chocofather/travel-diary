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
import java.util.Collections;
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
        if (files == null || files.length == 0) return;

        List<DestinationImage> existingImages = destinationMapper.findImagesByDestinationId(destId);
        int orderIndex = nextOrderIndex(existingImages);
        int uploadIndex = 0;

        for (MultipartFile file : files) {
            if (file == null || file.isEmpty()) continue;

            // ✅ 실제 저장 및 URL 경로 반환
            String imageUrl = fileUploadService.saveFile(file, "destinations");
            boolean isMain = mainIdx != null && mainIdx == uploadIndex;
            if (isMain) {
                destinationMapper.clearMainImagesByDestinationId(destId);
            }

            DestinationImage img = new DestinationImage();
            img.setDestinationId(destId);
            img.setImageUrl(imageUrl);
            img.setIsMain(isMain);

            int finalIdx = uploadIndex;
            img.setIsSlide(slideIdx != null &&
                    Arrays.stream(slideIdx).anyMatch(i -> i == finalIdx));

            img.setOrderIndex(orderIndex++);
            destinationMapper.insertImage(img);
            uploadIndex++;
        }
    }

    @Transactional
    public void saveImages(Long destId,
                           MultipartFile[] files,
                           boolean main,
                           boolean slide) {
        Integer mainIdx = main ? 0 : null;
        Integer[] slideIdx = slide ? allUploadIndexes(files) : new Integer[0];
        saveImages(destId, files, mainIdx, slideIdx);
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

            List<DestinationImage> remainingImages = destinationMapper
                    .findImagesByDestinationId(image.getDestinationId());
            reorderImages(remainingImages);

            if (Boolean.TRUE.equals(image.getIsMain()) && !remainingImages.isEmpty()) {
                destinationMapper.clearMainImagesByDestinationId(image.getDestinationId());
                destinationMapper.setMainImage(remainingImages.get(0).getId());
            }
        }
    }

    private int nextOrderIndex(List<DestinationImage> images) {
        return images == null || images.isEmpty()
                ? 0
                : images.stream()
                .map(DestinationImage::getOrderIndex)
                .filter(java.util.Objects::nonNull)
                .max(Integer::compareTo)
                .orElse(-1) + 1;
    }

    private Integer[] allUploadIndexes(MultipartFile[] files) {
        if (files == null || files.length == 0) return new Integer[0];

        int nonEmptyCount = (int) Arrays.stream(files)
                .filter(file -> file != null && !file.isEmpty())
                .count();
        Integer[] indexes = new Integer[nonEmptyCount];
        for (int i = 0; i < nonEmptyCount; i++) {
            indexes[i] = i;
        }
        return indexes;
    }

    private void reorderImages(List<DestinationImage> images) {
        List<DestinationImage> remainingImages = images == null
                ? Collections.emptyList()
                : images;
        for (int orderIndex = 0; orderIndex < remainingImages.size(); orderIndex++) {
            destinationMapper.updateImageOrder(remainingImages.get(orderIndex).getId(), orderIndex);
        }
    }
}
