package com.labmind.business.chat.papergraph.gateway;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.labmind.business.chat.papergraph.api.dto.PaperGraphCreateRequest;
import com.labmind.business.chat.papergraph.config.PaperGraphServiceProperties;
import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.util.UriComponentsBuilder;

@Component
@RequiredArgsConstructor
public class PaperGraphGatewayClient {

    private static final String INTERNAL_TOKEN_HEADER = "X-Lab-Mind-Internal-Token";
    private static final String USER_ID_HEADER = "X-Lab-Mind-User-Id";
    private static final String WORKSPACE_ID_HEADER = "X-Lab-Mind-Workspace-Id";

    private final PaperGraphServiceProperties properties;

    private final ObjectMapper objectMapper;

    public JsonNode createGraph(PaperGraphGatewayContext context, PaperGraphCreateRequest request) {
        return executeJson(HttpMethod.POST, serviceUri("/api/paper-graphs"), context, request);
    }

    public JsonNode listGraphs(PaperGraphGatewayContext context) {
        return executeJson(HttpMethod.GET, serviceUri("/api/paper-graphs"), context, null);
    }

    public JsonNode getGraph(PaperGraphGatewayContext context, UUID graphId) {
        return executeJson(HttpMethod.GET, serviceUri("/api/paper-graphs/" + graphId), context, null);
    }

    public void deleteGraph(PaperGraphGatewayContext context, UUID graphId) {
        executeNoContent(HttpMethod.DELETE, serviceUri("/api/paper-graphs/" + graphId), context);
    }

    public JsonNode uploadDocument(
            PaperGraphGatewayContext context,
            UUID graphId,
            MultipartFile file) {
        String filename = file.getOriginalFilename();
        if (!StringUtils.hasText(filename)) {
            throw new IllegalArgumentException("uploaded PDF filename is required");
        }
        byte[] content;
        try {
            content = file.getBytes();
        } catch (IOException error) {
            throw new IllegalStateException("failed to read uploaded PDF", error);
        }
        ByteArrayResource resource = new ByteArrayResource(content) {
            @Override
            public String getFilename() {
                return filename.strip();
            }
        };
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", resource);
        try {
            ResponseEntity<String> response = client()
                    .post()
                    .uri(serviceUri("/api/paper-graphs/" + graphId + "/documents"))
                    .headers(headers -> addContextHeaders(headers, context))
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(body)
                    .retrieve()
                    .toEntity(String.class);
            return parseRequiredJson(response.getBody());
        } catch (RestClientResponseException error) {
            throw responseException(error);
        } catch (ResourceAccessException error) {
            throw unavailableException(error);
        }
    }

    public JsonNode listDocuments(PaperGraphGatewayContext context, UUID graphId) {
        return executeJson(
                HttpMethod.GET,
                serviceUri("/api/paper-graphs/" + graphId + "/documents"),
                context,
                null);
    }

    public JsonNode documentStatus(PaperGraphGatewayContext context, UUID documentId) {
        return executeJson(
                HttpMethod.GET,
                serviceUri("/api/paper-documents/" + documentId + "/status"),
                context,
                null);
    }

    public JsonNode rebuildDocument(PaperGraphGatewayContext context, UUID documentId) {
        return executeJson(
                HttpMethod.POST,
                serviceUri("/api/paper-documents/" + documentId + "/rebuild"),
                context,
                null);
    }

