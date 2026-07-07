import axios, { type AxiosRequestConfig } from 'axios';
import { message } from 'antd';
import type { Result } from '@/types/api';
import { useAuthStore } from '@/stores/authStore';

// baseURL=/api，开发环境经 Vite 代理转发到后端 8080。
const instance = axios.create({
  baseURL: '/api',
  timeout: 30000,
});

// 请求拦截器：自动注入 Bearer token（从 authStore 取）。
instance.interceptors.request.use((config) => {
  const token = useAuthStore.getState().token;
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

// 响应拦截器：统一解包 Result。
// - HTTP 200 且 code===0：返回 data
// - HTTP 200 但 code!==0：业务错误，弹 message 并 reject
// - HTTP 401：清 token 跳登录
instance.interceptors.response.use(
  (response) => {
    // blob 下载等非 Result 响应，直接透传（由调用方处理）。
    if (response.config.responseType === 'blob') {
      return response;
    }
    const body = response.data as Result<unknown>;
    if (body && typeof body === 'object' && 'code' in body) {
      if (body.code === 0) {
        return body.data;
      }
      message.error(body.message || '请求失败');
      return Promise.reject(body);
    }
    return response.data;
  },
  (error) => {
    const status = error?.response?.status;
    if (status === 401) {
      useAuthStore.getState().clear();
      message.error('登录已失效，请重新登录');
      if (!location.pathname.startsWith('/login')) {
        location.href = '/login';
      }
    } else if (status === 403) {
      message.error('无权限执行该操作');
    } else {
      const body = error?.response?.data;
      message.error(body?.message || error.message || '网络错误');
    }
    return Promise.reject(error);
  },
);

/** 返回解包后的 data（T）。 */
export function get<T>(url: string, config?: AxiosRequestConfig): Promise<T> {
  return instance.get(url, config) as unknown as Promise<T>;
}

export function post<T>(url: string, data?: unknown, config?: AxiosRequestConfig): Promise<T> {
  return instance.post(url, data, config) as unknown as Promise<T>;
}

export function put<T>(url: string, data?: unknown, config?: AxiosRequestConfig): Promise<T> {
  return instance.put(url, data, config) as unknown as Promise<T>;
}

export function del<T>(url: string, config?: AxiosRequestConfig): Promise<T> {
  return instance.delete(url, config) as unknown as Promise<T>;
}

export default instance;
