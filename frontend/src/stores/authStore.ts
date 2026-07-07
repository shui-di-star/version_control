import { create } from 'zustand';
import { persist } from 'zustand/middleware';
import type { UserInfo } from '@/types/api';

interface AuthState {
  token: string | null;
  user: UserInfo | null;
  setToken: (token: string) => void;
  setUser: (user: UserInfo | null) => void;
  clear: () => void;
}

// token 与当前用户持久化到 localStorage，刷新不丢登录态。
export const useAuthStore = create<AuthState>()(
  persist(
    (set) => ({
      token: null,
      user: null,
      setToken: (token) => set({ token }),
      setUser: (user) => set({ user }),
      clear: () => set({ token: null, user: null }),
    }),
    { name: 'vcs-auth' },
  ),
);