    public PaperGraphDownload downloadDocument(PaperGraphGatewayContext context, UUID documentId) {
        try {
            ResponseEntity<byte[]> response = client()
                    .get()
                    .uri(serviceUri("/api/paper-documents/" + documentId + "/download"))
                    .headers(headers -> addContextHeaders(headers, context))
                    .retrieve()
                    .toEntity(byte[].class);
            byte[] content = response.getBody();
            if (content == null || content.length == 0) {
                throw new IllegalStateException("paper graph service returned an empty PDF");
            }
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_PDF);
            if (response.getHeaders().getContentDisposition() != null) {
                headers.setContentDisposition(response.getHeaders().getContentDisposition());
            }
            headers.setContentLength(content.length);
            return new PaperGraphDownload(content, headers);
        } catch (RestClientResponseException error) {
            throw responseException(error);
        } catch (ResourceAccessException error) {
            throw unavailableException(error);
        }
    }

    public void deleteDocument(PaperGraphGatewayContext context, UUID documentId) {
        executeNoContent(
                HttpMethod.DELETE,
                serviceUri("/api/paper-documents/" + documentId),
                context);
    }

    public JsonNode visualization(
            PaperGraphGatewayContext context,
            UUID graphId,
            UUID documentId,
            List<String> entityTypes,
            String query) {
        UriComponentsBuilder uriBuilder = UriComponentsBuilder.fromUri(
                serviceUri("/api/paper-graphs/" + graphId + "/visualization"));
        if (documentId != null) {
            uriBuilder.queryParam("documentId", documentId);
        }
        for (String entityType : entityTypes) {
            uriBuilder.queryParam("entityType", entityType);
        }
        if (StringUtils.hasText(query)) {
            uriBuilder.queryParam("query", query.strip());
        }
        return executeJson(HttpMethod.GET, uriBuilder.build().encode().toUri(), context, null);
    }

    public JsonNode nodeDetail(PaperGraphGatewayContext context, UUID graphId, UUID nodeId) {
        return executeJson(
                HttpMethod.GET,
                serviceUri("/api/paper-graphs/" + graphId + "/nodes/" + nodeId),
                context,
                null);
    }

    public JsonNode neighbors(PaperGraphGatewayContext context, UUID graphId, UUID nodeId) {
        return executeJson(
                HttpMethod.GET,
                serviceUri("/api/paper-graphs/" + graphId + "/nodes/" + nodeId + "/neighbors"),
                context,
                null);
    }

    public JsonNode edgeEvidence(PaperGraphGatewayContext context, UUID graphId, UUID edgeId) {
        return executeJson(
                HttpMethod.GET,
                serviceUri("/api/paper-graphs/" + graphId + "/edges/" + edgeId + "/evidence"),
                context,
                null);
    }

    private JsonNode executeJson(
            HttpMethod method,
            URI uri,
            PaperGraphGatewayContext context,
            Object body) {
        try {
            RestClient.RequestBodySpec request = client()
                    .method(method)
                    .uri(uri)
                    .headers(headers -> addContextHeaders(headers, context));
            ResponseEntity<String> response;
            if (body == null) {
                response = request.retrieve().toEntity(String.class);
            } else {
                response = request
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(body)
                        .retrieve()
                        .toEntity(String.class);
            }
            return parseRequiredJson(response.getBody());
        } catch (RestClientResponseException error) {
            throw responseException(error);
        } catch (ResourceAccessException error) {
            throw unavailableException(error);
        }
    }

    private void executeNoContent(
            HttpMethod method,
            URI uri,
            PaperGraphGatewayContext context) {
        try {
            ResponseEntity<Void> response = client()
                    .method(method)
                    .uri(uri)
                    .headers(headers -> addContextHeaders(headers, context))
                    .retrieve()
                    .toBodilessEntity();
            if (response.getStatusCode() != HttpStatus.NO_CONTENT) {
                throw new IllegalStateException(
                        "paper graph service did not return HTTP 204 for delete");
            }
        } catch (RestClientResponseException error) {
            throw responseException(error);
        } catch (ResourceAccessException error) {
            throw unavailableException(error);
        }
    }

    private RestClient client() {
        return RestClient.builder().baseUrl(requiredBaseUrl()).build();
    }

    private URI serviceUri(String path) {
        return URI.create(requiredBaseUrl() + path);
    }

    private String requiredBaseUrl() {
        if (!StringUtils.hasText(properties.getBaseUrl())) {
            throw new IllegalStateException(
                    "LAB_MIND_PAPER_GRAPH_SERVICE_BASE_URL must be configured");
        }
        String baseUrl = properties.getBaseUrl().strip();
        URI uri = URI.create(baseUrl);
        if (!("http".equals(uri.getScheme()) || "https".equals(uri.getScheme()))
                || !StringUtils.hasText(uri.getHost())) {
            throw new IllegalStateException(
                    "LAB_MIND_PAPER_GRAPH_SERVICE_BASE_URL must be an absolute http(s) URL");
        }
        return baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }

    private void addContextHeaders(HttpHeaders headers, PaperGraphGatewayContext context) {
        if (!StringUtils.hasText(properties.getInternalToken())) {
            throw new IllegalStateException(
                    "LAB_MIND_PAPER_GRAPH_INTERNAL_API_TOKEN must be configured");
        }
        headers.set(INTERNAL_TOKEN_HEADER, properties.getInternalToken().strip());
        headers.set(USER_ID_HEADER, context.userId());
        headers.set(WORKSPACE_ID_HEADER, context.workspaceId());
    }

    private JsonNode parseRequiredJson(String body) {
        if (!StringUtils.hasText(body)) {
            throw new IllegalStateException("paper graph service returned an empty JSON response");
        }
        try {
            return objectMapper.readTree(body);
        } catch (JsonProcessingException error) {
            throw new IllegalStateException("paper graph service returned invalid JSON", error);
        }
    }

    private PaperGraphGatewayException responseException(RestClientResponseException error) {
        String body = error.getResponseBodyAsString();
        String detail;
        try {
            JsonNode payload = objectMapper.readTree(body);
            JsonNode detailNode = payload.get("detail");
            if (detailNode == null || !detailNode.isTextual() || !StringUtils.hasText(detailNode.textValue())) {
                throw new IllegalStateException(
                        "paper graph service error response does not contain detail");
            }
            detail = detailNode.textValue().strip();
        } catch (JsonProcessingException parseError) {
            throw new IllegalStateException(
                    "paper graph service returned invalid error JSON", parseError);
        }
        return new PaperGraphGatewayException(error.getStatusCode(), detail, error);
    }

    private PaperGraphGatewayException unavailableException(ResourceAccessException error) {
        return new PaperGraphGatewayException(
                HttpStatus.BAD_GATEWAY,
                "论文知识图谱 Python 服务不可用",
                error);
    }
}
