package com.superagent.business.chat.knowledge.retrieval;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.superagent.business.chat.knowledge.retrieval.config.KnowledgeRetrievalProperties;
import java.util.ArrayList;
import java.util.Comparator;
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
public class KnowledgeRerankService {

    private final KnowledgeRetrievalProperties retrievalProperties;

    private final ObjectMapper objectMapper;

    public List<KnowledgeRetrievalFusedChild> rerank(String question, List<KnowledgeRetrievalFusedChild> childList) {
        if (!retrievalProperties.getRerank().isEnabled()) {
            return childList;
        }
        if (childList.isEmpty()) {
            return childList;
        }
        KnowledgeRetrievalProperties.Rerank rerank = retrievalProperties.getRerank();
        requireText(rerank.getBaseUrl(), "retrieval rerank baseUrl");
        requireText(rerank.getApiKey(), "retrieval rerank apiKey");
        requireText(rerank.getModel(), "retrieval rerank model");
        try {
            String response = RestClient.builder()
                    .baseUrl(rerank.getBaseUrl())
                    .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + rerank.getApiKey())
                    .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .build()
                    .post()
                    .uri("/rerank")
                    .body(Map.of(
                            "model", rerank.getModel(),
                            "query", question,
                            "documents", childList.stream()
                                    .map(KnowledgeRetrievalFusedChild::chunkText)
                                    .toList()))
                    .retrieve()
                    .body(String.class);
            JsonNode resultsNode = objectMapper.readTree(response).path("results");
            if (!resultsNode.isArray()) {
                throw new IllegalStateException("rerank response results is not array.");
            }
            List<KnowledgeRetrievalFusedChild> rerankedList = new ArrayList<>();
            for (JsonNode resultNode : resultsNode) {
                int index = resultNode.path("index").asInt(-1);
                if (index < 0 || index >= childList.size()) {
                    throw new IllegalStateException("rerank response index out of range: " + index);
                }
                double score = resultNode.path("relevance_score").asDouble();
                KnowledgeRetrievalFusedChild child = childList.get(index);
                rerankedList.add(new KnowledgeRetrievalFusedChild(
                        child.chunkId(),
                        child.documentId(),
                        child.parentBlockId(),
                        child.chunkNo(),
                        child.documentName(),
                        child.sectionPath(),
                        child.chunkText(),
                        child.rrfScore(),
                        score,
                        child.channels()));
            }
            return rerankedList.stream()
                    .sorted(Comparator.comparing(KnowledgeRetrievalFusedChild::finalScore).reversed())
                    .toList();
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to rerank knowledge retrieval candidates.", exception);
        }
    }

    private void requireText(String value, String fieldName) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalStateException(fieldName + " is required.");
        }
    }
}
