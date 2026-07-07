import { useState } from 'react';
import { Card, Descriptions, Tag, Select, Input, Button, message, Space } from 'antd';
import { entityApi } from '@/api/entity';
import { relationApi } from '@/api/misc';
import { useProjectStore } from '@/stores/projectStore';
import { useTreeStore } from '@/stores/treeStore';
import type { EdgeInfo } from './TreeGraph';

interface Props {
  edge: EdgeInfo;
  onChanged: () => void;
}

export default function EdgeDetailPanel({ edge, onChanged }: Props) {
  const pid = useProjectStore((s) => s.currentProject?.id);
  const canWrite = useProjectStore((s) => s.hasRole('EDITOR'));
  const relationTemplates = useTreeStore((s) => s.relationTemplates);

  const [templateId, setTemplateId] = useState(edge.templateId);
  const [remark, setRemark] = useState(edge.remark ?? '');
  const [saving, setSaving] = useState(false);

  const dirty = templateId !== edge.templateId || remark !== (edge.remark ?? '');

  const handleSave = async () => {
    if (!pid) return;
    setSaving(true);
    try {
      if (edge.type === 'parent') {
        // 父子关系：通过 reparent 接口修改模板和备注（parentId 保持不变）
        await entityApi.reparent(pid, edge.targetId, {
          parentId: edge.sourceId,
          parentRelationTemplateId: templateId || undefined,
          parentRelationRemark: remark || undefined,
        });
      } else {
        // 语义关系：通过 relation update 接口
        await relationApi.update(pid, edge.relationId!, {
          templateId: templateId || undefined,
          remark: remark || undefined,
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
        <div style={{ marginBottom: 8 }}>
          <label style={{ display: 'block', marginBottom: 4, fontWeight: 500 }}>备注</label>
          {canWrite ? (
            <Input.TextArea
              rows={3}
              value={remark}
              placeholder="输入备注（可选）"
              onChange={(e) => setRemark(e.target.value)}
            />
          ) : (
            <span>{remark || '无'}</span>
          )}
        </div>
        {canWrite && dirty && (
          <Space>
            <Button type="primary" size="small" loading={saving} onClick={handleSave}>
              保存
            </Button>
            <Button size="small" onClick={() => { setTemplateId(edge.templateId); setRemark(edge.remark ?? ''); }}>
              重置
            </Button>
          </Space>
        )}
      </div>
    </Card>
  );
}
