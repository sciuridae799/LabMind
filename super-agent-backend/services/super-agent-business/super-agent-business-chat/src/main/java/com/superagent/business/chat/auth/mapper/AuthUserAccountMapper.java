package com.superagent.business.chat.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.superagent.business.chat.auth.data.AuthUserAccountData;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AuthUserAccountMapper extends BaseMapper<AuthUserAccountData> {
}
