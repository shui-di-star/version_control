package com.example.version_control_system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.version_control_system.dto.SearchHit;
import com.example.version_control_system.entity.EdgeRemark;
import com.example.version_control_system.entity.SimEntity;
import com.example.version_control_system.mapper.EdgeRemarkMapper;
import com.example.version_control_system.mapper.SimEntityMapper;
import com.example.version_control_system.service.SearchService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 搜索实现：对 t_entity.attributes 中指定字段（card_name/owner/conclusion_suggestion）
 * + t_entity.remark + t_edge_remark.content 做 LIKE 匹配，可选时间范围过滤。
 */
@Service
public class SearchServiceImpl implements SearchService {

    private final SimEntityMapper entityMapper;
    private final EdgeRemarkMapper edgeRemarkMapper;

    /** attributes 中参与搜索的字段 key。 */
    private static final List<String> SEARCH_ATTR_KEYS = List.of(
            "card_name", "owner", "conclusion_suggestion");

    public SearchServiceImpl(SimEntityMapper entityMapper, EdgeRemarkMapper edgeRemarkMapper) {
        this.entityMapper = entityMapper;
        this.edgeRemarkMapper = edgeRemarkMapper;
    }

    @Override
    public List<SearchHit> search(Long projectId, String keyword, String startDate, String endDate) {
        List<SearchHit> hits = new ArrayList<>();
        if (keyword == null || keyword.isBlank()) {
            return hits;
        }

        // 实体：搜索 attributes 中指定字段 + remark 字段
        List<SimEntity> entities = entityMapper.searchByKeywordAndDate(projectId, keyword, startDate, endDate);
        for (SimEntity e : entities) {
            // 先检查 remark
            if (e.getRemark() != null && e.getRemark().contains(keyword)) {
                hits.add(new SearchHit("ENTITY", e.getId(), null, null, null, "remark", e.getRemark()));
                continue;
            }
            // 检查 attributes 中的字段
            String attrs = e.getAttributes();
            if (attrs != null) {
                for (String key : SEARCH_ATTR_KEYS) {
                    String value = extractJsonValue(attrs, key);
                    if (value != null && value.contains(keyword)) {
                        hits.add(new SearchHit("ENTITY", e.getId(), null, null, null, key, value));
                        break;
                    }
                }
            }
        }

        // 父子关系备注（t_edge_remark.content）
        List<EdgeRemark> edgeRemarks = edgeRemarkMapper.selectList(new LambdaQueryWrapper<EdgeRemark>()
                .eq(EdgeRemark::getProjectId, projectId)
                .like(EdgeRemark::getContent, keyword));
        for (EdgeRemark er : edgeRemarks) {
            hits.add(new SearchHit("PARENT_RELATION", er.getEntityId(), null, null, null,
                    "edge_remark", er.getContent()));
        }

        return hits;
    }

    /** 简单从 JSON 字符串中提取指定 key 的值（避免引入额外解析开销）。 */
    private static String extractJsonValue(String json, String key) {
        String pattern = "\"" + key + "\"";
        int idx = json.indexOf(pattern);
        if (idx < 0) return null;
        int colonIdx = json.indexOf(':', idx + pattern.length());
        if (colonIdx < 0) return null;
        int start = colonIdx + 1;
        while (start < json.length() && json.charAt(start) == ' ') start++;
        if (start >= json.length()) return null;
        char ch = json.charAt(start);
        if (ch == '"') {
            int end = json.indexOf('"', start + 1);
            return end > 0 ? json.substring(start + 1, end) : null;
        } else if (ch == 'n') {
            return null;
        } else {
            int end = start;
            while (end < json.length() && json.charAt(end) != ',' && json.charAt(end) != '}') end++;
            return json.substring(start, end).trim();
        }
    }
}
