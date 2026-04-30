package com.superagent.business.chat.knowledge.route.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.superagent.business.chat.knowledge.route.config.KnowledgeRouteProperties;
import com.superagent.business.chat.knowledge.route.data.KnowledgeRouteTraceCandidateData;
import com.superagent.business.chat.knowledge.route.data.KnowledgeRouteTraceData;
import com.superagent.business.chat.knowledge.route.mapper.KnowledgeRouteTraceCandidateMapper;
import com.superagent.business.chat.knowledge.route.mapper.KnowledgeRouteTraceMapper;
import com.superagent.business.chat.knowledge.route.model.KnowledgeRouteCandidate;
import com.superagent.business.chat.knowledge.route.model.KnowledgeRouteDecision;
import com.superagent.business.chat.knowledge.route.model.KnowledgeRouteRankedCandidate;
import com.superagent.idgenerator.toolkit.SnowflakeIdGenerator;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class KnowledgeRouteTraceServiceImplTest {

    @Mock
    private KnowledgeRouteTraceMapper traceMapper;

    @Mock
    private KnowledgeRouteTraceCandidateMapper candidateMapper;

    @Mock
    private SnowflakeIdGenerator snowflakeIdGenerator;

    private KnowledgeRouteTraceServiceImpl service;

    @BeforeEach
    void setUp() {
        KnowledgeRouteProperties routeProperties = new KnowledgeRouteProperties();
        routeProperties.setSuccessConfidence(0.55D);
        service = new KnowledgeRouteTraceServiceImpl(
                traceMapper,
                candidateMapper,
                snowflakeIdGenerator,
                new ObjectMapper(),
                routeProperties);
        when(snowflakeIdGenerator.nextId()).thenReturn(1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L, 9L, 10L);
    }

    @Test
    void shouldRecordTopThreeCandidatesForEveryRouteLevel() {
        KnowledgeRouteDecision decision = new KnowledgeRouteDecision(
                List.of(
                        ranked("SCOPE", "s1", "订单", 9),
                        ranked("SCOPE", "s2", "合同", 8),
                        ranked("SCOPE", "s3", "费用", 7),
                        ranked("SCOPE", "s4", "库存", 6)),
                List.of(
                        ranked("TOPIC", "t1", "审核", 9),
                        ranked("TOPIC", "t2", "配置", 8),
                        ranked("TOPIC", "t3", "归档", 7),
                        ranked("TOPIC", "t4", "通知", 6)),
                List.of(
                        document(9001L, "订单审核", 10),
                        document(9002L, "订单风控", 3),
                        document(9003L, "订单归档", 2),
                        document(9004L, "订单通知", 1)));

        service.recordRouteTrace(
                "trace-1",
                "conversation-1",
                2001L,
                "原始问题",
                "改写问题",
                "document_question_answer",
                "SHADOW",
                9001L,
                decision);

        ArgumentCaptor<KnowledgeRouteTraceData> traceCaptor = ArgumentCaptor.forClass(KnowledgeRouteTraceData.class);
        verify(traceMapper).insert(traceCaptor.capture());
        assertThat(traceCaptor.getValue().getUserSelectedDocumentId()).isEqualTo(9001L);
        assertThat(traceCaptor.getValue().getRouteTopDocumentId()).isEqualTo(9001L);
        assertThat(traceCaptor.getValue().getHitSelectedDocument()).isEqualTo(1);
        assertThat(traceCaptor.getValue().getRouteResultJson()).contains("scopeCandidates", "topicCandidates", "documentCandidates");

        ArgumentCaptor<KnowledgeRouteTraceCandidateData> candidateCaptor =
                ArgumentCaptor.forClass(KnowledgeRouteTraceCandidateData.class);
        verify(candidateMapper, org.mockito.Mockito.times(9)).insert(candidateCaptor.capture());
        assertThat(candidateCaptor.getAllValues())
                .extracting(KnowledgeRouteTraceCandidateData::getCandidateType)
                .containsExactly("SCOPE", "SCOPE", "SCOPE", "TOPIC", "TOPIC", "TOPIC", "DOCUMENT", "DOCUMENT", "DOCUMENT");
    }

    private KnowledgeRouteRankedCandidate ranked(
            String candidateType,
            String candidateId,
            String candidateName,
            double score) {
        return new KnowledgeRouteRankedCandidate(candidateType, candidateId, candidateName, score, "命中原因");
    }

    private KnowledgeRouteCandidate document(long documentId, String documentName, double score) {
        return new KnowledgeRouteCandidate(
                documentId,
                documentName,
                "scope",
                "知识域",
                "topic",
                "专题",
                score,
                0D,
                score,
                score,
                0D,
                List.of(),
                List.of(),
                "命中原因");
    }
}
