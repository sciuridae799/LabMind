package com.superagent.business.chat.chatagent.service;

import com.superagent.business.chat.chatagent.dto.BusinessChatModelApiConfigIdRequest;
import com.superagent.business.chat.chatagent.dto.BusinessChatModelApiConfigMoveRequest;
import com.superagent.business.chat.chatagent.dto.BusinessChatModelApiConfigSaveRequest;
import com.superagent.business.chat.chatagent.model.BusinessChatModelApiConfigSnapshot;
import com.superagent.business.chat.chatagent.vo.BusinessChatModelApiConfigVo;
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
