package com.labmind.business.chat.knowledge.retrieval;

import com.labmind.business.chat.knowledge.retrieval.config.KnowledgeRetrievalProperties;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class KnowledgeRrfFusionService {

    private final KnowledgeRetrievalProperties retrievalProperties;

    public List<KnowledgeRetrievalFusedChild> fuse(
            List<KnowledgeRetrievalChildHit> vectorHitList,
            List<KnowledgeRetrievalChildHit> keywordHitList) {
        Map<Long, List<KnowledgeRetrievalChildHit>> hitMap = new ArrayList<KnowledgeRetrievalChildHit>() {{
            addAll(vectorHitList);
            addAll(keywordHitList);
        }}.stream().collect(Collectors.groupingBy(KnowledgeRetrievalChildHit::chunkId));
        int k = retrievalProperties.getRrf().getK();
        return hitMap.values().stream()
                .map(hitList -> toFusedChild(hitList, k))
                .sorted(Comparator.comparing(KnowledgeRetrievalFusedChild::rrfScore).reversed())
                .limit(retrievalProperties.getChildTopK())
                .toList();
    }

    private KnowledgeRetrievalFusedChild toFusedChild(List<KnowledgeRetrievalChildHit> hitList, int k) {
        KnowledgeRetrievalChildHit first = hitList.get(0);
        double score = hitList.stream()
                .mapToDouble(hit -> 1D / (k + hit.rank()))
                .sum();
        Set<String> channelSet = hitList.stream()
                .map(KnowledgeRetrievalChildHit::channel)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        return new KnowledgeRetrievalFusedChild(
                first.chunkId(),
                first.documentId(),
                first.parentBlockId(),
                first.chunkNo(),
                first.documentName(),
                first.sectionPath(),
                first.chunkText(),
                score,
                null,
                List.copyOf(channelSet));
    }
}
