package com.labmind.business.chat.chatagent.persistence.data;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.labmind.common.web.database.BaseTableData;
import java.math.BigDecimal;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("lab_mind_model_api_config")
public class BusinessChatModelApiConfigData extends BaseTableData {

    @TableId(type = IdType.INPUT)
    private Long id;

    private String provider;

    private String displayName;

    private String baseUrl;

    private String modelName;

    private String apiKeyCipher;

    private BigDecimal inputTokenUnitPrice;

    private BigDecimal outputTokenUnitPrice;

    private Integer priceUnitTokens;

    private String currency;

    private Integer enabled;

    private Integer sortOrder;

    private Integer status;
}
