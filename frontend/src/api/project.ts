import { get, post, put, del } from './http';
import type {
  MemberAddRequest,
  MemberVO,
  ProjectCreateRequest,
  ProjectUpdateRequest,
  ProjectVO,
} from '@/types/api';

export const projectApi = {
  list: () => get<ProjectVO[]>('/projects'),
  create: (req: ProjectCreateRequest) => post<ProjectVO>('/projects', req),
  update: (id: string, req: ProjectUpdateRequest) => put<ProjectVO>(`/projects/${id}`, req),
  remove: (id: string) => del<void>(`/projects/${id}`),
};

export const memberApi = {
  list: (pid: string) => get<MemberVO[]>(`/projects/${pid}/members`),
  add: (pid: string, req: MemberAddRequest) => post<MemberVO>(`/projects/${pid}/members`, req),
  remove: (pid: string, uid: string) => del<void>(`/projects/${pid}/members/${uid}`),
};
