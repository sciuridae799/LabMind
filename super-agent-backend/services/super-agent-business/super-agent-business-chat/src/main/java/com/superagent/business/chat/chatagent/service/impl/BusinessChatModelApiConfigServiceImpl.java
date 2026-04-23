package com.superagent.business.chat.chatagent.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.superagent.business.chat.chatagent.data.BusinessChatModelApiConfigData;
import com.superagent.business.chat.chatagent.dto.BusinessChatModelApiConfigIdRequest;
import com.superagent.business.chat.chatagent.dto.BusinessChatModelApiConfigSaveRequest;
import com.superagent.business.chat.chatagent.mapper.BusinessChatModelApiConfigMapper;
import com.superagent.business.chat.chatagent.model.BusinessChatModelApiConfigSnapshot;
import com.superagent.business.chat.chatagent.model.BusinessChatModelProvider;
import com.superagent.business.chat.chatagent.service.BusinessChatErrorCode;
import com.superagent.business.chat.chatagent.service.BusinessChatModelApiConfigService;
import com.superagent.business.chat.chatagent.vo.BusinessChatModelApiConfigVo;
import com.superagent.common.frame.enums.BaseCode;
import com.superagent.common.frame.exception.BaseException;
import com.superagent.idgenerator.toolkit.SnowflakeIdGenerator;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class BusinessChatModelApiConfigServiceImpl implements BusinessChatModelApiConfigService {

    private static final int NORMAL_STATUS = 1;

    private static final int DELETED_STATUS = 0;

    private static final int ENABLED = 1;

    private static final int DISABLED = 0;

    private final BusinessChatModelApiConfigMapper modelApiConfigMapper;

    private final SnowflakeIdGenerator snowflakeIdGenerator;

    @Override
    public List<BusinessChatModelApiConfigVo> listAll() {
        return modelApiConfigMapper.selectList(Wrappers.<BusinessChatModelApiConfigData>lambdaQuery()
                        .eq(BusinessChatModelApiConfigData::getStatus, NORMAL_STATUS)
                        .orderByDesc(BusinessChatModelApiConfigData::getEditTime)
                        .orderByDesc(BusinessChatModelApiConfigData::getId))
                .stream()
                .map(this::toVo)
                .toList();
    }

    @Override
    public List<BusinessChatModelApiConfigVo> listAvailable() {
        return modelApiConfigMapper.selectList(Wrappers.<BusinessChatModelApiConfigData>lambdaQuery()
                        .eq(BusinessChatModelApiConfigData::getStatus, NORMAL_STATUS)
                        .eq(BusinessChatModelApiConfigData::getEnabled, ENABLED)
                        .isNotNull(BusinessChatModelApiConfigData::getApiKeyCipher)
                        .ne(BusinessChatModelApiConfigData::getApiKeyCipher, "")
                        .isNotNull(BusinessChatModelApiConfigData::getBaseUrl)
                        .ne(BusinessChatModelApiConfigData::getBaseUrl, "")
                        .isNotNull(BusinessChatModelApiConfigData::getModelName)
                        .ne(BusinessChatModelApiConfigData::getModelName, "")
                        .orderByDesc(BusinessChatModelApiConfigData::getEditTime)
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
            data.setId(snowflakeIdGenerator.nextId());
            data.setStatus(NORMAL_STATUS);
        }
        data.setProvider(provider.getValue());
        data.setDisplayName(normalizeRequiredText(request.getDisplayName(), "displayName"));
        data.setBaseUrl(normalizeBaseUrl(request.getBaseUrl()));
        data.setModelName(normalizeRequiredText(request.getModelName(), "modelName"));
        data.setEnabled(Boolean.TRUE.equals(request.getEnabled()) ? ENABLED : DISABLED);
        String normalizedApiKey = normalizeOptionalText(request.getApiKey());
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
    public BusinessChatModelApiConfigSnapshot getRequiredAvailableSnapshot(String id) {
        BusinessChatModelApiConfigData data = loadExisting(parseRequiredId(id));
        if (!isAvailable(data)) {
            throw new BaseException(
                    BusinessChatErrorCode.CHAT_MODEL_CONFIG_UNAVAILABLE,
                    "model config is unavailable: " + id);
        }
        return new BusinessChatModelApiConfigSnapshot(
                data.getId(),
                BusinessChatModelProvider.fromValue(data.getProvider()),
                data.getDisplayName(),
                normalizeBaseUrl(data.getBaseUrl()),
                data.getModelName(),
                decodeApiKey(data.getApiKeyCipher()));
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
        return new BusinessChatModelApiConfigVo(
                String.valueOf(data.getId()),
                data.getProvider(),
                data.getDisplayName(),
                data.getBaseUrl(),
                data.getModelName(),
                StringUtils.hasText(data.getApiKeyCipher()),
                Integer.valueOf(ENABLED).equals(data.getEnabled()));
    }

    private boolean isAvailable(BusinessChatModelApiConfigData data) {
        return Integer.valueOf(ENABLED).equals(data.getEnabled())
                && StringUtils.hasText(data.getApiKeyCipher())
                && StringUtils.hasText(data.getBaseUrl())
                && StringUtils.hasText(data.getModelName());
    }

    private Long parseOptionalId(String value) {
        String normalizedValue = normalizeOptionalText(value);
        return StringUtils.hasText(normalizedValue) ? parseRequiredId(normalizedValue) : null;
    }

    private Long parseRequiredId(String value) {
        String normalizedValue = normalizeRequiredText(value, "id");
        try {
            return Long.parseLong(normalizedValue);
        } catch (NumberFormatException exception) {
            throw new BaseException(BaseCode.INVALID_PARAMETER, "id must be a valid long integer");
        }
    }

    private String normalizeRequiredText(String value, String fieldName) {
        String normalizedValue = normalizeOptionalText(value);
        if (!StringUtils.hasText(normalizedValue)) {
            throw new BaseException(BaseCode.INVALID_PARAMETER, fieldName + " must not be blank");
        }
        return normalizedValue;
    }

    private String normalizeBaseUrl(String value) {
        String normalizedValue = normalizeRequiredText(value, "baseUrl");
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

    private String normalizeOptionalText(String value) {
        return value == null ? null : value.strip();
    }

    private String encodeApiKey(String apiKey) {
        return Base64.getEncoder().encodeToString(apiKey.getBytes(StandardCharsets.UTF_8));
    }

    private String decodeApiKey(String apiKeyCipher) {
        return new String(Base64.getDecoder().decode(apiKeyCipher), StandardCharsets.UTF_8);
    }
}
