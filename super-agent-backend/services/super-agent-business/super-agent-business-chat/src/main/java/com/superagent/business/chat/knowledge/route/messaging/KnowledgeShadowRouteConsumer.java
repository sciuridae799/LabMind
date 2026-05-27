package com.superagent.business.chat.knowledge.route.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.superagent.business.chat.knowledge.route.graph.KnowledgeGraphClient;
import com.superagent.business.chat.knowledge.document.service.KnowledgeManageService;
import com.superagent.business.chat.knowledge.route.model.KnowledgeRouteCandidate;
import com.superagent.business.chat.knowledge.route.model.KnowledgeRouteDecision;
import com.superagent.business.chat.knowledge.route.service.KnowledgeRouteTraceService;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * 影子路由消费者。
 */
@Component
@RequiredArgsConstructor
public class KnowledgeShadowRouteConsumer {

    private final ObjectMapper objectMapper;

    private final KnowledgeGraphClient knowledgeGraphClient;

    private final KnowledgeRouteTraceService knowledgeRouteTraceService;

    private final KnowledgeManageService knowledgeManageService;

    @KafkaListener(
            topics = "${super-agent.kafka.topics.shadow-route-requested}",
            groupId = "${spring.kafka.consumer.group-id}")
    public void consume(String payload) {
        KnowledgeShadowRouteRequestedMessage message = readMessage(payload);
        KnowledgeRouteDecision routeDecision = filterRouteDecisionByWorkspace(
                knowledgeGraphClient.routeQuestion(message.rewrittenQuestion(), 5),
                message.workspaceId());
        knowledgeRouteTraceService.recordRouteTrace(
                message.traceId(),
                message.workspaceId(),
                message.conversationId(),
                message.exchangeId(),
                message.question(),
                message.rewrittenQuestion(),
                message.intentType(),
                "SHADOW",
                message.userSelectedDocumentId(),
                routeDecision);
    }

    private KnowledgeRouteDecision filterRouteDecisionByWorkspace(
            KnowledgeRouteDecision routeDecision,
            String workspaceId) {
        if (routeDecision == null || routeDecision.documentCandidates() == null
                || routeDecision.documentCandidates().isEmpty()) {
            return KnowledgeRouteDecision.empty();
        }
        java.util.List<Long> allowedDocumentIds = knowledgeManageService.filterDocumentIdsByWorkspace(
                routeDecision.documentCandidates().stream()
                        .map(KnowledgeRouteCandidate::documentId)
                        .toList(),
                workspaceId);
        java.util.List<KnowledgeRouteCandidate> filteredCandidates = routeDecision.documentCandidates().stream()
                .filter(candidate -> allowedDocumentIds.contains(candidate.documentId()))
                .toList();
        return new KnowledgeRouteDecision(routeDecision.scopeCandidates(), routeDecision.topicCandidates(), filteredCandidates);
    }

    private KnowledgeShadowRouteRequestedMessage readMessage(String payload) {
        try {
            return objectMapper.readValue(payload, KnowledgeShadowRouteRequestedMessage.class);
        } catch (JsonProcessingException error) {
            throw new IllegalStateException("failed to parse shadow route message", error);
        }
    }
}
