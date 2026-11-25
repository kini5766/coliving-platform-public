package com.coliving.dashboard.property;

/** 매물 서비스 호실 목록 조회가 실패했을 때. 화면은 이미 저장해 둔 호실을 보여 준다. */
public class PropertyRoomsException extends RuntimeException {

    public PropertyRoomsException(String message, Throwable cause) {
        super(message, cause);
    }
}
