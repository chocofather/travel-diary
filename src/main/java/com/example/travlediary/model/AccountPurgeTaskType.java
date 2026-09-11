package com.example.travlediary.model;

/**
 * 커밋 이후에 따로 처리해야 하는 후처리 종류.
 * DB row 를 지우고 나면 다시 알아낼 수 없는 값을 미리 task 로 옮겨 둔다.
 */
public enum AccountPurgeTaskType {
    /** 업로드 폴더의 파일 삭제. target_value 는 관리 업로드 경로다. */
    FILE_DELETE,
    /** 외부 provider 연결 해제. target_value 는 provider 사용자 식별값이다. */
    SOCIAL_UNLINK
}
