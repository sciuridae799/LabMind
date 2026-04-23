package com.superagent.business.chat.chatagent.data;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.superagent.common.web.database.BaseTableData;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("super_agent_model_api_config")
public class BusinessChatModelApiConfigData extends BaseTableData {

    @TableId(type = IdType.INPUT)
    private Long id;

    private String provider;

    private String displayName;

    private String baseUrl;

    private String modelName;

    private String apiKeyCipher;

    private Integer enabled;

    private Integer status;
}
