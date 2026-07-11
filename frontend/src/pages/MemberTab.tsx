import { useEffect, useState } from 'react';
import { App, Table, Button, Space, Modal, Form, Input, Select, Popconfirm, Tag } from 'antd';
import { memberApi } from '@/api/project';
import { useProjectStore } from '@/stores/projectStore';
import type { MemberVO, ProjectRoleName } from '@/types/api';

export default function MemberTab() {
  const { message } = App.useApp();
  const currentProject = useProjectStore((s) => s.currentProject);
  const isAdmin = useProjectStore((s) => s.hasRole('ADMIN'));
  const pid = currentProject?.id;

  const [data, setData] = useState<MemberVO[]>([]);
  const [loading, setLoading] = useState(false);
  const [open, setOpen] = useState(false);
  const [form] = Form.useForm<{ username: string; role: ProjectRoleName }>();

  const reload = async () => {
    if (!pid) return;
    setLoading(true);
    try {
      setData(await memberApi.list(pid));
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    reload();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [pid]);

  const onAdd = async () => {
    if (!pid) return;
    const v = await form.validateFields();
    await memberApi.add(pid, v);
    message.success('已添加成员');
    setOpen(false);
    reload();
  };

  const onRemove = async (m: MemberVO) => {
    if (!pid) return;
    await memberApi.remove(pid, m.userId);
    message.success('已移除');
    reload();
  };

  const columns = [
    { title: '用户名', dataIndex: 'username' },
    { title: '显示名', dataIndex: 'displayName' },
    {
      title: '角色',
      dataIndex: 'role',
      render: (r: string) => <Tag color={r === 'ADMIN' ? 'gold' : r === 'EDITOR' ? 'blue' : 'default'}>{r}</Tag>,
    },
    {
      title: '操作',
      key: 'op',
      render: (_: unknown, m: MemberVO) => (
        <Popconfirm title="确认移除该成员？" onConfirm={() => onRemove(m)} disabled={!isAdmin}>
          <Button type="link" danger disabled={!isAdmin}>
            移除
          </Button>
        </Popconfirm>
      ),
    },
  ];

  return (
    <div>
      <Space style={{ marginBottom: 12 }}>
        <Button type="primary" onClick={() => { form.resetFields(); setOpen(true); }} disabled={!isAdmin}>
          添加成员
        </Button>
      </Space>
      <Table rowKey="userId" loading={loading} columns={columns} dataSource={data} />
      <Modal title="添加成员" open={open} onOk={onAdd} onCancel={() => setOpen(false)} forceRender>
        <Form form={form} layout="vertical">
          <Form.Item name="username" label="用户名" rules={[{ required: true }]}>
            <Input placeholder="输入目标用户的用户名" />
          </Form.Item>
          <Form.Item name="role" label="角色" rules={[{ required: true }]}>
            <Select
              options={[
                { value: 'ADMIN', label: 'ADMIN' },
                { value: 'EDITOR', label: 'EDITOR' },
                { value: 'VIEWER', label: 'VIEWER' },
              ]}
            />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
}
