package com.ticketmind.common;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Result<T> implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Integer code;
    private String message;
    private T data;

    private Result(Integer code, String message, T data) {
        this.code = code;
        this.message = message;
        this.data = data;
    }

    public static <T> Result<T> success() {
        return new Result<>(ResultCode.OK.getCode(), "操作成功", null);
    }

    public static <T> Result<T> success(String message) {
        return new Result<>(ResultCode.OK.getCode(), message, null);
    }

    public static <T> Result<T> success(T data) {
        return new Result<>(ResultCode.OK.getCode(), "操作成功", data);
    }

    public static <T> Result<T> success(String message, T data) {
        return new Result<>(ResultCode.OK.getCode(), message, data);
    }

    public static <T> Result<T> fail() {
        return new Result<>(ResultCode.OK.getCode(), "操作失败", null);
    }

    public static <T> Result<T> fail(String message) {
        return new Result<>(ResultCode.OK.getCode(), message, null);
    }

    public static <T> Result<T> fail(Integer code, String message) {
        return new Result<>(code, message, null);
    }

    public static <T> Result<T> fail(ResultCode resultCode) {
        return new Result<>(resultCode.getCode(), resultCode.getMessage(), null);
    }
}
