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
 * 若某节点可见，则其所有祖先也强制可见（否则子节点会”悬空”）。
 * 空条件表示该维度不过滤。
 */
export function computeVisibleIds(
  roots: EntityTreeNode[],
  _filter: Pick<TreeFilter, 'statuses' | 'templateIds' | 'milestoneOnly'>,
): Set<string> {
  // 有过滤条件时仍显示全部节点（不隐藏，改为变淡）
  const visible = new Set<string>();
  flattenTree(roots).forEach((n) => visible.add(n.id));
  return visible;
}

/**
 * 计算真正匹配过滤条件的节点 id 集合（不含因祖先补齐而强制可见的节点）。
 * 空过滤条件时返回全部节点。
 */
export function computeMatchedIds(
  roots: EntityTreeNode[],
  filter: Pick<TreeFilter, 'statuses' | 'templateIds' | 'milestoneOnly'>,
): Set<string> {
  const noFilter =
    filter.statuses.length === 0 && filter.templateIds.length === 0 && !filter.milestoneOnly;

  const matched = new Set<string>();
  const matches = (n: EntityTreeNode): boolean => {
    if (filter.statuses.length > 0) {
      if (!n.status || !filter.statuses.includes(n.status as EntityStatus)) return false;
    }
    if (filter.templateIds.length > 0 && !filter.templateIds.includes(n.templateId)) return false;
    if (filter.milestoneOnly && n.isMilestone !== 1) return false;
    return true;
  };

  const walk = (n: EntityTreeNode) => {
    if (noFilter || matches(n)) {
      matched.add(n.id);
    }
    n.children?.forEach(walk);
  };
  roots.forEach(walk);
  return matched;
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
