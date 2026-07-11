import { lazy, Suspense } from 'react';
import { Routes, Route, Navigate } from 'react-router-dom';
import { App as AntdApp, Spin } from 'antd';
import RequireAuth from '@/router/RequireAuth';
import MainLayout from '@/components/MainLayout';
import LoginPage from '@/pages/LoginPage';
import RegisterPage from '@/pages/RegisterPage';
import ProjectListPage from '@/pages/ProjectListPage';
import { setGlobalMessage } from '@/utils/appMessage';

// 重型页面按需加载：TreeViewPage 依赖 G6（~2MB），仅进入 /tree 时才拉取其 chunk。
const TreeViewPage = lazy(() => import('@/pages/TreeViewPage'));
const TemplatePage = lazy(() => import('@/pages/TemplatePage'));
const SettingsPage = lazy(() => import('@/pages/SettingsPage'));

function PageFallback() {
  return (
    <div style={{ display: 'flex', justifyContent: 'center', paddingTop: 120 }}>
      <Spin size="large" />
    </div>
  );
}

/** 在 AntdApp 内部初始化全局 message 实例，供 http 拦截器等非组件代码使用。 */
function GlobalMessageInit() {
  const { message } = AntdApp.useApp();
  setGlobalMessage(message);
  return null;
}

export default function App() {
  return (
    <AntdApp>
      <GlobalMessageInit />
      <Routes>
        <Route path="/login" element={<LoginPage />} />
        <Route path="/register" element={<RegisterPage />} />
        <Route
          element={
            <RequireAuth>
              <MainLayout />
            </RequireAuth>
          }
        >
          <Route path="/projects" element={<ProjectListPage />} />
          <Route
            path="/tree"
            element={
              <Suspense fallback={<PageFallback />}>
                <TreeViewPage />
              </Suspense>
            }
          />
          <Route
            path="/templates"
            element={
              <Suspense fallback={<PageFallback />}>
                <TemplatePage />
              </Suspense>
            }
          />
          <Route
            path="/settings"
            element={
              <Suspense fallback={<PageFallback />}>
                <SettingsPage />
              </Suspense>
            }
          />
        </Route>
        <Route path="*" element={<Navigate to="/tree" replace />} />
      </Routes>
    </AntdApp>
  );
}
