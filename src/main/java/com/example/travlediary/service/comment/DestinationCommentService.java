package com.example.travlediary.service.comment;

import com.example.travlediary.dto.CommentDto;
import com.example.travlediary.dto.CommentImageDto;
import com.example.travlediary.dto.CommentLocationDto;
import com.example.travlediary.dto.PageResult;
import com.example.travlediary.dto.WriterDto;
import com.example.travlediary.model.DestinationComment;
import com.example.travlediary.model.User;
import com.example.travlediary.repository.comment.DestinationCommentMapper;
import com.example.travlediary.repository.destination.DestinationMapper;
import com.example.travlediary.repository.user.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.sql.Timestamp;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DestinationCommentService {
    private static final int DEFAULT_PAGE_SIZE = 5;

    private final DestinationMapper destinationMapper; // 또는 별도 CommentMapper
    private final DestinationCommentMapper destinationCommentMapper;
    private final UserMapper userMapper;

    @Value("${custom.upload-path}")
    private String uploadPath;

    public List<DestinationComment> getCommentsByDestination(Long destinationId) {
        return destinationCommentMapper.findByDestinationId(destinationId);
    }

    public int getCommentCountByDestinationId(Long destinationId) {
        return destinationCommentMapper.countByDestinationId(destinationId);
    }

    private Long findRootId(Long commentId, Map<Long, Long> parentMap) {
        while (parentMap.get(commentId) != null) {
            commentId = parentMap.get(commentId);
        }
        return commentId;
    }

    private List<DestinationComment> mergeParentAndReplies(List<DestinationComment> parents, List<DestinationComment> replies) {
        Map<Long, List<DestinationComment>> groupedReplies = replies.stream()
                .collect(Collectors.groupingBy(DestinationComment::getParentCommentId));

        List<DestinationComment> result = new java.util.ArrayList<>();

        for (DestinationComment parent : parents) {
            result.add(parent); // 부모 먼저 추가
            List<DestinationComment> children = groupedReplies.get(parent.getId());
            if (children != null) {
                result.addAll(children); // 대댓글 추가
            }
        }

        return result;
    }

    @Transactional
    public boolean softDelete(Long commentId, Long userId) {
        // 댓글 하나 가져오기
        DestinationComment comment = destinationCommentMapper.findById(commentId);

        // 존재하지 않거나 이미 삭제된 경우 false
        if (comment == null || comment.getDeleted()) return false;

        // 사용자 정보 조회
        User user = userMapper.findById(userId);
        boolean isAdmin = user != null && user.getRole().equals("ADMIN");

        // 본인이거나 관리자일 때만 삭제 가능
        if (!comment.getUserId().equals(userId) && !isAdmin) {
            return false;
        }

        // 삭제 처리
        comment.setDeleted(true);
        comment.setDeletedAt(new Timestamp(System.currentTimeMillis()));
        destinationCommentMapper.updateDeleted(comment); // DB 반영
        return true;
    }

    @Transactional
    public boolean updateComment(Long commentId, Long userId, String content) {
        // 댓글 1개 조회
        DestinationComment comment = destinationCommentMapper.findById(commentId);

        // 댓글이 없거나 삭제됐거나 작성자가 아니면 실패
        if (comment == null || comment.getDeleted() || !comment.getUserId().equals(userId)) {
            return false;
        }

        // 내용과 수정 시간 업데이트
        comment.setContent(content);
        comment.setUpdatedAt(new Timestamp(System.currentTimeMillis()));

        // DB 반영
        destinationCommentMapper.updateContent(comment);
        return true;
    }


    @Transactional
    public CommentDto create(Long destinationId,
                             Long userId,
                             String content,
                             MultipartFile imageFile,
                             Long parentCommentId) {
        String imageUrl = null;

        // 1) 이미지가 있을 경우 저장
        if (imageFile != null && !imageFile.isEmpty()) {
            try {
                Path dir = Paths.get(uploadPath, "comments");
                if (Files.notExists(dir)) Files.createDirectories(dir);

                String filename = UUID.randomUUID() + "_" + imageFile.getOriginalFilename();
                Path filePath = dir.resolve(filename);
                imageFile.transferTo(filePath.toFile());
                imageUrl = "/uploads/comments/" + filename;
            } catch (IOException e) {
                throw new RuntimeException("댓글 이미지 업로드 실패", e);
            }
        }

        // 2) 댓글 객체 생성
        DestinationComment comment = new DestinationComment();
        comment.setDestinationId(destinationId);
        comment.setUserId(userId);
        comment.setContent(content);
        comment.setImageUrl(imageUrl);
        comment.setLikes(0);
        comment.setDeleted(false);
        comment.setCreatedAt(new Timestamp(System.currentTimeMillis()));
        comment.setUpdatedAt(new Timestamp(System.currentTimeMillis()));
        comment.setParentCommentId(parentCommentId);

        // 3) DB 저장 (MyBatis라면 auto_increment 키 적용됨)
        destinationCommentMapper.insert(comment);

        // 4) 저장 후, 방금 등록된 댓글의 전체 정보(+작성자, etc)로 CommentDto 생성
        //    (writer 등은 엔티티가 아니라 DTO로 만들어서 리턴해야 프론트에서 바로 append 가능)
        // -- writer(작성자) 정보 넣기 위해 user 테이블도 조회
        User user = userMapper.findById(userId);
        WriterDto writerDto = new WriterDto();
        writerDto.setId(user.getId());
        writerDto.setNickname(user.getNickname());
        writerDto.setProfileImage(user.getProfileImage());
        writerDto.setIsWriter(false); // 방금 작성한 건 본인이니까, 필요시 true도 가능

        CommentDto dto = new CommentDto();
        dto.setId(comment.getId());
        dto.setContent(comment.getContent());
        dto.setImageUrl(comment.getImageUrl());
        dto.setCreatedAt(comment.getCreatedAt().toString());
        dto.setUpdatedAt(comment.getUpdatedAt().toString());
        dto.setLikes(0); // 최초 0
        dto.setParentCommentId(comment.getParentCommentId());
        dto.setWriter(writerDto);
        dto.setMyComment(true);
        dto.setAdmin(user.getRole().equals("ADMIN"));
        dto.setIsLoggedIn(true);
        dto.setLikedByMe(false);

        return dto;
    }


    public List<CommentImageDto> getCommentImageDtos(Long destinationId) {
        List<DestinationComment> comments = destinationCommentMapper.selectCommentsWithImages(destinationId);
        return comments.stream()
                .map(c -> new CommentImageDto(c.getImageUrl()))
                .collect(Collectors.toList());
    }


    public List<CommentDto> getCommentDtosWithWriter(Long destinationId, Long userId, String sort) {
        List<DestinationComment> comments = switch (sort) {
            case "recent" -> destinationCommentMapper.findByDestinationIdOrderByCreatedAtDesc(destinationId);
            case "likes" -> destinationCommentMapper.findByDestinationIdOrderByLikesDesc(destinationId);
            default -> destinationCommentMapper.findByDestinationIdWithWriter(destinationId);
        };

        return enrichComments(comments, userId);
    }

    private List<CommentDto> enrichComments(List<DestinationComment> comments, Long currentUserId) {
        Map<Long, Long> parentMap = new HashMap<>();
        Map<Long, Long> commentIdToUserIdMap = new HashMap<>();

        for (DestinationComment c : comments) {
            parentMap.put(c.getId(), c.getParentCommentId());
            commentIdToUserIdMap.put(c.getId(), c.getUserId());
        }

        boolean tempIsAdmin = false;
        if (currentUserId != null) {
            User user = userMapper.findById(currentUserId);
            if (user != null && "ADMIN".equals(user.getRole())) {
                tempIsAdmin = true;
            }
        }
        final boolean isAdmin = tempIsAdmin;

        return comments.stream().map(comment -> {
            CommentDto dto = new CommentDto();
            dto.setId(comment.getId());
            dto.setContent(comment.getContent());
            dto.setImageUrl(comment.getImageUrl());
            dto.setCreatedAt(comment.getCreatedAt().toString());
            dto.setUpdatedAt(comment.getUpdatedAt().toString());
            dto.setLikes(comment.getLikes().intValue());
            dto.setParentCommentId(comment.getParentCommentId());

            // 작성자 정보
            WriterDto writerDto = new WriterDto();
            writerDto.setId(comment.getWriter().getId());
            writerDto.setNickname(comment.getWriter().getNickname());
            writerDto.setProfileImage(comment.getWriter().getProfileImage());

            // [작성자] 태그 여부 판단
            boolean isWriter = false;
            if (comment.getParentCommentId() != null) {
                Long rootId = findRootId(comment.getParentCommentId(), parentMap);
                Long rootWriterId = commentIdToUserIdMap.get(rootId);
                if (comment.getUserId().equals(rootWriterId)) {
                    isWriter = true;
                }
            }
            writerDto.setIsWriter(isWriter);
            dto.setWriter(writerDto);

            dto.setMyComment(currentUserId != null && comment.getUserId().equals(currentUserId));
            dto.setAdmin(isAdmin);
            dto.setIsLoggedIn(currentUserId != null);

            // 좋아요 여부
            boolean liked = destinationCommentMapper.existsLikeByUserAndComment(currentUserId, comment.getId());
            dto.setLikedByMe(liked);

            return dto;
        }).collect(Collectors.toList());
    }


    // 댓글 이미지 수정
    public void updateCommentImage(Long commentId, MultipartFile imageFile) throws IOException {
        // 1. 기존 이미지 경로 조회
        String oldImagePath = destinationCommentMapper.findImagePathById(commentId);

        // 2. 기존 이미지가 있다면 삭제
        if (oldImagePath != null && !oldImagePath.isEmpty()) {
            // "/uploads/comments/abc.jpg" → "comments/abc.jpg"
            String relativePath = oldImagePath.replaceFirst("^/uploads/", "");
            Path oldPath = Paths.get(uploadPath, relativePath);
            Files.deleteIfExists(oldPath);
        }

        // 3. 새 이미지가 있다면 저장
        if (imageFile != null && !imageFile.isEmpty()) {
            String newFilename = UUID.randomUUID() + "_" + imageFile.getOriginalFilename();
            Path savePath = Paths.get(uploadPath, "comments", newFilename);
            Files.createDirectories(savePath.getParent()); // 폴더 없으면 생성
            Files.copy(imageFile.getInputStream(), savePath, StandardCopyOption.REPLACE_EXISTING);

            // 4. DB에 새 이미지 경로 반영
            destinationCommentMapper.updateImagePath(commentId, "/uploads/comments/" + newFilename);
        }
    }

    public PageResult<CommentDto> getCommentsPaged(Long destinationId, Long userId, int page, int size, String sort) {
        int safePage = Math.max(page, 0);
        int safeSize = size <= 0 ? DEFAULT_PAGE_SIZE : Math.min(size, 50);
        String safeSort = normalizePageSort(sort);
        int totalThreads = destinationCommentMapper.countRootComments(destinationId);
        int totalCommentCount = destinationCommentMapper.countByDestinationId(destinationId);
        long offset = (long) safePage * safeSize;

        if (totalThreads == 0 || offset >= totalThreads) {
            return new PageResult<>(List.of(), totalThreads, safePage, safeSize, totalCommentCount);
        }

        // 1. 부모 댓글만 페이징으로 가져오기
        List<DestinationComment> parentComments = destinationCommentMapper.findPagedParentComments(
                destinationId, (int) offset, safeSize, safeSort);

        if (parentComments.isEmpty()) {
            return new PageResult<>(List.of(), totalThreads, safePage, safeSize, totalCommentCount);
        }

        // 2. 부모 ID 목록 뽑아서
        List<Long> parentIds = parentComments.stream()
                .map(DestinationComment::getId)
                .toList();

        // 3. 그 부모들의 모든 대댓글(자식) 한 번에 조회
        List<DestinationComment> replies = parentIds.isEmpty()
                ? List.of()
                : destinationCommentMapper.findRepliesForParents(destinationId, parentIds);

        // 4. 부모+자식 합친 flat 리스트로 만들기 (트리 계층 필요하면 JS에서 groupByParent)
        List<DestinationComment> merged = new java.util.ArrayList<>(parentComments);
        merged.addAll(replies);

        // 5. DTO 변환
        List<CommentDto> dtos = merged.stream()
                .map(c -> enrichComment(c, userId))
                .toList();

        return new PageResult<>(dtos, totalThreads, safePage, safeSize, totalCommentCount);
    }

    @Transactional(readOnly = true)
    public Optional<CommentLocationDto> getCommentLocation(Long destinationId, Long commentId) {
        if (destinationId == null || commentId == null) {
            return Optional.empty();
        }
        Long rootId = destinationCommentMapper.findActiveRootIdForLocation(
                destinationId, commentId);
        if (rootId == null) {
            return Optional.empty();
        }
        int precedingRootCount = destinationCommentMapper.countRootCommentsBefore(
                destinationId, rootId);
        return Optional.of(new CommentLocationDto(
                precedingRootCount / DEFAULT_PAGE_SIZE + 1));
    }

    private String normalizePageSort(String sort) {
        return switch (sort == null ? "" : sort) {
            case "oldest" -> "oldest";
            case "likes" -> "likes";
            case "recent", "latest" -> "latest";
            default -> "latest";
        };
    }



    private CommentDto enrichComment(DestinationComment comment, Long currentUserId) {
        CommentDto dto = new CommentDto();
        dto.setId(comment.getId());
        dto.setContent(comment.getContent());
        dto.setImageUrl(comment.getImageUrl());
        dto.setCreatedAt(comment.getCreatedAt().toString());
        dto.setUpdatedAt(comment.getUpdatedAt().toString());
        dto.setLikes(comment.getLikes().intValue());
        dto.setParentCommentId(comment.getParentCommentId());

        WriterDto writerDto = new WriterDto();
        writerDto.setId(comment.getWriter().getId());
        writerDto.setNickname(comment.getWriter().getNickname());
        writerDto.setProfileImage(comment.getWriter().getProfileImage());
        writerDto.setIsWriter(false); // 계층 판단 생략 (단일 기준이므로)

        dto.setWriter(writerDto);
        dto.setMyComment(currentUserId != null && comment.getUserId().equals(currentUserId));

        boolean isAdmin = false;
        if (currentUserId != null) {
            User user = userMapper.findById(currentUserId);
            if (user != null && "ADMIN".equals(user.getRole())) {
                isAdmin = true;
            }
        }

        dto.setAdmin(isAdmin);
        dto.setIsLoggedIn(currentUserId != null);

        boolean liked = destinationCommentMapper.existsLikeByUserAndComment(currentUserId, comment.getId());
        dto.setLikedByMe(liked);

        return dto;
    }

    // 여러 여행지 댓글 수 카운트
    public Map<Long, Integer> countCommentsByDestinationIds(List<Long> destinationIds) {
        if (destinationIds == null || destinationIds.isEmpty()) {
            return Collections.emptyMap();
        }
        // 1. 모든 id 0으로 초기화 (댓글 0개인 여행지 대응)
        Map<Long, Integer> result = new HashMap<>();
        for (Long id : destinationIds) {
            result.put(id, 0);
        }
        // 2. 쿼리 결과값만 실제 숫자로 덮어씀
        List<Map<String, Object>> rows = destinationCommentMapper.countByDestinationIds(destinationIds);
        for (Map<String, Object> row : rows) {
            Long destId = ((Number) row.get("destination_id")).longValue(); // alias 꼭 소문자!
            Integer cnt = row.get("cnt") == null ? 0 : ((Number) row.get("cnt")).intValue();
            result.put(destId, cnt);
        }
        return result;
    }


}
