import { Form, Input, Button, Card, Typography, message } from 'antd';
import { useNavigate, Link } from 'react-router-dom';
import { authApi } from '@/api/auth';
import type { RegisterRequest } from '@/types/api';

export default function RegisterPage() {
  const navigate = useNavigate();

  const onFinish = async (values: RegisterRequest) => {
    await authApi.register(values);
    message.success('注册成功，请登录');
    navigate('/login', { replace: true });
  };

  return (
    <div style={{ display: 'flex', justifyContent: 'center', paddingTop: 100 }}>
      <Card style={{ width: 360 }}>
        <Typography.Title level={3} style={{ textAlign: 'center' }}>
          注册
        </Typography.Title>
        <Form layout="vertical" onFinish={onFinish}>
          <Form.Item
            name="username"
            label="用户名"
            rules={[{ required: true, min: 3, max: 64 }]}
          >
            <Input autoFocus />
          </Form.Item>
          <Form.Item
            name="password"
            label="密码"
            rules={[{ required: true, min: 6, max: 64 }]}
          >
            <Input.Password />
          </Form.Item>
          <Form.Item name="displayName" label="显示名" rules={[{ max: 64 }]}>
            <Input />
          </Form.Item>
          <Form.Item name="email" label="邮箱" rules={[{ max: 128 }]}>
            <Input />
          </Form.Item>
          <Button type="primary" htmlType="submit" block>
            注册
          </Button>
        </Form>
        <div style={{ textAlign: 'center', marginTop: 12 }}>
          已有账号？<Link to="/login">登录</Link>
        </div>
      </Card>
    </div>
  );
}
