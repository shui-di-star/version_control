package com.example.version_control_system.service;

import com.example.version_control_system.dto.SearchHit;

import java.util.List;

/** 项目内关键字搜索：实体 attributes 指定字段 + 关系 remark 的模糊匹配，可选时间范围过滤。 */
public interface SearchService {

    List<SearchHit> search(Long projectId, String keyword, String startDate, String endDate);
}
