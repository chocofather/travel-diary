package com.example.travlediary.service.comment;

import com.example.travlediary.dto.MyPageCommentDto;
import com.example.travlediary.dto.MyPageCommentPageDto;
import com.example.travlediary.repository.comment.MyPageCommentMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class MyPageCommentService {

    private static final int PAGE_SIZE = 10;
    private static final Set<String> COMMENT_TYPES =
            Set.of("all", "destination", "post", "course");

    private final MyPageCommentMapper myPageCommentMapper;

    @Transactional(readOnly = true)
    public MyPageCommentPageDto getMyComments(Long userId, String type, int page) {
        if (userId == null) {
            throw new IllegalArgumentException("사용자 정보를 확인할 수 없습니다.");
        }

        String safeType = normalizeType(type);
        String mapperType = "all".equals(safeType) ? null : safeType;
        int safePage = Math.max(page, 1);
        long offset = (long) (safePage - 1) * PAGE_SIZE;
        int totalCount = myPageCommentMapper.countMyComments(userId, mapperType);
        int totalPages = (int) Math.ceil((double) totalCount / PAGE_SIZE);
        List<MyPageCommentDto> comments = totalCount == 0
                ? List.of()
                : myPageCommentMapper.findMyComments(
                        userId, mapperType, offset, PAGE_SIZE);

        return new MyPageCommentPageDto(
                comments, safeType, safePage, totalPages, totalCount);
    }

    private String normalizeType(String type) {
        if (type == null) {
            return "all";
        }
        String normalized = type.toLowerCase(Locale.ROOT);
        return COMMENT_TYPES.contains(normalized) ? normalized : "all";
    }
}
