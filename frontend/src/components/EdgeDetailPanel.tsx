import { useState } from 'react';
import { App, Card, Descriptions, Tag, Select, Button, Space } from 'antd';
import { entityApi } from '@/api/entity';
import { relationApi } from '@/api/misc';
import { useProjectStore } from '@/stores/projectStore';
import { useTreeStore } from '@/stores/treeStore';
import EdgeRemarkPanel from './EdgeRemarkPanel';
import type { EdgeInfo } from './TreeGraph';

interface Props {
  edge: EdgeInfo;
  onChanged: () => void;
}

export default function EdgeDetailPanel({ edge, onChanged }: Props) {
  const { message } = App.useApp();
  const pid = useProjectStore((s) => s.currentProject?.id);
  const canWrite = useProjectStore((s) => s.hasRole('EDITOR'));
  const relationTemplates = useTreeStore((s) => s.relationTemplates);

  const [templateId, setTemplateId] = useState(edge.templateId);
  const [saving, setSaving] = useState(false);

  const dirty = templateId !== edge.templateId;

  const handleSave = async () => {
    if (!pid) return;
    setSaving(true);
    try {
      if (edge.type === 'parent') {
        await entityApi.reparent(pid, edge.targetId, {
          parentId: edge.sourceId,
          parentRelationTemplateId: templateId || undefined,
        });
      } else {
        await relationApi.update(pid, edge.relationId!, {
          templateId: templateId || undefined,
        });
      }
      message.success('已保存');
      onChanged();
    } catch {
      message.error('保存失败');
    } finally {
      setSaving(false);
    }
  };

  return (
    <Card size="small" title="连线详情" style={{ height: '100%', overflow: 'auto' }}>
      <Descriptions column={1} size="small">
        <Descriptions.Item label="类型">
          {edge.type === 'parent' ? '父子关系' : '语义关系'}
        </Descriptions.Item>
        <Descriptions.Item label="源节点">{edge.sourceName}</Descriptions.Item>
        <Descriptions.Item label="目标节点">{edge.targetName}</Descriptions.Item>
      </Descriptions>

      <div style={{ marginTop: 12 }}>
        <div style={{ marginBottom: 8 }}>
          <label style={{ display: 'block', marginBottom: 4, fontWeight: 500 }}>关系类型</label>
          {canWrite ? (
            <Select
              style={{ width: '100%' }}
              value={templateId || undefined}
              placeholder="选择关系类型"
              onChange={(v) => setTemplateId(v)}
              options={relationTemplates.map((t) => ({ value: t.id, label: t.name }))}
            />
          ) : (
            <Tag color={edge.templateColor}>{edge.templateName || '未知'}</Tag>
          )}
        </div>
        {edge.type === 'parent' && (
          <div style={{ marginBottom: 8 }}>
            <label style={{ display: 'block', marginBottom: 4, fontWeight: 500 }}>备注</label>
            <EdgeRemarkPanel entityId={edge.targetId} />
          </div>
        )}
        {canWrite && dirty && (
          <Space>
            <Button type="primary" size="small" loading={saving} onClick={handleSave}>
              保存
            </Button>
            <Button size="small" onClick={() => { setTemplateId(edge.templateId); }}>
              重置
            </Button>
          </Space>
        )}
      </div>
    </Card>
  );
}
