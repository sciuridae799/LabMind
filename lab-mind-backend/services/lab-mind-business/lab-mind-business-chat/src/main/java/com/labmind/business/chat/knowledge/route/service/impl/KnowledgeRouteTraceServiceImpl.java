package com.labmind.business.chat.knowledge.route.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.labmind.business.chat.knowledge.route.config.KnowledgeRouteProperties;
import com.labmind.business.chat.knowledge.route.data.KnowledgeRouteTraceCandidateData;
import com.labmind.business.chat.knowledge.route.data.KnowledgeRouteTraceData;
import com.labmind.business.chat.knowledge.route.mapper.KnowledgeRouteTraceCandidateMapper;
import com.labmind.business.chat.knowledge.route.mapper.KnowledgeRouteTraceMapper;
import com.labmind.business.chat.knowledge.route.model.KnowledgeRouteCandidate;
import com.labmind.business.chat.knowledge.route.model.KnowledgeRouteDecision;
import com.labmind.business.chat.knowledge.route.model.KnowledgeRouteRankedCandidate;
import com.labmind.business.chat.knowledge.route.service.KnowledgeRouteTraceService;
import com.labmind.idgenerator.toolkit.SnowflakeIdGenerator;
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
            String workspaceId,
            String conversationId,
            Long exchangeId,
            String question,
            String rewrittenQuestion,
            String intentType,
            String routeMode,
            Long userSelectedDocumentId,
            KnowledgeRouteDecision routeDecision) {
        // trace 必须保存当时的候选快照和置信度，后台复盘不能重新跑路由得到另一组结果。
        KnowledgeRouteDecision decision = routeDecision == null ? KnowledgeRouteDecision.empty() : routeDecision;
        List<KnowledgeRouteCandidate> documentCandidateList = decision.documentCandidates() == null
                ? List.of()
                : decision.documentCandidates();
        KnowledgeRouteCandidate topCandidate = documentCandidateList.isEmpty() ? null : documentCandidateList.get(0);
        KnowledgeRouteCandidate secondCandidate = documentCandidateList.size() > 1 ? documentCandidateList.get(1) : null;
        double confidence = calculateConfidence(topCandidate, secondCandidate);
        String routeStatus = resolveRouteStatus(documentCandidateList, confidence);

        KnowledgeRouteTraceData traceData = new KnowledgeRouteTraceData();
        traceData.setId(snowflakeIdGenerator.nextId());
        traceData.setTraceId(traceId);
        traceData.setWorkspaceId(workspaceId);
        traceData.setConversationId(conversationId);
        traceData.setExchangeId(exchangeId);
        traceData.setQuestion(question);
        traceData.setRewrittenQuestion(rewrittenQuestion);
        traceData.setIntentType(intentType);
        traceData.setSelectedScopeCode(topCandidate == null ? null : topCandidate.scopeCode());
        traceData.setSelectedTopicCode(topCandidate == null ? null : topCandidate.topicCode());
        traceData.setSelectedDocumentIds(writeJson(documentCandidateList.stream()
                .limit(3)
                .map(KnowledgeRouteCandidate::documentId)
                .toList()));
        traceData.setRouteResultJson(writeJson(decision));
        traceData.setUserSelectedDocumentId(userSelectedDocumentId);
        traceData.setRouteTopDocumentId(topCandidate == null ? null : topCandidate.documentId());
        traceData.setHitSelectedDocument(resolveHitSelectedDocument(userSelectedDocumentId, topCandidate));
        traceData.setConfidence(confidence);
        traceData.setRouteStatus(routeStatus);
        traceData.setRouteMode(routeMode);
        traceData.setStatus(NORMAL_STATUS);
        traceMapper.insert(traceData);

        insertRankedCandidates(traceId, decision.scopeCandidates());
        insertRankedCandidates(traceId, decision.topicCandidates());
        insertDocumentCandidates(traceId, documentCandidateList);
    }

    private void insertRankedCandidates(String traceId, List<KnowledgeRouteRankedCandidate> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            return;
        }
        for (int index = 0; index < Math.min(3, candidates.size()); index++) {
            KnowledgeRouteRankedCandidate candidate = candidates.get(index);
            insertCandidate(
                    traceId,
                    candidate.candidateType(),
                    candidate.candidateId(),
                    candidate.candidateName(),
                    candidate.score(),
                    candidate.hitReason(),
                    index + 1);
        }
    }

    private void insertDocumentCandidates(String traceId, List<KnowledgeRouteCandidate> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            return;
        }
        for (int index = 0; index < Math.min(3, candidates.size()); index++) {
            KnowledgeRouteCandidate candidate = candidates.get(index);
            insertCandidate(
                    traceId,
                    "DOCUMENT",
                    String.valueOf(candidate.documentId()),
                    candidate.documentName(),
                    candidate.score(),
                    candidate.hitReason(),
                    index + 1);
        }
    }

    private void insertCandidate(
            String traceId,
            String candidateType,
            String candidateId,
            String candidateName,
            Double score,
            String hitReason,
            int rankNo) {
        KnowledgeRouteTraceCandidateData candidateData = new KnowledgeRouteTraceCandidateData();
        candidateData.setId(snowflakeIdGenerator.nextId());
        candidateData.setTraceId(traceId);
        candidateData.setCandidateType(candidateType);
        candidateData.setCandidateId(candidateId);
        candidateData.setCandidateName(candidateName);
        candidateData.setScore(score);
        candidateData.setHitReason(hitReason);
        candidateData.setRankNo(rankNo);
        candidateData.setStatus(NORMAL_STATUS);
        candidateMapper.insert(candidateData);
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
