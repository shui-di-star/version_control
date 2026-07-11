import { useEffect, useState } from 'react';
import {
  Table,
  Button,
  Space,
  Tag,
  Modal,
  Form,
  Input,
  App,
  Popconfirm,
} from 'antd';
import { PlusOutlined } from '@ant-design/icons';
import { useNavigate } from 'react-router-dom';
import { projectApi } from '@/api/project';
import { useAuthStore } from '@/stores/authStore';
import { useProjectStore } from '@/stores/projectStore';
import type { ProjectVO, ProjectCreateRequest } from '@/types/api';

const ROLE_COLOR: Record<string, string> = {
  ADMIN: 'gold',
  EDITOR: 'blue',
  VIEWER: 'default',
};

export default function ProjectListPage() {
  const navigate = useNavigate();
  const { message } = App.useApp();
  const user = useAuthStore((s) => s.user);
  const projects = useProjectStore((s) => s.projects);
  const currentProject = useProjectStore((s) => s.currentProject);
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
    if (currentProject?.id === p.id) {
      setCurrentProject(null);
    }
    reload();
  };

  const enter = (p: ProjectVO) => {
    setCurrentProject(p);
    navigate('/tree');
  };

  const columns = [
    {
      title: '项目名称',
      dataIndex: 'name',
      key: 'name',
      render: (name: string, record: ProjectVO) => (
        <Button type="link" style={{ padding: 0, fontWeight: 600 }} onClick={() => enter(record)}>
          {name}
        </Button>
      ),
    },
    {
      title: '描述',
      dataIndex: 'description',
      key: 'description',
      ellipsis: true,
      render: (desc: string) => desc || <span style={{ color: 'var(--muted)' }}>暂无描述</span>,
    },
    {
      title: '创建者',
      dataIndex: 'ownerName',
      key: 'ownerName',
      width: 120,
      render: (name: string) => name || '-',
    },
    {
      title: '我的角色',
      dataIndex: 'myRole',
      key: 'myRole',
      width: 100,
      render: (role: string) => (
        <Tag color={ROLE_COLOR[role] ?? 'default'}>{role}</Tag>
      ),
    },
    {
      title: '操作',
      key: 'op',
      width: 150,
      render: (_: unknown, record: ProjectVO) => {
        const isAdmin = record.myRole === 'ADMIN';
        const isOwner = user?.id === record.ownerId;
        return (
          <Space>
            <Button type="link" size="small" onClick={() => enter(record)}>
              进入
            </Button>
            {isAdmin && (
              <Button type="link" size="small" onClick={() => openEdit(record)}>
                编辑
              </Button>
            )}
            {isOwner && (
              <Popconfirm title="确认删除该项目？" onConfirm={() => onDelete(record)}>
                <Button type="link" size="small" danger>
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
    <div style={{ padding: '20px 24px' }}>
      <Space style={{ marginBottom: 12 }}>
        <Button type="primary" icon={<PlusOutlined />} onClick={openCreate}>
          新建项目
        </Button>
      </Space>
      <Table
        rowKey="id"
        loading={loading}
        columns={columns}
        dataSource={projects}
        pagination={false}
      />
      <Modal
        title={editing ? '编辑项目' : '新建项目'}
        open={modalOpen}
        onOk={onSubmit}
        onCancel={() => setModalOpen(false)}
        forceRender
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
