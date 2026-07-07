import { create } from 'zustand';
import type {
  EntityStatus,
  EntityTemplateVO,
  EntityTreeNode,
  RelationTemplateVO,
  RelationVO,
  SearchHit,
} from '@/types/api';

/** 过滤条件：在已加载全树上做前端显隐，不请求后端。 */
export interface TreeFilter {
  statuses: EntityStatus[]; // 空 = 不按状态过滤
  templateIds: string[]; // 空 = 不按实体类型过滤
  relationTemplateIds: string[]; // 空 = 不按关系类型过滤
  milestoneOnly: boolean;
}

/** 搜索命中连线时，由 GlobalSearch 写入，TreeViewPage 消费后清除。 */
export interface EdgeSearchTarget {
  sourceType: 'RELATION' | 'PARENT_RELATION';
  fromEntityId: string;
  toEntityId: string;
  relationId?: string;
}

interface TreeState {
  tree: EntityTreeNode[];
  relations: RelationVO[];
  entityTemplates: EntityTemplateVO[];
  relationTemplates: RelationTemplateVO[];
  selectedId: string | null;
  filter: TreeFilter;
  searchHits: SearchHit[];
  pathHighlight: string[];
  selectedEdgeFromSearch: EdgeSearchTarget | null;

  setTree: (tree: EntityTreeNode[]) => void;
  setRelations: (relations: RelationVO[]) => void;
  setEntityTemplates: (t: EntityTemplateVO[]) => void;
  setRelationTemplates: (t: RelationTemplateVO[]) => void;
  select: (id: string | null) => void;
  setFilter: (patch: Partial<TreeFilter>) => void;
  resetFilter: () => void;
  setSearchHits: (hits: SearchHit[]) => void;
  setPathHighlight: (ids: string[]) => void;
  setSelectedEdgeFromSearch: (t: EdgeSearchTarget | null) => void;
}

const EMPTY_FILTER: TreeFilter = {
  statuses: [],
  templateIds: [],
  relationTemplateIds: [],
  milestoneOnly: false,
};

export const useTreeStore = create<TreeState>((set) => ({
  tree: [],
  relations: [],
  entityTemplates: [],
  relationTemplates: [],
  selectedId: null,
  filter: EMPTY_FILTER,
  searchHits: [],
  pathHighlight: [],
  selectedEdgeFromSearch: null,

  setTree: (tree) => set({ tree }),
  setRelations: (relations) => set({ relations }),
  setEntityTemplates: (entityTemplates) => set({ entityTemplates }),
  setRelationTemplates: (relationTemplates) => set({ relationTemplates }),
  select: (selectedId) => set({ selectedId }),
  setFilter: (patch) => set((s) => ({ filter: { ...s.filter, ...patch } })),
  resetFilter: () => set({ filter: EMPTY_FILTER }),
  setSearchHits: (searchHits) => set({ searchHits }),
  setPathHighlight: (pathHighlight) => set({ pathHighlight }),
  setSelectedEdgeFromSearch: (selectedEdgeFromSearch) => set({ selectedEdgeFromSearch }),
}));
