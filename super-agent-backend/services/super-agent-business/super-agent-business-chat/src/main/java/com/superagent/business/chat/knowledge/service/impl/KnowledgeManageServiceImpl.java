package com.superagent.business.chat.knowledge.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.superagent.business.chat.chatagent.execution.BusinessChatDynamicModelClient;
import com.superagent.business.chat.chatagent.mapper.BusinessChatExchangeMapper;
import com.superagent.business.chat.chatagent.model.BusinessChatMode;
import com.superagent.business.chat.chatagent.model.BusinessChatModelApiConfigSnapshot;
import com.superagent.business.chat.chatagent.service.BusinessChatModelApiConfigService;
import com.superagent.business.chat.knowledge.data.KnowledgeDocumentData;
import com.superagent.business.chat.knowledge.data.KnowledgeDocumentProfileData;
import com.superagent.business.chat.knowledge.data.KnowledgeDocumentTaskData;
import com.superagent.business.chat.knowledge.data.KnowledgeDocumentTaskLogData;
import com.superagent.business.chat.knowledge.data.KnowledgeScopeNodeData;
import com.superagent.business.chat.knowledge.data.KnowledgeTopicNodeData;
import com.superagent.business.chat.knowledge.dto.KnowledgeDocumentIdRequest;
import com.superagent.business.chat.knowledge.dto.KnowledgeDocumentPageRequest;
import com.superagent.business.chat.knowledge.dto.KnowledgeDocumentUploadMetaRequest;
import com.superagent.business.chat.knowledge.dto.KnowledgeRouteAssetPageRequest;
import com.superagent.business.chat.knowledge.dto.KnowledgeRoutePreviewRequest;
import com.superagent.business.chat.knowledge.dto.KnowledgeRouteTracePageRequest;
import com.superagent.business.chat.knowledge.graph.KnowledgeGraphClient;
import com.superagent.business.chat.knowledge.mapper.KnowledgeDocumentTaskLogMapper;
import com.superagent.business.chat.knowledge.mapper.KnowledgeDocumentTaskMapper;
import com.superagent.business.chat.knowledge.mapper.KnowledgeDocumentMapper;
import com.superagent.business.chat.knowledge.mapper.KnowledgeDocumentProfileMapper;
import com.superagent.business.chat.knowledge.mapper.KnowledgeScopeNodeMapper;
import com.superagent.business.chat.knowledge.mapper.KnowledgeTopicNodeMapper;
import com.superagent.business.chat.knowledge.messaging.KnowledgeDocumentParseProducer;
import com.superagent.business.chat.knowledge.model.KnowledgeDocumentRouteAsset;
import com.superagent.business.chat.knowledge.model.KnowledgeRouteCandidate;
import com.superagent.business.chat.knowledge.model.KnowledgeRouteTraceRow;
import com.superagent.business.chat.knowledge.objectstore.KnowledgeDocumentObjectStorage;
import com.superagent.business.chat.knowledge.service.KnowledgeManageService;
import com.superagent.business.chat.knowledge.vo.KnowledgeDocumentPageVo;
import com.superagent.business.chat.knowledge.vo.KnowledgeDocumentProfileVo;
import com.superagent.business.chat.knowledge.vo.KnowledgeDocumentVo;
import com.superagent.business.chat.knowledge.vo.KnowledgeRouteAssetPageVo;
import com.superagent.business.chat.knowledge.vo.KnowledgeRouteAssetVo;
import com.superagent.business.chat.knowledge.vo.KnowledgeRouteCandidateVo;
import com.superagent.business.chat.knowledge.vo.KnowledgeRouteTracePageVo;
import com.superagent.business.chat.knowledge.vo.KnowledgeRouteTraceVo;
import com.superagent.business.chat.support.BusinessInputValidator;
import com.superagent.common.frame.enums.BaseCode;
import com.superagent.common.frame.exception.BaseException;
import com.superagent.common.web.jackson.JacksonCustom;
import com.superagent.idgenerator.toolkit.SnowflakeIdGenerator;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.jsoup.Jsoup;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

/**
 * 知识文档管理服务。
 *
 * <p>这是知识库文档链路的业务聚合点，负责把“用户上传的文件”变成“对话可路由的知识资产”。</p>
 *
 * <p>全链路顺序：</p>
 * <ol>
 *     <li>上传接口保存原文对象，并在 MySQL 中创建 document 和 parse task。</li>
 *     <li>上传事务提交后发布 Kafka 解析消息，消息只携带 documentId/taskId。</li>
 *     <li>消费者按 task 快照读取原文、解析正文、补齐知识域/专题/画像字段。</li>
 *     <li>解析结果写回 MySQL document/profile，并把最小路由资产写入 Neo4j。</li>
 *     <li>对话知识库模式只从 Neo4j 召回候选，真正正文仍从对象存储或执行计划上下文读取。</li>
 *     <li>删除文档时同步删除 MySQL 有效态、Neo4j 路由资产和对象存储内容。</li>
 * </ol>
 *
 * <p>本类不做兼容性补默认值：画像字段缺失、模型返回非法 JSON、对象存储缺失等都直接失败，
 * 避免错误知识资产进入路由链路。</p>
 */
@Service
@RequiredArgsConstructor
public class KnowledgeManageServiceImpl implements KnowledgeManageService {

    private static final int NORMAL_STATUS = 1;
    private static final int DELETED_STATUS = 0;
    private static final int PARSE_PENDING = 1;
    private static final int PARSE_RUNNING = 2;
    private static final int PARSE_SUCCEEDED = 3;
    private static final int PARSE_FAILED = 4;
    private static final int STRATEGY_PENDING = 1;
    private static final int STRATEGY_RECOMMENDED = 2;
    private static final int INDEX_PENDING = 1;
    private static final int PROFILE_GENERATED = 1;
    private static final int MINIO_STORAGE = 1;
    private static final int TASK_TYPE_PARSE_ROUTE = 1;
    private static final int TASK_STATUS_CREATED = 1;
    private static final int TASK_STATUS_RUNNING = 2;
    private static final int TASK_STATUS_SUCCEEDED = 3;
    private static final int TASK_STATUS_FAILED = 4;
    private static final int TASK_STAGE_FILE_UPLOAD = 1;
    private static final int TASK_STAGE_CONTENT_PARSE = 2;
    private static final int TASK_STAGE_ROUTE = 3;
    private static final int TASK_TRIGGER_SYSTEM_AUTO = 1;
    private static final int TASK_EVENT_STARTED = 1;
    private static final int TASK_EVENT_COMPLETED = 2;
    private static final int TASK_EVENT_FAILED = 3;
    private static final int TASK_LOG_INFO = 1;
    private static final int TASK_LOG_ERROR = 3;
    private static final int TASK_OPERATOR_SYSTEM = 1;
    private static final int METADATA_TEXT_SAMPLE_LIMIT = 12000;
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern(JacksonCustom.DATE_TIME_PATTERN);

    private final KnowledgeDocumentMapper documentMapper;
    private final BusinessChatExchangeMapper businessChatExchangeMapper;
    private final KnowledgeDocumentTaskMapper taskMapper;
    private final KnowledgeDocumentTaskLogMapper taskLogMapper;
    private final KnowledgeScopeNodeMapper scopeNodeMapper;
    private final KnowledgeTopicNodeMapper topicNodeMapper;
    private final KnowledgeDocumentProfileMapper profileMapper;
    private final KnowledgeGraphClient knowledgeGraphClient;
    private final KnowledgeDocumentObjectStorage objectStorage;
    private final SnowflakeIdGenerator snowflakeIdGenerator;
    private final ObjectMapper objectMapper;
    private final BusinessChatDynamicModelClient modelClient;
    private final BusinessChatModelApiConfigService modelApiConfigService;
    private final KnowledgeDocumentParseProducer documentParseProducer;
    private final TransactionTemplate transactionTemplate;

