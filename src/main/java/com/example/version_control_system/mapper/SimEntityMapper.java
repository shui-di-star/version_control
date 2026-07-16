package com.example.version_control_system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.version_control_system.dto.EntityTreeRow;
import com.example.version_control_system.entity.SimEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface SimEntityMapper extends BaseMapper<SimEntity> {

    /** 递归 CTE：从项目根节点向下查出整树平铺行（含 depth），排除软删。 */
    @Select("""
            WITH RECURSIVE entity_tree AS (
                SELECT id, parent_id, name, template_id, status, is_milestone, 0 AS depth, parent_relation_template_id, parent_relation_remark, attributes
                FROM t_entity
                WHERE project_id = #{projectId} AND parent_id IS NULL AND deleted = 0
              UNION ALL
                SELECT e.id, e.parent_id, e.name, e.template_id, e.status, e.is_milestone, t.depth + 1, e.parent_relation_template_id, e.parent_relation_remark, e.attributes
                FROM t_entity e
                JOIN entity_tree t ON e.parent_id = t.id
                WHERE e.deleted = 0
            )
            SELECT * FROM entity_tree
            """)
    List<EntityTreeRow> selectTree(@Param("projectId") Long projectId);

    /** 递归 CTE：查出以 rootId 为根的子树全部实体 id（含自身），排除软删。 */
    @Select("""
            WITH RECURSIVE subtree AS (
                SELECT id FROM t_entity WHERE id = #{rootId} AND deleted = 0
              UNION ALL
                SELECT e.id FROM t_entity e
                JOIN subtree s ON e.parent_id = s.id
                WHERE e.deleted = 0
            )
            SELECT id FROM subtree
            """)
    List<Long> selectSubtreeIds(@Param("rootId") Long rootId);

    /** 递归 CTE：从当前节点向上查到根，返回自当前至根的平铺行（顺序由 Service 反转为根→当前）。 */
    @Select("""
            WITH RECURSIVE up_path AS (
                SELECT id, parent_id, name, template_id, status, is_milestone, 0 AS depth, parent_relation_template_id, parent_relation_remark, attributes
                FROM t_entity WHERE id = #{id} AND deleted = 0
              UNION ALL
                SELECT e.id, e.parent_id, e.name, e.template_id, e.status, e.is_milestone, p.depth + 1, e.parent_relation_template_id, e.parent_relation_remark, e.attributes
                FROM t_entity e
                JOIN up_path p ON e.id = p.parent_id
                WHERE e.deleted = 0
            )
            SELECT * FROM up_path
            """)
    List<EntityTreeRow> selectPathToRoot(@Param("id") Long id);

    /** 项目内某状态的实体数（status 为 null 时统计无状态实体），排除软删。 */
    @Select("""
            SELECT COUNT(*) FROM t_entity
            WHERE project_id = #{projectId} AND deleted = 0
              AND (#{status} IS NULL AND status IS NULL OR status = #{status})
            """)
    long countByStatus(@Param("projectId") Long projectId, @Param("status") String status);

    /**
     * 项目内指定 NUMBER 属性字段的最大值（决策 4）。用 JSON_EXTRACT 提 key，
     * key 由 #{key} 参数化绑定进 JSON path，避免注入。无匹配返回 null。
     * <p>JSON path 成员名用双引号包裹（{@code $."key"}）：未加引号的 path 仅允许合法标识符，
     * 含中文/空格/数字开头等的 key 会报 "Invalid JSON path expression"。加引号后任意 key 均合法。</p>
     */
    @Select("""
            SELECT MAX(CAST(JSON_UNQUOTE(JSON_EXTRACT(attributes, CONCAT('$."', #{key}, '"'))) AS DECIMAL(30,10)))
            FROM t_entity
            WHERE project_id = #{projectId} AND deleted = 0
              AND JSON_EXTRACT(attributes, CONCAT('$."', #{key}, '"')) IS NOT NULL
            """)
    Double maxNumberValue(@Param("projectId") Long projectId, @Param("key") String key);

    /**
     * 搜索实体 attributes 中指定字段值包含关键字的实体，或 remark 包含关键字，可选时间范围过滤。
     * 搜索字段：card_name、owner、conclusion_suggestion、remark。
     */
    @Select("""
            <script>
            SELECT * FROM t_entity
            WHERE project_id = #{projectId} AND deleted = 0
              AND (
                JSON_UNQUOTE(JSON_EXTRACT(attributes, '$."card_name"')) LIKE CONCAT('%', #{keyword}, '%')
                OR JSON_UNQUOTE(JSON_EXTRACT(attributes, '$."owner"')) LIKE CONCAT('%', #{keyword}, '%')
                OR JSON_UNQUOTE(JSON_EXTRACT(attributes, '$."conclusion_suggestion"')) LIKE CONCAT('%', #{keyword}, '%')
                OR remark LIKE CONCAT('%', #{keyword}, '%')
              )
              <if test="startDate != null">
                AND created_at &gt;= #{startDate}
              </if>
              <if test="endDate != null">
                AND created_at &lt;= #{endDate}
              </if>
            </script>
            """)
    List<SimEntity> searchByKeywordAndDate(@Param("projectId") Long projectId,
                                           @Param("keyword") String keyword,
                                           @Param("startDate") String startDate,
                                           @Param("endDate") String endDate);
}
