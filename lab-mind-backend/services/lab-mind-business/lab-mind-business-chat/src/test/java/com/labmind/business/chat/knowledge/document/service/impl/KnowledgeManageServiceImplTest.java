package com.labmind.business.chat.knowledge.document.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.labmind.business.chat.auth.AuthRole;
import com.labmind.business.chat.auth.AuthSessionContext;
import com.labmind.business.chat.auth.AuthSessionHolder;
import com.labmind.business.chat.knowledge.api.dto.KnowledgeRouteTracePageRequest;
import com.labmind.business.chat.knowledge.api.dto.KnowledgeDocumentIdRequest;
import com.labmind.business.chat.knowledge.api.dto.KnowledgeRoutePreviewRequest;
import com.labmind.business.chat.knowledge.document.data.KnowledgeDocumentData;
import com.labmind.business.chat.knowledge.document.data.KnowledgeDocumentTaskData;
import com.labmind.business.chat.knowledge.document.mapper.KnowledgeDocumentMapper;
import com.labmind.business.chat.knowledge.document.mapper.KnowledgeDocumentTaskLogMapper;
import com.labmind.business.chat.knowledge.document.mapper.KnowledgeDocumentTaskMapper;
import com.labmind.business.chat.knowledge.document.objectstore.KnowledgeDocumentObjectStorage;
import com.labmind.business.chat.knowledge.indexing.data.KnowledgeDocumentStrategyPlanData;
import com.labmind.business.chat.knowledge.indexing.data.KnowledgeDocumentStrategyStepData;
import com.labmind.business.chat.knowledge.indexing.mapper.KnowledgeDocumentStrategyPlanMapper;
import com.labmind.business.chat.knowledge.indexing.mapper.KnowledgeDocumentStrategyStepMapper;
import com.labmind.business.chat.knowledge.indexing.KnowledgeRetrievalIndexService;
import com.labmind.business.chat.knowledge.profile.mapper.KnowledgeDocumentProfileMapper;
import com.labmind.business.chat.knowledge.route.mapper.KnowledgeDocumentStructureNodeMapper;
import com.labmind.business.chat.knowledge.route.data.KnowledgeDocumentStructureNodeData;
import com.labmind.business.chat.knowledge.route.graph.KnowledgeGraphClient;
import com.labmind.business.chat.knowledge.route.mapper.KnowledgeRouteTraceMapper;
import com.labmind.business.chat.knowledge.route.model.KnowledgeRouteCandidate;
import com.labmind.business.chat.knowledge.route.model.KnowledgeRouteDecision;
import com.labmind.idgenerator.toolkit.SnowflakeIdGenerator;
import java.util.List;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

@ExtendWith(MockitoExtension.class)
class KnowledgeManageServiceImplTest {

