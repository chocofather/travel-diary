package com.example.travlediary.service.kto;

/** 일괄등록에서 한 항목만 실패시키는 예외. 메시지는 관리자 화면의 실패 사유로 그대로 보인다. */
public class KtoTourBulkImportException extends RuntimeException {

    public KtoTourBulkImportException(String message) {
        super(message);
    }
}
