package com.ticketmind.common;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum ResultCode {

    OK(0, "操作成功", HttpStatus.OK),

    MISSING_REQUIRED_PARAMETER(40001, "缺少必填参数", HttpStatus.BAD_REQUEST),
    INVALID_PARAMETER_FORMAT(40002, "参数格式错误", HttpStatus.BAD_REQUEST),
    PARAMETER_TYPE_MISMATCH(40003, "参数类型不匹配", HttpStatus.BAD_REQUEST),
    PARAMETER_LENGTH_EXCEEDED(40004, "参数值长度超出限制", HttpStatus.BAD_REQUEST),
    INVALID_JSON_BODY(40005, "请求体JSON格式错误", HttpStatus.BAD_REQUEST),
    MISSING_REQUIRED_HEADER(40006, "请求头缺少必要字段", HttpStatus.BAD_REQUEST),
    REQUEST_BODY_TOO_LARGE(40021, "请求体大小超出限制", HttpStatus.BAD_REQUEST),

    MISSING_TOKEN(40101, "缺少token", HttpStatus.UNAUTHORIZED),
    INVALID_TOKEN(40103, "Token无效", HttpStatus.UNAUTHORIZED),
    ACCESS_TOKEN_EXPIRED(40104, "AccessToken过期", HttpStatus.UNAUTHORIZED),
    REFRESH_TOKEN_EXPIRED(40105, "RefreshToken过期", HttpStatus.UNAUTHORIZED),
    INVALID_USERNAME_OR_PASSWORD(40106, "用户名/密码错误", HttpStatus.UNAUTHORIZED),
    USERNAME_ALREADY_EXISTS(40901, "用户名已存在", HttpStatus.CONFLICT),

    INTERFACE_ACCESS_DENIED(40301, "没有接口访问权限", HttpStatus.FORBIDDEN),
    DATA_ACCESS_DENIED(40302, "没有数据访问权限", HttpStatus.FORBIDDEN),
    DATA_OPERATION_DENIED(40303, "没有数据操作权限", HttpStatus.FORBIDDEN),

    API_NOT_FOUND(40401, "接口不存在", HttpStatus.NOT_FOUND),
    DATA_NOT_FOUND(40402, "数据不存在", HttpStatus.NOT_FOUND),
    OSS_FILE_NOT_FOUND(40403, "OSS文件不存在", HttpStatus.NOT_FOUND),
    DATABASE_QUERY_TIMEOUT(40801, "数据库查询超时", HttpStatus.REQUEST_TIMEOUT),

    UNKNOWN_SERVER_ERROR(50001, "服务器内部出现未知错误", HttpStatus.INTERNAL_SERVER_ERROR),
    OSS_SERVICE_ERROR(50002, "OSS服务异常", HttpStatus.INTERNAL_SERVER_ERROR),
    DATABASE_ERROR(50003, "数据库异常", HttpStatus.INTERNAL_SERVER_ERROR),
    CACHE_ERROR(50004, "缓存异常", HttpStatus.INTERNAL_SERVER_ERROR),
    MESSAGE_QUEUE_ERROR(50005, "消息队列异常", HttpStatus.INTERNAL_SERVER_ERROR);

    private final int code;

    private final String message;

    private final HttpStatus httpStatus;

    ResultCode(int code, String message, HttpStatus httpStatus) {
        this.code = code;
        this.message = message;
        this.httpStatus = httpStatus;
    }
}
