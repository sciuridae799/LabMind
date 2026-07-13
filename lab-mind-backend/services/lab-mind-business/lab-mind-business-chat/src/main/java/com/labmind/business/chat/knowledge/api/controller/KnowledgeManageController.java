package com.labmind.business.chat.knowledge.api.controller;

import com.labmind.business.chat.knowledge.api.dto.KnowledgeDocumentIdRequest;
import com.labmind.business.chat.knowledge.api.dto.KnowledgeDocumentPageRequest;
import com.labmind.business.chat.knowledge.api.dto.KnowledgeDocumentStrategyConfirmRequest;
import com.labmind.business.chat.knowledge.api.dto.KnowledgeDocumentUploadMetaRequest;
import com.labmind.business.chat.knowledge.api.dto.KnowledgeRouteAssetPageRequest;
import com.labmind.business.chat.knowledge.api.dto.KnowledgeRoutePreviewRequest;
import com.labmind.business.chat.knowledge.api.dto.KnowledgeRouteTracePageRequest;
import com.labmind.business.chat.auth.service.AuthWorkspaceScopeService;
import com.labmind.business.chat.knowledge.document.service.KnowledgeManageService;
import com.labmind.business.chat.knowledge.api.vo.KnowledgeDocumentPageVo;
import com.labmind.business.chat.knowledge.api.vo.KnowledgeDocumentProfileVo;
import com.labmind.business.chat.knowledge.api.vo.KnowledgeDocumentStrategyPlanVo;
import com.labmind.business.chat.knowledge.api.vo.KnowledgeDocumentVo;
import com.labmind.business.chat.knowledge.api.vo.KnowledgeRouteAssetPageVo;
import com.labmind.business.chat.knowledge.api.vo.KnowledgeRouteCandidateVo;
import com.labmind.business.chat.knowledge.api.vo.KnowledgeRouteTracePageVo;
import com.labmind.common.frame.response.ApiResponse;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 知识管理 HTTP 入口。
 *
 * <p>面向管理端提供文档上传、文档查询、解析正文、画像、路由预览、路由资产和路由追踪能力。</p>
 */
@RestController
@RequestMapping("/manage")
@RequiredArgsConstructor
public class KnowledgeManageController {

    private final KnowledgeManageService knowledgeManageService;

    private final AuthWorkspaceScopeService workspaceScopeService;

    @PostMapping(value = "/document/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<KnowledgeDocumentVo> uploadDocument(
            @RequestPart("file") MultipartFile file,
            @Valid @RequestPart("meta") KnowledgeDocumentUploadMetaRequest meta) {
        meta.setWorkspaceId(workspaceScopeService.resolveWritableWorkspace(meta.getWorkspaceId()));
        return ApiResponse.ok(knowledgeManageService.uploadDocument(file, meta));
    }

    @PostMapping("/document/page/query")
    public ApiResponse<KnowledgeDocumentPageVo> queryDocumentPage(
            @RequestBody KnowledgeDocumentPageRequest request) {
        request.setWorkspaceId(workspaceScopeService.resolveReadableWorkspace(request.getWorkspaceId()));
        return ApiResponse.ok(knowledgeManageService.queryDocumentPage(request));
    }

    @PostMapping("/document/detail/query")
    public ApiResponse<KnowledgeDocumentVo> queryDocumentDetail(
            @Valid @RequestBody KnowledgeDocumentIdRequest request) {
        request.setWorkspaceId(workspaceScopeService.resolveReadableWorkspace(request.getWorkspaceId()));
        return ApiResponse.ok(knowledgeManageService.queryDocumentDetail(request));
    }

    @PostMapping("/document/delete")
    public ApiResponse<Void> deleteDocument(@Valid @RequestBody KnowledgeDocumentIdRequest request) {
        request.setWorkspaceId(workspaceScopeService.resolveWritableWorkspace(request.getWorkspaceId()));
        knowledgeManageService.deleteDocument(request);
        return ApiResponse.ok();
    }

    @PostMapping("/document/strategy/plan/query")
    public ApiResponse<KnowledgeDocumentStrategyPlanVo> queryStrategyPlan(
            @Valid @RequestBody KnowledgeDocumentIdRequest request) {
        request.setWorkspaceId(workspaceScopeService.resolveReadableWorkspace(request.getWorkspaceId()));
        return ApiResponse.ok(knowledgeManageService.queryStrategyPlan(request));
    }

    @PostMapping("/document/strategy/confirm")
    public ApiResponse<KnowledgeDocumentStrategyPlanVo> confirmStrategy(
            @Valid @RequestBody KnowledgeDocumentStrategyConfirmRequest request) {
        request.setWorkspaceId(workspaceScopeService.resolveWritableWorkspace(request.getWorkspaceId()));
        return ApiResponse.ok(knowledgeManageService.confirmStrategy(request));
    }

    @PostMapping("/knowledge/document/profile/detail")
    public ApiResponse<KnowledgeDocumentProfileVo> queryDocumentProfile(
            @Valid @RequestBody KnowledgeDocumentIdRequest request) {
        request.setWorkspaceId(workspaceScopeService.resolveReadableWorkspace(request.getWorkspaceId()));
        return ApiResponse.ok(knowledgeManageService.queryDocumentProfile(request));
    }

    @PostMapping("/knowledge/document/parsed-text/query")
    public ApiResponse<String> queryDocumentParsedText(
            @Valid @RequestBody KnowledgeDocumentIdRequest request) {
        request.setWorkspaceId(workspaceScopeService.resolveReadableWorkspace(request.getWorkspaceId()));
        return ApiResponse.ok(knowledgeManageService.queryDocumentParsedText(request));
    }

    @PostMapping("/knowledge/route/preview")
    public ApiResponse<List<KnowledgeRouteCandidateVo>> previewRoute(
            @Valid @RequestBody KnowledgeRoutePreviewRequest request) {
        request.setWorkspaceId(workspaceScopeService.resolveReadableWorkspace(request.getWorkspaceId()));
        return ApiResponse.ok(knowledgeManageService.previewRoute(request));
    }

    @PostMapping("/knowledge/route/asset/page/query")
    public ApiResponse<KnowledgeRouteAssetPageVo> queryRouteAssetPage(
            @RequestBody KnowledgeRouteAssetPageRequest request) {
        request.setWorkspaceId(workspaceScopeService.resolveReadableWorkspace(request.getWorkspaceId()));
        return ApiResponse.ok(knowledgeManageService.queryRouteAssetPage(request));
    }

    @PostMapping("/knowledge/route/trace/page/query")
    public ApiResponse<KnowledgeRouteTracePageVo> queryRouteTracePage(
            @RequestBody KnowledgeRouteTracePageRequest request) {
        request.setWorkspaceId(workspaceScopeService.resolveReadableWorkspace(request.getWorkspaceId()));
        return ApiResponse.ok(knowledgeManageService.queryRouteTracePage(request));
    }
}
