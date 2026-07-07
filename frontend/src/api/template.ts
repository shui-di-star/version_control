import { get, post, put, del } from './http';
import type {
  EntityTemplateRequest,
  EntityTemplateVO,
  RelationTemplateRequest,
  RelationTemplateVO,
} from '@/types/api';

export const entityTemplateApi = {
  list: (pid: string) => get<EntityTemplateVO[]>(`/projects/${pid}/entity-templates`),
  get: (pid: string, id: string) => get<EntityTemplateVO>(`/projects/${pid}/entity-templates/${id}`),
  create: (pid: string, req: EntityTemplateRequest) =>
    post<EntityTemplateVO>(`/projects/${pid}/entity-templates`, req),
  update: (pid: string, id: string, req: EntityTemplateRequest) =>
    put<EntityTemplateVO>(`/projects/${pid}/entity-templates/${id}`, req),
  remove: (pid: string, id: string) => del<void>(`/projects/${pid}/entity-templates/${id}`),
};

export const relationTemplateApi = {
  list: (pid: string) => get<RelationTemplateVO[]>(`/projects/${pid}/relation-templates`),
  get: (pid: string, id: string) =>
    get<RelationTemplateVO>(`/projects/${pid}/relation-templates/${id}`),
  create: (pid: string, req: RelationTemplateRequest) =>
    post<RelationTemplateVO>(`/projects/${pid}/relation-templates`, req),
  update: (pid: string, id: string, req: RelationTemplateRequest) =>
    put<RelationTemplateVO>(`/projects/${pid}/relation-templates/${id}`, req),
  remove: (pid: string, id: string) => del<void>(`/projects/${pid}/relation-templates/${id}`),
};
