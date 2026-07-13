package com.labmind.business.chat.auth.data;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.labmind.common.web.database.BaseTableData;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("lab_mind_user_workspace")
public class AuthUserWorkspaceData extends BaseTableData {

    @TableId(type = IdType.INPUT)
    private Long id;

    private Long userId;

    private String workspaceId;

    private Integer status;
}
