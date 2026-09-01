package com.drakkar.erp.domain;

import org.springframework.http.HttpStatus;

public final class DomainException extends RuntimeException {
    private final String code;
    private final HttpStatus status;

    public DomainException(String code, String message, HttpStatus status) {
        super(message);
        this.code = code;
        this.status = status;
    }

    public String code() {
        return code;
    }

    public HttpStatus status() {
        return status;
    }

    public static DomainException conflict(String code, String message) {
        return new DomainException(code, message, HttpStatus.CONFLICT);
    }

    public static DomainException forbidden(String message) {
        return new DomainException("ROLE_FORBIDDEN", message, HttpStatus.FORBIDDEN);
    }

    public static DomainException notFound(String aggregate) {
        return new DomainException("NOT_FOUND", aggregate + " не найден", HttpStatus.NOT_FOUND);
    }
}
