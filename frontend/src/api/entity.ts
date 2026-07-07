import { get, post, put, del } from './http';
import type {
  ChildStrategy,
  EntityCreateRequest,
  EntityTreeNode,
  EntityUpdateRequest,
  EntityVO,
} from '@/types/api';

export const entityApi = {
  tree: (pid: string) => get<EntityTreeNode[]>(`/projects/${pid}/entities/tree`),
  get: (pid: string, id: string) => get<EntityVO>(`/projects/${pid}/entities/${id}`),
  create: (pid: string, req: EntityCreateRequest) =>
    post<EntityVO>(`/projects/${pid}/entities`, req),
  update: (pid: string, id: string, req: EntityUpdateRequest) =>
    put<EntityVO>(`/projects/${pid}/entities/${id}`, req),
  remove: (pid: string, id: string, childStrategy: ChildStrategy) =>
    del<void>(`/projects/${pid}/entities/${id}`, { params: { childStrategy } }),
  path: (pid: string, id: string) => get<EntityVO[]>(`/projects/${pid}/entities/${id}/path`),
  toggleMilestone: (pid: string, id: string) =>
    put<EntityVO>(`/projects/${pid}/entities/${id}/milestone`),
  setStatus: (pid: string, id: string, status: string | null) =>
    put<EntityVO>(`/projects/${pid}/entities/${id}/status`, { status }),
  reparent: (pid: string, entityId: string, body: { parentId: string | null; parentRelationTemplateId?: string; parentRelationRemark?: string }) =>
    put<EntityVO>(`/projects/${pid}/entities/${entityId}/parent`, body),
};
