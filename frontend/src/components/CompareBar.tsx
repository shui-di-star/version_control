import { useEffect, useState } from 'react';
import { Button, Table, Tag, Space } from 'antd';
import { CopyOutlined, CloseOutlined } from '@ant-design/icons';
import { entityApi } from '@/api/entity';
import { useProjectStore } from '@/stores/projectStore';
import { useTreeStore } from '@/stores/treeStore';
import { parseAttributes, parseSchemaFields } from '@/utils/json';
import { STATUS_META } from '@/utils/constants';
import type { EntityVO } from '@/types/api';

interface Column {
  entity: EntityVO;
  attrs: Record<string, unknown>;
}

/**
 * 底部对比框：仅在有选中对比卡片时从下方弹出，替代原 CompareModal。
 */
export default function CompareBar() {
  const currentProject = useProjectStore((s) => s.currentProject);
  const entityTemplates = useTreeStore((s) => s.entityTemplates);
  const compareIds = useTreeStore((s) => s.compareIds);
  const clearCompare = useTreeStore((s) => s.clearCompare);
  const pid = currentProject?.id;

  const [cols, setCols] = useState<Column[]>([]);

  useEffect(() => {
    if (!pid || compareIds.length === 0) {
      setCols([]);
      return;
    }
    Promise.all(
      compareIds.map(async (id) => {
        const entity = await entityApi.get(pid, id);
        return { entity, attrs: parseAttributes(entity.attributes) };
      }),
    ).then(setCols);
  }, [pid, compareIds]);

  if (compareIds.length === 0) return null;

  // 收集所有对比列涉及的属性 key（按模板字段并集，仅含 compareInCard=true 的字段）。
  const allKeys = Array.from(
    new Set(
      cols.flatMap((c) =>
        parseSchemaFields(
          entityTemplates.find((t) => t.id === c.entity.templateId)?.fieldSchema,
        )
          .filter((f) => f.compareInCard)
          .map((f) => f.key),
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

  const isDiff = (key: string) => {
    const vals = cols.map((c) => JSON.stringify(c.attrs[key] ?? null));
    return new Set(vals).size > 1;
  };

  const rows = [
    { key: '__name__', label: '名称', render: (c: Column) => c.entity.name, copyText: (c: Column) => c.entity.name, diff: () => new Set(cols.map((c) => c.entity.name)).size > 1 },
    {
      key: '__status__', label: '状态',
      render: (c: Column) => c.entity.status
        ? <Tag color={STATUS_META[c.entity.status].color}>{STATUS_META[c.entity.status].label}</Tag>
        : '—',
      copyText: (c: Column) => c.entity.status ? (STATUS_META[c.entity.status]?.label ?? '') : '—',
      diff: () => new Set(cols.map((c) => c.entity.status ?? '')).size > 1,
    },
    ...allKeys.map((key) => ({
      key, label: labelOf(key),
      render: (c: Column) => String(c.attrs[key] ?? '—'),
      copyText: (c: Column) => String(c.attrs[key] ?? '—'),
      diff: () => isDiff(key),
    })),
  ];

  const tableColumns = [
    { title: '属性', dataIndex: 'label', width: 120, fixed: 'left' as const },
    ...cols.map((c, i) => ({
      title: c.entity.name,
      key: `col-${i}`,
      render: (_: unknown, row: (typeof rows)[number]) => {
        const diff = row.diff();
        return (
          <span style={diff ? { background: '#fff1d6', padding: '0 4px', borderRadius: 3 } : undefined}>
            {row.render(c)}
          </span>
        );
      },
    })),
  ];

  return (
    <div
      style={{
        position: 'fixed',
        bottom: 0,
        left: 0,
        right: 0,
        maxHeight: '40vh',
        background: 'var(--panel)',
        borderTop: '1px solid var(--line)',
        boxShadow: '0 -4px 16px rgba(24,39,75,0.08)',
        zIndex: 100,
        display: 'flex',
        flexDirection: 'column',
        animation: 'slideUp 0.25s ease',
      }}
    >
      <div
        style={{
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
          padding: '8px 16px',
          borderBottom: '1px solid var(--line)',
          flexShrink: 0,
          height: 44,
        }}
      >
        <span style={{ fontWeight: 700, fontSize: 14, color: 'var(--text)' }}>
          已选卡片对比（{compareIds.length}）
        </span>
        <Space>
          <Button size="small" icon={<CopyOutlined />} onClick={() => {
            // 简单复制表格文本
            const text = rows.map((r) => [r.label, ...cols.map((c) => r.copyText(c))].join('\t')).join('\n');
            navigator.clipboard.writeText(text).catch(() => undefined);
          }}>
            复制表格
          </Button>
          <Button size="small" icon={<CloseOutlined />} onClick={clearCompare}>
            清空选择
          </Button>
        </Space>
      </div>
      <div style={{ flex: 1, overflow: 'auto', padding: '0 8px 8px' }}>
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
          <div style={{ padding: 16, color: 'var(--muted)', textAlign: 'center' }}>
            请至少选择两个节点进行对比
          </div>
        )}
      </div>
      <style>{`
        @keyframes slideUp {
          from { transform: translateY(100%); }
          to { transform: translateY(0); }
        }
      `}</style>
    </div>
  );
}
