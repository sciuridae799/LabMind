package com.superagent.business.chat.chatagent.api.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.superagent.business.chat.chatagent.api.dto.BusinessChatSessionDetailRequest;
import com.superagent.business.chat.chatagent.api.dto.BusinessChatSessionListRequest;
import com.superagent.business.chat.chatagent.api.dto.BusinessChatExchangeDetailRequest;
import com.superagent.business.chat.chatagent.service.BusinessChatModelApiConfigService;
import com.superagent.business.chat.chatagent.service.BusinessChatQueryService;
import com.superagent.business.chat.chatagent.service.BusinessChatSessionService;
import com.superagent.business.chat.chatagent.service.BusinessChatSessionStateService;
import com.superagent.business.chat.chatagent.service.BusinessChatService;
import com.superagent.business.chat.chatagent.api.vo.BusinessChatSessionDetailVo;
import com.superagent.business.chat.chatagent.api.vo.BusinessChatSessionListPageVo;
import com.superagent.business.chat.chatagent.api.vo.BusinessChatExchangeDetailVo;
import com.superagent.business.chat.knowledge.document.service.KnowledgeManageService;
import com.superagent.common.web.advice.DefaultExceptionHandler;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import reactor.core.publisher.Flux;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class BusinessChatControllerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        BusinessChatService businessChatService = request -> Flux.empty();
        BusinessChatQueryService businessChatQueryService = new BusinessChatQueryService() {
            @Override
            public BusinessChatSessionListPageVo listSessionsPage(BusinessChatSessionListRequest request) {
                return new BusinessChatSessionListPageVo();
            }

            @Override
            public BusinessChatSessionDetailVo getSession(BusinessChatSessionDetailRequest request) {
                return new BusinessChatSessionDetailVo();
            }

            @Override
            public BusinessChatExchangeDetailVo getExchangeDetail(BusinessChatExchangeDetailRequest request) {
                return new BusinessChatExchangeDetailVo();
            }

            @Override
            public String getActiveConversationId() {
                return null;
            }
        };
        BusinessChatSessionService businessChatSessionService = request -> {
        };
        BusinessChatSessionStateService businessChatSessionStateService =
                org.mockito.Mockito.mock(BusinessChatSessionStateService.class);
        BusinessChatModelApiConfigService modelApiConfigService = new BusinessChatModelApiConfigService() {
            @Override
            public List<com.superagent.business.chat.chatagent.api.vo.BusinessChatModelApiConfigVo> listAll() {
                return List.of();
            }

            @Override
            public List<com.superagent.business.chat.chatagent.api.vo.BusinessChatModelApiConfigVo> listAvailable() {
                return List.of();
            }

            @Override
            public com.superagent.business.chat.chatagent.api.vo.BusinessChatModelApiConfigVo save(
                    com.superagent.business.chat.chatagent.api.dto.BusinessChatModelApiConfigSaveRequest request) {
                return null;
            }

            @Override
            public void delete(com.superagent.business.chat.chatagent.api.dto.BusinessChatModelApiConfigIdRequest request) {
            }

            @Override
            public void clearApiKey(
                    com.superagent.business.chat.chatagent.api.dto.BusinessChatModelApiConfigIdRequest request) {
            }

            @Override
            public void move(com.superagent.business.chat.chatagent.api.dto.BusinessChatModelApiConfigMoveRequest request) {
            }

            @Override
            public com.superagent.business.chat.chatagent.execution.model.BusinessChatModelApiConfigSnapshot getRequiredAvailableSnapshot(
                    String id) {
                return null;
            }

            @Override
            public com.superagent.business.chat.chatagent.execution.model.BusinessChatModelApiConfigSnapshot getLatestAvailableSnapshot() {
                return null;
            }
        };
        KnowledgeManageService knowledgeManageService = org.mockito.Mockito.mock(KnowledgeManageService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new BusinessChatController(
                        businessChatService,
                        businessChatQueryService,
                        businessChatSessionService,
                        businessChatSessionStateService,
                        modelApiConfigService,
                        knowledgeManageService))
                .setControllerAdvice(new DefaultExceptionHandler())
                .build();
    }

    @Test
    void shouldRejectBlankQuestion() throws Exception {
        mockMvc.perform(post("/api/chat/stream")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "question": "  ",
                                  "conversationId": "conversation-1",
                                  "chatMode": "OPEN_ENDED",
                                  "modelConfigId": "3001"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("question must not be blank")));
    }

    @Test
    void shouldAllowMissingConversationIdWhenStreamChat() throws Exception {
        mockMvc.perform(post("/api/chat/stream")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "question": "请说明这条链路",
                                  "chatMode": "OPEN_ENDED",
                                  "modelConfigId": "3001"
                                }
                                """))
                .andExpect(status().isOk());
    }

    @Test
    void shouldRejectBlankModelConfigIdWhenStreamChat() throws Exception {
        mockMvc.perform(post("/api/chat/stream")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "question": "请说明这条链路",
                                  "chatMode": "OPEN_ENDED",
                                  "modelConfigId": "   "
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("modelConfigId must not be blank")));
    }

    @Test
    void shouldRejectBlankConversationIdWhenGetSession() throws Exception {
        mockMvc.perform(post("/api/chat/session/detail")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "conversationId": "   "
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("conversationId must not be blank")));
    }

    @Test
    void shouldRejectBlankConversationIdWhenDeleteSession() throws Exception {
        mockMvc.perform(post("/api/chat/session/delete")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "conversationId": "   "
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("conversationId must not be blank")));
    }
}
