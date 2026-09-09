package com.example.travlediary.service.translation;

import com.example.travlediary.model.translation.CourseLanguageBackfillRow;
import com.example.travlediary.repository.course.CourseMapper;
import com.example.travlediary.service.post.PostContentSanitizer;
import org.jsoup.Jsoup;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class CourseLanguageBackfillService {
    private static final int MAX_BATCH_SIZE = 2_000;

    private final CourseMapper mapper;
    private final LocalContentLanguageDetector languageDetector;
    private final PostContentSanitizer contentSanitizer;
    private final int batchSize;

    public CourseLanguageBackfillService(
            CourseMapper mapper,
            LocalContentLanguageDetector languageDetector,
            PostContentSanitizer contentSanitizer,
            @Value("${translation.backfill.courses.batch-size:500}") int batchSize) {
        this.mapper = mapper;
        this.languageDetector = languageDetector;
        this.contentSanitizer = contentSanitizer;
        this.batchSize = Math.max(1, Math.min(batchSize, MAX_BATCH_SIZE));
    }

    public CourseLanguageBackfillResult run() {
        long cursor = 0L;
        long scanned = 0L;
        long titleUpdated = 0L;
        long contentUpdated = 0L;
        long undeterminedFields = 0L;

        while (true) {
            List<CourseLanguageBackfillRow> rows =
                    mapper.findUndeterminedCourseLanguagesAfter(cursor, batchSize);
            if (rows == null || rows.isEmpty()) break;

            for (CourseLanguageBackfillRow row : rows) {
                cursor = Math.max(cursor, row.getId());
                scanned++;
                if ("und".equals(normalizeLanguage(row.getTitleSourceLanguage()))) {
                    String detected = languageDetector.detect(row.getTitle()).code();
                    if ("und".equals(detected)) {
                        undeterminedFields++;
                    } else {
                        titleUpdated += mapper.updateCourseTitleLanguageIfUndetermined(
                                row.getId(), detected, row.getTitle(), row.getUpdatedAt());
                    }
                }
                if ("und".equals(normalizeLanguage(row.getContentSourceLanguage()))) {
                    String safeHtml = contentSanitizer.sanitize(row.getContent());
                    String detected = languageDetector.detect(
                            Jsoup.parseBodyFragment(safeHtml).text()).code();
                    if ("und".equals(detected)) {
                        undeterminedFields++;
                    } else {
                        contentUpdated += mapper.updateCourseContentLanguageIfUndetermined(
                                row.getId(), detected, row.getContent(), row.getUpdatedAt());
                    }
                }
            }
        }
        return new CourseLanguageBackfillResult(
                scanned, titleUpdated, contentUpdated, undeterminedFields);
    }

    private String normalizeLanguage(String language) {
        return language == null || language.isBlank() ? "und" : language;
    }
}
