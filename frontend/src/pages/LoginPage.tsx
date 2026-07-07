import { useEffect } from 'react';
import { Form, Input, Button, Card, Typography, message } from 'antd';
import { useNavigate, Link } from 'react-router-dom';
import { authApi } from '@/api/auth';
import { useAuthStore } from '@/stores/authStore';
import type { LoginRequest } from '@/types/api';

export default function LoginPage() {
  const navigate = useNavigate();
  const token = useAuthStore((s) => s.token);
  const setToken = useAuthStore((s) => s.setToken);
  const setUser = useAuthStore((s) => s.setUser);

  // 已登录访问 /login 直接跳项目页。
  useEffect(() => {
    if (token) navigate('/projects', { replace: true });
  }, [token, navigate]);

  const onFinish = async (values: LoginRequest) => {
    const resp = await authApi.login(values);
    setToken(resp.token);
    const me = await authApi.me();
    setUser(me);
    message.success('登录成功');
    navigate('/projects', { replace: true });
  };

  return (
    <div style={{ display: 'flex', justifyContent: 'center', paddingTop: 120 }}>
      <Card style={{ width: 360 }}>
        <Typography.Title level={3} style={{ textAlign: 'center' }}>
          登录
        </Typography.Title>
        <Form layout="vertical" onFinish={onFinish}>
          <Form.Item name="username" label="用户名" rules={[{ required: true }]}>
            <Input autoFocus />
          </Form.Item>
          <Form.Item name="password" label="密码" rules={[{ required: true }]}>
            <Input.Password />
          </Form.Item>
          <Button type="primary" htmlType="submit" block>
            登录
          </Button>
        </Form>
        <div style={{ textAlign: 'center', marginTop: 12 }}>
          还没有账号？<Link to="/register">注册</Link>
        </div>
      </Card>
    </div>
  );
}
