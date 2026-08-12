package com.example.travlediary.service.destination;

import com.example.travlediary.model.Bookmark;
import com.example.travlediary.model.BookmarkTargetType;
import com.example.travlediary.repository.bookmark.BookmarkMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;

@Service
@RequiredArgsConstructor
public class DestinationBookmarkService {

    private final BookmarkMapper bookmarkMapper;

    private static final String TARGET_TYPE = BookmarkTargetType.DESTINATION.name();

    // 찜 여부 확인
    public boolean isBookmarked(Long userId, Long destinationId) {
        return bookmarkMapper.findByUserAndTarget(userId, TARGET_TYPE, destinationId) != null;
    }

    // 찜 토글 (찜 → 찜취소 / 없으면 추가)
    @Transactional
    public boolean toggleBookmark(Long userId, Long destinationId) {
        if (isBookmarked(userId, destinationId)) {
            bookmarkMapper.delete(userId, TARGET_TYPE, destinationId);
            return false; // 찜 해제
        } else {
            Bookmark bookmark = new Bookmark();
            bookmark.setUserId(userId);
            bookmark.setTargetType(BookmarkTargetType.DESTINATION);
            bookmark.setTargetId(destinationId);
            bookmark.setCreatedAt(new Timestamp(System.currentTimeMillis()));
            bookmarkMapper.insert(bookmark);
            return true; // 찜 추가
        }
    }

    //  여행지별 찜한 사람 수
    public int getBookmarkCount(Long destinationId) {
        return bookmarkMapper.countByTarget(TARGET_TYPE, destinationId);
    }

    @Transactional
    public void removeBookmark(Long userId, Long destinationId) {
        bookmarkMapper.delete(userId, TARGET_TYPE, destinationId);
    }
}