    @Override
    public KnowledgeDocumentVo uploadDocument(MultipartFile file, KnowledgeDocumentUploadMetaRequest meta) {
        validateFile(file);

        // 上传入口只负责建立“可追踪任务”：原文对象、document 记录、task 记录必须先同时存在，
        // Kafka 消费端才能凭 documentId/taskId 恢复完整上下文并继续生成画像和路由资产。
        long documentId = snowflakeIdGenerator.nextId();
        long taskId = snowflakeIdGenerator.nextId();
        String originalFileName = normalizeRequiredText(file.getOriginalFilename(), "originalFileName");
        String documentName = StringUtils.hasText(meta.getDocumentName())
                ? meta.getDocumentName().strip()
                : originalFileName;
        int fileType = resolveFileType(originalFileName);
        byte[] originalContent = readUploadedBytes(file);
        String originalObjectName = buildOriginalObjectName(documentId, originalFileName);
        String bucketName = objectStorage.bucket();
        boolean originalUploaded = false;
        boolean documentRegistered = false;

        try {
            objectStorage.put(originalObjectName, originalContent, file.getContentType());
            originalUploaded = true;

            KnowledgeDocumentData documentData = new KnowledgeDocumentData();
            documentData.setId(documentId);
            documentData.setDocumentName(documentName);
            documentData.setOriginalFileName(originalFileName);
            documentData.setFileType(fileType);
            documentData.setMimeType(file.getContentType());
            documentData.setFileSize(file.getSize());
            documentData.setStorageType(MINIO_STORAGE);
            documentData.setBucketName(bucketName);
            documentData.setObjectName(originalObjectName);
            documentData.setParseStatus(PARSE_PENDING);
            documentData.setStrategyStatus(STRATEGY_PENDING);
            documentData.setIndexStatus(INDEX_PENDING);
            documentData.setCharCount(0);
            documentData.setTokenCount(0);
            documentData.setStructureLevel(0);
            documentData.setContentQualityLevel(0);
            documentData.setLastParseTaskId(taskId);
            documentData.setStructureNodeCount(0);
            documentData.setStatus(NORMAL_STATUS);

            KnowledgeDocumentTaskData taskData = buildCreatedParseTask(taskId, documentId, meta);
            transactionTemplate.executeWithoutResult(status -> {
                documentMapper.insert(documentData);
                taskMapper.insert(taskData);
                insertTaskLog(taskId, documentId, TASK_STAGE_FILE_UPLOAD, TASK_EVENT_COMPLETED, TASK_LOG_INFO,
                        "文档原文已上传，等待 Kafka 解析任务消费。", null);
            });
            documentRegistered = true;
            // 这里同步等待 send 结果：如果消息没有真正进入 Kafka，不能把文档留成“待解析”假状态。
            documentParseProducer.publish(documentId, taskId);
            return toDocumentVo(documentData);
        } catch (RuntimeException error) {
            if (documentRegistered) {
                markUploadFailedAfterKafkaPublishError(documentId, taskId, error);
            }
            removeUploadedObjectAfterFailure(error, bucketName, originalObjectName, originalUploaded);
            throw error;
        }
    }

    /**
     * 执行 Kafka 消费到的文档解析任务。
     *
     * <p>该方法是异步链路的核心入口。它通过 documentId/taskId 重新加载任务快照，
     * 校验任务仍然是当前文档的 lastParseTaskId，然后完成正文解析、画像生成、MySQL 更新和 Neo4j 写入。</p>
     */
    @Override
    @Transactional
    public void processDocumentParseTask(String documentIdValue, String taskIdValue) {
        long documentId = BusinessInputValidator.parsePositiveLong(documentIdValue, "documentId");
        long taskId = BusinessInputValidator.parsePositiveLong(taskIdValue, "taskId");
        KnowledgeDocumentTaskData taskData = loadNormalTask(taskId, documentId);
        if (Integer.valueOf(TASK_STATUS_SUCCEEDED).equals(taskData.getTaskStatus())) {
            return;
        }
        if (!Integer.valueOf(TASK_TYPE_PARSE_ROUTE).equals(taskData.getTaskType())) {
            throw new IllegalStateException("document task is not parse task: " + taskId);
        }

        KnowledgeDocumentData documentData = loadNormalDocument(documentId);
        if (!Long.valueOf(taskId).equals(documentData.getLastParseTaskId())) {
            throw new IllegalStateException("document parse task is not current: " + taskId);
        }

        // 解析任务只处理文档当前 lastParseTaskId。Kafka 可能重投旧消息，旧任务必须失败在入口，
        // 否则旧正文会覆盖当前文档的画像、MySQL 路由字段和 Neo4j 路由资产。
        LocalDateTime startTime = LocalDateTime.now();
        markTaskRunning(taskId, startTime);
        markDocumentParsing(documentId);
        insertTaskLog(taskId, documentId, TASK_STAGE_CONTENT_PARSE, TASK_EVENT_STARTED, TASK_LOG_INFO,
                "开始解析文档正文。", null);

        try {
            String originalText = objectStorage.getText(documentData.getBucketName(), documentData.getObjectName());
            String parsedText = parseDocumentText(
                    originalText.getBytes(StandardCharsets.UTF_8),
                    documentData.getOriginalFileName(),
                    documentData.getFileType());
            String parsedTextObjectName = buildParsedTextObjectName(documentId);
            objectStorage.put(
                    parsedTextObjectName,
                    parsedText.getBytes(StandardCharsets.UTF_8),
                    "text/plain; charset=utf-8");
            insertTaskLog(taskId, documentId, TASK_STAGE_CONTENT_PARSE, TASK_EVENT_COMPLETED, TASK_LOG_INFO,
                    "文档正文解析完成。", null);

            // extJson 是上传时用户填写的知识归属快照；解析阶段不能再读请求对象，只能读任务快照。
            UploadedKnowledgeMetadata uploadedMetadata = readUploadedKnowledgeMetadata(taskData.getExtJson());
            CompletedKnowledgeMetadata completedMetadata = completeMetadata(
                    documentData.getOriginalFileName(),
                    documentData.getDocumentName(),
                    documentData.getMimeType(),
                    documentData.getFileSize(),
                    uploadedMetadata,
                    parsedText);
            upsertScope(completedMetadata);
            upsertTopic(completedMetadata);

            // MySQL document 行保存面向列表、详情和状态机的事实字段；profile 行保存面向路由的画像字段。
            KnowledgeDocumentData completedDocumentData = buildCompletedDocumentUpdate(
                    documentData,
                    completedMetadata,
                    parsedText,
                    parsedTextObjectName);
            documentMapper.updateById(completedDocumentData);

            KnowledgeDocumentProfileData profileData = buildProfile(completedDocumentData, completedMetadata);
            profileMapper.insert(profileData);
            // Neo4j 只保存“可路由资产”，不保存正文；正文仍在对象存储中，避免路由召回与回答证据混在一起。
            knowledgeGraphClient.upsertDocumentRouteAsset(
                    toRouteAsset(completedDocumentData, completedMetadata, profileData));

            markTaskSucceeded(taskId, startTime);
            insertTaskLog(taskId, documentId, TASK_STAGE_ROUTE, TASK_EVENT_COMPLETED, TASK_LOG_INFO,
                    "文档路由元数据生成完成。", null);
        } catch (RuntimeException error) {
            markTaskFailed(taskId, documentId, startTime, error);
        }
    }

    @Override
    public KnowledgeDocumentPageVo queryDocumentPage(KnowledgeDocumentPageRequest request) {
        int pageNo = BusinessInputValidator.parsePositiveInt(request.getPageNo(), "pageNo");
        int pageSize = BusinessInputValidator.parsePositiveInt(request.getPageSize(), "pageSize");
        Page<KnowledgeDocumentData> page = documentMapper.selectPage(
                new Page<>(pageNo, pageSize),
                Wrappers.<KnowledgeDocumentData>lambdaQuery()
                        .eq(KnowledgeDocumentData::getStatus, NORMAL_STATUS)
                        .eq(StringUtils.hasText(request.getKnowledgeScopeCode()),
                                KnowledgeDocumentData::getKnowledgeScopeCode,
                                request.getKnowledgeScopeCode())
                        .and(StringUtils.hasText(request.getKeyword()), wrapper -> wrapper
                                .like(KnowledgeDocumentData::getDocumentName, request.getKeyword())
                                .or()
                                .like(KnowledgeDocumentData::getOriginalFileName, request.getKeyword()))
                        .orderByDesc(KnowledgeDocumentData::getCreateTime)
                        .orderByDesc(KnowledgeDocumentData::getId));

        KnowledgeDocumentPageVo vo = new KnowledgeDocumentPageVo();
        vo.setPageNo(pageNo);
        vo.setPageSize(pageSize);
        vo.setTotalSize(page.getTotal());
        vo.setTotalPages(page.getPages());
        vo.setDocuments(page.getRecords().stream().map(this::toDocumentVo).toList());
        return vo;
    }

