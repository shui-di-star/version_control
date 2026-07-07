package com.example.version_control_system.service;

import com.example.version_control_system.dto.ProjectExport;

/** 项目导出 / 导入服务。 */
public interface ProjectPortService {

    /** 导出项目全量模板/实体/关系/产出物元信息。 */
    ProjectExport export(Long projectId);

    /** 将导出载荷导入到目标项目：一律分配新 id，按旧→新映射重建所有引用。 */
    void importInto(Long projectId, ProjectExport data);
}
