import { Select, Checkbox, Space, Button, Typography } from 'antd';
import { useTreeStore } from '@/stores/treeStore';
import { STATUS_OPTIONS } from '@/utils/constants';
import type { EntityStatus } from '@/types/api';

// 过滤器：状态/实体类型/关系类型/里程碑，多条件组合。前端在全树上显隐，不请求后端。
export default function FilterPanel() {
  const filter = useTreeStore((s) => s.filter);
  const setFilter = useTreeStore((s) => s.setFilter);
  const resetFilter = useTreeStore((s) => s.resetFilter);
  const entityTemplates = useTreeStore((s) => s.entityTemplates);
  const relationTemplates = useTreeStore((s) => s.relationTemplates);

  return (
    <Space direction="vertical" style={{ width: '100%' }} size="small">
      <Typography.Text type="secondary">过滤器</Typography.Text>
      <Select
        mode="multiple"
        size="small"
        style={{ width: '100%' }}
        placeholder="状态"
        allowClear
        value={filter.statuses}
        options={STATUS_OPTIONS}
        onChange={(v) => setFilter({ statuses: v as EntityStatus[] })}
      />
      <Select
        mode="multiple"
        size="small"
        style={{ width: '100%' }}
        placeholder="实体类型"
        allowClear
        value={filter.templateIds}
        options={entityTemplates.map((t) => ({ value: t.id, label: t.name }))}
        onChange={(v) => setFilter({ templateIds: v })}
      />
      <Select
        mode="multiple"
        size="small"
        style={{ width: '100%' }}
        placeholder="关系类型"
        allowClear
        value={filter.relationTemplateIds}
        options={relationTemplates.map((t) => ({ value: t.id, label: t.name }))}
        onChange={(v) => setFilter({ relationTemplateIds: v })}
      />
      <Checkbox
        checked={filter.milestoneOnly}
        onChange={(e) => setFilter({ milestoneOnly: e.target.checked })}
      >
        仅里程碑
      </Checkbox>
      <Button size="small" block onClick={resetFilter}>
        清除筛选
      </Button>
    </Space>
  );
}
