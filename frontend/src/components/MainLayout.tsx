import { useEffect } from 'react';
import { Layout, Menu, Select, Dropdown, Space, Avatar, Button } from 'antd';
import { UserOutlined, LogoutOutlined } from '@ant-design/icons';
import { Outlet, useNavigate, useLocation } from 'react-router-dom';
import { useAuthStore } from '@/stores/authStore';
import { useProjectStore } from '@/stores/projectStore';
import { authApi } from '@/api/auth';
import { projectApi } from '@/api/project';
import GlobalSearch from '@/components/GlobalSearch';

const { Header, Content } = Layout;

export default function MainLayout() {
  const navigate = useNavigate();
  const location = useLocation();
  const user = useAuthStore((s) => s.user);
  const setUser = useAuthStore((s) => s.setUser);
  const clear = useAuthStore((s) => s.clear);
  const projects = useProjectStore((s) => s.projects);
  const setProjects = useProjectStore((s) => s.setProjects);
  const currentProject = useProjectStore((s) => s.currentProject);
  const setCurrentProject = useProjectStore((s) => s.setCurrentProject);

  // 首次进入主布局：确保有当前用户与项目列表。
  useEffect(() => {
    if (!user) {
      authApi.me().then(setUser).catch(() => undefined);
    }
    if (projects.length === 0) {
      projectApi.list().then(setProjects).catch(() => undefined);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const onLogout = async () => {
    try {
      await authApi.logout();
    } finally {
      clear();
      navigate('/login', { replace: true });
    }
  };

  const menuKey = location.pathname.startsWith('/tree')
    ? '/tree'
    : location.pathname.startsWith('/templates')
      ? '/templates'
      : location.pathname.startsWith('/settings')
        ? '/settings'
        : '/projects';

  return (
    <Layout style={{ minHeight: '100vh' }}>
      <Header style={{ display: 'flex', alignItems: 'center', gap: 16, paddingInline: 16 }}>
        <div style={{ color: '#fff', fontWeight: 600, fontSize: 16, whiteSpace: 'nowrap' }}>
          仿真版本管理
        </div>
        <Select
          style={{ minWidth: 200 }}
          placeholder="选择项目"
          value={currentProject?.id}
          options={projects.map((p) => ({ value: p.id, label: `${p.name}（${p.myRole}）` }))}
          onChange={(id) => {
            const p = projects.find((x) => x.id === id) ?? null;
            setCurrentProject(p);
          }}
        />
        <Menu
          theme="dark"
          mode="horizontal"
          selectedKeys={[menuKey]}
          style={{ flex: 1, minWidth: 0 }}
          onClick={({ key }) => navigate(key)}
          items={[
            { key: '/projects', label: '项目列表' },
            { key: '/tree', label: '迭代树', disabled: !currentProject },
            { key: '/templates', label: '内容管理', disabled: !currentProject },
            { key: '/settings', label: '设置', disabled: !currentProject },
          ]}
        />
        <GlobalSearch />
        <Dropdown
          menu={{
            items: [{ key: 'logout', icon: <LogoutOutlined />, label: '登出', onClick: onLogout }],
          }}
        >
          <Space style={{ color: '#fff', cursor: 'pointer' }}>
            <Avatar size="small" icon={<UserOutlined />} />
            {user?.displayName || user?.username || '用户'}
          </Space>
        </Dropdown>
        <Button type="text" style={{ color: '#fff' }} icon={<LogoutOutlined />} onClick={onLogout}>
          登出
        </Button>
      </Header>
      <Content style={{ padding: 16 }}>
        <Outlet />
      </Content>
    </Layout>
  );
}
