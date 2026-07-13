package com.labmind.business.chat.knowledge.retrieval;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.labmind.business.chat.knowledge.retrieval.config.KnowledgeRetrievalProperties;
import com.labmind.business.chat.knowledge.document.data.KnowledgeDocumentData;
import com.labmind.business.chat.knowledge.indexing.data.KnowledgeDocumentParentBlockData;
import com.labmind.business.chat.knowledge.document.mapper.KnowledgeDocumentMapper;
import com.labmind.business.chat.knowledge.indexing.mapper.KnowledgeDocumentParentBlockMapper;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class ParentChildEvidenceAssembler {

    private static final int NORMAL_STATUS = 1;

    private final KnowledgeDocumentParentBlockMapper parentBlockMapper;

    private final KnowledgeDocumentMapper documentMapper;

    private final KnowledgeRetrievalProperties retrievalProperties;

    public KnowledgeRetrievalResult assemble(List<KnowledgeRetrievalFusedChild> childList, int parentTopK) {
        if (childList == null || childList.isEmpty()) {
            return KnowledgeRetrievalResult.empty();
        }
        List<Long> parentBlockIdList = childList.stream()
                .map(KnowledgeRetrievalFusedChild::parentBlockId)
                .distinct()
                .toList();
        Map<Long, KnowledgeDocumentParentBlockData> parentBlockMap = parentBlockMapper.selectList(
                        Wrappers.<KnowledgeDocumentParentBlockData>lambdaQuery()
                                .in(KnowledgeDocumentParentBlockData::getId, parentBlockIdList)
                                .eq(KnowledgeDocumentParentBlockData::getStatus, NORMAL_STATUS))
                .stream()
                .collect(Collectors.toMap(KnowledgeDocumentParentBlockData::getId, Function.identity()));
        if (parentBlockMap.size() != parentBlockIdList.size()) {
            throw new IllegalStateException("retrieval parent block is missing.");
        }
        Map<Long, String> documentNameMap = documentMapper.selectList(
                        Wrappers.<KnowledgeDocumentData>lambdaQuery()
                                .in(KnowledgeDocumentData::getId, childList.stream()
                                        .map(KnowledgeRetrievalFusedChild::documentId)
                                        .distinct()
                                        .toList())
                                .eq(KnowledgeDocumentData::getStatus, NORMAL_STATUS))
                .stream()
                .collect(Collectors.toMap(KnowledgeDocumentData::getId, KnowledgeDocumentData::getDocumentName));
        List<KnowledgeRetrievalParentEvidence> rankedParentEvidenceList = childList.stream()
                .collect(Collectors.groupingBy(
                        KnowledgeRetrievalFusedChild::parentBlockId,
                        java.util.LinkedHashMap::new,
                        Collectors.toList()))
                .entrySet()
                .stream()
                .map(entry -> toParentEvidence(entry.getKey(), entry.getValue(), parentBlockMap, documentNameMap))
                .sorted(Comparator.comparing(KnowledgeRetrievalParentEvidence::score).reversed())
                .limit(parentTopK)
                .toList();
        List<KnowledgeRetrievalParentEvidence> parentEvidenceList = applyTotalEvidenceBudget(rankedParentEvidenceList);
        return new KnowledgeRetrievalResult(buildEvidenceContextText(parentEvidenceList), parentEvidenceList);
    }

    private KnowledgeRetrievalParentEvidence toParentEvidence(
            Long parentBlockId,
            List<KnowledgeRetrievalFusedChild> childList,
            Map<Long, KnowledgeDocumentParentBlockData> parentBlockMap,
            Map<Long, String> documentNameMap) {
        KnowledgeDocumentParentBlockData parentBlock = parentBlockMap.get(parentBlockId);
        KnowledgeRetrievalFusedChild topChild = childList.stream()
                .max(Comparator.comparing(KnowledgeRetrievalFusedChild::finalScore))
                .orElseThrow();
        Set<String> channelSet = new LinkedHashSet<>();
        childList.forEach(child -> channelSet.addAll(child.channels()));
        String parentText = parentBlock.getParentText();
        if (!StringUtils.hasText(parentText)) {
            throw new IllegalStateException("retrieval parent text is empty: " + parentBlockId);
        }
        return new KnowledgeRetrievalParentEvidence(
                parentBlockId,
                parentBlock.getDocumentId(),
                documentNameMap.get(parentBlock.getDocumentId()),
                parentBlock.getSectionPath(),
                limitText(parentText.strip(), maxSingleParentChars()),
                topChild.finalScore(),
                childList.stream()
                        .map(KnowledgeRetrievalFusedChild::chunkId)
                        .distinct()
                        .toList(),
                List.copyOf(channelSet));
    }

    private String buildEvidenceContextText(List<KnowledgeRetrievalParentEvidence> parentEvidenceList) {
        if (parentEvidenceList.isEmpty()) {
            return null;
        }
        StringBuilder builder = new StringBuilder();
        for (int index = 0; index < parentEvidenceList.size(); index++) {
            KnowledgeRetrievalParentEvidence evidence = parentEvidenceList.get(index);
            builder.append(renderEvidenceText(evidence, index + 1)).append("\n\n");
        }
        return builder.toString().strip();
    }

    private List<KnowledgeRetrievalParentEvidence> applyTotalEvidenceBudget(
            List<KnowledgeRetrievalParentEvidence> rankedParentEvidenceList) {
        if (rankedParentEvidenceList.isEmpty()) {
            return List.of();
        }
        int maxTotalEvidenceChars = retrievalProperties.getMaxTotalEvidenceChars();
        if (maxTotalEvidenceChars <= 0) {
            throw new IllegalStateException("retrieval maxTotalEvidenceChars must be positive.");
        }
        List<KnowledgeRetrievalParentEvidence> resultList = new ArrayList<>();
        int usedChars = 0;
        for (KnowledgeRetrievalParentEvidence evidence : rankedParentEvidenceList) {
            int evidenceNumber = resultList.size() + 1;
            int evidenceChars = renderEvidenceText(evidence, evidenceNumber).length();
            if (!resultList.isEmpty()) {
                evidenceChars += 2;
            }
            if (usedChars + evidenceChars > maxTotalEvidenceChars) {
                break;
            }
            resultList.add(evidence);
            usedChars += evidenceChars;
        }
        return resultList;
    }

    private int maxSingleParentChars() {
        int maxParentChars = retrievalProperties.getMaxParentChars();
        int maxSingleQuestionEvidenceChars = retrievalProperties.getMaxSingleQuestionEvidenceChars();
        if (maxParentChars <= 0 || maxSingleQuestionEvidenceChars <= 0) {
            throw new IllegalStateException("retrieval evidence char limits must be positive.");
        }
        return Math.min(maxParentChars, maxSingleQuestionEvidenceChars);
    }

    private String renderEvidenceText(KnowledgeRetrievalParentEvidence evidence, int evidenceNumber) {
        return new StringBuilder()
                .append("[").append(evidenceNumber).append("]\n")
                .append("文档ID：").append(evidence.documentId()).append("\n")
                .append("文档名称：").append(nullToEmpty(evidence.documentName())).append("\n")
                .append("章节路径：").append(nullToEmpty(evidence.sectionPath())).append("\n")
                .append("命中通道：").append(String.join(",", evidence.channels())).append("\n")
                .append("命中Child：").append(evidence.hitChunkIdList()).append("\n")
                .append("Parent正文：\n").append(evidence.parentText())
                .toString();
    }

    private String limitText(String value, int maxChars) {
        if (value.length() <= maxChars) {
            return value;
        }
        return value.substring(0, maxChars).strip();
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
