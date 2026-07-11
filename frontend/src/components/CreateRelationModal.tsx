import { Modal, Form, Select, Input, App } from 'antd';
import { relationApi } from '@/api/misc';
import { useProjectStore } from '@/stores/projectStore';
import { useTreeStore } from '@/stores/treeStore';
import { flattenTree } from '@/utils/tree';

interface Props {
  open: boolean;
  onClose: () => void;
  onCreated: () => void;
}

// 新建语义关系：选关系模板 + 源/目标节点。受 allowed_from/to 约束（后端校验，返 1000）。
export default function CreateRelationModal({ open, onClose, onCreated }: Props) {
  const { message } = App.useApp();
  const currentProject = useProjectStore((s) => s.currentProject);
  const tree = useTreeStore((s) => s.tree);
  const relationTemplates = useTreeStore((s) => s.relationTemplates);
  const pid = currentProject?.id;
  const [form] = Form.useForm();

  const nodeOptions = flattenTree(tree).map((n) => ({ value: n.id, label: n.name }));

  const onOk = async () => {
    if (!pid) return;
    const v = await form.validateFields();
    if (v.fromEntityId === v.toEntityId) {
      message.error('源与目标不能相同');
      return;
    }
    await relationApi.create(pid, v);
    message.success('已创建关系');
    form.resetFields();
    onClose();
    onCreated();
  };

  return (
    <Modal title="新建语义关系" open={open} onOk={onOk} onCancel={onClose} forceRender>
      <Form form={form} layout="vertical">
        <Form.Item name="templateId" label="关系模板" rules={[{ required: true }]}>
          <Select options={relationTemplates.map((t) => ({ value: t.id, label: t.name }))} />
        </Form.Item>
        <Form.Item name="fromEntityId" label="源节点" rules={[{ required: true }]}>
          <Select showSearch optionFilterProp="label" options={nodeOptions} />
        </Form.Item>
        <Form.Item name="toEntityId" label="目标节点" rules={[{ required: true }]}>
          <Select showSearch optionFilterProp="label" options={nodeOptions} />
        </Form.Item>
        <Form.Item name="remark" label="备注" rules={[{ max: 500 }]}>
          <Input.TextArea rows={2} />
        </Form.Item>
      </Form>
    </Modal>
  );
}