    @Override
    public KnowledgeDocumentVo queryDocumentDetail(KnowledgeDocumentIdRequest request) {
        long documentId = BusinessInputValidator.parsePositiveLong(request.getDocumentId(), "documentId");
        return toDocumentVo(loadNormalDocument(documentId));
    }

    @Override
    public List<KnowledgeDocumentVo> listDocumentOptions() {
        return documentMapper.selectList(
                        Wrappers.<KnowledgeDocumentData>lambdaQuery()
                                .eq(KnowledgeDocumentData::getStatus, NORMAL_STATUS)
                                .eq(KnowledgeDocumentData::getStorageType, MINIO_STORAGE)
                                .isNotNull(KnowledgeDocumentData::getBucketName)
                                .eq(KnowledgeDocumentData::getParseStatus, PARSE_SUCCEEDED)
                                .isNotNull(KnowledgeDocumentData::getParseTextPath)
                                .orderByDesc(KnowledgeDocumentData::getCreateTime)
                                .orderByDesc(KnowledgeDocumentData::getId)
                                .last("limit 100"))
                .stream()
                .map(this::toDocumentVo)
                .toList();
    }

    @Override
    public String queryDocumentParsedText(KnowledgeDocumentIdRequest request) {
        long documentId = BusinessInputValidator.parsePositiveLong(request.getDocumentId(), "documentId");
        KnowledgeDocumentData documentData = loadNormalDocument(documentId);
        validateReadableParsedDocument(documentData);
        String parsedText = objectStorage.getText(documentData.getBucketName(), documentData.getParseTextPath()).strip();
        if (!StringUtils.hasText(parsedText)) {
            throw new IllegalStateException("parsed document text is empty: " + documentId);
        }
        return parsedText;
    }

    /**
     * 删除文档及其所有可路由痕迹。
     *
     * <p>删除不只是把列表状态改成不可见，还必须删除画像有效态、Neo4j Document 节点和对象存储文件，
     * 否则对话路由仍可能命中已经被用户删除的文档。</p>
     */
    @Override
    @Transactional
    public void deleteDocument(KnowledgeDocumentIdRequest request) {
        long documentId = BusinessInputValidator.parsePositiveLong(request.getDocumentId(), "documentId");
        KnowledgeDocumentData documentData = loadNormalDocument(documentId);
        // 删除是全链路删除：MySQL 有效态、画像有效态、Neo4j 路由资产、对象存储内容必须一起收束。
        // 只删列表可见性会留下“前台看不到，但知识路由还能命中”的悬挂文档。
        documentData.setStatus(DELETED_STATUS);
        documentMapper.updateById(documentData);
        profileMapper.update(null, Wrappers.<KnowledgeDocumentProfileData>lambdaUpdate()
                .eq(KnowledgeDocumentProfileData::getDocumentId, documentId)
                .eq(KnowledgeDocumentProfileData::getStatus, NORMAL_STATUS)
                .set(KnowledgeDocumentProfileData::getStatus, DELETED_STATUS));
        knowledgeGraphClient.deleteDocumentRouteAsset(documentId);
        removeStoredObjects(documentData);
    }

    @Override
    public KnowledgeDocumentProfileVo queryDocumentProfile(KnowledgeDocumentIdRequest request) {
        long documentId = BusinessInputValidator.parsePositiveLong(request.getDocumentId(), "documentId");
        KnowledgeDocumentProfileData profileData = profileMapper.selectOne(
                Wrappers.<KnowledgeDocumentProfileData>lambdaQuery()
                        .eq(KnowledgeDocumentProfileData::getDocumentId, documentId)
                        .eq(KnowledgeDocumentProfileData::getStatus, NORMAL_STATUS)
                        .orderByDesc(KnowledgeDocumentProfileData::getProfileVersion)
                        .last("limit 1"));
        if (profileData == null) {
            throw new BaseException(BaseCode.INVALID_PARAMETER, "document profile was not found: " + documentId);
        }
        return toProfileVo(profileData);
    }

    @Override
    public KnowledgeRouteAssetPageVo queryRouteAssetPage(KnowledgeRouteAssetPageRequest request) {
        int pageNo = BusinessInputValidator.parsePositiveInt(request.getPageNo(), "pageNo");
        int pageSize = BusinessInputValidator.parsePositiveInt(request.getPageSize(), "pageSize");
        Page<KnowledgeDocumentData> page = documentMapper.selectPage(
                new Page<>(pageNo, pageSize),
                Wrappers.<KnowledgeDocumentData>lambdaQuery()
                        .eq(KnowledgeDocumentData::getStatus, NORMAL_STATUS)
                        .eq(KnowledgeDocumentData::getParseStatus, PARSE_SUCCEEDED)
                        .isNotNull(KnowledgeDocumentData::getParseTextPath)
                        .eq(StringUtils.hasText(request.getKnowledgeScopeCode()),
                                KnowledgeDocumentData::getKnowledgeScopeCode,
                                request.getKnowledgeScopeCode())
                        .and(StringUtils.hasText(request.getKeyword()), wrapper -> wrapper
                                .like(KnowledgeDocumentData::getDocumentName, request.getKeyword())
                                .or()
                                .like(KnowledgeDocumentData::getOriginalFileName, request.getKeyword())
                                .or()
                                .like(KnowledgeDocumentData::getDocumentTags, request.getKeyword()))
                        .orderByDesc(KnowledgeDocumentData::getEditTime)
                        .orderByDesc(KnowledgeDocumentData::getId));

        KnowledgeRouteAssetPageVo vo = new KnowledgeRouteAssetPageVo();
        vo.setPageNo(pageNo);
        vo.setPageSize(pageSize);
        vo.setTotalSize(page.getTotal());
        vo.setTotalPages(page.getPages());
        Map<Long, KnowledgeDocumentProfileData> profileByDocumentId = loadLatestProfileByDocumentId(page.getRecords());
        Map<String, String> topicNameByCode = loadTopicNameByCode(profileByDocumentId.values().stream().toList());
        vo.setAssets(page.getRecords().stream()
                .map(documentData -> toRouteAssetVo(
                        documentData,
                        profileByDocumentId.get(documentData.getId()),
                        topicNameByCode))
                .toList());
        return vo;
    }

    private KnowledgeDocumentData loadNormalDocument(long documentId) {
        KnowledgeDocumentData documentData = documentMapper.selectOne(
                Wrappers.<KnowledgeDocumentData>lambdaQuery()
                        .eq(KnowledgeDocumentData::getId, documentId)
                        .eq(KnowledgeDocumentData::getStatus, NORMAL_STATUS)
                        .last("limit 1"));
        if (documentData == null) {
            throw new BaseException(BaseCode.INVALID_PARAMETER, "document was not found: " + documentId);
        }
        return documentData;
    }

    @Override
    public List<KnowledgeRouteCandidateVo> previewRoute(KnowledgeRoutePreviewRequest request) {
        String question = normalizeRequiredText(request.getQuestion(), "question");
        int limit = BusinessInputValidator.parsePositiveInt(request.getLimit(), "limit");
        return knowledgeGraphClient.routeQuestion(question, limit)
                .stream()
                .map(this::toRouteCandidateVo)
                .toList();
    }

    /**
     * 查询历史知识路由追踪。
     *
     * <p>数据源是对话归档中的 debugTraceJson，而不是当前 Neo4j 重新计算的候选。
     * 这样后台看到的是“当时那轮回答实际使用的路由计划”。</p>
     */
    @Override
    public KnowledgeRouteTracePageVo queryRouteTracePage(KnowledgeRouteTracePageRequest request) {
        int pageNo = BusinessInputValidator.parsePositiveInt(request.getPageNo(), "pageNo");
        int pageSize = BusinessInputValidator.parsePositiveInt(request.getPageSize(), "pageSize");
        String keyword = normalizeOptionalText(request.getKeyword());
        // 路由追踪读取归档时写入的 debugTrace，不重新计算路由。
        // 复盘页面要展示“当时执行过什么”，而不是用当前图谱状态推导一个新结果。
        long totalSize = businessChatExchangeMapper.countKnowledgeRouteTraceRows(
                keyword,
                BusinessChatMode.KNOWLEDGE_BASE.getDatabaseCode(),
                NORMAL_STATUS);
        long offset = (long) (pageNo - 1) * pageSize;
        List<KnowledgeRouteTraceRow> traceRows = businessChatExchangeMapper.selectKnowledgeRouteTraceRows(
                keyword,
                BusinessChatMode.KNOWLEDGE_BASE.getDatabaseCode(),
                NORMAL_STATUS,
                offset,
                pageSize);

        KnowledgeRouteTracePageVo vo = new KnowledgeRouteTracePageVo();
        vo.setPageNo(pageNo);
        vo.setPageSize(pageSize);
        vo.setTotalSize(totalSize);
        vo.setTotalPages(totalSize == 0 ? 0 : (long) Math.ceil(totalSize / (double) pageSize));
        vo.setTraces(traceRows.stream().map(this::toRouteTraceVo).toList());
        return vo;
    }

