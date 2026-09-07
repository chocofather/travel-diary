package com.example.travlediary.service.faq;

/**
 * 사용 중인 FAQ 카테고리를 지우려 할 때 던진다.
 *
 * <p>faqs.category_id 는 ON DELETE 규칙이 없어 DB 도 삭제를 막지만,
 * 관리자에게 이유를 보여 주기 위해 삭제 전에 서비스가 먼저 확인한다.
 */
public class FaqCategoryInUseException extends RuntimeException {

    public FaqCategoryInUseException(int faqCount) {
        super("현재 " + faqCount + "개의 FAQ에서 사용 중이라 삭제할 수 없습니다.");
    }

    public FaqCategoryInUseException(Throwable cause) {
        super("이 카테고리를 사용하는 FAQ가 있어 삭제할 수 없습니다.", cause);
    }
}
