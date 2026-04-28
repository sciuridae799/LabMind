package com.superagent.business.chat.knowledge.service;

import com.superagent.business.chat.knowledge.dto.KnowledgeDocumentIdRequest;
import com.superagent.business.chat.knowledge.dto.KnowledgeDocumentPageRequest;
import com.superagent.business.chat.knowledge.dto.KnowledgeDocumentUploadMetaRequest;
import com.superagent.business.chat.knowledge.dto.KnowledgeRouteAssetPageRequest;
import com.superagent.business.chat.knowledge.dto.KnowledgeRoutePreviewRequest;
import com.superagent.business.chat.knowledge.dto.KnowledgeRouteTracePageRequest;
import com.superagent.business.chat.knowledge.vo.KnowledgeDocumentPageVo;
import com.superagent.business.chat.knowledge.vo.KnowledgeDocumentProfileVo;
import com.superagent.business.chat.knowledge.vo.KnowledgeDocumentVo;
import com.superagent.business.chat.knowledge.vo.KnowledgeRouteAssetPageVo;
import com.superagent.business.chat.knowledge.vo.KnowledgeRouteCandidateVo;
import com.superagent.business.chat.knowledge.vo.KnowledgeRouteTracePageVo;
import java.util.List;
import org.springframework.web.multipart.MultipartFile;

public interface KnowledgeManageService {

    KnowledgeDocumentVo uploadDocument(MultipartFile file, KnowledgeDocumentUploadMetaRequest meta);

    void processDocumentParseTask(String documentId, String taskId);

    KnowledgeDocumentPageVo queryDocumentPage(KnowledgeDocumentPageRequest request);

    List<KnowledgeDocumentVo> listDocumentOptions();

    KnowledgeDocumentVo queryDocumentDetail(KnowledgeDocumentIdRequest request);

    String queryDocumentParsedText(KnowledgeDocumentIdRequest request);

    void deleteDocument(KnowledgeDocumentIdRequest request);

    KnowledgeDocumentProfileVo queryDocumentProfile(KnowledgeDocumentIdRequest request);

    KnowledgeRouteAssetPageVo queryRouteAssetPage(KnowledgeRouteAssetPageRequest request);

    List<KnowledgeRouteCandidateVo> previewRoute(KnowledgeRoutePreviewRequest request);

    KnowledgeRouteTracePageVo queryRouteTracePage(KnowledgeRouteTracePageRequest request);
}
