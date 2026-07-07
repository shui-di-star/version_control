import { create } from 'zustand';
import type { ProjectRoleName, ProjectVO } from '@/types/api';

interface ProjectState {
  projects: ProjectVO[];
  currentProject: ProjectVO | null;
  setProjects: (projects: ProjectVO[]) => void;
  setCurrentProject: (project: ProjectVO | null) => void;
  /** 当前项目角色，未选项目为 null。 */
  currentRole: () => ProjectRoleName | null;
  /** 角色 ≥ 要求（VIEWER<EDITOR<ADMIN）。 */
  hasRole: (required: ProjectRoleName) => boolean;
}

const RANK: Record<ProjectRoleName, number> = { VIEWER: 1, EDITOR: 2, ADMIN: 3 };

export const useProjectStore = create<ProjectState>((set, getRaw) => ({
  projects: [],
  currentProject: null,
  setProjects: (projects) => set({ projects }),
  setCurrentProject: (project) => set({ currentProject: project }),
  currentRole: () => getRaw().currentProject?.myRole ?? null,
  hasRole: (required) => {
    const role = getRaw().currentProject?.myRole;
    if (!role) return false;
    return RANK[role] >= RANK[required];
  },
}));
