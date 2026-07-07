package com.example.version_control_system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.version_control_system.dto.OperationLogVO;
import com.example.version_control_system.entity.OperationLog;
import com.example.version_control_system.mapper.OperationLogMapper;
import com.example.version_control_system.service.OperationLogService;
import org.springframework.stereotype.Service;

import java.util.List;

/** 操作日志查询实现：按项目倒序返回。 */
@Service
public class OperationLogServiceImpl implements OperationLogService {

    private final OperationLogMapper operationLogMapper;

    public OperationLogServiceImpl(OperationLogMapper operationLogMapper) {
        this.operationLogMapper = operationLogMapper;
    }

    @Override
    public List<OperationLogVO> list(Long projectId) {
        return operationLogMapper.selectList(new LambdaQueryWrapper<OperationLog>()
                        .eq(OperationLog::getProjectId, projectId)
                        .orderByDesc(OperationLog::getId))
                .stream().map(OperationLogVO::from).toList();
    }
}
