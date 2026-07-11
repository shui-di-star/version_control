import React from 'react';
import ReactDOM from 'react-dom/client';
import { BrowserRouter } from 'react-router-dom';
import { ConfigProvider } from 'antd';
import zhCN from 'antd/locale/zh_CN';
import App from './App';
import 'antd/dist/reset.css';
import './styles/theme.css';

ReactDOM.createRoot(document.getElementById('root')!).render(
  <React.StrictMode>
    <ConfigProvider
      locale={zhCN}
      theme={{
        token: {
          colorPrimary: '#276ef1',
          colorError: '#c73b3b',
          colorSuccess: '#168a4a',
          colorWarning: '#b26a00',
          colorBgContainer: '#ffffff',
          colorBgLayout: '#f5f7fa',
          colorBorder: '#d8e0ea',
          colorText: '#17202a',
          colorTextSecondary: '#657386',
          borderRadius: 8,
          fontFamily: 'Inter, "SF Pro Display", "Segoe UI", "PingFang SC", "Microsoft YaHei", sans-serif',
        },
      }}
    >
      <BrowserRouter future={{ v7_startTransition: true, v7_relativeSplatPath: true }}>
        <App />
      </BrowserRouter>
    </ConfigProvider>
  </React.StrictMode>,
);
