import instance, { get, post, put, del } from './http';
import type {
  AssetVO,
  GlobalStatsVO,
  OperationLogVO,
  ProjectExport,
  ProjectStatsVO,
  RelationCreateRequest,
  RelationUpdateRequest,
  RelationVO,
  SearchHit,
} from '@/types/api';

export const relationApi = {
  list: (pid: string) => get<RelationVO[]>(`/projects/${pid}/relations`),
  get: (pid: string, id: string) => get<RelationVO>(`/projects/${pid}/relations/${id}`),
  create: (pid: string, req: RelationCreateRequest) =>
    post<RelationVO>(`/projects/${pid}/relations`, req),
  update: (pid: string, id: string, req: RelationUpdateRequest) =>
    put<RelationVO>(`/projects/${pid}/relations/${id}`, req),
  remove: (pid: string, id: string) => del<void>(`/projects/${pid}/relations/${id}`),
};

export const assetApi = {
  list: (pid: string, entityId: string) =>
    get<AssetVO[]>(`/projects/${pid}/entities/${entityId}/assets`),
  upload: (pid: string, entityId: string, assetType: string, file: File) => {
    const form = new FormData();
    form.append('assetType', assetType);
    form.append('file', file);
    return post<AssetVO>(`/projects/${pid}/entities/${entityId}/assets`, form, {
      headers: { 'Content-Type': 'multipart/form-data' },
    });
  },
  uploadText: (pid: string, entityId: string, fileName: string, contentText: string) => {
    const form = new FormData();
    form.append('fileName', fileName);
    form.append('contentText', contentText);
    return post<AssetVO>(`/projects/${pid}/entities/${entityId}/assets/text`, form, {
      headers: { 'Content-Type': 'multipart/form-data' },
    });
  },
  // 下载走原始实例（blob，绕过 Result 解包）。
  download: (pid: string, assetId: string) =>
    instance.get(`/projects/${pid}/assets/${assetId}/download`, { responseType: 'blob' }),
  remove: (pid: string, assetId: string) => del<void>(`/projects/${pid}/assets/${assetId}`),
  /** 图片内联预览 URL（供 <img src> 使用）。 */
  previewUrl: (pid: string, assetId: string) => `/api/projects/${pid}/assets/${assetId}/preview`,
};

export const statsApi = {
  get: (pid: string, numberFieldKey?: string) =>
    get<ProjectStatsVO>(`/projects/${pid}/stats`, {
      params: numberFieldKey ? { numberFieldKey } : undefined,
    }),
  global: () => get<GlobalStatsVO>('/stats/global'),
};

export const searchApi = {
  search: (pid: string, keyword: string, startDate?: string, endDate?: string) => {
    const params: Record<string, string> = { keyword };
    if (startDate) params.startDate = startDate;
    if (endDate) params.endDate = endDate;
    return get<SearchHit[]>(`/projects/${pid}/search`, { params });
  },
};

export const logApi = {
  list: (pid: string) => get<OperationLogVO[]>(`/projects/${pid}/logs`),
};

export const portApi = {
  export: (pid: string) => get<ProjectExport>(`/projects/${pid}/export`),
  import: (pid: string, data: ProjectExport) => post<void>(`/projects/${pid}/import`, data),
};

/** 属性图片接口（与产出物 asset 完全独立）。 */
export const attrImageApi = {
  upload: (pid: string, file: File) => {
    const form = new FormData();
    form.append('file', file);
    return post<{ objectKey: string }>(`/projects/${pid}/attr-images`, form, {
      headers: { 'Content-Type': 'multipart/form-data' },
    });
  },
  /** 属性图片预览 URL（公开端点，供 <img src> 使用）。 */
  previewUrl: (pid: string, objectKey: string) =>
    `/api/projects/${pid}/attr-images/preview?key=${encodeURIComponent(objectKey)}`,
};
