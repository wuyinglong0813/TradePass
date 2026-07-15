package com.tradepass.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tradepass.entity.AuthSession;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AuthSessionMapper extends BaseMapper<AuthSession> {
}
