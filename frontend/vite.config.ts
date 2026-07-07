import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
import path from 'node:path';

// 开发代理：/api 转发到后端 8080，避免跨域。
export default defineConfig({
  plugins: [react()],
  resolve: {
    alias: {
      '@': path.resolve(__dirname, 'src'),
    },
  },
  server: {
    port: 5173,
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },
  build: {
    rollupOptions: {
      output: {
        // 把重型三方库拆成独立可缓存 chunk：g6 仅随 /tree 页按需加载，
        // react/antd 变动少、可长期缓存，业务代码更新不必让用户重下它们。
        manualChunks: {
          g6: ['@antv/g6'],
          antd: ['antd', '@ant-design/icons'],
          react: ['react', 'react-dom', 'react-router-dom'],
        },
      },
    },
  },
});
