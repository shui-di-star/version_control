import { useEffect, useMemo, useState } from 'react';
import { Card, Statistic, Row, Col, Select, Button, Space } from 'antd';
import { PlusOutlined, CloseOutlined } from '@ant-design/icons';
import { statsApi } from '@/api/misc';
import { useProjectStore } from '@/stores/projectStore';
import { useTreeStore } from '@/stores/treeStore';
import { parseSchemaFields } from '@/utils/json';
import { parseAttributes } from '@/utils/json';
import type { ProjectStatsVO, EntityTreeNode } from '@/types/api';

type AggType = 'MAX' | 'MIN';

interface AggItem {
  key: string;       // NUMBER 字段 key
  agg: AggType;      // 最大/最小
}

/** 从整棵树中提取所有节点的指定字段数值。 */
function collectNumbers(roots: EntityTreeNode[], fieldKey: string): number[] {
  const nums: number[] = [];
  const walk = (nodes: EntityTreeNode[]) => {
    for (const n of nodes) {
      const attrs = parseAttributes(n.attributes);
      const v = attrs[fieldKey];
      if (v !== undefined && v !== null && v !== '') {
        const num = Number(v);
        if (!isNaN(num)) nums.push(num);
      }
      walk(n.children ?? []);
    }
  };
  walk(roots);
  return nums;
}

// 统计面板：方案节点/已完成仿真/仿真中/推荐 + 多个 NUMBER 字段最大/最小值。
export default function StatsPanel({ refreshKey }: { refreshKey: number }) {
  const currentProject = useProjectStore((s) => s.currentProject);
  const entityTemplates = useTreeStore((s) => s.entityTemplates);
  const tree = useTreeStore((s) => s.tree);
  const pid = currentProject?.id;
  const [stats, setStats] = useState<ProjectStatsVO | null>(null);
  const [aggItems, setAggItems] = useState<AggItem[]>([{ key: '', agg: 'MAX' }]);

  // 汇总所有实体模板的 NUMBER 字段 key 供选择。
  const numberKeys = Array.from(
    new Set(
      entityTemplates.flatMap((t) =>
        parseSchemaFields(t.fieldSchema).filter((f) => f.type === 'NUMBER').map((f) => f.key),
      ),
    ),
  );

  // 获取字段的 label（如果有）
  const fieldLabelMap = useMemo(() => {
    const m = new Map<string, string>();
    entityTemplates.forEach((t) => {
      parseSchemaFields(t.fieldSchema).filter((f) => f.type === 'NUMBER').forEach((f) => {
        if (!m.has(f.key)) m.set(f.key, f.label || f.key);
      });
    });
    return m;
  }, [entityTemplates]);

  useEffect(() => {
    if (!pid) return;
    statsApi.get(pid).then(setStats).catch(() => undefined);
  }, [pid, refreshKey]);

  // 前端计算聚合值
  const aggResults = useMemo(() => {
    return aggItems.map((item) => {
      if (!item.key) return null;
      const nums = collectNumbers(tree, item.key);
      if (nums.length === 0) return null;
      return item.agg === 'MAX' ? Math.max(...nums) : Math.min(...nums);
    });
  }, [aggItems, tree]);

  const addAggItem = () => {
    setAggItems((prev) => [...prev, { key: '', agg: 'MAX' }]);
  };

  const removeAggItem = (index: number) => {
    setAggItems((prev) => prev.filter((_, i) => i !== index));
  };

  const updateAggItem = (index: number, patch: Partial<AggItem>) => {
    setAggItems((prev) => prev.map((item, i) => (i === index ? { ...item, ...patch } : item)));
  };

  return (
    <Card size="small" styles={{ body: { padding: 12 } }}>
      <Row gutter={16} align="middle" wrap={false} style={{ overflowX: 'auto' }}>
        <Col>
          <Statistic title="方案节点" value={stats?.totalNodes ?? 0} />
        </Col>
        <Col>
          <Statistic title="已完成仿真" value={stats?.completedSim ?? 0} />
        </Col>
        <Col>
          <Statistic title="仿真中" value={stats?.simulating ?? 0} />
        </Col>
        <Col>
          <Statistic title="推荐" value={stats?.recommended ?? 0} />
        </Col>
        {aggItems.map((item, idx) => (
          <Col key={idx}>
            <Space size={4} style={{ marginBottom: 4 }}>
              <Select
                size="small"
                style={{ width: 110 }}
                placeholder="字段"
                allowClear
                value={item.key || undefined}
                options={numberKeys.map((k) => ({ value: k, label: fieldLabelMap.get(k) || k }))}
                onChange={(v) => updateAggItem(idx, { key: v ?? '' })}
              />
              <Select
                size="small"
                style={{ width: 72 }}
                value={item.agg}
                options={[
                  { value: 'MAX', label: '最大值' },
                  { value: 'MIN', label: '最小值' },
                ]}
                onChange={(v) => updateAggItem(idx, { agg: v })}
              />
              {aggItems.length > 1 && (
                <Button
                  type="text"
                  size="small"
                  icon={<CloseOutlined />}
                  onClick={() => removeAggItem(idx)}
                  style={{ color: '#999' }}
                />
              )}
            </Space>
            <Statistic
              title={item.key ? `${item.agg === 'MAX' ? '最大' : '最小'}(${fieldLabelMap.get(item.key) || item.key})` : '—'}
              value={aggResults[idx] ?? '—'}
            />
          </Col>
        ))}
        <Col>
          <Button
            type="dashed"
            size="small"
            icon={<PlusOutlined />}
            onClick={addAggItem}
            style={{ marginTop: 16 }}
          >
            添加
          </Button>
        </Col>
      </Row>
    </Card>
  );
}
