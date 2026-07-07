package com.example.version_control_system.service;

import com.example.version_control_system.dto.OperationLogVO;

import java.util.List;

/** 操作日志查询服务。 */
public interface OperationLogService {

    /** 按项目倒序（最新在前）查询操作日志。 */
    List<OperationLogVO> list(Long projectId);
}
