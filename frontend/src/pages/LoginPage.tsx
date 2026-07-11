import { useEffect } from 'react';
import { App, Form, Input, Button } from 'antd';
import { useNavigate, Link } from 'react-router-dom';
import { authApi } from '@/api/auth';
import { useAuthStore } from '@/stores/authStore';
import type { LoginRequest } from '@/types/api';

export default function LoginPage() {
  const { message } = App.useApp();
  const navigate = useNavigate();
  const token = useAuthStore((s) => s.token);
  const setToken = useAuthStore((s) => s.setToken);
  const setUser = useAuthStore((s) => s.setUser);

  // 已登录访问 /login 直接跳项目页。
  useEffect(() => {
    if (token) navigate('/tree', { replace: true });
  }, [token, navigate]);

  const onFinish = async (values: LoginRequest) => {
    const resp = await authApi.login(values);
    setToken(resp.token);
    const me = await authApi.me();
    setUser(me);
    message.success('登录成功');
    navigate('/tree', { replace: true });
  };

  return (
    <div style={{ display: 'flex', justifyContent: 'center', alignItems: 'center', minHeight: '100vh', background: 'var(--bg)' }}>
      <div className="theme-panel" style={{ width: 380, padding: '32px 28px' }}>
        <h2 style={{ textAlign: 'center', fontSize: 22, fontWeight: 700, color: 'var(--text)', marginBottom: 24 }}>
          仿真版本管理平台
        </h2>
        <Form layout="vertical" onFinish={onFinish}>
          <Form.Item name="username" label="用户名" rules={[{ required: true }]}>
            <Input autoFocus size="large" />
          </Form.Item>
          <Form.Item name="password" label="密码" rules={[{ required: true }]}>
            <Input.Password size="large" />
          </Form.Item>
          <Button type="primary" htmlType="submit" block size="large">
            登录
          </Button>
        </Form>
        <div style={{ textAlign: 'center', marginTop: 16, fontSize: 13, color: 'var(--muted)' }}>
          还没有账号？<Link to="/register">注册</Link>
        </div>
      </div>
    </div>
  );
}
