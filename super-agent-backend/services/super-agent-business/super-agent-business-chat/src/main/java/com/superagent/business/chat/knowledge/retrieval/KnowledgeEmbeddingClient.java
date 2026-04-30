package com.superagent.business.chat.knowledge.retrieval;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.superagent.business.chat.knowledge.retrieval.config.KnowledgeRetrievalProperties;
import java.util.ArrayList;
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
public class KnowledgeEmbeddingClient {

    private final KnowledgeRetrievalProperties retrievalProperties;

    private final ObjectMapper objectMapper;

    public List<Double> embed(String text) {
        KnowledgeRetrievalProperties.Embedding embedding = retrievalProperties.getEmbedding();
        requireText(embedding.getBaseUrl(), "retrieval embedding baseUrl");
        requireText(embedding.getApiKey(), "retrieval embedding apiKey");
        requireText(embedding.getModel(), "retrieval embedding model");
        String response = RestClient.builder()
                .baseUrl(embedding.getBaseUrl())
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + embedding.getApiKey())
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build()
                .post()
                .uri("/embeddings")
                .body(Map.of("model", embedding.getModel(), "input", text))
                .retrieve()
                .body(String.class);
        try {
            JsonNode embeddingNode = objectMapper.readTree(response)
                    .path("data")
                    .path(0)
                    .path("embedding");
            if (!embeddingNode.isArray() || embeddingNode.isEmpty()) {
                throw new IllegalStateException("embedding response data[0].embedding is empty.");
            }
            List<Double> vector = new ArrayList<>(embeddingNode.size());
            for (JsonNode valueNode : embeddingNode) {
                vector.add(valueNode.asDouble());
            }
            return vector;
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to parse embedding response.", exception);
        }
    }

    private void requireText(String value, String fieldName) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalStateException(fieldName + " is required.");
        }
    }
}
