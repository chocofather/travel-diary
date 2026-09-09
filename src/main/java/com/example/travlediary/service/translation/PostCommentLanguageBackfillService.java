package com.example.travlediary.service.translation;

import com.example.travlediary.model.translation.PostCommentLanguageBackfillRow;
import com.example.travlediary.repository.post.PostCommentMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class PostCommentLanguageBackfillService {
    private static final int MAX_BATCH_SIZE = 2_000;

    private final PostCommentMapper mapper;
    private final LocalContentLanguageDetector languageDetector;
    private final int batchSize;

    public PostCommentLanguageBackfillService(
            PostCommentMapper mapper,
            LocalContentLanguageDetector languageDetector,
            @Value("${translation.backfill.post-comments.batch-size:500}") int batchSize) {
        this.mapper = mapper;
        this.languageDetector = languageDetector;
        this.batchSize = Math.max(1, Math.min(batchSize, MAX_BATCH_SIZE));
    }

    public PostCommentLanguageBackfillResult run() {
        long cursor = 0L;
        long scanned = 0L;
        long updated = 0L;
        long undetermined = 0L;

        while (true) {
            List<PostCommentLanguageBackfillRow> rows =
                    mapper.findUndeterminedLanguagesAfter(cursor, batchSize);
            if (rows == null || rows.isEmpty()) break;

            for (PostCommentLanguageBackfillRow row : rows) {
                cursor = Math.max(cursor, row.getId());
                scanned++;
                DetectedLanguage detected = languageDetector.detect(row.getContent());
                if ("und".equals(detected.code())) {
                    undetermined++;
                    continue;
                }
                updated += mapper.updateDetectedLanguageIfUndetermined(
                        row.getId(), detected.code(), row.getContent(), row.getUpdatedAt());
            }
        }
        return new PostCommentLanguageBackfillResult(scanned, updated, undetermined);
    }
}
