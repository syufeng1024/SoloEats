package com.soloeats.message.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.soloeats.message.domain.entity.PushLog;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface PushLogMapper extends BaseMapper<PushLog> {
}
