package com.labmind.business.chat.chatagent.api.controller;

import com.labmind.business.chat.chatagent.api.dto.BusinessChatDeleteSessionRequest;
import com.labmind.business.chat.chatagent.api.dto.BusinessChatExchangeDetailRequest;
import com.labmind.business.chat.chatagent.api.dto.BusinessChatModelApiConfigIdRequest;
import com.labmind.business.chat.chatagent.api.dto.BusinessChatModelApiConfigMoveRequest;
import com.labmind.business.chat.chatagent.api.dto.BusinessChatModelApiConfigSaveRequest;
import com.labmind.business.chat.chatagent.api.dto.BusinessChatSessionDetailRequest;
import com.labmind.business.chat.chatagent.api.dto.BusinessChatSessionListRequest;
import com.labmind.business.chat.chatagent.api.dto.BusinessChatStreamRequest;
import com.labmind.business.chat.chatagent.service.BusinessChatModelApiConfigService;
import com.labmind.business.chat.chatagent.service.BusinessChatQueryService;
import com.labmind.business.chat.chatagent.service.BusinessChatSessionService;
import com.labmind.business.chat.chatagent.service.BusinessChatSessionStateService;
import com.labmind.business.chat.chatagent.service.BusinessChatService;
import com.labmind.business.chat.chatagent.api.vo.BusinessChatActiveSessionVo;
import com.labmind.business.chat.auth.AuthRole;
import com.labmind.business.chat.auth.AuthSessionContext;
import com.labmind.business.chat.auth.AuthSessionHolder;
import com.labmind.business.chat.auth.service.AuthWorkspaceScopeService;
import com.labmind.business.chat.chatagent.api.vo.BusinessChatExchangeDetailVo;
import com.labmind.business.chat.chatagent.api.vo.BusinessChatModelApiConfigVo;
import com.labmind.business.chat.chatagent.api.vo.BusinessChatSessionDetailVo;
import com.labmind.business.chat.chatagent.api.vo.BusinessChatSessionListPageVo;
import com.labmind.business.chat.chatagent.api.vo.BusinessChatStreamEvent;
import com.labmind.business.chat.knowledge.document.service.KnowledgeManageService;
import com.labmind.business.chat.knowledge.api.vo.KnowledgeDocumentVo;
import com.labmind.common.frame.response.ApiResponse;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

/**
 * 对话模块 HTTP 入口。
 *
 * <p>这里只负责把前端请求分发到流式问答、会话查询、当前会话游标和模型配置服务，不承载执行编排逻辑。</p>
 */
