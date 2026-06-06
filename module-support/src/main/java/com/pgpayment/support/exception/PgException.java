package com.pgpayment.support.exception;

import lombok.Getter;

@Getter
public class PgException extends RuntimeException {

    private final PgErrorCode errorCode;

    public PgException(PgErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }

    public PgException(PgErrorCode errorCode, String detail) {
        super(detail);
        this.errorCode = errorCode;
    }

    public PgException(PgErrorCode errorCode, Throwable cause) {
        super(errorCode.getMessage(), cause);
        this.errorCode = errorCode;
    }
}
