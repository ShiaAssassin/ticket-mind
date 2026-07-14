package com.ticketmind.common;

import lombok.Getter;
import org.springframework.util.StringUtils;

@Getter
public class BusinessException extends RuntimeException {

    private final ResultCode resultCode;

    public BusinessException(ResultCode resultCode) {
        this(resultCode, resultCode.getMessage());
    }

    public BusinessException(ResultCode resultCode, String message) {
        super(StringUtils.hasText(message) ? message : resultCode.getMessage());
        this.resultCode = resultCode;
    }
}
