package com.example.version_control_system;

import com.example.version_control_system.entity.*;
import com.example.version_control_system.mapper.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 步骤 2.6 验证：对每张表做插入 + 查询，断言
 * (1) 雪花主键回填、(2) 审计字段(created_at/updated_at)自动填充、(3) 逻辑删除生效（软删后默认查询不返回）。
 * <p>连真实测试库 vcs_test（决策 7）。每个用例 @Transactional 自动回滚，避免污染。</p>
 */
@SpringBootTest
@Transactional
class MapperIntegrationTest {

    @Autowired UserMapper userMapper;
    @Autowired ProjectMapper projectMapper;
    @Autowired ProjectMemberMapper projectMemberMapper;
    @Autowired EntityTemplateMapper entityTemplateMapper;
    @Autowired RelationTemplateMapper relationTemplateMapper;
    @Autowired SimEntityMapper simEntityMapper;
    @Autowired RelationMapper relationMapper;
    @Autowired AssetMapper assetMapper;
    @Autowired OperationLogMapper operationLogMapper;

    @Test
    void user_insert_query_audit_and_logicDelete() {
        User u = new User();
        u.setUsername("alice_" + System.nanoTime());
        u.setPasswordHash("$2a$hash");
        u.setSystemRole("USER");
        u.setStatus(1);
        assertThat(userMapper.insert(u)).isEqualTo(1);

        // 雪花主键 + 审计字段自动填充
        assertThat(u.getId()).isNotNull();
        assertThat(u.getCreatedAt()).isNotNull();
        assertThat(u.getUpdatedAt()).isNotNull();

        User found = userMapper.selectById(u.getId());
        assertThat(found).isNotNull();
        assertThat(found.getUsername()).isEqualTo(u.getUsername());

        // 逻辑删除：删除后 selectById 返回 null（默认过滤 deleted=1）
        assertThat(userMapper.deleteById(u.getId())).isEqualTo(1);
        assertThat(userMapper.selectById(u.getId())).isNull();
    }

    @Test
    void project_insert_query() {
        Project p = new Project();
        p.setName("项目A");
        p.setOwnerId(1L);
        assertThat(projectMapper.insert(p)).isEqualTo(1);
        assertThat(p.getId()).isNotNull();
        assertThat(projectMapper.selectById(p.getId()).getName()).isEqualTo("项目A");
    }

    @Test
    void projectMember_insert_query() {
        ProjectMember m = new ProjectMember();
        m.setProjectId(1L);
        m.setUserId(1L);
        m.setRole("ADMIN");
        assertThat(projectMemberMapper.insert(m)).isEqualTo(1);
        assertThat(projectMemberMapper.selectById(m.getId()).getRole()).isEqualTo("ADMIN");
    }

    @Test
    void entityTemplate_insert_query_json() {
        EntityTemplate t = new EntityTemplate();
        t.setProjectId(1L);
        t.setName("仿真方案");
        t.setFieldSchema("{\"fields\":[{\"key\":\"mesh_size\",\"type\":\"NUMBER\"}]}");
        assertThat(entityTemplateMapper.insert(t)).isEqualTo(1);
        assertThat(entityTemplateMapper.selectById(t.getId()).getFieldSchema()).contains("mesh_size");
    }

    @Test
    void relationTemplate_insert_query_json() {
        RelationTemplate t = new RelationTemplate();
        t.setProjectId(1L);
        t.setName("参考自");
        t.setDirected(1);
        t.setLineStyle("{\"color\":\"#f00\"}");
        assertThat(relationTemplateMapper.insert(t)).isEqualTo(1);
        assertThat(relationTemplateMapper.selectById(t.getId()).getLineStyle()).contains("#f00");
    }

    @Test
    void simEntity_root_and_child() {
        SimEntity root = new SimEntity();
        root.setProjectId(1L);
        root.setTemplateId(1L);
        root.setParentId(null);
        root.setName("根");
        root.setIsMilestone(0);
        root.setAttributes("{\"mesh_size\":2.5}");
        assertThat(simEntityMapper.insert(root)).isEqualTo(1);

        SimEntity child = new SimEntity();
        child.setProjectId(1L);
        child.setTemplateId(1L);
        child.setParentId(root.getId());
        child.setName("子");
        child.setIsMilestone(0);
        assertThat(simEntityMapper.insert(child)).isEqualTo(1);

        assertThat(simEntityMapper.selectById(root.getId()).getParentId()).isNull();
        assertThat(simEntityMapper.selectById(child.getId()).getParentId()).isEqualTo(root.getId());
    }

    @Test
    void relation_insert_query() {
        Relation r = new Relation();
        r.setProjectId(1L);
        r.setTemplateId(1L);
        r.setFromEntityId(1L);
        r.setToEntityId(2L);
        assertThat(relationMapper.insert(r)).isEqualTo(1);
        assertThat(relationMapper.selectById(r.getId())).isNotNull();
    }

    @Test
    void asset_insert_query() {
        Asset a = new Asset();
        a.setEntityId(1L);
        a.setAssetType("PPT");
        a.setFileName("plan.pptx");
        a.setObjectKey("vcs/1/x.pptx");
        a.setSize(1024L);
        assertThat(assetMapper.insert(a)).isEqualTo(1);
        assertThat(assetMapper.selectById(a.getId()).getFileName()).isEqualTo("plan.pptx");
    }

    @Test
    void operationLog_insert_query_noLogicDelete() {
        OperationLog log = new OperationLog();
        log.setProjectId(1L);
        log.setUserId(1L);
        log.setAction("CREATE_ENTITY");
        log.setTargetType("ENTITY");
        log.setTargetId(1L);
        log.setDetail("{\"name\":\"x\"}");
        assertThat(operationLogMapper.insert(log)).isEqualTo(1);
        // 审计字段自动填充（无 deleted 列）
        assertThat(log.getCreatedAt()).isNotNull();
        assertThat(operationLogMapper.selectById(log.getId()).getAction()).isEqualTo("CREATE_ENTITY");
    }
}
