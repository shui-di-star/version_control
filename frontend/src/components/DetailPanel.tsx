import { useEffect, useState } from 'react';
import { Form, Input, Select, Switch, Divider, Typography, Space, Button, message } from 'antd';
import { AimOutlined, SaveOutlined } from '@ant-design/icons';
import { entityApi } from '@/api/entity';
import { useProjectStore } from '@/stores/projectStore';
import { useTreeStore } from '@/stores/treeStore';
import { parseAttributes, parseSchemaFields } from '@/utils/json';
import { STATUS_OPTIONS } from '@/utils/constants';
import AttributeFields, { attrsToForm, formToAttrs } from '@/components/AttributeFields';
import AssetSection from '@/components/AssetSection';
import type { EntityVO } from '@/types/api';

interface Props {
  entityId: string;
  onChanged: () => void;
  onSaveState: (s: 'saving' | 'saved' | null) => void;
}

export default function DetailPanel({ entityId, onChanged, onSaveState }: Props) {
  const currentProject = useProjectStore((s) => s.currentProject);
  const canWrite = useProjectStore((s) => s.hasRole('EDITOR'));
  const entityTemplates = useTreeStore((s) => s.entityTemplates);
  const setPathHighlight = useTreeStore((s) => s.setPathHighlight);
  const pid = currentProject?.id;

  const [entity, setEntity] = useState<EntityVO | null>(null);
  const [form] = Form.useForm();
  const [dirty, setDirty] = useState(false);
  const [saving, setSaving] = useState(false);

  const fields = entity
    ? parseSchemaFields(entityTemplates.find((t) => t.id === entity.templateId)?.fieldSchema)
    : [];

  useEffect(() => {
    if (!pid) return;
    entityApi.get(pid, entityId).then((e) => {
      setEntity(e);
      const f = parseSchemaFields(entityTemplates.find((t) => t.id === e.templateId)?.fieldSchema);
      form.setFieldsValue({
        name: e.name,
        remark: e.remark,
        attrs: attrsToForm(parseAttributes(e.attributes), f),
      });
      setDirty(false);
    });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [pid, entityId]);

  const handleSave = async () => {
    if (!pid || !entity) return;
    const v = form.getFieldsValue();
    setSaving(true);
    onSaveState('saving');
    try {
      await entityApi.update(pid, entity.id, {
        name: v.name,
        remark: v.remark,
        attributes: JSON.stringify(formToAttrs(v.attrs ?? {}, fields)),
      });
      onSaveState('saved');
      setDirty(false);
      onChanged();
    } catch {
      onSaveState(null);
      message.error('保存失败');
    } finally {
      setSaving(false);
    }
  };

  const onValuesChange = () => {
    if (canWrite) setDirty(true);
  };

  const toggleMilestone = async () => {
    if (!pid || !entity) return;
    const e = await entityApi.toggleMilestone(pid, entity.id);
    setEntity(e);
    onChanged();
  };

  const setStatus = async (status: string | null) => {
    if (!pid || !entity) return;
    const e = await entityApi.setStatus(pid, entity.id, status ?? null);
    setEntity(e);
    onChanged();
  };

  const tracePath = async () => {
    if (!pid || !entity) return;
    const path = await entityApi.path(pid, entity.id);
    setPathHighlight(path.map((p) => p.id));
    message.success(`已高亮根→当前路径（${path.length} 个节点）`);
  };

  if (!entity) return null;

  return (
    <div style={{ padding: 12, overflowY: 'auto', height: '100%' }}>
      <Space style={{ justifyContent: 'space-between', width: '100%' }}>
        <Typography.Title level={5} style={{ margin: 0 }}>
          节点详情
        </Typography.Title>
        <Space>
          {canWrite && dirty && (
            <Button type="primary" size="small" icon={<SaveOutlined />} loading={saving} onClick={handleSave}>
              保存
            </Button>
          )}
          <Button size="small" icon={<AimOutlined />} onClick={tracePath}>
            路径追溯
          </Button>
        </Space>
      </Space>
      <Divider style={{ margin: '12px 0' }} />
      <Form form={form} layout="vertical" onValuesChange={onValuesChange} disabled={!canWrite}>
        <Form.Item name="name" label="名称" rules={[{ required: true, max: 128 }]}>
          <Input />
        </Form.Item>
        <Space size="large" style={{ marginBottom: 8 }}>
          <span>
            里程碑：
            <Switch
              checkedChildren="★"
              checked={entity.isMilestone === 1}
              onChange={toggleMilestone}
              disabled={!canWrite}
            />
          </span>
        </Space>
        <Form.Item label="状态">
          <Select
            allowClear
            placeholder="无状态"
            value={entity.status ?? undefined}
            options={STATUS_OPTIONS}
            onChange={(v) => setStatus(v ?? null)}
            disabled={!canWrite}
          />
        </Form.Item>
        <Divider orientation="left" plain>
          动态属性
        </Divider>
        <AttributeFields fields={fields} disabled={!canWrite} />
        <Form.Item name="remark" label="备注" rules={[{ max: 512 }]}>
          <Input.TextArea rows={3} />
        </Form.Item>
      </Form>
      <Divider orientation="left" plain>
        产出物
      </Divider>
      <AssetSection entityId={entity.id} />
    </div>
  );
}