    private void upsertScope(CompletedKnowledgeMetadata metadata) {
        KnowledgeScopeNodeData existing = scopeNodeMapper.selectOne(
                Wrappers.<KnowledgeScopeNodeData>lambdaQuery()
                        .eq(KnowledgeScopeNodeData::getScopeCode, metadata.knowledgeScopeCode())
                        .eq(KnowledgeScopeNodeData::getStatus, NORMAL_STATUS)
                        .last("limit 1"));
        if (existing != null) {
            return;
        }
        KnowledgeScopeNodeData scopeData = new KnowledgeScopeNodeData();
        scopeData.setId(snowflakeIdGenerator.nextId());
        scopeData.setScopeCode(metadata.knowledgeScopeCode());
        scopeData.setScopeName(metadata.knowledgeScopeName());
        scopeData.setDescription(metadata.knowledgeScopeName());
        scopeData.setAliases("");
        scopeData.setExamples("[]");
        scopeData.setSortOrder(0);
        scopeData.setStatus(NORMAL_STATUS);
        scopeNodeMapper.insert(scopeData);
    }

    private void upsertTopic(CompletedKnowledgeMetadata metadata) {
        KnowledgeTopicNodeData existing = topicNodeMapper.selectOne(
                Wrappers.<KnowledgeTopicNodeData>lambdaQuery()
                        .eq(KnowledgeTopicNodeData::getTopicCode, metadata.knowledgeTopicCode())
                        .eq(KnowledgeTopicNodeData::getStatus, NORMAL_STATUS)
                        .last("limit 1"));
        if (existing != null) {
            return;
        }
        KnowledgeTopicNodeData topicData = new KnowledgeTopicNodeData();
        topicData.setId(snowflakeIdGenerator.nextId());
        topicData.setTopicCode(metadata.knowledgeTopicCode());
        topicData.setTopicName(metadata.knowledgeTopicName());
        topicData.setScopeCode(metadata.knowledgeScopeCode());
        topicData.setDescription(metadata.knowledgeTopicName());
        topicData.setAliases("");
        topicData.setExamples("[]");
        topicData.setAnswerShape("explain");
        topicData.setExecutionPreference("graph_then_evidence");
        topicData.setSortOrder(0);
        topicData.setStatus(NORMAL_STATUS);
        topicNodeMapper.insert(topicData);
    }

    private KnowledgeDocumentTaskData buildCreatedParseTask(
            long taskId,
            long documentId,
            KnowledgeDocumentUploadMetaRequest meta) {
        KnowledgeDocumentTaskData taskData = new KnowledgeDocumentTaskData();
        taskData.setId(taskId);
        taskData.setDocumentId(documentId);
        taskData.setTaskType(TASK_TYPE_PARSE_ROUTE);
        taskData.setTaskStatus(TASK_STATUS_CREATED);
        taskData.setCurrentStage(TASK_STAGE_FILE_UPLOAD);
        taskData.setTriggerSource(TASK_TRIGGER_SYSTEM_AUTO);
        taskData.setRetryCount(0);
        taskData.setCostMillis(0L);
        taskData.setExtJson(writeUploadedKnowledgeMetadata(meta));
        taskData.setStatus(NORMAL_STATUS);
        return taskData;
    }

    private KnowledgeDocumentTaskData loadNormalTask(long taskId, long documentId) {
        KnowledgeDocumentTaskData taskData = taskMapper.selectOne(
                Wrappers.<KnowledgeDocumentTaskData>lambdaQuery()
                        .eq(KnowledgeDocumentTaskData::getId, taskId)
                        .eq(KnowledgeDocumentTaskData::getDocumentId, documentId)
                        .eq(KnowledgeDocumentTaskData::getStatus, NORMAL_STATUS)
                        .last("limit 1"));
        if (taskData == null) {
            throw new IllegalStateException("document parse task was not found: " + taskId);
        }
        return taskData;
    }

    private void markTaskRunning(long taskId, LocalDateTime startTime) {
        KnowledgeDocumentTaskData taskData = new KnowledgeDocumentTaskData();
        taskData.setId(taskId);
        taskData.setTaskStatus(TASK_STATUS_RUNNING);
        taskData.setCurrentStage(TASK_STAGE_CONTENT_PARSE);
        taskData.setStartTime(startTime);
        taskMapper.updateById(taskData);
    }

    private void markDocumentParsing(long documentId) {
        documentMapper.update(null, Wrappers.<KnowledgeDocumentData>lambdaUpdate()
                .eq(KnowledgeDocumentData::getId, documentId)
                .eq(KnowledgeDocumentData::getStatus, NORMAL_STATUS)
                .set(KnowledgeDocumentData::getParseStatus, PARSE_RUNNING)
                .set(KnowledgeDocumentData::getParseErrorMsg, null));
    }

    private KnowledgeDocumentData buildCompletedDocumentUpdate(
            KnowledgeDocumentData documentData,
            CompletedKnowledgeMetadata metadata,
            String parsedText,
            String parsedTextObjectName) {
        KnowledgeDocumentData completedDocumentData = new KnowledgeDocumentData();
        completedDocumentData.setId(documentData.getId());
        completedDocumentData.setDocumentName(documentData.getDocumentName());
        completedDocumentData.setOriginalFileName(documentData.getOriginalFileName());
        completedDocumentData.setParseStatus(PARSE_SUCCEEDED);
        completedDocumentData.setStrategyStatus(STRATEGY_RECOMMENDED);
        completedDocumentData.setIndexStatus(INDEX_PENDING);
        completedDocumentData.setCharCount(parsedText.length());
        completedDocumentData.setTokenCount(estimateTokenCount(parsedText));
        completedDocumentData.setStructureLevel(0);
        completedDocumentData.setContentQualityLevel(0);
        completedDocumentData.setParseTextPath(parsedTextObjectName);
        completedDocumentData.setParseErrorMsg(null);
        completedDocumentData.setKnowledgeScopeCode(metadata.knowledgeScopeCode());
        completedDocumentData.setKnowledgeScopeName(metadata.knowledgeScopeName());
        completedDocumentData.setBusinessCategory(metadata.businessCategory());
        completedDocumentData.setDocumentTags(String.join(",", metadata.documentTags()));
        completedDocumentData.setStructureNodeCount(0);
        completedDocumentData.setStatus(NORMAL_STATUS);
        return completedDocumentData;
    }

    private void markTaskSucceeded(long taskId, LocalDateTime startTime) {
        LocalDateTime finishTime = LocalDateTime.now();
        KnowledgeDocumentTaskData taskData = new KnowledgeDocumentTaskData();
        taskData.setId(taskId);
        taskData.setTaskStatus(TASK_STATUS_SUCCEEDED);
        taskData.setCurrentStage(TASK_STAGE_ROUTE);
        taskData.setFinishTime(finishTime);
        taskData.setCostMillis(Duration.between(startTime, finishTime).toMillis());
        taskMapper.updateById(taskData);
    }

    private void markTaskFailed(long taskId, long documentId, LocalDateTime startTime, RuntimeException error) {
        LocalDateTime finishTime = LocalDateTime.now();
        String errorMessage = normalizeErrorMessage(error);
        KnowledgeDocumentTaskData taskData = new KnowledgeDocumentTaskData();
        taskData.setId(taskId);
        taskData.setTaskStatus(TASK_STATUS_FAILED);
        taskData.setFinishTime(finishTime);
        taskData.setCostMillis(Duration.between(startTime, finishTime).toMillis());
        taskData.setErrorCode(error.getClass().getSimpleName());
        taskData.setErrorMsg(errorMessage);
        taskMapper.updateById(taskData);

        documentMapper.update(null, Wrappers.<KnowledgeDocumentData>lambdaUpdate()
                .eq(KnowledgeDocumentData::getId, documentId)
                .eq(KnowledgeDocumentData::getStatus, NORMAL_STATUS)
                .set(KnowledgeDocumentData::getParseStatus, PARSE_FAILED)
                .set(KnowledgeDocumentData::getParseErrorMsg, errorMessage));
        insertTaskLog(taskId, documentId, TASK_STAGE_CONTENT_PARSE, TASK_EVENT_FAILED, TASK_LOG_ERROR,
                errorMessage, error.getClass().getName());
    }

