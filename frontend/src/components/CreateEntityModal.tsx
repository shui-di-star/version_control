import { useState } from 'react';
import { Modal, Form, Input, Select, App } from 'antd';
import { entityApi } from '@/api/entity';
import { useProjectStore } from '@/stores/projectStore';
import { useTreeStore } from '@/stores/treeStore';
import { parseSchemaFields } from '@/utils/json';
import AttributeFields, { formToAttrs } from '@/components/AttributeFields';

interface Props {
  open: boolean;
  parentId: string | null; // null = 根节点
  onClose: () => void;
  onCreated: () => void;
}

export default function CreateEntityModal({ open, parentId, onClose, onCreated }: Props) {
  const { message } = App.useApp();
  const currentProject = useProjectStore((s) => s.currentProject);
  const entityTemplates = useTreeStore((s) => s.entityTemplates);
  const relationTemplates = useTreeStore((s) => s.relationTemplates);
  const pid = currentProject?.id;
  const [form] = Form.useForm();
  const [templateId, setTemplateId] = useState<string | undefined>();

  const fields = templateId
    ? parseSchemaFields(entityTemplates.find((t) => t.id === templateId)?.fieldSchema)
    : [];

  const onOk = async () => {
    if (!pid) return;
    const v = await form.validateFields();
    const attrValues = formToAttrs(v.attrs ?? {}, fields);
    // 用卡片名称作为实体 name；如果模板没有 card_name 字段则用第一个属性值或模板名
    const name = String(attrValues['card_name'] ?? v.attrs?.['card_name'] ?? '未命名');
    await entityApi.create(pid, {
      templateId: v.templateId,
      parentId: parentId ?? null,
      name,
      remark: v.remark,
      attributes: JSON.stringify(attrValues),
      parentRelationTemplateId: v.parentRelationTemplateId,
      parentRelationRemark: v.parentRelationRemark,
    });
    message.success('已创建');
    form.resetFields();
    setTemplateId(undefined);
    onClose();
    onCreated();
  };

  return (
    <Modal
      title={parentId ? '新增子实体' : '新增根实体'}
      open={open}
      onOk={onOk}
      onCancel={onClose}
      forceRender
    >
      <Form form={form} layout="vertical">
        <Form.Item name="templateId" label="实体模板" rules={[{ required: true }]}>
          <Select
            options={entityTemplates.map((t) => ({ value: t.id, label: t.name }))}
            onChange={(v) => {
              setTemplateId(v);
              form.setFieldValue('attrs', {});
            }}
          />
        </Form.Item>
        {parentId && (
          <>
            <Form.Item name="parentRelationTemplateId" label="关系类型" rules={[{ required: true, message: '创建子节点必须选择关系类型' }]}>
              <Select
                placeholder="选择关系类型"
                options={relationTemplates.map((t) => ({ value: t.id, label: t.name }))}
              />
            </Form.Item>
            <Form.Item name="parentRelationRemark" label="关系备注">
              <Input.TextArea rows={2} placeholder="可选" />
            </Form.Item>
          </>
        )}
        <AttributeFields fields={fields} projectId={pid} />
        <Form.Item name="remark" label="备注" rules={[{ max: 512 }]}>
          <Input.TextArea rows={2} />
        </Form.Item>
      </Form>
    </Modal>
  );
}
