package com.superagent.business.chat.knowledge.document.service;

import com.superagent.business.chat.knowledge.api.dto.KnowledgeDocumentIdRequest;
import com.superagent.business.chat.knowledge.api.dto.KnowledgeDocumentPageRequest;
import com.superagent.business.chat.knowledge.api.dto.KnowledgeDocumentStrategyConfirmRequest;
import com.superagent.business.chat.knowledge.api.dto.KnowledgeDocumentUploadMetaRequest;
import com.superagent.business.chat.knowledge.api.dto.KnowledgeRouteAssetPageRequest;
import com.superagent.business.chat.knowledge.api.dto.KnowledgeRoutePreviewRequest;
import com.superagent.business.chat.knowledge.api.dto.KnowledgeRouteTracePageRequest;
import com.superagent.business.chat.knowledge.api.vo.KnowledgeDocumentPageVo;
import com.superagent.business.chat.knowledge.api.vo.KnowledgeDocumentProfileVo;
import com.superagent.business.chat.knowledge.api.vo.KnowledgeDocumentStrategyPlanVo;
import com.superagent.business.chat.knowledge.api.vo.KnowledgeDocumentVo;
import com.superagent.business.chat.knowledge.api.vo.KnowledgeRouteAssetPageVo;
import com.superagent.business.chat.knowledge.api.vo.KnowledgeRouteCandidateVo;
import com.superagent.business.chat.knowledge.api.vo.KnowledgeRouteTracePageVo;
import java.util.List;
import org.springframework.web.multipart.MultipartFile;

public interface KnowledgeManageService {

    KnowledgeDocumentVo uploadDocument(MultipartFile file, KnowledgeDocumentUploadMetaRequest meta);

    void processDocumentParseTask(String documentId, String taskId);

    void processDocumentIndexTask(String documentId, String taskId, String planId);

    KnowledgeDocumentPageVo queryDocumentPage(KnowledgeDocumentPageRequest request);

    List<KnowledgeDocumentVo> listDocumentOptions();

    List<Long> filterDocumentIdsByWorkspace(List<Long> documentIdList, String workspaceId);

    KnowledgeDocumentVo queryDocumentDetail(KnowledgeDocumentIdRequest request);

    KnowledgeDocumentStrategyPlanVo queryStrategyPlan(KnowledgeDocumentIdRequest request);

    KnowledgeDocumentStrategyPlanVo confirmStrategy(KnowledgeDocumentStrategyConfirmRequest request);

    String queryDocumentParsedText(KnowledgeDocumentIdRequest request);

    void deleteDocument(KnowledgeDocumentIdRequest request);

    KnowledgeDocumentProfileVo queryDocumentProfile(KnowledgeDocumentIdRequest request);

    KnowledgeRouteAssetPageVo queryRouteAssetPage(KnowledgeRouteAssetPageRequest request);

    List<KnowledgeRouteCandidateVo> previewRoute(KnowledgeRoutePreviewRequest request);

    KnowledgeRouteTracePageVo queryRouteTracePage(KnowledgeRouteTracePageRequest request);
}