    private void markUploadFailedAfterKafkaPublishError(long documentId, long taskId, RuntimeException error) {
        try {
            String errorMessage = normalizeErrorMessage(error);
            transactionTemplate.executeWithoutResult(status -> {
                taskMapper.update(null, Wrappers.<KnowledgeDocumentTaskData>lambdaUpdate()
                        .eq(KnowledgeDocumentTaskData::getId, taskId)
                        .eq(KnowledgeDocumentTaskData::getDocumentId, documentId)
                        .eq(KnowledgeDocumentTaskData::getStatus, NORMAL_STATUS)
                        .set(KnowledgeDocumentTaskData::getTaskStatus, TASK_STATUS_FAILED)
                        .set(KnowledgeDocumentTaskData::getErrorCode, error.getClass().getSimpleName())
                        .set(KnowledgeDocumentTaskData::getErrorMsg, errorMessage));
                documentMapper.update(null, Wrappers.<KnowledgeDocumentData>lambdaUpdate()
                        .eq(KnowledgeDocumentData::getId, documentId)
                        .eq(KnowledgeDocumentData::getStatus, NORMAL_STATUS)
                        .set(KnowledgeDocumentData::getParseStatus, PARSE_FAILED)
                        .set(KnowledgeDocumentData::getParseErrorMsg, errorMessage)
                        .set(KnowledgeDocumentData::getStatus, DELETED_STATUS));
                insertTaskLog(taskId, documentId, TASK_STAGE_FILE_UPLOAD, TASK_EVENT_FAILED, TASK_LOG_ERROR,
                        errorMessage, error.getClass().getName());
            });
        } catch (RuntimeException cleanupError) {
            error.addSuppressed(cleanupError);
        }
    }

    private void insertTaskLog(
            long taskId,
            long documentId,
            int stageType,
            int eventType,
            int logLevel,
            String content,
            String detailJson) {
        KnowledgeDocumentTaskLogData logData = new KnowledgeDocumentTaskLogData();
        logData.setId(snowflakeIdGenerator.nextId());
        logData.setTaskId(taskId);
        logData.setDocumentId(documentId);
        logData.setStageType(stageType);
        logData.setEventType(eventType);
        logData.setLogLevel(logLevel);
        logData.setOperatorType(TASK_OPERATOR_SYSTEM);
        logData.setContent(content);
        logData.setDetailJson(detailJson);
        logData.setStatus(NORMAL_STATUS);
        taskLogMapper.insert(logData);
    }

    private String normalizeErrorMessage(Throwable error) {
        String message = error.getMessage();
        if (!StringUtils.hasText(message)) {
            message = error.getClass().getSimpleName();
        }
        return message.length() > 1000 ? message.substring(0, 1000) : message;
    }

    private KnowledgeDocumentProfileData buildProfile(
            KnowledgeDocumentData documentData,
            CompletedKnowledgeMetadata metadata) {
        List<String> terms = new ArrayList<>(metadata.terms());
        if (!terms.contains(documentData.getDocumentName())) {
            terms.add(documentData.getDocumentName());
        }

        KnowledgeDocumentProfileData profileData = new KnowledgeDocumentProfileData();
        profileData.setId(snowflakeIdGenerator.nextId());
        profileData.setDocumentId(documentData.getId());
        profileData.setScopeCode(metadata.knowledgeScopeCode());
        profileData.setTopicCode(metadata.knowledgeTopicCode());
        profileData.setProfileStatus(PROFILE_GENERATED);
        profileData.setSummaryText(metadata.summaryText());
        profileData.setAnswerableQuestionsJson(toJson(metadata.answerableQuestions()));
        profileData.setUnanswerableQuestionsJson(toJson(List.of()));
        profileData.setBusinessEntitiesJson(toJson(splitCsv(metadata.businessCategory())));
        profileData.setTermsJson(toJson(terms));
        profileData.setQuestionPatternsJson(toJson(metadata.questionPatterns()));
        profileData.setProfileVersion(1);
        profileData.setStatus(NORMAL_STATUS);
        return profileData;
    }

    private KnowledgeDocumentRouteAsset toRouteAsset(
            KnowledgeDocumentData documentData,
            CompletedKnowledgeMetadata metadata,
            KnowledgeDocumentProfileData profileData) {
        return new KnowledgeDocumentRouteAsset(
                documentData.getId(),
                documentData.getDocumentName(),
                metadata.knowledgeScopeCode(),
                metadata.knowledgeScopeName(),
                metadata.knowledgeTopicCode(),
                metadata.knowledgeTopicName(),
                profileData.getSummaryText(),
                readStringList(profileData.getTermsJson()),
                readStringList(profileData.getQuestionPatternsJson()));
    }

    /**
     * 将上传时的人工元数据和模型生成结果合并成完整知识画像。
     *
     * <p>人工填写的知识域/专题优先级最高；只有这些路由归属不完整时才调用模型补齐。
     * 合并后的结果必须通过统一校验，才能进入 MySQL 和 Neo4j。</p>
     */
    private CompletedKnowledgeMetadata completeMetadata(
            String originalFileName,
            String documentName,
            String contentType,
            long fileSize,
            UploadedKnowledgeMetadata uploadedMetadata,
            String parsedText) {
        String knowledgeScopeCode = uploadedMetadata.knowledgeScopeCode();
        String knowledgeScopeName = uploadedMetadata.knowledgeScopeName();
        String knowledgeTopicCode = uploadedMetadata.knowledgeTopicCode();
        String knowledgeTopicName = uploadedMetadata.knowledgeTopicName();
        String businessCategory = uploadedMetadata.businessCategory();
        String documentTags = uploadedMetadata.documentTags();
        if (hasCompleteRouteMetadata(knowledgeScopeCode, knowledgeScopeName, knowledgeTopicCode, knowledgeTopicName)) {
            List<String> manualDocumentTags = splitCsv(documentTags);
            if (manualDocumentTags.isEmpty()) {
                manualDocumentTags = List.of(knowledgeTopicName.strip(), documentName);
            }
            // 用户已完整指定知识域和专题时，业务归属已经确定。
            // 此时不再让模型改写归属，只用确定归属派生最小画像字段并进入同一套校验。
            return validateCompletedMetadata(new CompletedKnowledgeMetadata(
                    knowledgeScopeCode,
                    knowledgeScopeName,
                    knowledgeTopicCode,
                    knowledgeTopicName,
                    normalizeOptionalText(businessCategory),
                    manualDocumentTags,
                    "%s/%s 下的文档：%s".formatted(
                            knowledgeScopeName.strip(),
                            knowledgeTopicName.strip(),
                            documentName),
                    List.of(
                            "关于%s的问题".formatted(documentName),
                            "%s相关流程".formatted(knowledgeTopicName.strip())),
                    manualDocumentTags,
                    List.of("怎么申请", "如何办理", "是什么", "怎么使用")));
        }

        // 只有路由归属不完整时才调用模型。模型负责补缺，不负责推翻用户已填写的业务归属；
        // chooseProvided 会在模型返回后再次用用户字段覆盖，保证上传入口语义优先。
        BusinessChatModelApiConfigSnapshot modelConfig = modelApiConfigService.getLatestAvailableSnapshot();
        String modelResponse = modelClient.call(
                modelConfig,
                buildMetadataSystemPrompt(),
                buildMetadataUserMessage(
                        originalFileName,
                        documentName,
                        contentType,
                        fileSize,
                        knowledgeScopeCode,
                        knowledgeScopeName,
                        knowledgeTopicCode,
                        knowledgeTopicName,
                        businessCategory,
                        documentTags,
                        parsedText));
        CompletedKnowledgeMetadata generatedMetadata = parseGeneratedMetadata(modelResponse);
        return validateCompletedMetadata(new CompletedKnowledgeMetadata(
                chooseProvided(knowledgeScopeCode, generatedMetadata.knowledgeScopeCode()),
                chooseProvided(knowledgeScopeName, generatedMetadata.knowledgeScopeName()),
                chooseProvided(knowledgeTopicCode, generatedMetadata.knowledgeTopicCode()),
                chooseProvided(knowledgeTopicName, generatedMetadata.knowledgeTopicName()),
                chooseProvided(businessCategory, generatedMetadata.businessCategory()),
                generatedMetadata.documentTags(),
                generatedMetadata.summaryText(),
                generatedMetadata.answerableQuestions(),
                generatedMetadata.terms(),
                generatedMetadata.questionPatterns()));
    }

