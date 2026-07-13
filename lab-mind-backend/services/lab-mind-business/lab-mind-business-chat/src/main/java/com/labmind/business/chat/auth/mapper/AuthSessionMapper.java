package com.labmind.business.chat.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.labmind.business.chat.auth.data.AuthSessionData;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AuthSessionMapper extends BaseMapper<AuthSessionData> {
}
