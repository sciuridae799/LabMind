package com.superagent.business.chat.knowledge.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.superagent.business.chat.knowledge.config.KnowledgeRouteProperties;
import com.superagent.business.chat.knowledge.data.KnowledgeRouteTraceCandidateData;
import com.superagent.business.chat.knowledge.data.KnowledgeRouteTraceData;
import com.superagent.business.chat.knowledge.mapper.KnowledgeRouteTraceCandidateMapper;
import com.superagent.business.chat.knowledge.mapper.KnowledgeRouteTraceMapper;
import com.superagent.business.chat.knowledge.model.KnowledgeRouteCandidate;
import com.superagent.business.chat.knowledge.service.KnowledgeRouteTraceService;
import com.superagent.idgenerator.toolkit.SnowflakeIdGenerator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class KnowledgeRouteTraceServiceImpl implements KnowledgeRouteTraceService {

    private static final int NORMAL_STATUS = 1;

    private final KnowledgeRouteTraceMapper traceMapper;

    private final KnowledgeRouteTraceCandidateMapper candidateMapper;

    private final SnowflakeIdGenerator snowflakeIdGenerator;

    private final ObjectMapper objectMapper;

    private final KnowledgeRouteProperties routeProperties;

    @Override
    @Transactional
    public void recordRouteTrace(
            String traceId,
            String conversationId,
            Long exchangeId,
            String question,
            String rewrittenQuestion,
            String intentType,
            String routeMode,
            Long userSelectedDocumentId,
            List<KnowledgeRouteCandidate> candidates) {
        // trace 必须保存当时的候选快照和置信度，后台复盘不能重新跑路由得到另一组结果。
        List<KnowledgeRouteCandidate> candidateList = candidates == null ? List.of() : candidates;
        KnowledgeRouteCandidate topCandidate = candidateList.isEmpty() ? null : candidateList.get(0);
        KnowledgeRouteCandidate secondCandidate = candidateList.size() > 1 ? candidateList.get(1) : null;
        double confidence = calculateConfidence(topCandidate, secondCandidate);
        String routeStatus = resolveRouteStatus(candidateList, confidence);

        KnowledgeRouteTraceData traceData = new KnowledgeRouteTraceData();
        traceData.setId(snowflakeIdGenerator.nextId());
        traceData.setTraceId(traceId);
        traceData.setConversationId(conversationId);
        traceData.setExchangeId(exchangeId);
        traceData.setQuestion(question);
        traceData.setRewrittenQuestion(rewrittenQuestion);
        traceData.setIntentType(intentType);
        traceData.setSelectedScopeCode(topCandidate == null ? null : topCandidate.scopeCode());
        traceData.setSelectedTopicCode(topCandidate == null ? null : topCandidate.topicCode());
        traceData.setSelectedDocumentIds(writeJson(candidateList.stream().map(KnowledgeRouteCandidate::documentId).toList()));
        traceData.setRouteResultJson(writeJson(candidateList));
        traceData.setUserSelectedDocumentId(userSelectedDocumentId);
        traceData.setRouteTopDocumentId(topCandidate == null ? null : topCandidate.documentId());
        traceData.setHitSelectedDocument(resolveHitSelectedDocument(userSelectedDocumentId, topCandidate));
        traceData.setConfidence(confidence);
        traceData.setRouteStatus(routeStatus);
        traceData.setRouteMode(routeMode);
        traceData.setStatus(NORMAL_STATUS);
        traceMapper.insert(traceData);

        for (int index = 0; index < candidateList.size(); index++) {
            KnowledgeRouteCandidate candidate = candidateList.get(index);
            KnowledgeRouteTraceCandidateData candidateData = new KnowledgeRouteTraceCandidateData();
            candidateData.setId(snowflakeIdGenerator.nextId());
            candidateData.setTraceId(traceId);
            candidateData.setCandidateType("DOCUMENT");
            candidateData.setCandidateId(String.valueOf(candidate.documentId()));
            candidateData.setCandidateName(candidate.documentName());
            candidateData.setScore(candidate.score());
            candidateData.setHitReason(candidate.hitReason());
            candidateData.setRankNo(index + 1);
            candidateData.setStatus(NORMAL_STATUS);
            candidateMapper.insert(candidateData);
        }
    }

    /**
     * 计算路由相对置信度。
     *
     * <p>置信度只看 Top1 与 Top2 的相对差距，不使用绝对分数阈值判断知识库之间不可比的分值。</p>
     */
    private double calculateConfidence(KnowledgeRouteCandidate topCandidate, KnowledgeRouteCandidate secondCandidate) {
        if (topCandidate == null) {
            return 0D;
        }
        double secondScore = secondCandidate == null ? 0D : secondCandidate.score();
        return topCandidate.score() / Math.max(10D, topCandidate.score() + secondScore + 5D);
    }

    /**
     * 将候选和置信度转换为稳定路由状态。
     */
    private String resolveRouteStatus(List<KnowledgeRouteCandidate> candidates, double confidence) {
        if (candidates.isEmpty()) {
            return "FAILED";
        }
        return confidence >= routeProperties.getSuccessConfidence() ? "SUCCESS" : "LOW_CONFIDENCE";
    }

    private Integer resolveHitSelectedDocument(Long selectedDocumentId, KnowledgeRouteCandidate topCandidate) {
        if (selectedDocumentId == null || topCandidate == null) {
            return null;
        }
        return selectedDocumentId.equals(topCandidate.documentId()) ? 1 : 0;
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException error) {
            throw new IllegalStateException("failed to serialize knowledge route trace", error);
        }
    }
}
