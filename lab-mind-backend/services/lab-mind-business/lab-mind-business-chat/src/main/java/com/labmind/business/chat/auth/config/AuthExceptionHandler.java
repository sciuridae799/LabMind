package com.labmind.business.chat.auth.config;

import com.labmind.business.chat.auth.AuthErrorCode;
import com.labmind.business.chat.auth.AuthException;
import com.labmind.common.frame.response.ApiResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice
public class AuthExceptionHandler {

    @ExceptionHandler(AuthException.class)
    public ResponseEntity<ApiResponse<Void>> handleAuthException(AuthException exception) {
        HttpStatus status = AuthErrorCode.AUTH_FORBIDDEN.getCode().equals(exception.getCode())
                ? HttpStatus.FORBIDDEN
                : HttpStatus.UNAUTHORIZED;
        return ResponseEntity.status(status)
                .body(ApiResponse.error(exception.getErrorCode(), exception.getMessage()));
    }
}
