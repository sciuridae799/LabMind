package com.superagent.business.chat.chatagent.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.superagent.business.chat.chatagent.config.BusinessChatModelApiConfigProperties;
import com.superagent.business.chat.chatagent.persistence.data.BusinessChatModelApiConfigData;
import com.superagent.business.chat.chatagent.api.dto.BusinessChatModelApiConfigIdRequest;
import com.superagent.business.chat.chatagent.api.dto.BusinessChatModelApiConfigMoveRequest;
import com.superagent.business.chat.chatagent.api.dto.BusinessChatModelApiConfigSaveRequest;
import com.superagent.business.chat.chatagent.persistence.mapper.BusinessChatModelApiConfigMapper;
import com.superagent.business.chat.chatagent.execution.model.BusinessChatModelApiConfigSnapshot;
import com.superagent.business.chat.chatagent.execution.model.BusinessChatModelPricing;
import com.superagent.business.chat.chatagent.execution.model.BusinessChatModelProvider;
import com.superagent.business.chat.chatagent.service.BusinessChatErrorCode;
import com.superagent.business.chat.chatagent.service.BusinessChatModelApiConfigService;
import com.superagent.business.chat.chatagent.api.vo.BusinessChatModelApiConfigVo;
import com.superagent.business.chat.support.BusinessInputValidator;
import com.superagent.common.frame.enums.BaseCode;
import com.superagent.common.frame.exception.BaseException;
import com.superagent.idgenerator.toolkit.SnowflakeIdGenerator;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.math.BigDecimal;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Objects;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 模型 API 配置管理服务。
 *
 * <p>负责维护可选模型的 provider、baseUrl、modelName、启用状态和排序，并向执行链路提供可用配置快照。</p>
 */
@Service
@RequiredArgsConstructor
public class BusinessChatModelApiConfigServiceImpl implements BusinessChatModelApiConfigService {

    private static final int NORMAL_STATUS = 1;

    private static final int DELETED_STATUS = 0;

    private static final int ENABLED = 1;

    private static final int DISABLED = 0;

    private static final int SORT_ORDER_STEP = 1000;

    private static final int DEFAULT_PRICE_UNIT_TOKENS = 1000;

    private static final String DEFAULT_CURRENCY = "CNY";

    private static final String AES_TRANSFORMATION = "AES/GCM/NoPadding";

    private static final int GCM_IV_BYTES = 12;

    private static final int GCM_TAG_BITS = 128;

    private final BusinessChatModelApiConfigMapper modelApiConfigMapper;

    private final SnowflakeIdGenerator snowflakeIdGenerator;

    private final BusinessChatModelApiConfigProperties modelApiConfigProperties;

    private final SecureRandom secureRandom = new SecureRandom();

    @Override
    public List<BusinessChatModelApiConfigVo> listAll() {
        return modelApiConfigMapper.selectList(Wrappers.<BusinessChatModelApiConfigData>lambdaQuery()
                        .eq(BusinessChatModelApiConfigData::getStatus, NORMAL_STATUS)
                        .orderByAsc(BusinessChatModelApiConfigData::getSortOrder)
                        .orderByDesc(BusinessChatModelApiConfigData::getId))
                .stream()
                .map(this::toVo)
                .toList();
    }

    @Override
    public List<BusinessChatModelApiConfigVo> listAvailable() {
        return modelApiConfigMapper.selectList(availableConfigQuery()
                        .orderByAsc(BusinessChatModelApiConfigData::getSortOrder)
                        .orderByDesc(BusinessChatModelApiConfigData::getId))
                .stream()
                .map(this::toVo)
                .toList();
    }

    @Override
    @Transactional
    public BusinessChatModelApiConfigVo save(BusinessChatModelApiConfigSaveRequest request) {
        BusinessChatModelProvider provider = BusinessChatModelProvider.fromValue(request.getProvider());
        Long id = parseOptionalId(request.getId());
        BusinessChatModelApiConfigData data = id == null
                ? new BusinessChatModelApiConfigData()
                : loadExisting(id);
        if (id == null) {
            normalizeSortOrders();
            data.setId(snowflakeIdGenerator.nextId());
            data.setStatus(NORMAL_STATUS);
            data.setSortOrder(nextSortOrder());
        }
        data.setProvider(provider.getValue());
        data.setDisplayName(BusinessInputValidator.normalizeRequiredText(request.getDisplayName(), "displayName"));
        data.setBaseUrl(normalizeBaseUrl(request.getBaseUrl()));
        data.setModelName(BusinessInputValidator.normalizeRequiredText(request.getModelName(), "modelName"));
        BusinessChatModelPricing.UnitPrice unitPrice =
                BusinessChatModelPricing.configUnitPrice(provider, data.getBaseUrl(), data.getModelName());
        data.setInputTokenUnitPrice(unitPrice.inputTokenUnitPrice());
        data.setOutputTokenUnitPrice(unitPrice.outputTokenUnitPrice());
        data.setPriceUnitTokens(unitPrice.priceUnitTokens());
        data.setCurrency(unitPrice.currency());
        data.setEnabled(Boolean.TRUE.equals(request.getEnabled()) ? ENABLED : DISABLED);
        String normalizedApiKey = BusinessInputValidator.normalizeOptionalText(request.getApiKey());
        if (StringUtils.hasText(normalizedApiKey)) {
            data.setApiKeyCipher(encodeApiKey(normalizedApiKey));
        }
        if (id == null) {
            modelApiConfigMapper.insert(data);
        } else {
            modelApiConfigMapper.updateById(data);
        }
        return toVo(modelApiConfigMapper.selectById(data.getId()));
    }

