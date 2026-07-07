import { useEffect, useState } from 'react';
import { Modal, Select, Table, Tag, Space, Typography } from 'antd';
import { entityApi } from '@/api/entity';
import { assetApi } from '@/api/misc';
import { useProjectStore } from '@/stores/projectStore';
import { useTreeStore } from '@/stores/treeStore';
import { flattenTree } from '@/utils/tree';
import { parseAttributes, parseSchemaFields } from '@/utils/json';
import { STATUS_META } from '@/utils/constants';
import type { AssetVO, EntityVO } from '@/types/api';

interface Props {
  open: boolean;
  onClose: () => void;
}

interface Column {
  entity: EntityVO;
  attrs: Record<string, unknown>;
  assets: AssetVO[];
}

// 迭代对比：选 2+ 节点并排展示属性（差异高亮）+ 产出物元信息。复用 get/assets 接口。
export default function CompareModal({ open, onClose }: Props) {
  const currentProject = useProjectStore((s) => s.currentProject);
  const tree = useTreeStore((s) => s.tree);
  const entityTemplates = useTreeStore((s) => s.entityTemplates);
  const pid = currentProject?.id;

  const [selectedIds, setSelectedIds] = useState<string[]>([]);
  const [cols, setCols] = useState<Column[]>([]);

  const nodeOptions = flattenTree(tree).map((n) => ({ value: n.id, label: n.name }));

  useEffect(() => {
    if (!pid || selectedIds.length === 0) {
      setCols([]);
      return;
    }
    Promise.all(
      selectedIds.map(async (id) => {
        const [entity, assets] = await Promise.all([
          entityApi.get(pid, id),
          assetApi.list(pid, id),
        ]);
        return { entity, attrs: parseAttributes(entity.attributes), assets };
      }),
    ).then(setCols);
  }, [pid, selectedIds]);

  // 收集所有对比列涉及的属性 key（按模板字段并集）。
  const allKeys = Array.from(
    new Set(
      cols.flatMap((c) =>
        parseSchemaFields(
          entityTemplates.find((t) => t.id === c.entity.templateId)?.fieldSchema,
        ).map((f) => f.key),
      ),
    ),
  );

  const labelOf = (key: string) => {
    for (const c of cols) {
      const f = parseSchemaFields(
        entityTemplates.find((t) => t.id === c.entity.templateId)?.fieldSchema,
      ).find((x) => x.key === key);
      if (f) return f.label;
    }
    return key;
  };

  // 某属性行是否存在差异（各列值不全相同）。
  const isDiff = (key: string) => {
    const vals = cols.map((c) => JSON.stringify(c.attrs[key] ?? null));
    return new Set(vals).size > 1;
  };

  const rows = [
    { key: '__name__', label: '名称', render: (c: Column) => c.entity.name, diff: () => new Set(cols.map((c) => c.entity.name)).size > 1 },
    {
      key: '__status__',
      label: '状态',
      render: (c: Column) =>
        c.entity.status ? (
          <Tag color={STATUS_META[c.entity.status].color}>{STATUS_META[c.entity.status].label}</Tag>
        ) : (
          '—'
        ),
      diff: () => new Set(cols.map((c) => c.entity.status ?? '')).size > 1,
    },
    {
      key: '__milestone__',
      label: '里程碑',
      render: (c: Column) => (c.entity.isMilestone === 1 ? '★' : '—'),
      diff: () => new Set(cols.map((c) => c.entity.isMilestone ?? 0)).size > 1,
    },
    ...allKeys.map((key) => ({
      key,
      label: labelOf(key),
      render: (c: Column) => String(c.attrs[key] ?? '—'),
      diff: () => isDiff(key),
    })),
    {
      key: '__assets__',
      label: '产出物',
      render: (c: Column) => (
        <Space direction="vertical" size={0}>
          {c.assets.length === 0 ? '—' : c.assets.map((a) => <span key={a.id}>{a.fileName}</span>)}
        </Space>
      ),
      diff: () => false,
    },
  ];

  const tableColumns = [
    { title: '属性', dataIndex: 'label', width: 120, fixed: 'left' as const },
    ...cols.map((c, i) => ({
      title: c.entity.name,
      key: `col-${i}`,
      render: (_: unknown, row: (typeof rows)[number]) => {
        const diff = row.diff();
        return (
          <span style={diff ? { background: '#fffbe6', padding: '0 4px' } : undefined}>
            {row.render(c)}
          </span>
        );
      },
    })),
  ];

  return (
    <Modal title="迭代对比" open={open} onCancel={onClose} footer={null} width={880}>
      <Select
        mode="multiple"
        style={{ width: '100%', marginBottom: 12 }}
        placeholder="选择 2 个及以上节点进行对比"
        value={selectedIds}
        options={nodeOptions}
        onChange={setSelectedIds}
        optionFilterProp="label"
        showSearch
      />
      {cols.length >= 2 ? (
        <Table
          rowKey="key"
          size="small"
          pagination={false}
          columns={tableColumns}
          dataSource={rows}
          scroll={{ x: true }}
        />
      ) : (
        <Typography.Text type="secondary">请至少选择两个节点。</Typography.Text>
      )}
    </Modal>
  );
}
