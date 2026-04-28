package com.superagent.business.chat.knowledge.controller;

import com.superagent.business.chat.knowledge.dto.KnowledgeDocumentIdRequest;
import com.superagent.business.chat.knowledge.dto.KnowledgeDocumentPageRequest;
import com.superagent.business.chat.knowledge.dto.KnowledgeDocumentUploadMetaRequest;
import com.superagent.business.chat.knowledge.dto.KnowledgeRouteAssetPageRequest;
import com.superagent.business.chat.knowledge.dto.KnowledgeRoutePreviewRequest;
import com.superagent.business.chat.knowledge.dto.KnowledgeRouteTracePageRequest;
import com.superagent.business.chat.knowledge.service.KnowledgeManageService;
import com.superagent.business.chat.knowledge.vo.KnowledgeDocumentPageVo;
import com.superagent.business.chat.knowledge.vo.KnowledgeDocumentProfileVo;
import com.superagent.business.chat.knowledge.vo.KnowledgeDocumentVo;
import com.superagent.business.chat.knowledge.vo.KnowledgeRouteAssetPageVo;
import com.superagent.business.chat.knowledge.vo.KnowledgeRouteCandidateVo;
import com.superagent.business.chat.knowledge.vo.KnowledgeRouteTracePageVo;
import com.superagent.common.frame.response.ApiResponse;
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

    @PostMapping(value = "/document/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<KnowledgeDocumentVo> uploadDocument(
            @RequestPart("file") MultipartFile file,
            @Valid @RequestPart("meta") KnowledgeDocumentUploadMetaRequest meta) {
        return ApiResponse.ok(knowledgeManageService.uploadDocument(file, meta));
    }

    @PostMapping("/document/page/query")
    public ApiResponse<KnowledgeDocumentPageVo> queryDocumentPage(
            @RequestBody KnowledgeDocumentPageRequest request) {
        return ApiResponse.ok(knowledgeManageService.queryDocumentPage(request));
    }

    @PostMapping("/document/detail/query")
    public ApiResponse<KnowledgeDocumentVo> queryDocumentDetail(
            @Valid @RequestBody KnowledgeDocumentIdRequest request) {
        return ApiResponse.ok(knowledgeManageService.queryDocumentDetail(request));
    }

    @PostMapping("/document/delete")
    public ApiResponse<Void> deleteDocument(@Valid @RequestBody KnowledgeDocumentIdRequest request) {
        knowledgeManageService.deleteDocument(request);
        return ApiResponse.ok();
    }

    @PostMapping("/knowledge/document/profile/detail")
    public ApiResponse<KnowledgeDocumentProfileVo> queryDocumentProfile(
            @Valid @RequestBody KnowledgeDocumentIdRequest request) {
        return ApiResponse.ok(knowledgeManageService.queryDocumentProfile(request));
    }

    @PostMapping("/knowledge/document/parsed-text/query")
    public ApiResponse<String> queryDocumentParsedText(
            @Valid @RequestBody KnowledgeDocumentIdRequest request) {
        return ApiResponse.ok(knowledgeManageService.queryDocumentParsedText(request));
    }

    @PostMapping("/knowledge/route/preview")
    public ApiResponse<List<KnowledgeRouteCandidateVo>> previewRoute(
            @Valid @RequestBody KnowledgeRoutePreviewRequest request) {
        return ApiResponse.ok(knowledgeManageService.previewRoute(request));
    }

    @PostMapping("/knowledge/route/asset/page/query")
    public ApiResponse<KnowledgeRouteAssetPageVo> queryRouteAssetPage(
            @RequestBody KnowledgeRouteAssetPageRequest request) {
        return ApiResponse.ok(knowledgeManageService.queryRouteAssetPage(request));
    }

    @PostMapping("/knowledge/route/trace/page/query")
    public ApiResponse<KnowledgeRouteTracePageVo> queryRouteTracePage(
            @RequestBody KnowledgeRouteTracePageRequest request) {
        return ApiResponse.ok(knowledgeManageService.queryRouteTracePage(request));
    }
}
