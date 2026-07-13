package com.labmind.business.chat.auth.data;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.labmind.common.web.database.BaseTableData;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("lab_mind_user_account")
public class AuthUserAccountData extends BaseTableData {

    @TableId(type = IdType.INPUT)
    private Long id;

    private String account;

    private String displayName;

    private String passwordHash;

    private String passwordSalt;

    private String role;

    private String workspaceId;

    private Integer enabled;

    private Integer status;
}
