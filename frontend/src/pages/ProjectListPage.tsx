import { useEffect, useState } from 'react';
import {
  Table,
  Button,
  Space,
  Tag,
  Modal,
  Form,
  Input,
  message,
  Popconfirm,
  Typography,
} from 'antd';
import { useNavigate } from 'react-router-dom';
import { projectApi } from '@/api/project';
import { useAuthStore } from '@/stores/authStore';
import { useProjectStore } from '@/stores/projectStore';
import type { ProjectVO, ProjectCreateRequest } from '@/types/api';

export default function ProjectListPage() {
  const navigate = useNavigate();
  const user = useAuthStore((s) => s.user);
  const projects = useProjectStore((s) => s.projects);
  const setProjects = useProjectStore((s) => s.setProjects);
  const setCurrentProject = useProjectStore((s) => s.setCurrentProject);
  const [loading, setLoading] = useState(false);
  const [modalOpen, setModalOpen] = useState(false);
  const [editing, setEditing] = useState<ProjectVO | null>(null);
  const [form] = Form.useForm<ProjectCreateRequest>();

  const reload = async () => {
    setLoading(true);
    try {
      setProjects(await projectApi.list());
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    reload();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const openCreate = () => {
    setEditing(null);
    form.resetFields();
    setModalOpen(true);
  };

  const openEdit = (p: ProjectVO) => {
    setEditing(p);
    form.setFieldsValue({ name: p.name, description: p.description });
    setModalOpen(true);
  };

  const onSubmit = async () => {
    const values = await form.validateFields();
    if (editing) {
      await projectApi.update(editing.id, values);
      message.success('已更新');
    } else {
      await projectApi.create(values);
      message.success('已创建');
    }
    setModalOpen(false);
    reload();
  };

  const onDelete = async (p: ProjectVO) => {
    await projectApi.remove(p.id);
    message.success('已删除');
    reload();
  };

  const enter = (p: ProjectVO) => {
    setCurrentProject(p);
    navigate('/tree');
  };

  const columns = [
    { title: '项目名', dataIndex: 'name' },
    { title: '描述', dataIndex: 'description', ellipsis: true },
    {
      title: '我的角色',
      dataIndex: 'myRole',
      render: (r: string) => <Tag color={r === 'ADMIN' ? 'gold' : r === 'EDITOR' ? 'blue' : 'default'}>{r}</Tag>,
    },
    {
      title: '操作',
      key: 'action',
      render: (_: unknown, p: ProjectVO) => {
        const isAdmin = p.myRole === 'ADMIN';
        const isOwner = user?.id === p.ownerId;
        return (
          <Space>
            <Button type="link" onClick={() => enter(p)}>
              进入
            </Button>
            {isAdmin && (
              <Button type="link" onClick={() => openEdit(p)}>
                编辑
              </Button>
            )}
            {isOwner && (
              <Popconfirm title="确认删除该项目？" onConfirm={() => onDelete(p)}>
                <Button type="link" danger>
                  删除
                </Button>
              </Popconfirm>
            )}
          </Space>
        );
      },
    },
  ];

  return (
    <div>
      <Space style={{ marginBottom: 16, justifyContent: 'space-between', width: '100%' }}>
        <Typography.Title level={4} style={{ margin: 0 }}>
          我的项目
        </Typography.Title>
        <Button type="primary" onClick={openCreate}>
          新建项目
        </Button>
      </Space>
      <Table rowKey="id" loading={loading} columns={columns} dataSource={projects} />
      <Modal
        title={editing ? '编辑项目' : '新建项目'}
        open={modalOpen}
        onOk={onSubmit}
        onCancel={() => setModalOpen(false)}
        destroyOnHidden
      >
        <Form form={form} layout="vertical">
          <Form.Item name="name" label="项目名" rules={[{ required: true, max: 128 }]}>
            <Input />
          </Form.Item>
          <Form.Item name="description" label="描述" rules={[{ max: 512 }]}>
            <Input.TextArea rows={3} />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
}
