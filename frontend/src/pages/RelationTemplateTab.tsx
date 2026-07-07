import { useEffect, useState } from 'react';
import {
  Table,
  Button,
  Space,
  Modal,
  Form,
  Input,
  Select,
  Switch,
  message,
  Popconfirm,
  ColorPicker,
  Tag,
} from 'antd';
import { entityTemplateApi, relationTemplateApi } from '@/api/template';
import { useProjectStore } from '@/stores/projectStore';
import { safeParse } from '@/utils/json';
import type { EntityTemplateVO, RelationTemplateVO } from '@/types/api';

interface LineStyle {
  color?: string;
  dash?: boolean;
  width?: number;
}

export default function RelationTemplateTab() {
  const currentProject = useProjectStore((s) => s.currentProject);
  const isAdmin = useProjectStore((s) => s.hasRole('ADMIN'));
  const pid = currentProject?.id;

  const [data, setData] = useState<RelationTemplateVO[]>([]);
  const [entityTemplates, setEntityTemplates] = useState<EntityTemplateVO[]>([]);
  const [loading, setLoading] = useState(false);
  const [open, setOpen] = useState(false);
  const [editing, setEditing] = useState<RelationTemplateVO | null>(null);
  const [lineColor, setLineColor] = useState('#fa8c16');
  const [form] = Form.useForm<{
    name: string;
    directed: boolean;
    dash: boolean;
    width: number;
    allowedFrom: string[];
    allowedTo: string[];
  }>();

  const reload = async () => {
    if (!pid) return;
    setLoading(true);
    try {
      const [rels, ents] = await Promise.all([
        relationTemplateApi.list(pid),
        entityTemplateApi.list(pid),
      ]);
      setData(rels);
      setEntityTemplates(ents);
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
    form.setFieldsValue({ directed: true, dash: false, width: 2, allowedFrom: [], allowedTo: [] });
    setLineColor('#fa8c16');
    setOpen(true);
  };

  const openEdit = (t: RelationTemplateVO) => {
    setEditing(t);
    const ls = safeParse<LineStyle>(t.lineStyle, {});
    form.setFieldsValue({
      name: t.name,
      directed: t.directed !== 0,
      dash: !!ls.dash,
      width: ls.width ?? 2,
      allowedFrom: safeParse<string[]>(t.allowedFrom, []).map(String),
      allowedTo: safeParse<string[]>(t.allowedTo, []).map(String),
    });
    setLineColor(ls.color || '#fa8c16');
    setOpen(true);
  };

  const onSubmit = async () => {
    if (!pid) return;
    const v = await form.validateFields();
    const lineStyle = JSON.stringify({ color: lineColor, dash: v.dash, width: v.width });
    const payload = {
      name: v.name,
      directed: v.directed ? 1 : 0,
      lineStyle,
      allowedFrom: JSON.stringify(v.allowedFrom ?? []),
      allowedTo: JSON.stringify(v.allowedTo ?? []),
    };
    if (editing) {
      await relationTemplateApi.update(pid, editing.id, payload);
      message.success('已更新');
    } else {
      await relationTemplateApi.create(pid, payload);
      message.success('已创建');
    }
    setOpen(false);
    reload();
  };

  const onDelete = async (t: RelationTemplateVO) => {
    if (!pid) return;
    await relationTemplateApi.remove(pid, t.id);
    message.success('已删除');
    reload();
  };

  const templateOptions = entityTemplates.map((t) => ({ value: t.id, label: t.name }));

  const columns = [
    { title: '名称', dataIndex: 'name' },
    {
      title: '方向',
      dataIndex: 'directed',
      render: (d?: number) => (d === 0 ? <Tag>无向</Tag> : <Tag color="blue">有向</Tag>),
    },
    {
      title: '线样式',
      dataIndex: 'lineStyle',
      render: (ls?: string) => {
        const s = safeParse<LineStyle>(ls, {});
        return (
          <Space>
            <span
              style={{
                display: 'inline-block',
                width: 24,
                borderTop: `${s.width ?? 2}px ${s.dash ? 'dashed' : 'solid'} ${s.color || '#999'}`,
              }}
            />
          </Space>
        );
      },
    },
    {
      title: '操作',
      key: 'op',
      render: (_: unknown, t: RelationTemplateVO) => (
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
          新建关系模板
        </Button>
      </Space>
      <Table rowKey="id" loading={loading} columns={columns} dataSource={data} />
      <Modal
        title={editing ? '编辑关系模板' : '新建关系模板'}
        open={open}
        width={620}
        onOk={onSubmit}
        onCancel={() => setOpen(false)}
        destroyOnHidden
      >
        <Form form={form} layout="vertical">
          <Form.Item name="name" label="模板名" rules={[{ required: true, max: 64 }]}>
            <Input />
          </Form.Item>
          <Space size="large">
            <Form.Item name="directed" label="有向" valuePropName="checked">
              <Switch />
            </Form.Item>
            <Form.Item name="dash" label="虚线" valuePropName="checked">
              <Switch />
            </Form.Item>
            <Form.Item name="width" label="线宽">
              <Select
                style={{ width: 100 }}
                options={[1, 2, 3, 4].map((w) => ({ value: w, label: `${w}px` }))}
              />
            </Form.Item>
            <Form.Item label="颜色">
              <ColorPicker value={lineColor} onChange={(c) => setLineColor(c.toHexString())} showText />
            </Form.Item>
          </Space>
          <Form.Item name="allowedFrom" label="允许源实体类型（空=不限）">
            <Select mode="multiple" options={templateOptions} allowClear />
          </Form.Item>
          <Form.Item name="allowedTo" label="允许目标实体类型（空=不限）">
            <Select mode="multiple" options={templateOptions} allowClear />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
}
