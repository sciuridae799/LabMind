package com.labmind.business.chat.papergraph.gateway;

import org.springframework.http.HttpStatusCode;

public class PaperGraphGatewayException extends RuntimeException {

    private final HttpStatusCode statusCode;

    public PaperGraphGatewayException(HttpStatusCode statusCode, String message) {
        super(message);
        this.statusCode = statusCode;
    }

    public PaperGraphGatewayException(HttpStatusCode statusCode, String message, Throwable cause) {
        super(message, cause);
        this.statusCode = statusCode;
    }

    public HttpStatusCode getStatusCode() {
        return statusCode;
    }
}