    @BeforeAll
    static void initializeMybatisMetadata() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "test");
        assistant.setCurrentNamespace(KnowledgeManageServiceImplTest.class.getName());
        TableInfoHelper.initTableInfo(assistant, com.labmind.business.chat.knowledge.profile.data.KnowledgeDocumentProfileData.class);
        TableInfoHelper.initTableInfo(assistant, KnowledgeDocumentData.class);
        TableInfoHelper.initTableInfo(assistant, KnowledgeDocumentTaskData.class);
        TableInfoHelper.initTableInfo(assistant, KnowledgeDocumentStrategyPlanData.class);
        TableInfoHelper.initTableInfo(assistant, KnowledgeDocumentStrategyStepData.class);
        TableInfoHelper.initTableInfo(assistant, KnowledgeDocumentStructureNodeData.class);
    }

    @Mock
    private KnowledgeRouteTraceMapper routeTraceMapper;

    @Mock
    private KnowledgeDocumentMapper documentMapper;

    @Mock
    private KnowledgeDocumentTaskMapper taskMapper;

    @Mock
    private KnowledgeDocumentTaskLogMapper taskLogMapper;

    @Mock
    private KnowledgeDocumentProfileMapper profileMapper;

    @Mock
    private KnowledgeDocumentStrategyPlanMapper strategyPlanMapper;

    @Mock
    private KnowledgeDocumentStrategyStepMapper strategyStepMapper;

    @Mock
    private KnowledgeDocumentStructureNodeMapper structureNodeMapper;

    @Mock
    private KnowledgeRetrievalIndexService retrievalIndexService;

    @Mock
    private KnowledgeGraphClient knowledgeGraphClient;

    @Mock
    private KnowledgeDocumentObjectStorage objectStorage;

    @Mock
    private SnowflakeIdGenerator snowflakeIdGenerator;

    @Mock
    private TransactionTemplate transactionTemplate;

    @InjectMocks
    private KnowledgeManageServiceImpl service;

    @AfterEach
    void clearSession() {
        AuthSessionHolder.clear();
    }

    @Test
    void shouldScopeGuestRouteTracesByLoginToken() {
        AuthSessionHolder.set(session(AuthRole.GUEST, "guest-token-1"));
        KnowledgeRouteTracePageRequest request = request();
        when(routeTraceMapper.countTraceRows("public-demo", "guest-token-1", null, 1)).thenReturn(0L);
        when(routeTraceMapper.selectTraceRows("public-demo", "guest-token-1", null, 1, 0L, 20))
                .thenReturn(List.of());

        var result = service.queryRouteTracePage(request);

        assertThat(result.getTotalSize()).isZero();
        verify(routeTraceMapper).countTraceRows("public-demo", "guest-token-1", null, 1);
        verify(routeTraceMapper).selectTraceRows("public-demo", "guest-token-1", null, 1, 0L, 20);
    }

    @Test
    void shouldScopeMemberRouteTracesToNonGuestDialogues() {
        AuthSessionHolder.set(session(AuthRole.USER, "member-token-1"));
        KnowledgeRouteTracePageRequest request = request();
        when(routeTraceMapper.countTraceRows("workspace-1", "", null, 1)).thenReturn(0L);
        when(routeTraceMapper.selectTraceRows("workspace-1", "", null, 1, 0L, 20))
                .thenReturn(List.of());

        service.queryRouteTracePage(request);

        verify(routeTraceMapper).countTraceRows("workspace-1", "", null, 1);
        verify(routeTraceMapper).selectTraceRows("workspace-1", "", null, 1, 0L, 20);
    }

    @Test
    void shouldDeleteRetrievalIndexesWithDocument() {
        KnowledgeDocumentData documentData = new KnowledgeDocumentData();
        documentData.setId(2001L);
        documentData.setWorkspaceId("workspace-1");
        documentData.setStorageType(2);
        documentData.setStatus(1);
        when(documentMapper.selectOne(org.mockito.ArgumentMatchers.any())).thenReturn(documentData);
        KnowledgeDocumentIdRequest request = new KnowledgeDocumentIdRequest();
        request.setDocumentId("2001");
        request.setWorkspaceId("workspace-1");

        service.deleteDocument(request);

        verify(retrievalIndexService).deleteIndex(2001L);
        verify(knowledgeGraphClient).deleteDocumentRouteAsset("workspace-1", 2001L);
    }

    @Test
    void shouldPreviewRouteInsideRequestedWorkspace() {
        KnowledgeRoutePreviewRequest request = new KnowledgeRoutePreviewRequest();
        request.setWorkspaceId("workspace-2");
        request.setQuestion("订单审核怎么配置");
        request.setLimit("3");
        KnowledgeRouteCandidate candidate = new KnowledgeRouteCandidate(
                9001L,
                "订单审核手册",
                "order_scope",
                "订单知识域",
                "audit_topic",
                "审核专题",
                3.4D,
                1.6D,
                1.8D,
                1.0D,
                0.8D,
                List.of("订单审核"),
                List.of("订单审核怎么配置"),
                "术语命中：订单审核");
        when(knowledgeGraphClient.routeQuestion("workspace-2", "订单审核怎么配置", 3))
                .thenReturn(new KnowledgeRouteDecision(List.of(), List.of(), List.of(candidate)));

        var result = service.previewRoute(request);

        assertThat(result).singleElement().satisfies(routeCandidate -> {
            assertThat(routeCandidate.getDocumentId()).isEqualTo("9001");
            assertThat(routeCandidate.getDocumentName()).isEqualTo("订单审核手册");
        });
        verify(knowledgeGraphClient).routeQuestion("workspace-2", "订单审核怎么配置", 3);
    }

    @Test
    void shouldPropagateParseFailureAfterRecordingFailedState() {
        executeTransactionCallbacks();
        KnowledgeDocumentTaskData taskData = new KnowledgeDocumentTaskData();
        taskData.setId(3001L);
        taskData.setDocumentId(2001L);
        taskData.setTaskType(1);
        taskData.setTaskStatus(1);
        taskData.setStatus(1);
        KnowledgeDocumentData documentData = new KnowledgeDocumentData();
        documentData.setId(2001L);
        documentData.setWorkspaceId("workspace-1");
        documentData.setBucketName("knowledge");
        documentData.setObjectName("original.pdf");
        documentData.setLastParseTaskId(3001L);
        documentData.setStatus(1);
        when(taskMapper.selectOne(any())).thenReturn(taskData);
        when(documentMapper.selectOne(any())).thenReturn(documentData);
        when(objectStorage.getBytes("knowledge", "original.pdf"))
                .thenThrow(new IllegalStateException("object storage unavailable"));
        when(snowflakeIdGenerator.nextId()).thenReturn(9001L);

        assertThatThrownBy(() -> service.processDocumentParseTask("2001", "3001"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("object storage unavailable");

        verify(taskMapper, atLeastOnce()).updateById((KnowledgeDocumentTaskData) argThat(update ->
                Integer.valueOf(4).equals(((KnowledgeDocumentTaskData) update).getTaskStatus())));
        verify(objectStorage, never()).remove(any(), any());
    }

    @Test
    void shouldDeletePartialIndexesAndPropagateIndexFailure() {
        executeTransactionCallbacks();
        KnowledgeDocumentTaskData taskData = new KnowledgeDocumentTaskData();
        taskData.setId(4001L);
        taskData.setDocumentId(2001L);
        taskData.setPlanId(5001L);
        taskData.setTaskType(2);
        taskData.setTaskStatus(1);
        taskData.setStatus(1);
        KnowledgeDocumentData documentData = new KnowledgeDocumentData();
        documentData.setId(2001L);
        documentData.setBucketName("knowledge");
        documentData.setParseTextPath("parsed/content.txt");
        documentData.setLastParseTaskId(3001L);
        documentData.setLastIndexTaskId(4001L);
        documentData.setCurrentPlanId(5001L);
        documentData.setStatus(1);
        KnowledgeDocumentStrategyPlanData planData = new KnowledgeDocumentStrategyPlanData();
        planData.setId(5001L);
        planData.setDocumentId(2001L);
        planData.setPlanStatus(2);
        planData.setStatus(1);
        KnowledgeDocumentStrategyStepData stepData = new KnowledgeDocumentStrategyStepData();
        stepData.setStepNo(1);
        stepData.setStrategyType(1);
        stepData.setStatus(1);
        when(taskMapper.selectOne(any())).thenReturn(taskData);
        when(documentMapper.selectOne(any())).thenReturn(documentData);
        when(strategyPlanMapper.selectOne(any())).thenReturn(planData);
        when(objectStorage.getText("knowledge", "parsed/content.txt")).thenReturn("parsed text");
        when(structureNodeMapper.selectList(any())).thenReturn(List.of());
        when(strategyStepMapper.selectList(any())).thenReturn(List.of(stepData));
        when(snowflakeIdGenerator.nextId()).thenReturn(9001L);
        doThrow(new IllegalStateException("embedding unavailable"))
                .when(retrievalIndexService)
                .rebuildIndex(any(), anyLong(), any(), any(), any());

        assertThatThrownBy(() -> service.processDocumentIndexTask("2001", "4001", "5001"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("embedding unavailable");

        verify(retrievalIndexService).deleteIndex(2001L);
        verify(taskMapper, atLeastOnce()).updateById((KnowledgeDocumentTaskData) argThat(update ->
                Integer.valueOf(4).equals(((KnowledgeDocumentTaskData) update).getTaskStatus())));
    }

    private void executeTransactionCallbacks() {
        doAnswer(invocation -> {
            java.util.function.Consumer<TransactionStatus> action = invocation.getArgument(0);
            action.accept(org.mockito.Mockito.mock(TransactionStatus.class));
            return null;
        }).when(transactionTemplate).executeWithoutResult(any());
    }

    private KnowledgeRouteTracePageRequest request() {
        KnowledgeRouteTracePageRequest request = new KnowledgeRouteTracePageRequest();
        request.setWorkspaceId(AuthSessionHolder.required().workspaceId());
        return request;
    }

    private AuthSessionContext session(AuthRole role, String token) {
        return new AuthSessionContext(
                token,
                role == AuthRole.GUEST ? "guest" : "1001",
                "account-1",
                "User One",
                role,
                role == AuthRole.GUEST ? "public-demo" : "workspace-1",
                "Workspace One");
    }
}
