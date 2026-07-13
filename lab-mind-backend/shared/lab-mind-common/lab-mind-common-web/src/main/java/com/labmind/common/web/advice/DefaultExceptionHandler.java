package com.labmind.common.web.advice;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException.Reference;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException;
import com.labmind.common.frame.enums.BaseCode;
import com.labmind.common.frame.exception.BaseException;
import com.labmind.common.frame.response.ApiResponse;
import jakarta.validation.ConstraintViolationException;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.util.StringUtils;
import org.springframework.validation.BindException;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;
import org.springframework.validation.method.ParameterValidationResult;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice
public class DefaultExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(DefaultExceptionHandler.class);

    @ExceptionHandler(BaseException.class)
    public ResponseEntity<ApiResponse<Void>> handleBaseException(BaseException exception) {
        log.warn("Business exception captured. code={}, message={}", exception.getCode(), exception.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(exception.getErrorCode(), exception.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodArgumentNotValidException(
            MethodArgumentNotValidException exception) {
        return badRequest(validationErrorMessage(exception.getBindingResult()));
    }

    @ExceptionHandler(BindException.class)
    public ResponseEntity<ApiResponse<Void>> handleBindException(BindException exception) {
        return badRequest(validationErrorMessage(exception.getBindingResult()));
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ApiResponse<Void>> handleHandlerMethodValidationException(
            HandlerMethodValidationException exception) {
        Set<String> messages = new LinkedHashSet<>();
        for (ParameterValidationResult validationResult : exception.getParameterValidationResults()) {
            messages.addAll(validationResult.getResolvableErrors().stream()
                    .map(MessageSourceResolvable::getDefaultMessage)
                    .filter(StringUtils::hasText)
                    .collect(Collectors.toCollection(LinkedHashSet::new)));
        }
        if (messages.isEmpty()) {
            messages.add(BaseCode.INVALID_PARAMETER.getMessage());
        }
        return badRequest(String.join("; ", messages));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleConstraintViolationException(
            ConstraintViolationException exception) {
        Set<String> messages = exception.getConstraintViolations().stream()
                .map(violation -> violation.getPropertyPath() + ": " + violation.getMessage())
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (messages.isEmpty()) {
            messages.add(BaseCode.INVALID_PARAMETER.getMessage());
        }
        return badRequest(String.join("; ", messages));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleHttpMessageNotReadableException(
            HttpMessageNotReadableException exception) {
        return badRequest(resolveHttpMessageNotReadableMessage(exception));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodArgumentTypeMismatchException(
            MethodArgumentTypeMismatchException exception) {
        return badRequest(exception.getName() + ": invalid value");
    }

    @ExceptionHandler(ServletRequestBindingException.class)
    public ResponseEntity<ApiResponse<Void>> handleServletRequestBindingException(
            ServletRequestBindingException exception) {
        if (exception instanceof MissingServletRequestParameterException missing) {
            return badRequest(missing.getParameterName() + ": parameter is required");
        }
        return badRequest(BaseCode.INVALID_PARAMETER.getMessage());
    }

    @ExceptionHandler(AsyncRequestNotUsableException.class)
    public ResponseEntity<Void> handleAsyncRequestNotUsableException(AsyncRequestNotUsableException exception) {
        log.debug("Async request is no longer usable.", exception);
        return ResponseEntity.noContent().build();
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> handleHttpRequestMethodNotSupportedException(
            HttpRequestMethodNotSupportedException exception) {
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
                .body(ApiResponse.error(BaseCode.METHOD_NOT_ALLOWED));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNoResourceFoundException(NoResourceFoundException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error(BaseCode.NOT_FOUND));
    }

    @ExceptionHandler(Throwable.class)
    public ResponseEntity<ApiResponse<Void>> handleThrowable(Throwable exception) {
        log.error("Unhandled exception captured by default handler.", exception);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error(BaseCode.SYSTEM_ERROR));
    }

    private ResponseEntity<ApiResponse<Void>> badRequest(String message) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(BaseCode.INVALID_PARAMETER, message));
    }

    private String validationErrorMessage(BindingResult bindingResult) {
        Set<String> messages = new LinkedHashSet<>();
        for (FieldError fieldError : bindingResult.getFieldErrors()) {
            String defaultMessage = fieldError.getDefaultMessage();
            if (StringUtils.hasText(defaultMessage)) {
                messages.add(fieldError.getField() + ": " + defaultMessage);
            }
        }
        for (ObjectError globalError : bindingResult.getGlobalErrors()) {
            String defaultMessage = globalError.getDefaultMessage();
            if (StringUtils.hasText(defaultMessage)) {
                messages.add(defaultMessage);
            }
        }
        if (messages.isEmpty()) {
            messages.add(BaseCode.INVALID_PARAMETER.getMessage());
        }
        return String.join("; ", messages);
    }

    private String resolveHttpMessageNotReadableMessage(HttpMessageNotReadableException exception) {
        Throwable mostSpecificCause = exception.getMostSpecificCause();
        switch (mostSpecificCause) {
            case UnrecognizedPropertyException unrecognizedPropertyException -> {
                return unrecognizedPropertyException.getPropertyName() + ": unknown field";
            }
            case InvalidFormatException invalidFormatException -> {
                String path = resolveJsonPath(invalidFormatException.getPath());
                if (StringUtils.hasText(path)) {
                    return path + ": invalid value";
                }
                return BaseCode.INVALID_PARAMETER.getMessage();
            }
            case JsonParseException jsonParseException -> {
                return "request body is not valid JSON";
            }
            default -> {
            }
        }
        return BaseCode.INVALID_PARAMETER.getMessage();
    }

    private String resolveJsonPath(List<Reference> references) {
        return references.stream()
                .map(reference -> {
                    if (reference.getFieldName() != null) {
                        return reference.getFieldName();
                    }
                    if (reference.getIndex() >= 0) {
                        return "[" + reference.getIndex() + "]";
                    }
                    return null;
                })
                .filter(StringUtils::hasText)
                .collect(Collectors.joining("."));
    }
}