    private boolean hasCompleteRouteMetadata(
            String knowledgeScopeCode,
            String knowledgeScopeName,
            String knowledgeTopicCode,
            String knowledgeTopicName) {
        return StringUtils.hasText(knowledgeScopeCode)
                && StringUtils.hasText(knowledgeScopeName)
                && StringUtils.hasText(knowledgeTopicCode)
                && StringUtils.hasText(knowledgeTopicName);
    }

    private String buildMetadataSystemPrompt() {
        return """
                你是知识库文档路由元数据生成器。你必须只输出一个合法 JSON 对象，不能输出 Markdown、解释或额外文本。
                目标是为上传文档生成可用于知识路由的业务归属和画像字段。
                JSON schema:
                {
                  "knowledgeScopeCode": "lower_snake_case_code",
                  "knowledgeScopeName": "中文知识域名称",
                  "knowledgeTopicCode": "lower_snake_case_code",
                  "knowledgeTopicName": "中文专题名称",
                  "businessCategory": "业务分类",
                  "documentTags": ["标签1", "标签2"],
                  "summaryText": "一句话说明该文档可回答的问题范围",
                  "answerableQuestions": ["该文档能回答的问题"],
                  "terms": ["路由术语"],
                  "questionPatterns": ["用户可能提问模式"]
                }
                code 只能使用小写字母、数字和下划线，必须以小写字母开头。
                如果用户已提供某个字段，必须沿用该字段语义，不得改写为无关归属。
                所有数组必须至少包含 1 个非空字符串。
                """;
    }

    private String buildMetadataUserMessage(
            String originalFileName,
            String documentName,
            String contentType,
            long fileSize,
            String knowledgeScopeCode,
            String knowledgeScopeName,
            String knowledgeTopicCode,
            String knowledgeTopicName,
            String businessCategory,
            String documentTags,
            String parsedText) {
        return """
                请为以下上传文档生成知识库路由元数据。

                原始文件名：%s
                文档名称：%s
                MIME 类型：%s
                文件大小：%s
                用户提供的 knowledgeScopeCode：%s
                用户提供的 knowledgeScopeName：%s
                用户提供的 knowledgeTopicCode：%s
                用户提供的 knowledgeTopicName：%s
                用户提供的 businessCategory：%s
                用户提供的 documentTags：%s

                文本样本：
                %s
                """.formatted(
                originalFileName,
                documentName,
                normalizeOptionalText(contentType),
                fileSize,
                normalizeOptionalText(knowledgeScopeCode),
                normalizeOptionalText(knowledgeScopeName),
                normalizeOptionalText(knowledgeTopicCode),
                normalizeOptionalText(knowledgeTopicName),
                normalizeOptionalText(businessCategory),
                normalizeOptionalText(documentTags),
                textSample(parsedText));
    }

    private String textSample(String parsedText) {
        if (parsedText.length() > METADATA_TEXT_SAMPLE_LIMIT) {
            return parsedText.substring(0, METADATA_TEXT_SAMPLE_LIMIT);
        }
        return parsedText;
    }

    private void removeUploadedObjectAfterFailure(
            RuntimeException originalError,
            String bucketName,
            String objectName,
            boolean uploaded) {
        if (!uploaded) {
            return;
        }
        try {
            objectStorage.remove(bucketName, objectName);
        } catch (RuntimeException cleanupError) {
            // 清理失败不能吞掉真正的上传失败原因，只作为 suppressed 附在原异常上交给上层。
            originalError.addSuppressed(cleanupError);
        }
    }

    /**
     * 解析模型返回的知识画像 JSON。
     *
     * <p>模型必须返回一个对象，且每个必填字段都要有业务含义。
     * 这里不接受空数组、空字符串或非 JSON 文本，因为这些数据会直接影响路由结果。</p>
     */
    private CompletedKnowledgeMetadata parseGeneratedMetadata(String modelResponse) {
        try {
            JsonNode root = objectMapper.readTree(extractJsonObject(modelResponse));
            return new CompletedKnowledgeMetadata(
                    requiredJsonText(root, "knowledgeScopeCode"),
                    requiredJsonText(root, "knowledgeScopeName"),
                    requiredJsonText(root, "knowledgeTopicCode"),
                    requiredJsonText(root, "knowledgeTopicName"),
                    requiredJsonText(root, "businessCategory"),
                    requiredJsonTextArray(root, "documentTags"),
                    requiredJsonText(root, "summaryText"),
                    requiredJsonTextArray(root, "answerableQuestions"),
                    requiredJsonTextArray(root, "terms"),
                    requiredJsonTextArray(root, "questionPatterns"));
        } catch (JsonProcessingException error) {
            throw new IllegalStateException("knowledge metadata model response must be a JSON object", error);
        }
    }

    private String extractJsonObject(String text) {
        String normalized = normalizeRequiredText(text, "knowledge metadata model response");
        int start = normalized.indexOf('{');
        int end = normalized.lastIndexOf('}');
        if (start < 0 || end <= start) {
            throw new IllegalStateException("knowledge metadata model response must contain a JSON object");
        }
        return normalized.substring(start, end + 1);
    }

    private String requiredJsonText(JsonNode root, String fieldName) {
        JsonNode node = root.get(fieldName);
        if (node == null || !node.isTextual() || !StringUtils.hasText(node.asText())) {
            throw new IllegalStateException("knowledge metadata field must not be blank: " + fieldName);
        }
        return node.asText().strip();
    }

    private List<String> requiredJsonTextArray(JsonNode root, String fieldName) {
        JsonNode node = root.get(fieldName);
        if (node == null || !node.isArray()) {
            throw new IllegalStateException("knowledge metadata field must be an array: " + fieldName);
        }
        List<String> values = new ArrayList<>();
        node.forEach(item -> {
            if (item.isTextual() && StringUtils.hasText(item.asText())) {
                values.add(item.asText().strip());
            }
        });
        if (values.isEmpty()) {
            throw new IllegalStateException("knowledge metadata field must contain text: " + fieldName);
        }
        return values.stream().distinct().toList();
    }

    /**
     * 统一校验最终画像。
     *
     * <p>无论字段来自用户还是模型，都必须满足同一套规则。
     * 这是知识资产进入图谱前的最后一道边界。</p>
     */
    private CompletedKnowledgeMetadata validateCompletedMetadata(CompletedKnowledgeMetadata metadata) {
        // 所有来源的画像，包括用户填写和模型生成，都必须汇入这里做同一套强校验。
        // 不在下游补默认值，避免错误画像进入 MySQL/Neo4j 后被当成可路由资产。
        String scopeCode = normalizeCode(metadata.knowledgeScopeCode(), "knowledgeScopeCode");
        String topicCode = normalizeCode(metadata.knowledgeTopicCode(), "knowledgeTopicCode");
        String scopeName = normalizeRequiredText(metadata.knowledgeScopeName(), "knowledgeScopeName");
        String topicName = normalizeRequiredText(metadata.knowledgeTopicName(), "knowledgeTopicName");
        List<String> documentTags = validateTextList(metadata.documentTags(), "documentTags");
        List<String> answerableQuestions = validateTextList(metadata.answerableQuestions(), "answerableQuestions");
        List<String> terms = validateTextList(metadata.terms(), "terms");
        List<String> questionPatterns = validateTextList(metadata.questionPatterns(), "questionPatterns");
        String summaryText = normalizeRequiredText(metadata.summaryText(), "summaryText");
        return new CompletedKnowledgeMetadata(
                scopeCode,
                scopeName,
                topicCode,
                topicName,
                normalizeOptionalText(metadata.businessCategory()),
                documentTags,
                summaryText,
                answerableQuestions,
                terms,
                questionPatterns);
    }

    private List<String> validateTextList(List<String> values, String fieldName) {
        if (values == null) {
            throw new IllegalStateException(fieldName + " must not be empty");
        }
        List<String> normalizedValues = values.stream()
                .map(this::normalizeOptionalText)
                .filter(StringUtils::hasText)
                .distinct()
                .toList();
        if (normalizedValues.isEmpty()) {
            throw new IllegalStateException(fieldName + " must not be empty");
        }
        return normalizedValues;
    }

