import { useEffect, useState } from 'react';
import {
  Table,
  Button,
  Space,
  Modal,
  Form,
  Input,
  message,
  Popconfirm,
} from 'antd';
import { entityTemplateApi } from '@/api/template';
import { useProjectStore } from '@/stores/projectStore';
import FieldSchemaEditor from '@/components/FieldSchemaEditor';
import { parseSchemaFields } from '@/utils/json';
import type { EntityTemplateVO, SchemaField } from '@/types/api';

export default function EntityTemplateTab() {
  const currentProject = useProjectStore((s) => s.currentProject);
  const isAdmin = useProjectStore((s) => s.hasRole('ADMIN'));
  const pid = currentProject?.id;

  const [data, setData] = useState<EntityTemplateVO[]>([]);
  const [loading, setLoading] = useState(false);
  const [open, setOpen] = useState(false);
  const [editing, setEditing] = useState<EntityTemplateVO | null>(null);
  const [fields, setFields] = useState<SchemaField[]>([]);
  const [form] = Form.useForm<{ name: string; icon?: string }>();

  const reload = async () => {
    if (!pid) return;
    setLoading(true);
    try {
      setData(await entityTemplateApi.list(pid));
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    reload();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [pid]);

  const openCreate = () => {
    setEditing(null);
    form.resetFields();
    setFields([]);
    setOpen(true);
  };

  const openEdit = (t: EntityTemplateVO) => {
    setEditing(t);
    form.setFieldsValue({ name: t.name, icon: t.icon });
    setFields(parseSchemaFields(t.fieldSchema));
    setOpen(true);
  };

  const onSubmit = async () => {
    if (!pid) return;
    const values = await form.validateFields();
    for (const f of fields) {
      if (!f.key.trim() || !f.label.trim()) {
        message.error('字段 key/显示名不能为空');
        return;
      }
      if (f.type === 'ENUM' && (!f.options || f.options.length === 0)) {
        message.error(`ENUM 字段「${f.label}」必须填写选项`);
        return;
      }
    }
    const fieldSchema = JSON.stringify({ fields });
    const payload = { name: values.name, icon: values.icon, fieldSchema };
    if (editing) {
      await entityTemplateApi.update(pid, editing.id, payload);
      message.success('已更新');
    } else {
      await entityTemplateApi.create(pid, payload);
      message.success('已创建');
    }
    setOpen(false);
    reload();
  };

  const onDelete = async (t: EntityTemplateVO) => {
    if (!pid) return;
    await entityTemplateApi.remove(pid, t.id);
    message.success('已删除');
    reload();
  };

  const columns = [
    {
      title: '名称',
      dataIndex: 'name',
    },
    { title: '图标', dataIndex: 'icon' },
    {
      title: '字段数',
      key: 'fields',
      render: (_: unknown, t: EntityTemplateVO) => parseSchemaFields(t.fieldSchema).length,
    },
    {
      title: '操作',
      key: 'op',
      render: (_: unknown, t: EntityTemplateVO) => (
        <Space>
          <Button type="link" onClick={() => openEdit(t)} disabled={!isAdmin}>
            编辑
          </Button>
          <Popconfirm title="确认删除？" onConfirm={() => onDelete(t)} disabled={!isAdmin}>
            <Button type="link" danger disabled={!isAdmin}>
              删除
            </Button>
          </Popconfirm>
        </Space>
      ),
    },
  ];

  return (
    <div>
      <Space style={{ marginBottom: 12 }}>
        <Button type="primary" onClick={openCreate} disabled={!isAdmin}>
          新建实体模板
        </Button>
      </Space>
      <Table rowKey="id" loading={loading} columns={columns} dataSource={data} />
      <Modal
        title={editing ? '编辑实体模板' : '新建实体模板'}
        open={open}
        width={760}
        onOk={onSubmit}
        onCancel={() => setOpen(false)}
        destroyOnHidden
      >
        <Form form={form} layout="vertical">
          <Space size="large" align="start">
            <Form.Item name="name" label="模板名" rules={[{ required: true, max: 64 }]}>
              <Input style={{ width: 220 }} />
            </Form.Item>
            <Form.Item name="icon" label="图标（可选）" rules={[{ max: 32 }]}>
              <Input style={{ width: 160 }} placeholder="如 cube" />
            </Form.Item>
          </Space>
          <Form.Item label="动态字段 (field_schema)">
            <FieldSchemaEditor value={fields} onChange={setFields} />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
}
