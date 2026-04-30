package com.superagent.business.chat.knowledge.retrieval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.superagent.business.chat.knowledge.retrieval.config.KnowledgeRetrievalProperties;
import com.superagent.business.chat.knowledge.document.data.KnowledgeDocumentData;
import com.superagent.business.chat.knowledge.indexing.data.KnowledgeDocumentParentBlockData;
import com.superagent.business.chat.knowledge.document.mapper.KnowledgeDocumentMapper;
import com.superagent.business.chat.knowledge.indexing.mapper.KnowledgeDocumentParentBlockMapper;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ParentChildEvidenceAssemblerTest {

    @Mock
    private KnowledgeDocumentParentBlockMapper parentBlockMapper;

    @Mock
    private KnowledgeDocumentMapper documentMapper;

    @Test
    void shouldNumberEvidenceAndApplyParentAndTotalBudgets() {
        KnowledgeRetrievalProperties properties = new KnowledgeRetrievalProperties();
        properties.setMaxParentChars(20);
        properties.setMaxSingleQuestionEvidenceChars(18);
        properties.setMaxTotalEvidenceChars(170);
        ParentChildEvidenceAssembler assembler =
                new ParentChildEvidenceAssembler(parentBlockMapper, documentMapper, properties);
        when(parentBlockMapper.selectList(any())).thenReturn(List.of(
                parentBlock(101L, 9001L, "第一章", "一二三四五六七八九十一二三四五六七八九十"),
                parentBlock(102L, 9001L, "第二章", "第二个父块正文不会进入总预算")));
        when(documentMapper.selectList(any())).thenReturn(List.of(document(9001L, "订单审核手册")));

        KnowledgeRetrievalResult result = assembler.assemble(List.of(
                child(1001L, 101L, 1.0D),
                child(1002L, 102L, 0.5D)), 6);

        assertThat(result.parentEvidenceList()).hasSize(1);
        assertThat(result.parentEvidenceList().getFirst().parentText()).hasSize(18);
        assertThat(result.evidenceContextText()).startsWith("[1]\n");
        assertThat(result.evidenceContextText()).contains("命中Child：[1001]");
        assertThat(result.evidenceContextText()).doesNotContain("[2]");
        assertThat(result.evidenceContextText()).hasSizeLessThanOrEqualTo(170);
    }

    private KnowledgeRetrievalFusedChild child(Long chunkId, Long parentBlockId, double score) {
        return new KnowledgeRetrievalFusedChild(
                chunkId,
                9001L,
                parentBlockId,
                chunkId.intValue(),
                "订单审核手册",
                "第一章",
                "child",
                score,
                null,
                List.of("VECTOR"));
    }

    private KnowledgeDocumentParentBlockData parentBlock(
            Long id,
            Long documentId,
            String sectionPath,
            String parentText) {
        KnowledgeDocumentParentBlockData data = new KnowledgeDocumentParentBlockData();
        data.setId(id);
        data.setDocumentId(documentId);
        data.setSectionPath(sectionPath);
        data.setParentText(parentText);
        data.setStatus(1);
        return data;
    }

    private KnowledgeDocumentData document(Long id, String documentName) {
        KnowledgeDocumentData data = new KnowledgeDocumentData();
        data.setId(id);
        data.setDocumentName(documentName);
        data.setStatus(1);
        return data;
    }
}
