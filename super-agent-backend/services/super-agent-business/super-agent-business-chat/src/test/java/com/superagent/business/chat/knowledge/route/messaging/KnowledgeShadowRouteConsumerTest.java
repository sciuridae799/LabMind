package com.superagent.business.chat.knowledge.route.messaging;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.superagent.business.chat.knowledge.route.graph.KnowledgeGraphClient;
import com.superagent.business.chat.knowledge.route.model.KnowledgeRouteDecision;
import com.superagent.business.chat.knowledge.route.service.KnowledgeRouteTraceService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class KnowledgeShadowRouteConsumerTest {

    @Mock
    private KnowledgeGraphClient knowledgeGraphClient;

    @Mock
    private KnowledgeRouteTraceService knowledgeRouteTraceService;

    @Test
    void shouldRouteShadowQuestionInsideMessageWorkspace() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        KnowledgeShadowRouteConsumer consumer = new KnowledgeShadowRouteConsumer(
                objectMapper,
                knowledgeGraphClient,
                knowledgeRouteTraceService);
        KnowledgeRouteDecision decision = KnowledgeRouteDecision.empty();
        when(knowledgeGraphClient.routeQuestion("workspace-2", "改写后的问题", 5)).thenReturn(decision);
        KnowledgeShadowRouteRequestedMessage message = new KnowledgeShadowRouteRequestedMessage(
                "trace-1",
                "workspace-2",
                "conversation-1",
                1001L,
                "原始问题",
                "改写后的问题",
                "knowledge_question",
                9001L);

        consumer.consume(objectMapper.writeValueAsString(message));

        verify(knowledgeGraphClient).routeQuestion("workspace-2", "改写后的问题", 5);
        verify(knowledgeRouteTraceService).recordRouteTrace(
                "trace-1",
                "workspace-2",
                "conversation-1",
                1001L,
                "原始问题",
                "改写后的问题",
                "knowledge_question",
                "SHADOW",
                9001L,
                decision);
    }
}