@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class BusinessChatController {

    private final BusinessChatService businessChatService;

    private final BusinessChatQueryService businessChatQueryService;

    private final BusinessChatSessionService businessChatSessionService;

    private final BusinessChatSessionStateService businessChatSessionStateService;

    private final BusinessChatModelApiConfigService modelApiConfigService;

    private final KnowledgeManageService knowledgeManageService;

    private final AuthWorkspaceScopeService workspaceScopeService;

    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<BusinessChatStreamEvent>> streamChat(
            @Valid @RequestBody BusinessChatStreamRequest request) {
        request.setWorkspaceId(workspaceScopeService.resolveReadableWorkspace(request.getWorkspaceId()));
        return businessChatService.streamChat(request);
    }

    @PostMapping("/session/list")
    public ApiResponse<BusinessChatSessionListPageVo> listSessionsPage(
            @Valid @RequestBody BusinessChatSessionListRequest request) {
        request.setWorkspaceId(workspaceScopeService.resolveReadableWorkspace(request.getWorkspaceId()));
        return ApiResponse.ok(businessChatQueryService.listSessionsPage(request));
    }

    @PostMapping("/session/detail")
    public ApiResponse<BusinessChatSessionDetailVo> getSession(
            @Valid @RequestBody BusinessChatSessionDetailRequest request) {
        request.setWorkspaceId(workspaceScopeService.resolveReadableWorkspace(request.getWorkspaceId()));
        return ApiResponse.ok(businessChatQueryService.getSession(request));
    }

    @PostMapping("/exchange/detail")
    public ApiResponse<BusinessChatExchangeDetailVo> getExchangeDetail(
            @Valid @RequestBody BusinessChatExchangeDetailRequest request) {
        request.setWorkspaceId(workspaceScopeService.resolveReadableWorkspace(request.getWorkspaceId()));
        return ApiResponse.ok(businessChatQueryService.getExchangeDetail(request));
    }

    @PostMapping("/session/active")
    public ApiResponse<BusinessChatActiveSessionVo> getActiveSession() {
        String workspaceId = workspaceScopeService.resolveReadableWorkspace(null);
        BusinessChatActiveSessionVo vo = new BusinessChatActiveSessionVo();
        vo.setConversationId(businessChatQueryService.getActiveConversationId(workspaceId, currentAuthSessionToken()));
        return ApiResponse.ok(vo);
    }

    @PostMapping("/session/active/clear")
    public ApiResponse<Void> clearActiveSession() {
        businessChatSessionStateService.clearActive(
                workspaceScopeService.resolveReadableWorkspace(null),
                currentAuthSessionToken());
        return ApiResponse.ok();
    }

    @PostMapping("/session/delete")
    public ApiResponse<Void> deleteSession(@Valid @RequestBody BusinessChatDeleteSessionRequest request) {
        request.setWorkspaceId(workspaceScopeService.resolveReadableWorkspace(request.getWorkspaceId()));
        businessChatSessionService.deleteSession(request);
        return ApiResponse.ok();
    }

    @PostMapping("/model-config/list")
    public ApiResponse<List<BusinessChatModelApiConfigVo>> listModelConfigs() {
        workspaceScopeService.requireSuperAdmin();
        return ApiResponse.ok(modelApiConfigService.listAll());
    }

    @PostMapping("/model-config/available")
    public ApiResponse<List<BusinessChatModelApiConfigVo>> listAvailableModelConfigs() {
        return ApiResponse.ok(modelApiConfigService.listAvailable());
    }

    @PostMapping("/document/options")
    public ApiResponse<List<KnowledgeDocumentVo>> listKnowledgeDocumentOptions() {
        return ApiResponse.ok(knowledgeManageService.listDocumentOptions());
    }

    @PostMapping("/model-config/save")
    public ApiResponse<BusinessChatModelApiConfigVo> saveModelConfig(
            @Valid @RequestBody BusinessChatModelApiConfigSaveRequest request) {
        workspaceScopeService.requireSuperAdmin();
        return ApiResponse.ok(modelApiConfigService.save(request));
    }

    @PostMapping("/model-config/delete")
    public ApiResponse<Void> deleteModelConfig(@Valid @RequestBody BusinessChatModelApiConfigIdRequest request) {
        workspaceScopeService.requireSuperAdmin();
        modelApiConfigService.delete(request);
        return ApiResponse.ok();
    }

    @PostMapping("/model-config/clear-api-key")
    public ApiResponse<Void> clearModelConfigApiKey(@Valid @RequestBody BusinessChatModelApiConfigIdRequest request) {
        workspaceScopeService.requireSuperAdmin();
        modelApiConfigService.clearApiKey(request);
        return ApiResponse.ok();
    }

    @PostMapping("/model-config/move")
    public ApiResponse<Void> moveModelConfig(@Valid @RequestBody BusinessChatModelApiConfigMoveRequest request) {
        workspaceScopeService.requireSuperAdmin();
        modelApiConfigService.move(request);
        return ApiResponse.ok();
    }

    private String currentAuthSessionToken() {
        AuthSessionContext session = AuthSessionHolder.required();
        return session.role() == AuthRole.GUEST ? session.token() : "";
    }
}