    @Override
    @Transactional
    public void delete(BusinessChatModelApiConfigIdRequest request) {
        BusinessChatModelApiConfigData data = loadExisting(parseRequiredId(request.getId()));
        data.setStatus(DELETED_STATUS);
        modelApiConfigMapper.updateById(data);
    }

    @Override
    @Transactional
    public void clearApiKey(BusinessChatModelApiConfigIdRequest request) {
        BusinessChatModelApiConfigData data = loadExisting(parseRequiredId(request.getId()));
        data.setApiKeyCipher(null);
        modelApiConfigMapper.updateById(data);
    }

    @Override
    @Transactional
    public void move(BusinessChatModelApiConfigMoveRequest request) {
        normalizeSortOrders();
        Long id = parseRequiredId(request.getId());
        String direction = BusinessInputValidator.normalizeRequiredText(request.getDirection(), "direction");
        List<BusinessChatModelApiConfigData> configList = loadOrderedConfigList();
        int currentIndex = -1;
        for (int index = 0; index < configList.size(); index++) {
            if (id.equals(configList.get(index).getId())) {
                currentIndex = index;
                break;
            }
        }
        if (currentIndex < 0) {
            throw new BaseException(
                    BusinessChatErrorCode.CHAT_MODEL_CONFIG_NOT_FOUND,
                    "model config was not found: " + id);
        }
        int targetIndex = switch (direction) {
            case "UP" -> currentIndex - 1;
            case "DOWN" -> currentIndex + 1;
            default -> throw new BaseException(BaseCode.INVALID_PARAMETER, "direction must be UP or DOWN");
        };
        if (targetIndex < 0 || targetIndex >= configList.size()) {
            return;
        }
        BusinessChatModelApiConfigData current = configList.get(currentIndex);
        BusinessChatModelApiConfigData target = configList.get(targetIndex);
        Integer currentSortOrder = current.getSortOrder();
        current.setSortOrder(target.getSortOrder());
        target.setSortOrder(currentSortOrder);
        modelApiConfigMapper.updateById(current);
        modelApiConfigMapper.updateById(target);
    }

    @Override
    public BusinessChatModelApiConfigSnapshot getRequiredAvailableSnapshot(String id) {
        BusinessChatModelApiConfigData data = loadExisting(parseRequiredId(id));
        if (!isAvailable(data)) {
            throw new BaseException(
                    BusinessChatErrorCode.CHAT_MODEL_CONFIG_UNAVAILABLE,
                    "model config is unavailable: " + id);
        }
        return toSnapshot(data);
    }

    @Override
    public BusinessChatModelApiConfigSnapshot getLatestAvailableSnapshot() {
        BusinessChatModelApiConfigData data = modelApiConfigMapper.selectOne(availableConfigQuery()
                .orderByAsc(BusinessChatModelApiConfigData::getSortOrder)
                .orderByDesc(BusinessChatModelApiConfigData::getId)
                .last("limit 1"));
        if (data == null) {
            throw new BaseException(
                    BusinessChatErrorCode.CHAT_MODEL_CONFIG_UNAVAILABLE,
                    "available model config is required before auto completing knowledge metadata");
        }
        return toSnapshot(data);
    }

    private BusinessChatModelApiConfigData loadExisting(Long id) {
        BusinessChatModelApiConfigData data = modelApiConfigMapper.selectOne(
                Wrappers.<BusinessChatModelApiConfigData>lambdaQuery()
                        .eq(BusinessChatModelApiConfigData::getId, id)
                        .eq(BusinessChatModelApiConfigData::getStatus, NORMAL_STATUS));
        if (data == null) {
            throw new BaseException(
                    BusinessChatErrorCode.CHAT_MODEL_CONFIG_NOT_FOUND,
                    "model config was not found: " + id);
        }
        return data;
    }

