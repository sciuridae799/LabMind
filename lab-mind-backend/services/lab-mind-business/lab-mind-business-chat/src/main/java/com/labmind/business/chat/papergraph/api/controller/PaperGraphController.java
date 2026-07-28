package com.labmind.business.chat.papergraph.api.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.labmind.business.chat.auth.AuthSessionContext;
import com.labmind.business.chat.auth.AuthSessionHolder;
import com.labmind.business.chat.auth.service.AuthWorkspaceScopeService;
import com.labmind.business.chat.papergraph.api.dto.PaperGraphCreateRequest;
import com.labmind.business.chat.papergraph.gateway.PaperGraphDownload;
import com.labmind.business.chat.papergraph.gateway.PaperGraphGatewayClient;
import com.labmind.business.chat.papergraph.gateway.PaperGraphGatewayContext;
import com.labmind.business.chat.papergraph.gateway.PaperGraphGatewayException;
import com.labmind.common.frame.enums.BaseCode;
import com.labmind.common.frame.response.ApiResponse;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class PaperGraphController {

    private final PaperGraphGatewayClient gatewayClient;

    private final AuthWorkspaceScopeService workspaceScopeService;

    @PostMapping("/paper-graphs")
    public ApiResponse<JsonNode> createGraph(@Valid @RequestBody PaperGraphCreateRequest request) {
        return ApiResponse.ok(gatewayClient.createGraph(writableContext(), request));
    }

    @GetMapping("/paper-graphs")
    public ApiResponse<JsonNode> listGraphs() {
        return ApiResponse.ok(gatewayClient.listGraphs(readableContext()));
    }

    @GetMapping("/paper-graphs/{graphId}")
    public ApiResponse<JsonNode> getGraph(@PathVariable UUID graphId) {
        return ApiResponse.ok(gatewayClient.getGraph(readableContext(), graphId));
    }

    @DeleteMapping("/paper-graphs/{graphId}")
    public ApiResponse<Void> deleteGraph(@PathVariable UUID graphId) {
        gatewayClient.deleteGraph(writableContext(), graphId);
        return ApiResponse.ok();
    }

    @PostMapping("/paper-graphs/{graphId}/documents")
    public ApiResponse<JsonNode> uploadDocument(
            @PathVariable UUID graphId,
            @RequestPart("file") MultipartFile file) {
        return ApiResponse.ok(gatewayClient.uploadDocument(writableContext(), graphId, file));
    }

    @GetMapping("/paper-graphs/{graphId}/documents")
    public ApiResponse<JsonNode> listDocuments(@PathVariable UUID graphId) {
        return ApiResponse.ok(gatewayClient.listDocuments(readableContext(), graphId));
    }

    @GetMapping("/paper-documents/{documentId}/status")
    public ApiResponse<JsonNode> documentStatus(@PathVariable UUID documentId) {
        return ApiResponse.ok(gatewayClient.documentStatus(readableContext(), documentId));
    }

    @PostMapping("/paper-documents/{documentId}/rebuild")
    public ApiResponse<JsonNode> rebuildDocument(@PathVariable UUID documentId) {
        return ApiResponse.ok(gatewayClient.rebuildDocument(writableContext(), documentId));
    }

    @GetMapping("/paper-documents/{documentId}/download")
    public ResponseEntity<byte[]> downloadDocument(@PathVariable UUID documentId) {
        PaperGraphDownload download = gatewayClient.downloadDocument(readableContext(), documentId);
        return ResponseEntity.ok()
                .headers(download.headers())
                .body(download.content());
    }

    @DeleteMapping("/paper-documents/{documentId}")
    public ApiResponse<Void> deleteDocument(@PathVariable UUID documentId) {
        gatewayClient.deleteDocument(writableContext(), documentId);
        return ApiResponse.ok();
    }

    @GetMapping("/paper-graphs/{graphId}/visualization")
    public ApiResponse<JsonNode> visualization(
            @PathVariable UUID graphId,
            @RequestParam(required = false) UUID documentId,
            @RequestParam(required = false) List<String> entityType,
            @RequestParam(required = false) String query) {
        List<String> requestedEntityTypes = entityType == null ? List.of() : List.copyOf(entityType);
        return ApiResponse.ok(gatewayClient.visualization(
                readableContext(),
                graphId,
                documentId,
                requestedEntityTypes,
                query));
    }

    @GetMapping("/paper-graphs/{graphId}/nodes/{nodeId}")
    public ApiResponse<JsonNode> nodeDetail(
            @PathVariable UUID graphId,
            @PathVariable UUID nodeId) {
        return ApiResponse.ok(gatewayClient.nodeDetail(readableContext(), graphId, nodeId));
    }

    @GetMapping("/paper-graphs/{graphId}/nodes/{nodeId}/neighbors")
    public ApiResponse<JsonNode> neighbors(
            @PathVariable UUID graphId,
            @PathVariable UUID nodeId) {
        return ApiResponse.ok(gatewayClient.neighbors(readableContext(), graphId, nodeId));
    }

    @GetMapping("/paper-graphs/{graphId}/edges/{edgeId}/evidence")
    public ApiResponse<JsonNode> edgeEvidence(
            @PathVariable UUID graphId,
            @PathVariable UUID edgeId) {
        return ApiResponse.ok(gatewayClient.edgeEvidence(readableContext(), graphId, edgeId));
    }

    @ExceptionHandler(PaperGraphGatewayException.class)
    public ResponseEntity<ApiResponse<Void>> handleGatewayException(
            PaperGraphGatewayException exception) {
        BaseCode code = exception.getStatusCode().value() == 404
                ? BaseCode.NOT_FOUND
                : exception.getStatusCode().is4xxClientError()
                        ? BaseCode.INVALID_PARAMETER
                        : BaseCode.SYSTEM_ERROR;
        return ResponseEntity.status(exception.getStatusCode())
                .body(ApiResponse.error(code, exception.getMessage()));
    }

    private PaperGraphGatewayContext readableContext() {
        return context(workspaceScopeService.resolveReadableWorkspace(null));
    }

    private PaperGraphGatewayContext writableContext() {
        return context(workspaceScopeService.resolveWritableWorkspace(null));
    }

    private PaperGraphGatewayContext context(String workspaceId) {
        AuthSessionContext session = AuthSessionHolder.required();
        return new PaperGraphGatewayContext(session.userId(), workspaceId);
    }
}
