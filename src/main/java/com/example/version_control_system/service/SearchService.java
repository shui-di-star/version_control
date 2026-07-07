package com.example.version_control_system.service;

import com.example.version_control_system.dto.SearchHit;

import java.util.List;

/** 项目内关键字搜索：实体 name/remark、关系 remark 的模糊匹配。 */
public interface SearchService {

    List<SearchHit> search(Long projectId, String keyword);
}
