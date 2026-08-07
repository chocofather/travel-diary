package com.example.travlediary.service.board;

import com.example.travlediary.dto.BoardListDto;
import com.example.travlediary.repository.board.BoardMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class BoardServiceImpl implements BoardService {

    private static final Set<String> BOARD_TYPES = Set.of("post", "course");
    private static final Set<String> POST_TYPES = Set.of("QUESTION", "TIP");
    private static final Set<String> SORT_TYPES = Set.of("latest", "oldest", "views", "comments", "bookmarks");

    private final BoardMapper boardMapper;

    @Override
    public List<BoardListDto> getBoardList(String boardType, String postType, String sort, int page, int size) {
        int safePage = Math.max(page, 1);
        int safeSize = clampSize(size);
        long offset = (long) (safePage - 1) * safeSize;

        return boardMapper.findBoardList(
                normalizeBoardType(boardType),
                normalizePostType(postType),
                normalizeSort(sort),
                offset,
                safeSize
        );
    }

    @Override
    public int getBoardCount(String boardType, String postType) {
        return boardMapper.countBoard(
                normalizeBoardType(boardType),
                normalizePostType(postType)
        );
    }

    @Override
    public List<BoardListDto> getBoardListByUserId(Long userId, String type, int page, int size) {
        int safePage = Math.max(page, 1);
        int safeSize = clampSize(size);
        long offset = (long) (safePage - 1) * safeSize;
        ProfileFilter filter = profileFilter(type);

        return boardMapper.findBoardListByUserId(
                userId,
                filter.boardType(),
                filter.postType(),
                offset,
                safeSize
        );
    }

    @Override
    public int getBoardCountByUserId(Long userId, String type) {
        ProfileFilter filter = profileFilter(type);
        return boardMapper.countBoardByUserId(userId, filter.boardType(), filter.postType());
    }

    private int clampSize(int size) {
        return Math.min(Math.max(size, 1), 100);
    }

    private String normalizeBoardType(String boardType) {
        if (boardType == null) {
            return null;
        }
        String normalized = boardType.toLowerCase(Locale.ROOT);
        return BOARD_TYPES.contains(normalized) ? normalized : null;
    }

    private String normalizePostType(String postType) {
        if (postType == null) {
            return null;
        }
        String normalized = postType.toUpperCase(Locale.ROOT);
        return POST_TYPES.contains(normalized) ? normalized : null;
    }

    private String normalizeSort(String sort) {
        if (sort == null) {
            return "latest";
        }
        String normalized = sort.toLowerCase(Locale.ROOT);
        return SORT_TYPES.contains(normalized) ? normalized : "latest";
    }

    private ProfileFilter profileFilter(String type) {
        return switch (type == null ? "" : type.toLowerCase(Locale.ROOT)) {
            case "question" -> new ProfileFilter("post", "QUESTION");
            case "tip" -> new ProfileFilter("post", "TIP");
            case "course" -> new ProfileFilter("course", null);
            default -> new ProfileFilter(null, null);
        };
    }

    private record ProfileFilter(String boardType, String postType) {
    }
}
