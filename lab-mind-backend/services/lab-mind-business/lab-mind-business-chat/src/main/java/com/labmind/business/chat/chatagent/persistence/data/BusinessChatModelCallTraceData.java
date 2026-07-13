package com.labmind.business.chat.chatagent.persistence.data;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.labmind.common.web.database.BaseTableData;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("lab_mind_chat_model_call_trace")
public class BusinessChatModelCallTraceData extends BaseTableData {

    @TableId(type = IdType.INPUT)
    private Long id;

    private String dialogueCode;

    private Long exchangeId;

    private String traceId;

    private String stageCode;

    private String stageName;

    private String provider;

    private String baseUrl;

    private String modelName;

    private String callType;

    private Integer inputTokens;

    private Integer outputTokens;

    private Integer totalTokens;

    private BigDecimal inputTokenUnitPrice;

    private BigDecimal outputTokenUnitPrice;

    private Integer priceUnitTokens;

    private String currency;

    private BigDecimal estimatedCost;

    private Integer callState;

    private LocalDateTime startTime;

    private LocalDateTime endTime;

    private Long durationMs;

    private String errorMessage;

    private Integer status;
}
