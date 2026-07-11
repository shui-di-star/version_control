import { useEffect } from 'react';
import { Layout, Menu, Select, Dropdown, Space, Avatar } from 'antd';
import { UserOutlined, LogoutOutlined } from '@ant-design/icons';
import { Outlet, useNavigate, useLocation } from 'react-router-dom';
import { useAuthStore } from '@/stores/authStore';
import { useProjectStore, getLastProjectId } from '@/stores/projectStore';
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

  // 首次进入主布局：确保有当前用户与项目列表，自动恢复上次项目。
  useEffect(() => {
    if (!user) {
      authApi.me().then(setUser).catch(() => undefined);
    }
    if (projects.length === 0) {
      projectApi.list().then((list) => {
        setProjects(list);
        // 自动选择上次项目或第一个项目
        if (!currentProject && list.length > 0) {
          const lastId = getLastProjectId();
          const last = lastId ? list.find((p) => p.id === lastId) : null;
          setCurrentProject(last ?? list[0]);
        }
      }).catch(() => undefined);
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
      <Header style={{
        display: 'flex', alignItems: 'center', gap: 16, paddingInline: 16,
        background: 'var(--ink)', height: 48, lineHeight: '48px',
      }}>
        <div style={{ color: '#fff', fontWeight: 700, fontSize: 15, whiteSpace: 'nowrap', letterSpacing: '0.02em' }}>
          仿真版本管理平台
        </div>
        <Select
          style={{ minWidth: 180 }}
          size="small"
          placeholder="选择项目"
          value={currentProject?.id}
          options={projects.map((p) => ({ value: p.id, label: p.name }))}
          onChange={(id) => {
            const p = projects.find((x) => x.id === id) ?? null;
            setCurrentProject(p);
          }}
        />
        <Menu
          theme="dark"
          mode="horizontal"
          selectedKeys={[menuKey]}
          style={{ flex: 1, minWidth: 0, background: 'transparent', borderBottom: 'none' }}
          onClick={({ key }) => navigate(key)}
          items={[
            { key: '/projects', label: '项目列表' },
            { key: '/tree', label: '迭代图谱', disabled: !currentProject },
            { key: '/templates', label: '模板管理', disabled: !currentProject },
            { key: '/settings', label: '设置', disabled: !currentProject },
          ]}
        />
        <GlobalSearch />
        <Dropdown
          menu={{
            items: [{ key: 'logout', icon: <LogoutOutlined />, label: '登出', onClick: onLogout }],
          }}
        >
          <Space style={{ color: '#fff', cursor: 'pointer', fontSize: 13 }}>
            <Avatar size={24} icon={<UserOutlined />} style={{ background: 'var(--blue)' }} />
            {user?.displayName || user?.username || '用户'}
          </Space>
        </Dropdown>
      </Header>
      <Content style={{ padding: 16, background: 'var(--bg)' }}>
        <Outlet />
      </Content>
    </Layout>
  );
}