    private BusinessChatModelApiConfigVo toVo(BusinessChatModelApiConfigData data) {
        BusinessChatModelProvider provider = BusinessChatModelProvider.fromValue(data.getProvider());
        BusinessChatModelPricing.UnitPrice unitPrice =
                BusinessChatModelPricing.configUnitPrice(provider, data.getBaseUrl(), data.getModelName());
        return new BusinessChatModelApiConfigVo(
                String.valueOf(data.getId()),
                data.getProvider(),
                data.getDisplayName(),
                data.getBaseUrl(),
                data.getModelName(),
                unitPrice.inputTokenUnitPrice(),
                unitPrice.outputTokenUnitPrice(),
                unitPrice.priceUnitTokens(),
                unitPrice.currency(),
                StringUtils.hasText(data.getApiKeyCipher()),
                Objects.equals(ENABLED, data.getEnabled()));
    }

    private BusinessChatModelApiConfigSnapshot toSnapshot(BusinessChatModelApiConfigData data) {
        BusinessChatModelProvider provider = BusinessChatModelProvider.fromValue(data.getProvider());
        BusinessChatModelPricing.UnitPrice unitPrice =
                BusinessChatModelPricing.configUnitPrice(provider, data.getBaseUrl(), data.getModelName());
        return new BusinessChatModelApiConfigSnapshot(
                data.getId(),
                provider,
                data.getDisplayName(),
                normalizeBaseUrl(data.getBaseUrl()),
                data.getModelName(),
                decodeApiKey(data.getApiKeyCipher()),
                unitPrice.inputTokenUnitPrice(),
                unitPrice.outputTokenUnitPrice(),
                unitPrice.priceUnitTokens(),
                unitPrice.currency());
    }

    private void normalizeSortOrders() {
        List<BusinessChatModelApiConfigData> configList = modelApiConfigMapper.selectList(
                Wrappers.<BusinessChatModelApiConfigData>lambdaQuery()
                        .eq(BusinessChatModelApiConfigData::getStatus, NORMAL_STATUS)
                        .orderByAsc(BusinessChatModelApiConfigData::getSortOrder)
                        .orderByDesc(BusinessChatModelApiConfigData::getEditTime)
                        .orderByDesc(BusinessChatModelApiConfigData::getId));
        for (int index = 0; index < configList.size(); index++) {
            BusinessChatModelApiConfigData data = configList.get(index);
            int expectedSortOrder = (index + 1) * SORT_ORDER_STEP;
            if (!Integer.valueOf(expectedSortOrder).equals(data.getSortOrder())) {
                data.setSortOrder(expectedSortOrder);
                modelApiConfigMapper.updateById(data);
            }
        }
    }

    private LambdaQueryWrapper<BusinessChatModelApiConfigData> availableConfigQuery() {
        return Wrappers.<BusinessChatModelApiConfigData>lambdaQuery()
                .eq(BusinessChatModelApiConfigData::getStatus, NORMAL_STATUS)
                .eq(BusinessChatModelApiConfigData::getEnabled, ENABLED)
                .isNotNull(BusinessChatModelApiConfigData::getApiKeyCipher)
                .ne(BusinessChatModelApiConfigData::getApiKeyCipher, "")
                .isNotNull(BusinessChatModelApiConfigData::getBaseUrl)
                .ne(BusinessChatModelApiConfigData::getBaseUrl, "")
                .isNotNull(BusinessChatModelApiConfigData::getModelName)
                .ne(BusinessChatModelApiConfigData::getModelName, "");
    }

    private List<BusinessChatModelApiConfigData> loadOrderedConfigList() {
        return modelApiConfigMapper.selectList(Wrappers.<BusinessChatModelApiConfigData>lambdaQuery()
                .eq(BusinessChatModelApiConfigData::getStatus, NORMAL_STATUS)
                .orderByAsc(BusinessChatModelApiConfigData::getSortOrder)
                .orderByDesc(BusinessChatModelApiConfigData::getId));
    }

    private int nextSortOrder() {
        BusinessChatModelApiConfigData last = modelApiConfigMapper.selectOne(
                Wrappers.<BusinessChatModelApiConfigData>lambdaQuery()
                        .eq(BusinessChatModelApiConfigData::getStatus, NORMAL_STATUS)
                        .orderByDesc(BusinessChatModelApiConfigData::getSortOrder)
                        .orderByDesc(BusinessChatModelApiConfigData::getId)
                        .last("limit 1"));
        return last == null || last.getSortOrder() == null
                ? SORT_ORDER_STEP
                : last.getSortOrder() + SORT_ORDER_STEP;
    }

    private boolean isAvailable(BusinessChatModelApiConfigData data) {
        return Objects.equals(ENABLED, data.getEnabled())
                && StringUtils.hasText(data.getApiKeyCipher())
                && StringUtils.hasText(data.getBaseUrl())
                && StringUtils.hasText(data.getModelName());
    }