    private String normalizeCode(String value, String fieldName) {
        String normalized = normalizeRequiredText(value, fieldName);
        if (!normalized.matches("[a-z][a-z0-9_]{1,63}")) {
            throw new IllegalStateException(fieldName + " must match [a-z][a-z0-9_]{1,63}");
        }
        return normalized;
    }

    private String chooseProvided(String providedValue, String generatedValue) {
        return StringUtils.hasText(providedValue) ? providedValue.strip() : generatedValue;
    }

    private String writeUploadedKnowledgeMetadata(KnowledgeDocumentUploadMetaRequest meta) {
        try {
            return objectMapper.writeValueAsString(new UploadedKnowledgeMetadata(
                    normalizeOptionalText(meta.getKnowledgeScopeCode()),
                    normalizeOptionalText(meta.getKnowledgeScopeName()),
                    normalizeOptionalText(meta.getKnowledgeTopicCode()),
                    normalizeOptionalText(meta.getKnowledgeTopicName()),
                    normalizeOptionalText(meta.getBusinessCategory()),
                    normalizeOptionalText(meta.getDocumentTags())));
        } catch (JsonProcessingException error) {
            throw new IllegalStateException("failed to serialize uploaded knowledge metadata", error);
        }
    }

    private UploadedKnowledgeMetadata readUploadedKnowledgeMetadata(String json) {
        try {
            if (!StringUtils.hasText(json)) {
                return new UploadedKnowledgeMetadata(null, null, null, null, null, null);
            }
            return objectMapper.readValue(json, UploadedKnowledgeMetadata.class);
        } catch (IOException error) {
            throw new IllegalStateException("failed to read uploaded knowledge metadata", error);
        }
    }

    private String normalizeOptionalText(String value) {
        return value == null ? null : value.strip();
    }

    private record UploadedKnowledgeMetadata(
            String knowledgeScopeCode,
            String knowledgeScopeName,
            String knowledgeTopicCode,
            String knowledgeTopicName,
            String businessCategory,
            String documentTags) {
    }

