package com.example.travlediary.model;

import lombok.Data;
import lombok.NoArgsConstructor;
import java.sql.Timestamp;

@Data
@NoArgsConstructor
public class Bookmark {
    private Long id;
    private Long userId;
    private BookmarkTargetType targetType;
    private Long targetId;
    private Timestamp createdAt;
}