    private Long parseOptionalId(String value) {
        String normalizedValue = BusinessInputValidator.normalizeOptionalText(value);
        return StringUtils.hasText(normalizedValue) ? parseRequiredId(normalizedValue) : null;
    }

    private Long parseRequiredId(String value) {
        String normalizedValue = BusinessInputValidator.normalizeRequiredText(value, "id");
        try {
            return Long.parseLong(normalizedValue);
        } catch (NumberFormatException exception) {
            throw new BaseException(BaseCode.INVALID_PARAMETER, "id must be a valid long integer");
        }
    }

    private String normalizeBaseUrl(String value) {
        String normalizedValue = BusinessInputValidator.normalizeRequiredText(value, "baseUrl");
        while (normalizedValue.endsWith("/")) {
            normalizedValue = normalizedValue.substring(0, normalizedValue.length() - 1);
        }
        if (!StringUtils.hasText(normalizedValue)) {
            throw new BaseException(BaseCode.INVALID_PARAMETER, "baseUrl must not be blank");
        }
        if (normalizedValue.endsWith("/v1")) {
            normalizedValue = normalizedValue.substring(0, normalizedValue.length() - 3);
            if (!StringUtils.hasText(normalizedValue)) {
                throw new BaseException(BaseCode.INVALID_PARAMETER, "baseUrl must include host");
            }
        }
        return normalizedValue;
    }

    private BigDecimal normalizePrice(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private BigDecimal normalizePrice(BigDecimal value, String fieldName) {
        BigDecimal normalizedValue = normalizePrice(value);
        if (normalizedValue.signum() < 0) {
            throw new BaseException(BaseCode.INVALID_PARAMETER, fieldName + " must be greater than or equal to 0");
        }
        return normalizedValue;
    }

    private int normalizePriceUnitTokens(Integer value) {
        int normalizedValue = value == null ? DEFAULT_PRICE_UNIT_TOKENS : value;
        if (normalizedValue <= 0) {
            throw new BaseException(BaseCode.INVALID_PARAMETER, "priceUnitTokens must be greater than 0");
        }
        return normalizedValue;
    }

    private String normalizeCurrency(String value) {
        String normalizedValue = BusinessInputValidator.normalizeOptionalText(value);
        return StringUtils.hasText(normalizedValue) ? normalizedValue : DEFAULT_CURRENCY;
    }

    private String encodeApiKey(String apiKey) {
        try {
            byte[] iv = new byte[GCM_IV_BYTES];
            secureRandom.nextBytes(iv);
            Cipher cipher = Cipher.getInstance(AES_TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, apiKeySecretKey(), new GCMParameterSpec(GCM_TAG_BITS, iv));
            byte[] encryptedBytes = cipher.doFinal(apiKey.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder()
                    .encodeToString(ByteBuffer.allocate(iv.length + encryptedBytes.length)
                            .put(iv)
                            .put(encryptedBytes)
                            .array());
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("failed to encrypt model API key", exception);
        }
    }

    private String decodeApiKey(String apiKeyCipher) {
        try {
            byte[] encryptedPayload = Base64.getDecoder().decode(apiKeyCipher);
            if (encryptedPayload.length <= GCM_IV_BYTES) {
                throw new IllegalStateException("model API key cipher is invalid");
            }
            byte[] iv = Arrays.copyOfRange(encryptedPayload, 0, GCM_IV_BYTES);
            byte[] encryptedBytes = Arrays.copyOfRange(encryptedPayload, GCM_IV_BYTES, encryptedPayload.length);
            Cipher cipher = Cipher.getInstance(AES_TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, apiKeySecretKey(), new GCMParameterSpec(GCM_TAG_BITS, iv));
            return new String(cipher.doFinal(encryptedBytes), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException exception) {
            throw new IllegalStateException("failed to decrypt model API key", exception);
        }
    }

    private SecretKeySpec apiKeySecretKey() {
        String keyBase64 = modelApiConfigProperties.getApiKeyAesKeyBase64();
        if (!StringUtils.hasText(keyBase64)) {
            throw new IllegalStateException(
                    "super-agent.chat.model-api-config.api-key-aes-key-base64 must not be blank");
        }
        byte[] keyBytes;
        try {
            keyBytes = Base64.getDecoder().decode(keyBase64.strip());
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException(
                    "super-agent.chat.model-api-config.api-key-aes-key-base64 must be base64 encoded",
                    exception);
        }
        if (keyBytes.length != 16 && keyBytes.length != 24 && keyBytes.length != 32) {
            throw new IllegalStateException(
                    "super-agent.chat.model-api-config.api-key-aes-key-base64 must decode to 16, 24, or 32 bytes");
        }
        return new SecretKeySpec(keyBytes, "AES");
    }
}