    private record CompletedKnowledgeMetadata(
            String knowledgeScopeCode,
            String knowledgeScopeName,
            String knowledgeTopicCode,
            String knowledgeTopicName,
            String businessCategory,
            List<String> documentTags,
            String summaryText,
            List<String> answerableQuestions,
            List<String> terms,
            List<String> questionPatterns) {
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("file must not be empty");
        }
    }

    private byte[] readUploadedBytes(MultipartFile file) {
        try {
            byte[] content = file.getBytes();
            if (content.length == 0) {
                throw new IllegalArgumentException("file must not be empty");
            }
            return content;
        } catch (IOException error) {
            throw new IllegalStateException("failed to read uploaded document", error);
        }
    }

    private String parseDocumentText(byte[] content, String originalFileName, int fileType) {
        String text = switch (fileType) {
            case 4, 5 -> new String(content, StandardCharsets.UTF_8);
            case 6 -> Jsoup.parse(new String(content, StandardCharsets.UTF_8)).text();
            default -> throw new IllegalArgumentException("unsupported file type: " + originalFileName);
        };
        String parsedText = text.strip();
        if (!StringUtils.hasText(parsedText)) {
            throw new IllegalArgumentException("parsed document text must not be empty");
        }
        return parsedText;
    }

    private String buildOriginalObjectName(long documentId, String originalFileName) {
        return "knowledge-documents/%s/original/%s-%s".formatted(
                documentId,
                UUID.randomUUID(),
                sanitizeObjectFileName(originalFileName));
    }

    private String buildParsedTextObjectName(long documentId) {
        return "knowledge-documents/%s/parsed/content.txt".formatted(documentId);
    }

    private String sanitizeObjectFileName(String fileName) {
        String sanitized = normalizeRequiredText(fileName, "originalFileName")
                .replace('\\', '_')
                .replace('/', '_')
                .strip();
        if (!StringUtils.hasText(sanitized)) {
            throw new IllegalArgumentException("originalFileName must not be blank");
        }
        return sanitized;
    }

    private int estimateTokenCount(String text) {
        return Math.max(1, (int) Math.ceil(text.length() / 4.0));
    }

    private int resolveFileType(String fileName) {
        String lowerFileName = fileName.toLowerCase(Locale.ROOT);
        if (lowerFileName.endsWith(".txt")) {
            return 4;
        }
        if (lowerFileName.endsWith(".md")) {
            return 5;
        }
        if (lowerFileName.endsWith(".html") || lowerFileName.endsWith(".htm")) {
            return 6;
        }
        throw new IllegalArgumentException("unsupported file type: " + fileName);
    }

    private void validateReadableParsedDocument(KnowledgeDocumentData documentData) {
        if (!Integer.valueOf(MINIO_STORAGE).equals(documentData.getStorageType())) {
            throw new IllegalStateException("document storage type is not MinIO: " + documentData.getId());
        }
        if (!Integer.valueOf(PARSE_SUCCEEDED).equals(documentData.getParseStatus())) {
            throw new IllegalStateException("document was not parsed successfully: " + documentData.getId());
        }
        if (!StringUtils.hasText(documentData.getBucketName())) {
            throw new IllegalStateException("document bucketName is empty: " + documentData.getId());
        }
        if (!StringUtils.hasText(documentData.getParseTextPath())) {
            throw new IllegalStateException("document parseTextPath is empty: " + documentData.getId());
        }
    }

    private void removeStoredObjects(KnowledgeDocumentData documentData) {
        if (!Integer.valueOf(MINIO_STORAGE).equals(documentData.getStorageType())) {
            return;
        }
        if (!StringUtils.hasText(documentData.getBucketName())) {
            throw new IllegalStateException("document bucketName is empty: " + documentData.getId());
        }
        if (StringUtils.hasText(documentData.getObjectName())) {
            objectStorage.remove(documentData.getBucketName(), documentData.getObjectName());
        }
        if (StringUtils.hasText(documentData.getParseTextPath())) {
            objectStorage.remove(documentData.getBucketName(), documentData.getParseTextPath());
        }
    }

    private KnowledgeDocumentVo toDocumentVo(KnowledgeDocumentData data) {
        KnowledgeDocumentVo vo = new KnowledgeDocumentVo();
        vo.setDocumentId(String.valueOf(data.getId()));
        vo.setDocumentName(data.getDocumentName());
        vo.setOriginalFileName(data.getOriginalFileName());
        vo.setKnowledgeScopeCode(data.getKnowledgeScopeCode());
        vo.setKnowledgeScopeName(data.getKnowledgeScopeName());
        vo.setBusinessCategory(data.getBusinessCategory());
        vo.setDocumentTags(data.getDocumentTags());
        vo.setParseStatus(String.valueOf(data.getParseStatus()));
        vo.setStrategyStatus(String.valueOf(data.getStrategyStatus()));
        vo.setIndexStatus(String.valueOf(data.getIndexStatus()));
        vo.setCreateTime(data.getCreateTime() == null ? "" : TIME_FORMATTER.format(data.getCreateTime()));
        return vo;
    }

    private Map<Long, KnowledgeDocumentProfileData> loadLatestProfileByDocumentId(
            List<KnowledgeDocumentData> documentList) {
        List<Long> documentIds = documentList.stream()
                .map(KnowledgeDocumentData::getId)
                .toList();
        if (documentIds.isEmpty()) {
            return Map.of();
        }
        return profileMapper.selectList(Wrappers.<KnowledgeDocumentProfileData>lambdaQuery()
                        .in(KnowledgeDocumentProfileData::getDocumentId, documentIds)
                        .eq(KnowledgeDocumentProfileData::getStatus, NORMAL_STATUS)
                        .orderByDesc(KnowledgeDocumentProfileData::getProfileVersion)
                        .orderByDesc(KnowledgeDocumentProfileData::getId))
                .stream()
                .collect(Collectors.toMap(
                        KnowledgeDocumentProfileData::getDocumentId,
                        Function.identity(),
                        // 查询已按版本和 id 倒序排列；重复 documentId 的第一个就是当前最新画像。
                        (latestProfile, ignoredProfile) -> latestProfile));
    }

    private Map<String, String> loadTopicNameByCode(List<KnowledgeDocumentProfileData> profileList) {
        List<String> topicCodes = profileList.stream()
                .map(KnowledgeDocumentProfileData::getTopicCode)
                .filter(StringUtils::hasText)
                .distinct()
                .toList();
        if (topicCodes.isEmpty()) {
            return Map.of();
        }
        return topicNodeMapper.selectList(Wrappers.<KnowledgeTopicNodeData>lambdaQuery()
                        .in(KnowledgeTopicNodeData::getTopicCode, topicCodes)
                        .eq(KnowledgeTopicNodeData::getStatus, NORMAL_STATUS))
                .stream()
                .collect(Collectors.toMap(
                        KnowledgeTopicNodeData::getTopicCode,
                        KnowledgeTopicNodeData::getTopicName,
                        (firstName, ignoredName) -> firstName));
    }

    private KnowledgeRouteAssetVo toRouteAssetVo(
            KnowledgeDocumentData documentData,
            KnowledgeDocumentProfileData profileData,
            Map<String, String> topicNameByCode) {
        if (profileData == null) {
            throw new IllegalStateException("document route profile is missing: " + documentData.getId());
        }

        KnowledgeRouteAssetVo vo = new KnowledgeRouteAssetVo();
        vo.setDocumentId(String.valueOf(documentData.getId()));
        vo.setDocumentName(documentData.getDocumentName());
        vo.setOriginalFileName(documentData.getOriginalFileName());
        vo.setScopeCode(profileData.getScopeCode());
        vo.setScopeName(documentData.getKnowledgeScopeName());
        vo.setTopicCode(profileData.getTopicCode());
        String topicCode = profileData.getTopicCode();
        vo.setTopicName(StringUtils.hasText(topicCode) ? topicNameByCode.getOrDefault(topicCode, topicCode) : "");
        vo.setSummaryText(profileData.getSummaryText());
        vo.setTerms(readStringList(profileData.getTermsJson()));
        vo.setQuestionPatterns(readStringList(profileData.getQuestionPatternsJson()));
        vo.setRouteStatus("ROUTABLE");
        vo.setUpdateTime(documentData.getEditTime() == null ? "" : TIME_FORMATTER.format(documentData.getEditTime()));
        return vo;
    }

    private KnowledgeRouteCandidateVo toRouteCandidateVo(KnowledgeRouteCandidate candidate) {
        KnowledgeRouteCandidateVo vo = new KnowledgeRouteCandidateVo();
        vo.setDocumentId(String.valueOf(candidate.documentId()));
        vo.setDocumentName(candidate.documentName());
        vo.setScopeCode(candidate.scopeCode());
        vo.setScopeName(candidate.scopeName());
        vo.setTopicCode(candidate.topicCode());
        vo.setTopicName(candidate.topicName());
        vo.setScore(candidate.score());
        vo.setTermScore(candidate.termScore());
        vo.setPatternScore(candidate.patternScore());
        vo.setHitTerms(candidate.hitTerms() == null ? List.of() : candidate.hitTerms());
        vo.setMatchedPatterns(candidate.matchedPatterns() == null ? List.of() : candidate.matchedPatterns());
        vo.setHitReason(candidate.hitReason());
        return vo;
    }

    private KnowledgeRouteTraceVo toRouteTraceVo(KnowledgeRouteTraceRow row) {
        JsonNode executionPlan = readExecutionPlan(row.getDebugTraceJson(), row.getExchangeId());
        KnowledgeRouteTraceVo vo = new KnowledgeRouteTraceVo();
        vo.setConversationId(row.getConversationId());
        vo.setExchangeId(String.valueOf(row.getExchangeId()));
        vo.setQuestion(row.getQuestion());
        // 前端路由追踪页只展示执行计划中的路由摘要和候选，不解析完整调试快照。
        vo.setKnowledgeRoute(optionalJsonText(executionPlan, "knowledgeRoute"));
        vo.setCandidates(readRouteCandidateVoList(executionPlan.get("knowledgeRouteCandidateList")));
        vo.setCreateTime(row.getCreateTime() == null ? "" : TIME_FORMATTER.format(row.getCreateTime()));
        return vo;
    }

    private JsonNode readExecutionPlan(String debugTraceJson, Long exchangeId) {
        try {
            JsonNode root = objectMapper.readTree(normalizeRequiredText(debugTraceJson, "debugTraceJson"));
            JsonNode executionPlan = root.get("executionPlan");
            if (executionPlan == null || !executionPlan.isObject()) {
                throw new IllegalStateException("executionPlan is missing for exchangeId=" + exchangeId);
            }
            return executionPlan;
        } catch (IOException error) {
            throw new IllegalStateException("failed to parse route trace for exchangeId=" + exchangeId, error);
        }
    }

    private List<KnowledgeRouteCandidateVo> readRouteCandidateVoList(JsonNode candidateListNode) {
        if (candidateListNode == null || !candidateListNode.isArray()) {
            return List.of();
        }
        List<KnowledgeRouteCandidateVo> candidates = new ArrayList<>();
        candidateListNode.forEach(candidateNode -> {
            KnowledgeRouteCandidateVo vo = new KnowledgeRouteCandidateVo();
            vo.setDocumentId(optionalJsonText(candidateNode, "documentId"));
            vo.setDocumentName(optionalJsonText(candidateNode, "documentName"));
            vo.setScopeCode(optionalJsonText(candidateNode, "scopeCode"));
            vo.setScopeName(optionalJsonText(candidateNode, "scopeName"));
            vo.setTopicCode(optionalJsonText(candidateNode, "topicCode"));
            vo.setTopicName(optionalJsonText(candidateNode, "topicName"));
            vo.setScore(optionalJsonDouble(candidateNode, "score"));
            vo.setTermScore(optionalJsonDouble(candidateNode, "termScore"));
            vo.setPatternScore(optionalJsonDouble(candidateNode, "patternScore"));
            vo.setHitTerms(optionalJsonTextList(candidateNode.get("hitTerms")));
            vo.setMatchedPatterns(optionalJsonTextList(candidateNode.get("matchedPatterns")));
            vo.setHitReason(optionalJsonText(candidateNode, "hitReason"));
            candidates.add(vo);
        });
        return candidates;
    }

    private String optionalJsonText(JsonNode node, String fieldName) {
        JsonNode value = node == null ? null : node.get(fieldName);
        if (value == null || value.isNull()) {
            return "";
        }
        if (value.isTextual()) {
            return value.asText();
        }
        return value.asText("");
    }

    private double optionalJsonDouble(JsonNode node, String fieldName) {
        JsonNode value = node == null ? null : node.get(fieldName);
        return value == null || !value.isNumber() ? 0 : value.asDouble();
    }

    private List<String> optionalJsonTextList(JsonNode node) {
        if (node == null || !node.isArray()) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        node.forEach(item -> {
            String value = item.isTextual() ? item.asText() : item.asText("");
            if (StringUtils.hasText(value)) {
                values.add(value);
            }
        });
        return values;
    }

    private KnowledgeDocumentProfileVo toProfileVo(KnowledgeDocumentProfileData data) {
        KnowledgeDocumentProfileVo vo = new KnowledgeDocumentProfileVo();
        vo.setDocumentId(String.valueOf(data.getDocumentId()));
        vo.setScopeCode(data.getScopeCode());
        vo.setTopicCode(data.getTopicCode());
        vo.setSummaryText(data.getSummaryText());
        vo.setAnswerableQuestions(readStringList(data.getAnswerableQuestionsJson()));
        vo.setUnanswerableQuestions(readStringList(data.getUnanswerableQuestionsJson()));
        vo.setBusinessEntities(readStringList(data.getBusinessEntitiesJson()));
        vo.setTerms(readStringList(data.getTermsJson()));
        vo.setQuestionPatterns(readStringList(data.getQuestionPatternsJson()));
        return vo;
    }

    private List<String> splitCsv(String value) {
        if (!StringUtils.hasText(value)) {
            return List.of();
        }
        return Arrays.stream(value.split(","))
                .map(String::strip)
                .filter(StringUtils::hasText)
                .distinct()
                .toList();
    }

    private String toJson(List<String> values) {
        try {
            return objectMapper.writeValueAsString(values);
        } catch (JsonProcessingException error) {
            throw new IllegalStateException("failed to serialize knowledge profile", error);
        }
    }

    private List<String> readStringList(String json) {
        try {
            return objectMapper.readerForListOf(String.class).readValue(json);
        } catch (IOException error) {
            throw new IllegalStateException("failed to parse knowledge profile", error);
        }
    }

    private String normalizeRequiredText(String value, String fieldName) {
        return BusinessInputValidator.normalizeRequiredText(value, fieldName);
    }
}
