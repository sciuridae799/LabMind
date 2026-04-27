package com.superagent.business.chat.chatagent.controller;

import com.superagent.business.chat.chatagent.dto.BusinessChatDeleteSessionRequest;
import com.superagent.business.chat.chatagent.dto.BusinessChatModelApiConfigIdRequest;
import com.superagent.business.chat.chatagent.dto.BusinessChatModelApiConfigMoveRequest;
import com.superagent.business.chat.chatagent.dto.BusinessChatModelApiConfigSaveRequest;
import com.superagent.business.chat.chatagent.dto.BusinessChatSessionDetailRequest;
import com.superagent.business.chat.chatagent.dto.BusinessChatSessionListRequest;
import com.superagent.business.chat.chatagent.dto.BusinessChatStreamRequest;
import com.superagent.business.chat.chatagent.service.BusinessChatModelApiConfigService;
import com.superagent.business.chat.chatagent.service.BusinessChatQueryService;
import com.superagent.business.chat.chatagent.service.BusinessChatSessionService;
import com.superagent.business.chat.chatagent.service.BusinessChatService;
import com.superagent.business.chat.chatagent.vo.BusinessChatModelApiConfigVo;
import com.superagent.business.chat.chatagent.vo.BusinessChatSessionDetailVo;
import com.superagent.business.chat.chatagent.vo.BusinessChatSessionListPageVo;
import com.superagent.business.chat.chatagent.vo.BusinessChatStreamEvent;
import com.superagent.business.chat.knowledge.service.KnowledgeManageService;
import com.superagent.business.chat.knowledge.vo.KnowledgeDocumentVo;
import com.superagent.common.frame.response.ApiResponse;
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

@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class BusinessChatController {

    private final BusinessChatService businessChatService;

    private final BusinessChatQueryService businessChatQueryService;

    private final BusinessChatSessionService businessChatSessionService;

    private final BusinessChatModelApiConfigService modelApiConfigService;

    private final KnowledgeManageService knowledgeManageService;

    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<BusinessChatStreamEvent>> streamChat(
            @Valid @RequestBody BusinessChatStreamRequest request) {
        return businessChatService.streamChat(request);
    }

    @PostMapping("/session/list")
    public ApiResponse<BusinessChatSessionListPageVo> listSessionsPage(
            @Valid @RequestBody BusinessChatSessionListRequest request) {
        return ApiResponse.ok(businessChatQueryService.listSessionsPage(request));
    }

    @PostMapping("/session/detail")
    public ApiResponse<BusinessChatSessionDetailVo> getSession(
            @Valid @RequestBody BusinessChatSessionDetailRequest request) {
        return ApiResponse.ok(businessChatQueryService.getSession(request));
    }

    @PostMapping("/session/delete")
    public ApiResponse<Void> deleteSession(@Valid @RequestBody BusinessChatDeleteSessionRequest request) {
        businessChatSessionService.deleteSession(request);
        return ApiResponse.ok();
    }

    @PostMapping("/model-config/list")
    public ApiResponse<List<BusinessChatModelApiConfigVo>> listModelConfigs() {
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
        return ApiResponse.ok(modelApiConfigService.save(request));
    }

    @PostMapping("/model-config/delete")
    public ApiResponse<Void> deleteModelConfig(@Valid @RequestBody BusinessChatModelApiConfigIdRequest request) {
        modelApiConfigService.delete(request);
        return ApiResponse.ok();
    }

    @PostMapping("/model-config/clear-api-key")
    public ApiResponse<Void> clearModelConfigApiKey(@Valid @RequestBody BusinessChatModelApiConfigIdRequest request) {
        modelApiConfigService.clearApiKey(request);
        return ApiResponse.ok();
    }

    @PostMapping("/model-config/move")
    public ApiResponse<Void> moveModelConfig(@Valid @RequestBody BusinessChatModelApiConfigMoveRequest request) {
        modelApiConfigService.move(request);
        return ApiResponse.ok();
    }
}
