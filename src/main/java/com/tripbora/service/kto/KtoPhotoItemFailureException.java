package com.tripbora.service.kto;

/**
 * 선택한 관광사진 여러 장 중 한 장이 실패했다. 어느 사진이 어느 단계에서 왜 실패했는지 담는다.
 *
 * <p>여러 장 등록은 지금처럼 전부 저장하거나 전부 저장하지 않는다. 이 예외는 실패한 사진을 찾아
 * 로그와 관리 화면에 알리기 위한 것이다. 원래 예외는 cause 로 남긴다.</p>
 */
public class KtoPhotoItemFailureException extends KtoPhotoImportException {

    public enum Stage {
        LICENSE("license", "출처·라이선스 확인"),
        VALIDATION("validation", "이미지 주소 검증"),
        DOWNLOAD("download", "다운로드·이미지 검증"),
        PERSIST("persist", "DB 저장");

        private final String code;
        private final String label;

        Stage(String code, String label) {
            this.code = code;
            this.label = label;
        }

        public String code() {
            return code;
        }

        public String label() {
            return label;
        }
    }

    private final int position;
    private final String externalContentId;
    private final String imageUrl;
    private final String title;
    private final Stage stage;

    public KtoPhotoItemFailureException(int position, String externalContentId, String imageUrl, String title,
                                        Stage stage, String reason, Throwable cause) {
        super(reason, cause);
        this.position = position;
        this.externalContentId = externalContentId;
        this.imageUrl = imageUrl;
        this.title = title;
        this.stage = stage;
    }

    /** 선택한 순서 기준 1부터 시작하는 번호. */
    public int position() {
        return position;
    }

    public String externalContentId() {
        return externalContentId;
    }

    public String imageUrl() {
        return imageUrl;
    }

    public String title() {
        return title;
    }

    public Stage stage() {
        return stage;
    }

    public String causeType() {
        return getCause() == null ? null : getCause().getClass().getSimpleName();
    }
}
