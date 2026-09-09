package com.example.travlediarybackfill;

import com.example.travlediary.config.MyBatisConfig;
import com.example.travlediary.service.translation.LocalContentLanguageDetector;
import com.example.travlediary.service.translation.PostCommentLanguageBackfillResult;
import com.example.travlediary.service.translation.PostCommentLanguageBackfillService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Import;

@SpringBootConfiguration
@EnableAutoConfiguration
@Import({
        MyBatisConfig.class,
        LocalContentLanguageDetector.class,
        PostCommentLanguageBackfillService.class
})
public class PostCommentLanguageBackfillApplication {
    private static final Logger log =
            LoggerFactory.getLogger(PostCommentLanguageBackfillApplication.class);

    public static void main(String[] args) {
        SpringApplication application =
                new SpringApplication(PostCommentLanguageBackfillApplication.class);
        application.setWebApplicationType(WebApplicationType.NONE);
        try (ConfigurableApplicationContext context = application.run(args)) {
            PostCommentLanguageBackfillResult result = context
                    .getBean(PostCommentLanguageBackfillService.class)
                    .run();
            log.info("게시글 댓글 원문 언어 backfill 완료: 조회 {}, 갱신 {}, 미확정 {}",
                    result.scanned(), result.updated(), result.undetermined());
        }
    }
}
