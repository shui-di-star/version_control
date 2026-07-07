import type { EntityStatus, EntityTreeNode, RelationVO } from '@/types/api';
import type { TreeFilter } from '@/stores/treeStore';

export type { TreeFilter };

/** 展平嵌套树为节点数组。 */
export function flattenTree(nodes: EntityTreeNode[]): EntityTreeNode[] {
  const out: EntityTreeNode[] = [];
  const walk = (n: EntityTreeNode) => {
    out.push(n);
    n.children?.forEach(walk);
  };
  nodes.forEach(walk);
  return out;
}

/**
 * 依过滤条件计算「可见节点 id 集合」。
 * 规则：一个节点可见 = 自身通过 状态/类型/里程碑 过滤。为保持树连通，
 * 若某节点可见，则其所有祖先也强制可见（否则子节点会“悬空”）。
 * 空条件表示该维度不过滤。
 */
export function computeVisibleIds(
  roots: EntityTreeNode[],
  filter: Pick<TreeFilter, 'statuses' | 'templateIds' | 'milestoneOnly'>,
): Set<string> {
  const visible = new Set<string>();
  const parentMap = new Map<string, string | undefined>();

  const matches = (n: EntityTreeNode): boolean => {
    if (filter.statuses.length > 0) {
      if (!n.status || !filter.statuses.includes(n.status as EntityStatus)) return false;
    }
    if (filter.templateIds.length > 0 && !filter.templateIds.includes(n.templateId)) return false;
    if (filter.milestoneOnly && n.isMilestone !== 1) return false;
    return true;
  };

  const walk = (n: EntityTreeNode, parentId?: string) => {
    parentMap.set(n.id, parentId);
    if (matches(n)) {
      // 自身可见，向上补齐祖先。
      let cur: string | undefined = n.id;
      while (cur && !visible.has(cur)) {
        visible.add(cur);
        cur = parentMap.get(cur);
      }
    }
    n.children?.forEach((c) => walk(c, n.id));
  };
  roots.forEach((r) => walk(r));

  // 无任何过滤条件时全部可见。
  const noFilter =
    filter.statuses.length === 0 && filter.templateIds.length === 0 && !filter.milestoneOnly;
  if (noFilter) {
    flattenTree(roots).forEach((n) => visible.add(n.id));
  }
  return visible;
}

/** 依关系类型过滤，返回可见关系。 */
export function filterRelations(
  relations: RelationVO[],
  relationTemplateIds: string[],
  visibleNodeIds: Set<string>,
): RelationVO[] {
  return relations.filter((r) => {
    if (relationTemplateIds.length > 0 && !relationTemplateIds.includes(r.templateId)) return false;
    // 两端节点都可见才画线。
    return visibleNodeIds.has(r.fromEntityId) && visibleNodeIds.has(r.toEntityId);
  });
}
