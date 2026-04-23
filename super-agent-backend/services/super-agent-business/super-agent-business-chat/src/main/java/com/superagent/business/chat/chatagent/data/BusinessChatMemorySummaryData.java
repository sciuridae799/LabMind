package com.superagent.business.chat.chatagent.data;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.superagent.common.web.database.BaseTableData;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("super_agent_chat_memory_summary")
public class BusinessChatMemorySummaryData extends BaseTableData {

    @TableId(type = IdType.INPUT)
    private Long id;

    private String dialogueCode;

    private Long coveredExchangeId;

    private Integer coveredExchangeCount;

    private Integer compressionCount;

    private Integer summaryVersion;

    private String summaryText;

    private String summaryJson;

    private LocalDateTime lastSourceEditTime;

    private Integer status;
}
