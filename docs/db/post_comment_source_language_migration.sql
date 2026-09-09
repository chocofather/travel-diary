-- 게시글 댓글 원문 언어 저장 컬럼 적용 SQL 초안.
-- 이 파일은 애플리케이션에서 자동 실행되지 않으며, 배포 전에 DBA 검토 후 별도로 적용한다.

ALTER TABLE `post_comments`
  ADD COLUMN `source_language` varchar(10)
    CHARACTER SET ascii COLLATE ascii_bin
    NOT NULL DEFAULT 'und'
    AFTER `content`;
