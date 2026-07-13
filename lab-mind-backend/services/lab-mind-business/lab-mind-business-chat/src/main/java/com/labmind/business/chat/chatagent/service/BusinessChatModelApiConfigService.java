package com.labmind.business.chat.chatagent.service;

import com.labmind.business.chat.chatagent.api.dto.BusinessChatModelApiConfigIdRequest;
import com.labmind.business.chat.chatagent.api.dto.BusinessChatModelApiConfigMoveRequest;
import com.labmind.business.chat.chatagent.api.dto.BusinessChatModelApiConfigSaveRequest;
import com.labmind.business.chat.chatagent.execution.model.BusinessChatModelApiConfigSnapshot;
import com.labmind.business.chat.chatagent.api.vo.BusinessChatModelApiConfigVo;
import java.util.List;

public interface BusinessChatModelApiConfigService {

    List<BusinessChatModelApiConfigVo> listAll();

    List<BusinessChatModelApiConfigVo> listAvailable();

    BusinessChatModelApiConfigVo save(BusinessChatModelApiConfigSaveRequest request);

    void delete(BusinessChatModelApiConfigIdRequest request);

    void clearApiKey(BusinessChatModelApiConfigIdRequest request);

    void move(BusinessChatModelApiConfigMoveRequest request);

    BusinessChatModelApiConfigSnapshot getRequiredAvailableSnapshot(String id);

    BusinessChatModelApiConfigSnapshot getLatestAvailableSnapshot();
}
