package com.superagent.business.chat.knowledge.indexing;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.superagent.business.chat.chatagent.execution.BusinessChatDynamicModelClient;
import com.superagent.business.chat.chatagent.execution.model.BusinessChatModelApiConfigSnapshot;
import com.superagent.business.chat.chatagent.service.BusinessChatModelApiConfigService;
import com.superagent.business.chat.knowledge.indexing.data.KnowledgeDocumentChunkData;
import com.superagent.business.chat.knowledge.document.data.KnowledgeDocumentData;
import com.superagent.business.chat.knowledge.indexing.data.KnowledgeDocumentParentBlockData;
import com.superagent.business.chat.knowledge.indexing.data.KnowledgeDocumentStrategyStepData;
import com.superagent.business.chat.knowledge.route.data.KnowledgeDocumentStructureNodeData;
import com.superagent.business.chat.knowledge.indexing.mapper.KnowledgeDocumentChunkMapper;
import com.superagent.business.chat.knowledge.indexing.mapper.KnowledgeDocumentParentBlockMapper;
import com.superagent.business.chat.knowledge.retrieval.ElasticsearchKnowledgeRetriever;
import com.superagent.business.chat.knowledge.retrieval.KnowledgeEmbeddingClient;
import com.superagent.business.chat.knowledge.retrieval.PgVectorKnowledgeRetriever;
import com.superagent.idgenerator.toolkit.SnowflakeIdGenerator;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class KnowledgeRetrievalIndexService {

    private static final int NORMAL_STATUS = 1;
    private static final int SOURCE_ORIGINAL_TEXT = 1;
    private static final int VECTOR_SUCCEEDED = 3;
    private static final int PGVECTOR_STORE = 2;
    private static final int PARENT_MAX_CHARS = 2400;
    private static final int CHILD_MAX_CHARS = 700;
    private static final int TOKEN_DIVISOR = 2;
    private static final int STRATEGY_STRUCTURE = 1;
    private static final int STRATEGY_RECURSIVE = 2;
    private static final int STRATEGY_SEMANTIC = 3;
    private static final int STRATEGY_LLM = 4;

    private final KnowledgeDocumentParentBlockMapper parentBlockMapper;

    private final KnowledgeDocumentChunkMapper chunkMapper;

    private final SnowflakeIdGenerator snowflakeIdGenerator;

    private final ObjectMapper objectMapper;

    private final KnowledgeEmbeddingClient embeddingClient;

    private final PgVectorKnowledgeRetriever pgVectorRetriever;

    private final ElasticsearchKnowledgeRetriever elasticsearchRetriever;

    private final BusinessChatModelApiConfigService modelApiConfigService;

    private final BusinessChatDynamicModelClient modelClient;

    public void rebuildIndex(
            KnowledgeDocumentData documentData,
            long taskId,
            List<KnowledgeDocumentStructureNodeData> structureNodeList,
            String parsedText,
            List<KnowledgeDocumentStrategyStepData> strategySteps) {
        if (!StringUtils.hasText(parsedText)) {
            throw new IllegalStateException("parsed text is required to build retrieval index: " + documentData.getId());
        }
        validateStrategySteps(strategySteps);
        Set<Integer> strategyTypes = strategyTypeSet(strategySteps);
        deleteExistingIndex(documentData.getId());
        List<ParentDraft> parentDraftList = buildParentDraftList(structureNodeList, parsedText);
        int chunkNo = 1;
        for (int parentIndex = 0; parentIndex < parentDraftList.size(); parentIndex++) {
            ParentDraft parentDraft = parentDraftList.get(parentIndex);
            long parentBlockId = snowflakeIdGenerator.nextId();
            List<String> childTextList = buildChildTextList(parentDraft.text(), strategyTypes);
            KnowledgeDocumentParentBlockData parentBlockData = buildParentBlock(
                    documentData,
                    taskId,
                    parentBlockId,
                    parentIndex + 1,
                    parentDraft,
                    childTextList.size(),
                    chunkNo,
                    chunkNo + childTextList.size() - 1);
            parentBlockMapper.insert(parentBlockData);
            for (String childText : childTextList) {
                long chunkId = snowflakeIdGenerator.nextId();
                KnowledgeDocumentChunkData chunkData = buildChunk(
                        documentData,
                        taskId,
                        parentBlockId,
                        chunkId,
                        chunkNo,
                        parentDraft,
                        childText);
                chunkMapper.insert(chunkData);
                KnowledgeRetrievalIndexChunk indexChunk = toIndexChunk(chunkData, documentData);
                List<Double> embedding = embeddingClient.embed(childText);
                pgVectorRetriever.upsert(indexChunk, embedding);
                elasticsearchRetriever.index(indexChunk, documentData.getDocumentName());
                chunkNo++;
            }
        }
    }

    private void validateStrategySteps(List<KnowledgeDocumentStrategyStepData> strategySteps) {
        Set<Integer> strategyTypes = strategyTypeSet(strategySteps);
        if (!strategyTypes.contains(STRATEGY_STRUCTURE) || !strategyTypes.contains(STRATEGY_RECURSIVE)) {
            throw new IllegalStateException("confirmed strategy must contain structure and recursive chunking");
        }
        for (Integer strategyType : strategyTypes) {
            if (strategyType < STRATEGY_STRUCTURE || strategyType > STRATEGY_LLM) {
                throw new IllegalStateException("unsupported confirmed strategyType: " + strategyType);
            }
        }
    }

    private Set<Integer> strategyTypeSet(List<KnowledgeDocumentStrategyStepData> strategySteps) {
        if (strategySteps == null || strategySteps.isEmpty()) {
            throw new IllegalStateException("confirmed strategy steps are required");
        }
        return strategySteps.stream()
                .sorted(Comparator.comparing(KnowledgeDocumentStrategyStepData::getStepNo))
                .map(KnowledgeDocumentStrategyStepData::getStrategyType)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
    }

    private List<String> buildChildTextList(String text, Set<Integer> strategyTypes) {
        if (strategyTypes.contains(STRATEGY_LLM)) {
            return splitByLlm(text);
        }
        if (strategyTypes.contains(STRATEGY_SEMANTIC)) {
            return splitBySemanticBoundary(text);
        }
        return splitByMaxChars(text, CHILD_MAX_CHARS);
    }

    private void deleteExistingIndex(long documentId) {
        chunkMapper.delete(Wrappers.<KnowledgeDocumentChunkData>lambdaQuery()
                .eq(KnowledgeDocumentChunkData::getDocumentId, documentId));
        parentBlockMapper.delete(Wrappers.<KnowledgeDocumentParentBlockData>lambdaQuery()
                .eq(KnowledgeDocumentParentBlockData::getDocumentId, documentId));
        pgVectorRetriever.deleteByDocumentId(documentId);
        elasticsearchRetriever.deleteByDocumentId(documentId);
    }

    private List<ParentDraft> buildParentDraftList(
            List<KnowledgeDocumentStructureNodeData> structureNodeList,
            String parsedText) {
        List<ParentDraft> structuralDrafts = structureNodeList.stream()
                .filter(node -> StringUtils.hasText(node.getContentText()))
                .sorted(Comparator.comparing(KnowledgeDocumentStructureNodeData::getNodeNo))
                .flatMap(node -> splitByMaxChars(node.getContentText().strip(), PARENT_MAX_CHARS).stream()
                        .map(text -> new ParentDraft(
                                text,
                                node.getSectionPath(),
                                node.getId(),
                                node.getNodeType(),
                                node.getCanonicalPath(),
                                node.getItemIndex())))
                .toList();
        if (!structuralDrafts.isEmpty()) {
            return structuralDrafts;
        }
        return splitByMaxChars(parsedText.strip(), PARENT_MAX_CHARS).stream()
                .map(text -> new ParentDraft(text, null, null, null, null, null))
                .toList();
    }

    private List<String> splitByMaxChars(String text, int maxChars) {
        String normalizedText = text == null ? null : text.strip();
        if (!StringUtils.hasText(normalizedText)) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        int offset = 0;
        while (offset < normalizedText.length()) {
            int end = Math.min(normalizedText.length(), offset + maxChars);
            result.add(normalizedText.substring(offset, end).strip());
            offset = end;
        }
        return result.stream().filter(StringUtils::hasText).toList();
    }

    private List<String> splitBySemanticBoundary(String text) {
        String normalizedText = text == null ? null : text.strip();
        if (!StringUtils.hasText(normalizedText)) {
            return List.of();
        }
        List<String> sentences = List.of(normalizedText.split("(?<=[。！？.!?])\\s*"));
        List<String> chunks = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String sentence : sentences) {
            String normalizedSentence = sentence.strip();
            if (!StringUtils.hasText(normalizedSentence)) {
                continue;
            }
            if (normalizedSentence.length() > CHILD_MAX_CHARS) {
                flushChunk(chunks, current);
                chunks.addAll(splitByMaxChars(normalizedSentence, CHILD_MAX_CHARS));
                continue;
            }
            if (!current.isEmpty() && current.length() + normalizedSentence.length() + 1 > CHILD_MAX_CHARS) {
                flushChunk(chunks, current);
            }
            if (!current.isEmpty()) {
                current.append("\n");
            }
            current.append(normalizedSentence);
        }
        flushChunk(chunks, current);
        return chunks;
    }

    private void flushChunk(List<String> chunks, StringBuilder current) {
        if (current.isEmpty()) {
            return;
        }
        String chunk = current.toString().strip();
        current.setLength(0);
        if (StringUtils.hasText(chunk)) {
            chunks.add(chunk);
        }
    }

    private List<String> splitByLlm(String text) {
        String normalizedText = text == null ? null : text.strip();
        if (!StringUtils.hasText(normalizedText)) {
            return List.of();
        }
        if (normalizedText.length() <= CHILD_MAX_CHARS) {
            return List.of(normalizedText);
        }
        BusinessChatModelApiConfigSnapshot modelConfig = modelApiConfigService.getLatestAvailableSnapshot();
        String response = modelClient.call(
                modelConfig,
                "你是文档切块器。只输出 JSON 字符串数组，不输出解释。每个元素必须是原文连续片段，不能改写，长度不超过 700 字。",
                normalizedText);
        List<String> chunks = parseLlmChunks(response);
        if (chunks.stream().anyMatch(chunk -> chunk.length() > CHILD_MAX_CHARS)) {
            throw new IllegalStateException("LLM chunk result contains chunk longer than 700 characters");
        }
        String joined = String.join("", chunks).replaceAll("\\s+", "");
        String original = normalizedText.replaceAll("\\s+", "");
        if (!original.equals(joined)) {
            throw new IllegalStateException("LLM chunk result does not preserve original text");
        }
        return chunks;
    }

    private List<String> parseLlmChunks(String response) {
        try {
            List<String> chunks = objectMapper.readValue(response, new TypeReference<>() {
            });
            List<String> normalized = chunks.stream()
                    .map(chunk -> chunk == null ? "" : chunk.strip())
                    .filter(StringUtils::hasText)
                    .toList();
            if (normalized.isEmpty()) {
                throw new IllegalStateException("LLM chunk result is empty");
            }
            return normalized;
        } catch (Exception error) {
            throw new IllegalStateException("failed to parse LLM chunk result", error);
        }
    }

    private KnowledgeDocumentParentBlockData buildParentBlock(
            KnowledgeDocumentData documentData,
            long taskId,
            long parentBlockId,
            int parentNo,
            ParentDraft parentDraft,
            int childCount,
            int startChunkNo,
            int endChunkNo) {
        KnowledgeDocumentParentBlockData data = new KnowledgeDocumentParentBlockData();
        data.setId(parentBlockId);
        data.setDocumentId(documentData.getId());
        data.setTaskId(taskId);
        data.setPlanId(documentData.getCurrentPlanId());
        data.setParentNo(parentNo);
        data.setSourceType(SOURCE_ORIGINAL_TEXT);
        data.setSectionPath(parentDraft.sectionPath());
        data.setStructureNodeId(parentDraft.structureNodeId());
        data.setStructureNodeType(parentDraft.structureNodeType());
        data.setCanonicalPath(parentDraft.canonicalPath());
        data.setItemIndex(parentDraft.itemIndex());
        data.setParentText(parentDraft.text());
        data.setCharCount(parentDraft.text().length());
        data.setTokenCount(estimateTokenCount(parentDraft.text()));
        data.setChildCount(childCount);
        data.setStartChunkNo(startChunkNo);
        data.setEndChunkNo(endChunkNo);
        data.setStatus(NORMAL_STATUS);
        return data;
    }

    private KnowledgeDocumentChunkData buildChunk(
            KnowledgeDocumentData documentData,
            long taskId,
            long parentBlockId,
            long chunkId,
            int chunkNo,
            ParentDraft parentDraft,
            String childText) {
        KnowledgeDocumentChunkData data = new KnowledgeDocumentChunkData();
        data.setId(chunkId);
        data.setDocumentId(documentData.getId());
        data.setTaskId(taskId);
        data.setPlanId(documentData.getCurrentPlanId());
        data.setParentBlockId(parentBlockId);
        data.setChunkNo(chunkNo);
        data.setSourceType(SOURCE_ORIGINAL_TEXT);
        data.setSectionPath(parentDraft.sectionPath());
        data.setStructureNodeId(parentDraft.structureNodeId());
        data.setStructureNodeType(parentDraft.structureNodeType());
        data.setCanonicalPath(parentDraft.canonicalPath());
        data.setItemIndex(parentDraft.itemIndex());
        data.setChunkText(childText);
        data.setCharCount(childText.length());
        data.setTokenCount(estimateTokenCount(childText));
        data.setVectorStatus(VECTOR_SUCCEEDED);
        data.setVectorStoreType(PGVECTOR_STORE);
        data.setVectorId(String.valueOf(chunkId));
        data.setStatus(NORMAL_STATUS);
        return data;
    }

    private KnowledgeRetrievalIndexChunk toIndexChunk(
            KnowledgeDocumentChunkData chunkData,
            KnowledgeDocumentData documentData) {
        try {
            String metadataJson = objectMapper.writeValueAsString(Map.of(
                    "documentName", documentData.getDocumentName(),
                    "scopeCode", nullToEmpty(documentData.getKnowledgeScopeCode()),
                    "scopeName", nullToEmpty(documentData.getKnowledgeScopeName()),
                    "businessCategory", nullToEmpty(documentData.getBusinessCategory())));
            return new KnowledgeRetrievalIndexChunk(
                    chunkData.getId(),
                    chunkData.getDocumentId(),
                    chunkData.getTaskId(),
                    chunkData.getPlanId(),
                    chunkData.getParentBlockId(),
                    chunkData.getChunkNo(),
                    chunkData.getSourceType(),
                    chunkData.getSectionPath(),
                    chunkData.getStructureNodeId(),
                    chunkData.getStructureNodeType(),
                    chunkData.getCanonicalPath(),
                    chunkData.getItemIndex(),
                    chunkData.getChunkText(),
                    chunkData.getCharCount(),
                    chunkData.getTokenCount(),
                    metadataJson);
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to build retrieval chunk metadata.", exception);
        }
    }

    private int estimateTokenCount(String text) {
        return Math.max(1, text.length() / TOKEN_DIVISOR);
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private record ParentDraft(
            String text,
            String sectionPath,
            Long structureNodeId,
            Integer structureNodeType,
            String canonicalPath,
            Integer itemIndex) {
    }
}
