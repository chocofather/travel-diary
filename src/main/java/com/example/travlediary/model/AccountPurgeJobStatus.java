package com.example.travlediary.model;

/**
 * 최종 파기 작업의 진행 상태.
 *
 * <p>PENDING → DB_DONE → COMPLETED 순으로만 나아간다.
 * 후처리 task 가 한 건도 없으면 DB 처리만으로 끝이므로 곧바로 COMPLETED 가 된다.
 */
public enum AccountPurgeJobStatus {
    /** job 을 만들었고 DB 파기 전. */
    PENDING,
    /** DB 안의 파기와 회원 익명화가 끝났고, 파일/외부 연동 후처리가 남았다. */
    DB_DONE,
    /** 후처리까지 모두 끝났다. */
    COMPLETED
}
