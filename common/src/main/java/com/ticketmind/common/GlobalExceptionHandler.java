package com.ticketmind.common;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.RedisSystemException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.sql.SQLException;
import java.sql.SQLTimeoutException;
import java.util.List;

@ControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<Result<Void>> handleBusinessException(BusinessException exception,
                                                                HttpServletRequest request) {
        ResultCode code = exception.getResultCode();
        log.warn("业务异常: method={} path={} code={} msg={}",
                request.getMethod(), request.getRequestURI(), code.getCode(), exception.getMessage());
        return error(code, exception.getMessage());
    }

    @ExceptionHandler({
            MethodArgumentTypeMismatchException.class,
            HandlerMethodValidationException.class,
            HttpMessageNotReadableException.class,
            HttpMediaTypeNotSupportedException.class
    })
    public ResponseEntity<Result<Void>> handleBadRequest(Exception exception, HttpServletRequest request) {
        log.warn("请求参数异常: method={} path={} msg={}",
                request.getMethod(), request.getRequestURI(), exception.getMessage());
        if (exception instanceof MethodArgumentTypeMismatchException) {
            return error(ResultCode.PARAMETER_TYPE_MISMATCH);
        }
        if (exception instanceof HttpMessageNotReadableException) {
            return error(ResultCode.INVALID_JSON_BODY);
        }
        return error(ResultCode.INVALID_PARAMETER_FORMAT);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Result<Void>> handleMethodArgumentNotValid(
            MethodArgumentNotValidException exception,
            HttpServletRequest request) {
        return handleBindingResult(exception.getBindingResult(), exception, request);
    }

    @ExceptionHandler(BindException.class)
    public ResponseEntity<Result<Void>> handleBindException(
            BindException exception,
            HttpServletRequest request) {
        return handleBindingResult(exception.getBindingResult(), exception, request);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<Result<Void>> handleMissingServletRequestParameter(
            MissingServletRequestParameterException exception,
            HttpServletRequest request) {
        log.warn("缺少参数: method={} path={} parameter={}",
                request.getMethod(), request.getRequestURI(), exception.getParameterName());
        return error(ResultCode.MISSING_REQUIRED_PARAMETER, "缺少参数: " + exception.getParameterName());
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<Result<Void>> handleMissingRequestHeader(
            MissingRequestHeaderException exception,
            HttpServletRequest request) {
        log.warn("缺少请求头: method={} path={} header={}",
                request.getMethod(), request.getRequestURI(), exception.getHeaderName());
        return error(ResultCode.MISSING_REQUIRED_HEADER, "缺少请求头: " + exception.getHeaderName());
    }

    @ExceptionHandler({
            NoHandlerFoundException.class,
            NoResourceFoundException.class
    })
    public ResponseEntity<Result<Void>> handleNotFound(Exception exception, HttpServletRequest request) {
        log.warn("资源不存在: method={} path={} msg={}",
                request.getMethod(), request.getRequestURI(), exception.getMessage());
        return error(ResultCode.API_NOT_FOUND);
    }

    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<Result<Void>> handleDataAccessException(
            DataAccessException exception,
            HttpServletRequest request) {
        if (isQueryTimeout(exception)) {
            log.warn("数据库查询超时: method={} path={} msg={}",
                    request.getMethod(), request.getRequestURI(), exception.getMessage());
            return error(ResultCode.DATABASE_QUERY_TIMEOUT);
        }
        log.error("数据库异常: method={} path={}", request.getMethod(), request.getRequestURI(), exception);
        return error(ResultCode.DATABASE_ERROR);
    }

    @ExceptionHandler({
            RedisConnectionFailureException.class,
            RedisSystemException.class
    })
    public ResponseEntity<Result<Void>> handleRedisException(
            Exception exception,
            HttpServletRequest request) {
        log.error("缓存异常: method={} path={}", request.getMethod(), request.getRequestURI(), exception);
        return error(ResultCode.CACHE_ERROR);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Result<Void>> handleException(Exception exception, HttpServletRequest request) {
        log.error("未预期异常: method={} path={}", request.getMethod(), request.getRequestURI(), exception);
        return error(ResultCode.UNKNOWN_SERVER_ERROR);
    }

    private ResponseEntity<Result<Void>> error(ResultCode code) {
        return error(code, code.getMessage());
    }

    private ResponseEntity<Result<Void>> error(ResultCode code, String message) {
        return ResponseEntity.status(code.getHttpStatus())
                .body(Result.fail(code.getCode(), message));
    }

    private ResponseEntity<Result<Void>> handleBindingResult(
            BindingResult bindingResult,
            Exception exception,
            HttpServletRequest request) {
        List<String> missingFields = bindingResult.getFieldErrors().stream()
                .filter(this::isMissingRequiredField)
                .map(FieldError::getField)
                .distinct()
                .toList();
        if (!missingFields.isEmpty()) {
            String message = "缺少参数: " + String.join(", ", missingFields);
            log.warn("缺少参数: method={} path={} fields={}",
                    request.getMethod(), request.getRequestURI(), missingFields);
            return error(ResultCode.MISSING_REQUIRED_PARAMETER, message);
        }
        log.warn("请求参数异常: method={} path={} msg={}",
                request.getMethod(), request.getRequestURI(), exception.getMessage());
        return error(ResultCode.INVALID_PARAMETER_FORMAT);
    }

    private boolean isMissingRequiredField(FieldError fieldError) {
        Object rejectedValue = fieldError.getRejectedValue();
        return ("NotNull".equals(fieldError.getCode())
                || "NotBlank".equals(fieldError.getCode())
                || "NotEmpty".equals(fieldError.getCode()))
                && (rejectedValue == null || (rejectedValue instanceof String text && text.isBlank()));
    }

    private boolean isQueryTimeout(DataAccessException exception) {
        if (exception instanceof QueryTimeoutException) {
            return true;
        }
        Throwable cause = exception;
        while (cause != null) {
            if (cause instanceof SQLTimeoutException) {
                return true;
            }
            if (cause instanceof SQLException sqlException && "57014".equals(sqlException.getSQLState())) {
                return true;
            }
            cause = cause.getCause();
        }
        return false;
    }
}
