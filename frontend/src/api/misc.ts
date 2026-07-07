import instance, { get, post, put, del } from './http';
import type {
  AssetVO,
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
};

export const statsApi = {
  get: (pid: string, numberFieldKey?: string) =>
    get<ProjectStatsVO>(`/projects/${pid}/stats`, {
      params: numberFieldKey ? { numberFieldKey } : undefined,
    }),
};

export const searchApi = {
  search: (pid: string, keyword: string) =>
    get<SearchHit[]>(`/projects/${pid}/search`, { params: { keyword } }),
};

export const logApi = {
  list: (pid: string) => get<OperationLogVO[]>(`/projects/${pid}/logs`),
};

export const portApi = {
  export: (pid: string) => get<ProjectExport>(`/projects/${pid}/export`),
  import: (pid: string, data: ProjectExport) => post<void>(`/projects/${pid}/import`, data),
};
