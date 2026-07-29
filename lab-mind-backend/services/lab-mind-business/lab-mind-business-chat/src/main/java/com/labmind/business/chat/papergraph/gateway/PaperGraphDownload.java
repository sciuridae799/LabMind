package com.labmind.business.chat.papergraph.gateway;

import org.springframework.http.HttpHeaders;

public record PaperGraphDownload(byte[] content, HttpHeaders headers) {
}
