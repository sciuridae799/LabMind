package com.superagent.business.chat.knowledge.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.superagent.business.chat.chatagent.execution.BusinessChatDynamicModelClient;
import com.superagent.business.chat.chatagent.model.BusinessChatModelApiConfigSnapshot;
import com.superagent.business.chat.chatagent.service.BusinessChatModelApiConfigService;
import com.superagent.business.chat.knowledge.data.KnowledgeDocumentData;
import com.superagent.business.chat.knowledge.data.KnowledgeDocumentProfileData;
import com.superagent.business.chat.knowledge.data.KnowledgeScopeNodeData;
import com.superagent.business.chat.knowledge.data.KnowledgeTopicNodeData;
import com.superagent.business.chat.knowledge.dto.KnowledgeDocumentIdRequest;
import com.superagent.business.chat.knowledge.dto.KnowledgeDocumentPageRequest;
import com.superagent.business.chat.knowledge.dto.KnowledgeDocumentUploadMetaRequest;
import com.superagent.business.chat.knowledge.dto.KnowledgeRoutePreviewRequest;
import com.superagent.business.chat.knowledge.graph.KnowledgeGraphClient;
import com.superagent.business.chat.knowledge.mapper.KnowledgeDocumentMapper;
import com.superagent.business.chat.knowledge.mapper.KnowledgeDocumentProfileMapper;
import com.superagent.business.chat.knowledge.mapper.KnowledgeScopeNodeMapper;
import com.superagent.business.chat.knowledge.mapper.KnowledgeTopicNodeMapper;
import com.superagent.business.chat.knowledge.model.KnowledgeDocumentRouteAsset;
import com.superagent.business.chat.knowledge.objectstore.KnowledgeDocumentObjectStorage;
import com.superagent.business.chat.knowledge.service.KnowledgeManageService;
import com.superagent.business.chat.knowledge.vo.KnowledgeDocumentPageVo;
import com.superagent.business.chat.knowledge.vo.KnowledgeDocumentProfileVo;
import com.superagent.business.chat.knowledge.vo.KnowledgeDocumentVo;
import com.superagent.business.chat.knowledge.vo.KnowledgeRouteCandidateVo;
import com.superagent.common.frame.enums.BaseCode;
import com.superagent.common.frame.exception.BaseException;
import com.superagent.idgenerator.toolkit.SnowflakeIdGenerator;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.jsoup.Jsoup;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class KnowledgeManageServiceImpl implements KnowledgeManageService {

    private static final int NORMAL_STATUS = 1;
    private static final int DELETED_STATUS = 0;
    private static final int PARSE_SUCCEEDED = 3;
    private static final int STRATEGY_RECOMMENDED = 2;
    private static final int INDEX_PENDING = 1;
    private static final int PROFILE_GENERATED = 1;
    private static final int MINIO_STORAGE = 1;
    private static final int METADATA_TEXT_SAMPLE_LIMIT = 12000;
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final KnowledgeDocumentMapper documentMapper;
    private final KnowledgeScopeNodeMapper scopeNodeMapper;
    private final KnowledgeTopicNodeMapper topicNodeMapper;
    private final KnowledgeDocumentProfileMapper profileMapper;
    private final KnowledgeGraphClient knowledgeGraphClient;
    private final KnowledgeDocumentObjectStorage objectStorage;
    private final SnowflakeIdGenerator snowflakeIdGenerator;
    private final ObjectMapper objectMapper;
    private final BusinessChatDynamicModelClient modelClient;
    private final BusinessChatModelApiConfigService modelApiConfigService;

    @Override
    @Transactional
    public KnowledgeDocumentVo uploadDocument(MultipartFile file, KnowledgeDocumentUploadMetaRequest meta) {
        validateFile(file);

        long documentId = snowflakeIdGenerator.nextId();
        String originalFileName = normalizeRequiredText(file.getOriginalFilename(), "originalFileName");
        String documentName = StringUtils.hasText(meta.getDocumentName())
                ? meta.getDocumentName().strip()
                : originalFileName;
        int fileType = resolveFileType(originalFileName);
        byte[] originalContent = readUploadedBytes(file);
        String parsedText = parseDocumentText(originalContent, originalFileName, fileType);
        byte[] parsedTextContent = parsedText.getBytes(StandardCharsets.UTF_8);
        String originalObjectName = buildOriginalObjectName(documentId, originalFileName);
        String parsedTextObjectName = buildParsedTextObjectName(documentId);
        String bucketName = objectStorage.bucket();
        boolean originalUploaded = false;
        boolean parsedTextUploaded = false;

        try {
            objectStorage.put(originalObjectName, originalContent, file.getContentType());
            originalUploaded = true;
            objectStorage.put(parsedTextObjectName, parsedTextContent, "text/plain; charset=utf-8");
            parsedTextUploaded = true;
            CompletedKnowledgeMetadata completedMetadata = completeMetadata(
                    file,
                    originalFileName,
                    documentName,
                    meta,
                    parsedText);
            upsertScope(completedMetadata);
            upsertTopic(completedMetadata);

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
            documentData.setParseStatus(PARSE_SUCCEEDED);
            documentData.setStrategyStatus(STRATEGY_RECOMMENDED);
            documentData.setIndexStatus(INDEX_PENDING);
            documentData.setCharCount(parsedText.length());
            documentData.setTokenCount(estimateTokenCount(parsedText));
            documentData.setStructureLevel(0);
            documentData.setContentQualityLevel(0);
            documentData.setParseTextPath(parsedTextObjectName);
            documentData.setKnowledgeScopeCode(completedMetadata.knowledgeScopeCode());
            documentData.setKnowledgeScopeName(completedMetadata.knowledgeScopeName());
            documentData.setBusinessCategory(completedMetadata.businessCategory());
            documentData.setDocumentTags(String.join(",", completedMetadata.documentTags()));
            documentData.setStructureNodeCount(0);
            documentData.setStatus(NORMAL_STATUS);
            documentMapper.insert(documentData);

            KnowledgeDocumentProfileData profileData = buildProfile(documentData, completedMetadata);
            profileMapper.insert(profileData);
            knowledgeGraphClient.upsertDocumentRouteAsset(toRouteAsset(documentData, completedMetadata, profileData));
            return toDocumentVo(documentData);
        } catch (RuntimeException error) {
            removeUploadedObjectAfterFailure(error, bucketName, parsedTextObjectName, parsedTextUploaded);
            removeUploadedObjectAfterFailure(error, bucketName, originalObjectName, originalUploaded);
            throw error;
        }
    }

    @Override
    public KnowledgeDocumentPageVo queryDocumentPage(KnowledgeDocumentPageRequest request) {
        int pageNo = parsePositiveInt(request.getPageNo(), "pageNo");
        int pageSize = parsePositiveInt(request.getPageSize(), "pageSize");
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
        long documentId = parsePositiveLong(request.getDocumentId(), "documentId");
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
        long documentId = parsePositiveLong(request.getDocumentId(), "documentId");
        KnowledgeDocumentData documentData = loadNormalDocument(documentId);
        validateReadableParsedDocument(documentData);
        String parsedText = objectStorage.getText(documentData.getBucketName(), documentData.getParseTextPath()).strip();
        if (!StringUtils.hasText(parsedText)) {
            throw new IllegalStateException("parsed document text is empty: " + documentId);
        }
        return parsedText;
    }

    @Override
    @Transactional
    public void deleteDocument(KnowledgeDocumentIdRequest request) {
        long documentId = parsePositiveLong(request.getDocumentId(), "documentId");
        KnowledgeDocumentData documentData = loadNormalDocument(documentId);
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
        long documentId = parsePositiveLong(request.getDocumentId(), "documentId");
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
        int limit = parsePositiveInt(request.getLimit(), "limit");
        return knowledgeGraphClient.routeQuestion(question, limit)
                .stream()
                .map(candidate -> {
                    KnowledgeRouteCandidateVo vo = new KnowledgeRouteCandidateVo();
                    vo.setDocumentId(String.valueOf(candidate.documentId()));
                    vo.setDocumentName(candidate.documentName());
                    vo.setScopeCode(candidate.scopeCode());
                    vo.setScopeName(candidate.scopeName());
                    vo.setTopicCode(candidate.topicCode());
                    vo.setTopicName(candidate.topicName());
                    vo.setScore(candidate.score());
                    vo.setHitReason(candidate.hitReason());
                    return vo;
                })
                .toList();
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

    private CompletedKnowledgeMetadata completeMetadata(
            MultipartFile file,
            String originalFileName,
            String documentName,
            KnowledgeDocumentUploadMetaRequest meta,
            String parsedText) {
        if (hasCompleteRouteMetadata(meta)) {
            List<String> manualDocumentTags = splitCsv(meta.getDocumentTags());
            if (manualDocumentTags.isEmpty()) {
                manualDocumentTags = List.of(meta.getKnowledgeTopicName().strip(), documentName);
            }
            return validateCompletedMetadata(new CompletedKnowledgeMetadata(
                    meta.getKnowledgeScopeCode(),
                    meta.getKnowledgeScopeName(),
                    meta.getKnowledgeTopicCode(),
                    meta.getKnowledgeTopicName(),
                    normalizeOptionalText(meta.getBusinessCategory()),
                    manualDocumentTags,
                    "%s/%s 下的文档：%s".formatted(
                            meta.getKnowledgeScopeName().strip(),
                            meta.getKnowledgeTopicName().strip(),
                            documentName),
                    List.of(
                            "关于%s的问题".formatted(documentName),
                            "%s相关流程".formatted(meta.getKnowledgeTopicName().strip())),
                    manualDocumentTags,
                    List.of("怎么申请", "如何办理", "是什么", "怎么使用")));
        }

        BusinessChatModelApiConfigSnapshot modelConfig = modelApiConfigService.getLatestAvailableSnapshot();
        String modelResponse = modelClient.call(
                modelConfig,
                buildMetadataSystemPrompt(),
                buildMetadataUserMessage(file, originalFileName, documentName, meta, parsedText));
        CompletedKnowledgeMetadata generatedMetadata = parseGeneratedMetadata(modelResponse);
        return validateCompletedMetadata(new CompletedKnowledgeMetadata(
                chooseProvided(meta.getKnowledgeScopeCode(), generatedMetadata.knowledgeScopeCode()),
                chooseProvided(meta.getKnowledgeScopeName(), generatedMetadata.knowledgeScopeName()),
                chooseProvided(meta.getKnowledgeTopicCode(), generatedMetadata.knowledgeTopicCode()),
                chooseProvided(meta.getKnowledgeTopicName(), generatedMetadata.knowledgeTopicName()),
                chooseProvided(meta.getBusinessCategory(), generatedMetadata.businessCategory()),
                generatedMetadata.documentTags(),
                generatedMetadata.summaryText(),
                generatedMetadata.answerableQuestions(),
                generatedMetadata.terms(),
                generatedMetadata.questionPatterns()));
    }

    private boolean hasCompleteRouteMetadata(KnowledgeDocumentUploadMetaRequest meta) {
        return StringUtils.hasText(meta.getKnowledgeScopeCode())
                && StringUtils.hasText(meta.getKnowledgeScopeName())
                && StringUtils.hasText(meta.getKnowledgeTopicCode())
                && StringUtils.hasText(meta.getKnowledgeTopicName());
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
            MultipartFile file,
            String originalFileName,
            String documentName,
            KnowledgeDocumentUploadMetaRequest meta,
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
                normalizeOptionalText(file.getContentType()),
                file.getSize(),
                normalizeOptionalText(meta.getKnowledgeScopeCode()),
                normalizeOptionalText(meta.getKnowledgeScopeName()),
                normalizeOptionalText(meta.getKnowledgeTopicCode()),
                normalizeOptionalText(meta.getKnowledgeTopicName()),
                normalizeOptionalText(meta.getBusinessCategory()),
                normalizeOptionalText(meta.getDocumentTags()),
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
            originalError.addSuppressed(cleanupError);
        }
    }

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

    private CompletedKnowledgeMetadata validateCompletedMetadata(CompletedKnowledgeMetadata metadata) {
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

    private String normalizeOptionalText(String value) {
        return value == null ? null : value.strip();
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
        objectStorage.remove(documentData.getBucketName(), documentData.getObjectName());
        objectStorage.remove(documentData.getBucketName(), documentData.getParseTextPath());
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
        String normalized = value == null ? null : value.strip();
        if (!StringUtils.hasText(normalized)) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return normalized;
    }

    private int parsePositiveInt(String value, String fieldName) {
        long parsedValue = parsePositiveLong(value, fieldName);
        if (parsedValue > Integer.MAX_VALUE) {
            throw new IllegalArgumentException(fieldName + " is too large");
        }
        return (int) parsedValue;
    }

    private long parsePositiveLong(String value, String fieldName) {
        try {
            long parsedValue = Long.parseLong(normalizeRequiredText(value, fieldName));
            if (parsedValue <= 0) {
                throw new IllegalArgumentException(fieldName + " must be greater than 0");
            }
            return parsedValue;
        } catch (NumberFormatException error) {
            throw new IllegalArgumentException(fieldName + " must be a valid number", error);
        }
    }
}
