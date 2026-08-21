package com.example.travlediary.service.destination;

import com.example.travlediary.model.DestinationImage;
import com.example.travlediary.repository.destination.DestinationMapper;
import com.example.travlediary.service.file.FileUploadService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

@RequiredArgsConstructor
@Service
@Slf4j
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
            registerRollbackCleanup(imageUrl);
            boolean isMain = mainIdx != null && mainIdx == uploadIndex;

            DestinationImage img = new DestinationImage();
            img.setImageUrl(imageUrl);

            int finalIdx = uploadIndex;
            img.setIsSlide(slideIdx != null &&
                    Arrays.stream(slideIdx).anyMatch(i -> i == finalIdx));

            insertImage(destId, img, isMain, orderIndex++);
            uploadIndex++;
        }
    }

    @Transactional
    public void saveImages(Long destId, List<DestinationImage> images) {
        if (images == null || images.isEmpty()) return;

        List<DestinationImage> existingImages = destinationMapper.findImagesByDestinationId(destId);
        int orderIndex = nextOrderIndex(existingImages);
        for (DestinationImage image : images) {
            if (image == null) continue;
            insertImage(destId, image, Boolean.TRUE.equals(image.getIsMain()), orderIndex++);
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
    public void setMainImage(Long destinationId, Long imageId) {
        requireDestinationImage(destinationId, imageId);
        destinationMapper.clearMainImagesByDestinationId(destinationId);
        destinationMapper.setMainImage(imageId);
    }

    @Transactional
    public void toggleSlideImage(Long destinationId, Long imageId) {
        DestinationImage image = requireDestinationImage(destinationId, imageId);
        destinationMapper.updateImageSlide(imageId, !Boolean.TRUE.equals(image.getIsSlide()));
    }

    @Transactional
    public void deleteImageById(Long imageId) {
        DestinationImage image = destinationMapper.findImageById(imageId);
        if (image != null) {
            destinationMapper.deleteImageById(imageId);

            List<DestinationImage> remainingImages = destinationMapper
                    .findImagesByDestinationId(image.getDestinationId());
            reorderImages(remainingImages);

            if (Boolean.TRUE.equals(image.getIsMain()) && !remainingImages.isEmpty()) {
                destinationMapper.clearMainImagesByDestinationId(image.getDestinationId());
                destinationMapper.setMainImage(remainingImages.get(0).getId());
            }

            deleteFilesAfterCommit(Collections.singletonList(image.getImageUrl()));
        }
    }

    public void deleteFilesAfterCommit(List<String> imageUrls) {
        List<String> managedImageUrls = imageUrls == null
                ? List.of()
                : imageUrls.stream()
                .filter(imageUrl -> imageUrl != null && !imageUrl.isBlank())
                .distinct()
                .toList();
        if (managedImageUrls.isEmpty()) {
            return;
        }

        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    deleteFilesSafely(managedImageUrls);
                }
            });
            return;
        }

        deleteFilesSafely(managedImageUrls);
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

    private DestinationImage requireDestinationImage(Long destinationId, Long imageId) {
        DestinationImage image = destinationMapper.findImageById(imageId);
        if (image == null || !java.util.Objects.equals(destinationId, image.getDestinationId())) {
            throw new IllegalArgumentException("여행지 이미지를 찾을 수 없습니다.");
        }
        return image;
    }

    private void insertImage(Long destId,
                             DestinationImage image,
                             boolean isMain,
                             int orderIndex) {
        if (isMain) {
            destinationMapper.clearMainImagesByDestinationId(destId);
        }
        image.setDestinationId(destId);
        image.setIsMain(isMain);
        if (image.getIsSlide() == null) {
            image.setIsSlide(false);
        }
        image.setOrderIndex(orderIndex);
        destinationMapper.insertImage(image);
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

    private void registerRollbackCleanup(String imageUrl) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status == TransactionSynchronization.STATUS_COMMITTED) {
                    return;
                }
                try {
                    fileUploadService.deleteDestinationFile(imageUrl);
                } catch (RuntimeException cleanupFailure) {
                    log.warn("롤백된 신규 여행지 이미지 파일을 정리하지 못했습니다. (원인: {})",
                            cleanupFailure.getClass().getSimpleName());
                }
            }
        });
    }

    private void deleteFilesSafely(List<String> imageUrls) {
        for (String imageUrl : imageUrls) {
            try {
                fileUploadService.deleteDestinationFile(imageUrl);
            } catch (RuntimeException cleanupFailure) {
                log.warn("여행지 이미지 파일을 정리하지 못했습니다. (원인: {})",
                        cleanupFailure.getClass().getSimpleName());
            }
        }
    }
}
