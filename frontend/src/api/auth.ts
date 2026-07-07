import { get, post } from './http';
import type { LoginRequest, LoginResponse, RegisterRequest, UserInfo } from '@/types/api';

export const authApi = {
  register: (req: RegisterRequest) => post<UserInfo>('/auth/register', req),
  login: (req: LoginRequest) => post<LoginResponse>('/auth/login', req),
  me: () => get<UserInfo>('/auth/me'),
  logout: () => post<void>('/auth/logout'),
};
