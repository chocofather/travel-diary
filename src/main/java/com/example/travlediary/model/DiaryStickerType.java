package com.example.travlediary.model;

public enum DiaryStickerType {
    NORMAL,
    MASKING_TAPE;

    public String kindCode() {
        return this == MASKING_TAPE ? DiaryStickerKind.MASKING_TAPE : null;
    }

    public String directoryName() {
        return this == MASKING_TAPE ? "masking-tape" : "normal";
    }
}
