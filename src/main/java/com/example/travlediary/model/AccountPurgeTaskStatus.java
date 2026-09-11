package com.example.travlediary.model;

/** 후처리 task 한 건의 상태. 실행 worker 는 아직 없고 지금은 PENDING 으로만 쌓인다. */
public enum AccountPurgeTaskStatus {
    PENDING,
    COMPLETED,
    FAILED
}
