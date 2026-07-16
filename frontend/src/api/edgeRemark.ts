import { get, post, put, del } from './http';
import type { EdgeRemarkVO, EdgeRemarkImageVO } from '@/types/api';
import { useAuthStore } from '@/stores/authStore';

export const edgeRemarkApi = {
  list: (pid: string, entityId: string) =>
    get<EdgeRemarkVO[]>(`/projects/${pid}/entities/${entityId}/edge-remarks`),

  create: (pid: string, entityId: string, content: string) =>
    post<EdgeRemarkVO>(`/projects/${pid}/entities/${entityId}/edge-remarks`, { content }),

  update: (pid: string, remarkId: string, content: string) =>
    put<EdgeRemarkVO>(`/projects/${pid}/edge-remarks/${remarkId}`, { content }),

  remove: (pid: string, remarkId: string) =>
    del<void>(`/projects/${pid}/edge-remarks/${remarkId}`),

  uploadImage: (pid: string, remarkId: string, file: File) => {
    const form = new FormData();
    form.append('file', file);
    return post<EdgeRemarkImageVO>(`/projects/${pid}/edge-remarks/${remarkId}/images`, form, {
      headers: { 'Content-Type': 'multipart/form-data' },
    });
  },

  deleteImage: (pid: string, imageId: string) =>
    del<void>(`/projects/${pid}/edge-remark-images/${imageId}`),

  /** 图片预览 URL（直接用于 img src，附带 token 查询参数绕过 header 限制）。 */
  imagePreviewUrl: (pid: string, imageId: string) => {
    const token = useAuthStore.getState().token;
    return `/api/projects/${pid}/edge-remark-images/${imageId}/preview?token=${encodeURIComponent(token ?? '')}`;
  },
};
