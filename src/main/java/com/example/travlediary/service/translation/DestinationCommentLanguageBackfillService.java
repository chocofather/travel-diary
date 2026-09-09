package com.example.travlediary.service.translation;

import com.example.travlediary.model.translation.DestinationCommentLanguageBackfillRow;
import com.example.travlediary.repository.comment.DestinationCommentMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class DestinationCommentLanguageBackfillService {
    private static final int MAX_BATCH_SIZE = 2_000;

    private final DestinationCommentMapper mapper;
    private final LocalContentLanguageDetector languageDetector;
    private final int batchSize;

    public DestinationCommentLanguageBackfillService(
            DestinationCommentMapper mapper,
            LocalContentLanguageDetector languageDetector,
            @Value("${translation.backfill.destination-comments.batch-size:500}") int batchSize) {
        this.mapper = mapper;
        this.languageDetector = languageDetector;
        this.batchSize = Math.max(1, Math.min(batchSize, MAX_BATCH_SIZE));
    }

    public DestinationCommentLanguageBackfillResult run() {
        long cursor = 0L;
        long scanned = 0L;
        long updated = 0L;
        long undetermined = 0L;

        while (true) {
            List<DestinationCommentLanguageBackfillRow> rows =
                    mapper.findUndeterminedLanguagesAfter(cursor, batchSize);
            if (rows == null || rows.isEmpty()) break;

            for (DestinationCommentLanguageBackfillRow row : rows) {
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
        return new DestinationCommentLanguageBackfillResult(scanned, updated, undetermined);
    }
}
