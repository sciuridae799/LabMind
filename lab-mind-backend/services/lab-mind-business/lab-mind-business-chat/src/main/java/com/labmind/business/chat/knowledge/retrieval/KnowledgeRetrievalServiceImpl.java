package com.labmind.business.chat.knowledge.retrieval;

import com.labmind.business.chat.knowledge.retrieval.config.KnowledgeRetrievalProperties;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class KnowledgeRetrievalServiceImpl implements KnowledgeRetrievalService {

    private final KnowledgeRetrievalProperties retrievalProperties;

    private final KnowledgeEmbeddingClient embeddingClient;

    private final PgVectorKnowledgeRetriever pgVectorRetriever;

    private final ElasticsearchKnowledgeRetriever elasticsearchRetriever;

    private final KnowledgeRrfFusionService rrfFusionService;

    private final KnowledgeRerankService rerankService;

    private final ParentChildEvidenceAssembler evidenceAssembler;

    @Override
    public KnowledgeRetrievalResult retrieve(KnowledgeRetrievalRequest request) {
        if (!StringUtils.hasText(request.question())) {
            throw new IllegalStateException("retrieval question is required.");
        }
        if (request.documentIdList() == null || request.documentIdList().isEmpty()) {
            return KnowledgeRetrievalResult.empty();
        }
        List<Long> documentIdList = request.documentIdList().stream()
                .filter(documentId -> documentId != null && documentId > 0)
                .distinct()
                .toList();
        if (documentIdList.isEmpty()) {
            return KnowledgeRetrievalResult.empty();
        }
        CompletableFuture<List<KnowledgeRetrievalChildHit>> vectorFuture = CompletableFuture.supplyAsync(() -> {
            List<Double> questionEmbedding = embeddingClient.embed(request.question());
            return pgVectorRetriever.search(request.question(), documentIdList, questionEmbedding);
        });
        CompletableFuture<List<KnowledgeRetrievalChildHit>> keywordFuture = CompletableFuture.supplyAsync(() ->
                elasticsearchRetriever.search(request.question(), documentIdList));
        CompletableFuture.allOf(vectorFuture, keywordFuture).join();
        List<KnowledgeRetrievalChildHit> vectorHitList = vectorFuture.join();
        List<KnowledgeRetrievalChildHit> keywordHitList = keywordFuture.join();
        List<KnowledgeRetrievalFusedChild> fusedChildList =
                rrfFusionService.fuse(vectorHitList, keywordHitList);
        List<KnowledgeRetrievalFusedChild> finalChildList =
                rerankService.rerank(request.question(), fusedChildList);
        return evidenceAssembler.assemble(finalChildList, retrievalProperties.getFinalParentTopK());
    }
}
