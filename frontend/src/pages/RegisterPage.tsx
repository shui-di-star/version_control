import { App, Form, Input, Button } from 'antd';
import { useNavigate, Link } from 'react-router-dom';
import { authApi } from '@/api/auth';
import type { RegisterRequest } from '@/types/api';

export default function RegisterPage() {
  const { message } = App.useApp();
  const navigate = useNavigate();

  const onFinish = async (values: RegisterRequest) => {
    await authApi.register(values);
    message.success('注册成功，请登录');
    navigate('/login', { replace: true });
  };

  return (
    <div style={{ display: 'flex', justifyContent: 'center', alignItems: 'center', minHeight: '100vh', background: 'var(--bg)' }}>
      <div className="theme-panel" style={{ width: 380, padding: '32px 28px' }}>
        <h2 style={{ textAlign: 'center', fontSize: 22, fontWeight: 700, color: 'var(--text)', marginBottom: 24 }}>
          注册账号
        </h2>
        <Form layout="vertical" onFinish={onFinish}>
          <Form.Item name="username" label="用户名" rules={[{ required: true, min: 3, max: 64 }]}>
            <Input autoFocus size="large" />
          </Form.Item>
          <Form.Item name="password" label="密码" rules={[{ required: true, min: 6, max: 64 }]}>
            <Input.Password size="large" />
          </Form.Item>
          <Form.Item name="displayName" label="显示名" rules={[{ max: 64 }]}>
            <Input size="large" />
          </Form.Item>
          <Form.Item name="email" label="邮箱" rules={[{ max: 128 }]}>
            <Input size="large" />
          </Form.Item>
          <Button type="primary" htmlType="submit" block size="large">
            注册
          </Button>
        </Form>
        <div style={{ textAlign: 'center', marginTop: 16, fontSize: 13, color: 'var(--muted)' }}>
          已有账号？<Link to="/login">登录</Link>
        </div>
      </div>
    </div>
  );
}
