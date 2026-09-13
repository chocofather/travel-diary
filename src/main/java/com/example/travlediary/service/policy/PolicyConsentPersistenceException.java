package com.example.travlediary.service.policy;

/**
 * 동의 이력을 저장하지 못했다. 가입 트랜잭션 안에서 던져 users INSERT 까지 함께 되돌린다.
 * 회원만 생기고 동의 이력이 없는 상태를 남기지 않기 위해서다.
 */
public class PolicyConsentPersistenceException extends RuntimeException {

    public PolicyConsentPersistenceException(String message) {
        super(message);
    }
}
