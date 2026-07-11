package com.example.version_control_system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.version_control_system.dto.SearchHit;
import com.example.version_control_system.entity.Relation;
import com.example.version_control_system.entity.SimEntity;
import com.example.version_control_system.mapper.RelationMapper;
import com.example.version_control_system.mapper.SimEntityMapper;
import com.example.version_control_system.service.SearchService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 搜索实现：对 t_entity.attributes 中指定字段（card_name/owner/result_conclusion/other_notes）
 * + t_relation.remark 做 LIKE 匹配，可选时间范围过滤。
 */
@Service
public class SearchServiceImpl implements SearchService {

    private final SimEntityMapper entityMapper;
    private final RelationMapper relationMapper;

    /** attributes 中参与搜索的字段 key。 */
    private static final List<String> SEARCH_ATTR_KEYS = List.of(
            "card_name", "owner", "result_conclusion", "other_notes");

    public SearchServiceImpl(SimEntityMapper entityMapper, RelationMapper relationMapper) {
        this.entityMapper = entityMapper;
        this.relationMapper = relationMapper;
    }

    @Override
    public List<SearchHit> search(Long projectId, String keyword, String startDate, String endDate) {
        List<SearchHit> hits = new ArrayList<>();
        if (keyword == null || keyword.isBlank()) {
            return hits;
        }

        // 实体：通过 mapper 自定义 SQL 搜索 attributes 中指定字段
        List<SimEntity> entities = entityMapper.searchByKeywordAndDate(projectId, keyword, startDate, endDate);
        for (SimEntity e : entities) {
            // 检查 attributes 中的字段
            String attrs = e.getAttributes();
            if (attrs != null) {
                for (String key : SEARCH_ATTR_KEYS) {
                    String value = extractJsonValue(attrs, key);
                    if (value != null && value.contains(keyword)) {
                        hits.add(new SearchHit("ENTITY", e.getId(), null, null, null, key, value));
                        break; // 每个实体的 attributes 只命中一次避免重复
                    }
                }
            }
        }

        // 语义关系备注
        List<Relation> relations = relationMapper.selectList(new LambdaQueryWrapper<Relation>()
                .eq(Relation::getProjectId, projectId)
                .like(Relation::getRemark, keyword));
        for (Relation r : relations) {
            hits.add(new SearchHit("RELATION", null, r.getId(),
                    r.getFromEntityId(), r.getToEntityId(), "remark", r.getRemark()));
        }
        return hits;
    }

    /** 简单从 JSON 字符串中提取指定 key 的值（避免引入额外解析开销）。 */
    private static String extractJsonValue(String json, String key) {
        // 查找 "key":"value" 或 "key": "value" 模式
        String pattern = "\"" + key + "\"";
        int idx = json.indexOf(pattern);
        if (idx < 0) return null;
        int colonIdx = json.indexOf(':', idx + pattern.length());
        if (colonIdx < 0) return null;
        // 跳过空白
        int start = colonIdx + 1;
        while (start < json.length() && json.charAt(start) == ' ') start++;
        if (start >= json.length()) return null;
        char ch = json.charAt(start);
        if (ch == '"') {
            // 字符串值
            int end = json.indexOf('"', start + 1);
            return end > 0 ? json.substring(start + 1, end) : null;
        } else if (ch == 'n') {
            return null; // null
        } else {
            // 数字或其他
            int end = start;
            while (end < json.length() && json.charAt(end) != ',' && json.charAt(end) != '}') end++;
            return json.substring(start, end).trim();
        }
    }
}
