package com.superagent.business.chat.knowledge.retrieval;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.superagent.business.chat.knowledge.indexing.KnowledgeRetrievalIndexChunk;
import com.superagent.business.chat.knowledge.retrieval.config.KnowledgeRetrievalProperties;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

@Service
@RequiredArgsConstructor
public class ElasticsearchKnowledgeRetriever {

    private final KnowledgeRetrievalProperties retrievalProperties;

    private final ObjectMapper objectMapper;

    public void index(KnowledgeRetrievalIndexChunk chunk, String documentName) {
        try {
            client()
                    .put()
                    .uri("/{index}/_doc/{id}", indexName(), chunk.chunkId())
                    .body(Map.of(
                            "chunkId", chunk.chunkId(),
                            "documentId", chunk.documentId(),
                            "workspaceId", chunk.workspaceId(),
                            "parentBlockId", chunk.parentBlockId(),
                            "chunkNo", chunk.chunkNo(),
                            "documentName", documentName,
                            "sectionPath", nullToEmpty(chunk.sectionPath()),
                            "canonicalPath", nullToEmpty(chunk.canonicalPath()),
                            "chunkText", chunk.chunkText(),
                            "status", 1))
                    .retrieve()
                    .toBodilessEntity();
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to index Elasticsearch chunkId=" + chunk.chunkId(), exception);
        }
    }

    public void deleteByDocumentId(long documentId) {
        try {
            client()
                    .post()
                    .uri("/{index}/_delete_by_query?refresh=true", indexName())
                    .body(Map.of("query", Map.of("term", Map.of("documentId", documentId))))
                    .retrieve()
                    .toBodilessEntity();
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to delete Elasticsearch chunks for documentId=" + documentId, exception);
        }
    }

    public List<KnowledgeRetrievalChildHit> search(String question, List<Long> documentIdList) {
        if (documentIdList == null || documentIdList.isEmpty()) {
            return List.of();
        }
        try {
            Map<String, Object> multiMatch = Map.of(
                    "query", question,
                    "fields", List.of(
                            "chunkText^4",
                            "sectionPath^2",
                            "canonicalPath^2",
                            "documentName"));
            Map<String, Object> boolQuery = Map.of(
                    "filter", List.of(
                            Map.of("terms", Map.of("documentId", documentIdList)),
                            Map.of("term", Map.of("status", 1))),
                    "must", List.of(Map.of("multi_match", multiMatch)));
            Map<String, Object> requestBody = Map.of(
                    "size", retrievalProperties.getKeyword().getTopK(),
                    "query", Map.of("bool", boolQuery));
            String response = client()
                    .post()
                    .uri("/{index}/_search", indexName())
                    .body(requestBody)
                    .retrieve()
                    .body(String.class);
            JsonNode hitsNode = objectMapper.readTree(response).path("hits").path("hits");
            if (!hitsNode.isArray() || hitsNode.isEmpty()) {
                return List.of();
            }
            double topScore = hitsNode.get(0).path("_score").asDouble();
            double minScore = topScore * retrievalProperties.getKeyword().getRelativeThreshold();
            List<KnowledgeRetrievalChildHit> hitList = new ArrayList<>();
            int rank = 1;
            for (JsonNode hitNode : hitsNode) {
                double score = hitNode.path("_score").asDouble();
                if (score < minScore) {
                    continue;
                }
                JsonNode sourceNode = hitNode.path("_source");
                hitList.add(new KnowledgeRetrievalChildHit(
                        sourceNode.path("chunkId").asLong(),
                        sourceNode.path("documentId").asLong(),
                        sourceNode.path("parentBlockId").asLong(),
                        sourceNode.path("chunkNo").asInt(),
                        sourceNode.path("documentName").asText(null),
                        sourceNode.path("sectionPath").asText(null),
                        sourceNode.path("chunkText").asText(null),
                        "KEYWORD",
                        score,
                        rank));
                rank++;
            }
            return hitList;
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to search Elasticsearch knowledge chunks.", exception);
        }
    }

    private RestClient client() {
        KnowledgeRetrievalProperties.Elasticsearch elasticsearch = retrievalProperties.getElasticsearch();
        requireText(elasticsearch.getBaseUrl(), "retrieval elasticsearch baseUrl");
        RestClient.Builder builder = RestClient.builder()
                .baseUrl(elasticsearch.getBaseUrl())
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
        if (StringUtils.hasText(elasticsearch.getApiKey())) {
            builder.defaultHeader(HttpHeaders.AUTHORIZATION, "ApiKey " + elasticsearch.getApiKey());
        } else if (StringUtils.hasText(elasticsearch.getUsername()) || StringUtils.hasText(elasticsearch.getPassword())) {
            requireText(elasticsearch.getUsername(), "retrieval elasticsearch username");
            requireText(elasticsearch.getPassword(), "retrieval elasticsearch password");
            String token = Base64.getEncoder().encodeToString(
                    (elasticsearch.getUsername() + ":" + elasticsearch.getPassword())
                            .getBytes(StandardCharsets.UTF_8));
            builder.defaultHeader(HttpHeaders.AUTHORIZATION, "Basic " + token);
        }
        return builder.build();
    }

    private String indexName() {
        String indexName = retrievalProperties.getElasticsearch().getIndexName();
        requireText(indexName, "retrieval elasticsearch indexName");
        return indexName;
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private void requireText(String value, String fieldName) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalStateException(fieldName + " is required.");
        }
    }
}
