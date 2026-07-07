package com.example.version_control_system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.version_control_system.entity.OperationLog;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface OperationLogMapper extends BaseMapper<OperationLog> {
}
