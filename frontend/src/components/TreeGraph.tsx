import { forwardRef, useCallback, useEffect, useImperativeHandle, useRef, useState } from 'react';
import { Graph, treeToGraphData } from '@antv/g6';
import type { GraphData, TreeData, IElementEvent } from '@antv/g6';
import type { EntityTemplateVO, EntityTreeNode, RelationTemplateVO, RelationVO, FieldSchema } from '@/types/api';
import { STATUS_META } from '@/utils/constants';
import { safeParse } from '@/utils/json';

interface LineStyle {
  color?: string;
  dash?: boolean;
  width?: number;
}

interface Props {
  roots: EntityTreeNode[];
  relations: RelationVO[];
  entityTemplates: EntityTemplateVO[];
  relationTemplates: RelationTemplateVO[];
  visibleNodeIds: Set<string>;
  selectedId: string | null;
  highlightIds: string[];
  onSelectNode: (id: string) => void;
  onSelectEdge?: (edgeData: EdgeInfo) => void;
}

export interface EdgeInfo {
  type: 'parent' | 'relation';
  sourceId: string;
  targetId: string;
  sourceName: string;
  targetName: string;
  templateId: string;
  templateName: string;
  templateColor: string;
  relationId?: string;
  remark?: string;
}

const VIRTUAL_ROOT = '__virtual_root__';
const HIGHLIGHT_EDGE_COLOR = '#faad14';
const HIGHLIGHT_SHADOW = 'rgba(250, 173, 20, 0.5)';

function nd(d: Record<string, unknown>, key: string): any {
  return (d as any)?.[key];
}

function getShowOnCardText(node: EntityTreeNode, entityTemplates: EntityTemplateVO[]): string {
  const tpl = entityTemplates.find((t) => t.id === node.templateId);
  if (!tpl?.fieldSchema) return '';
  const schema = safeParse<FieldSchema>(tpl.fieldSchema, { fields: [] });
  const cardFields = schema.fields?.filter((f) => f.showOnCard) ?? [];
  if (cardFields.length === 0) return '';
  const attrs = safeParse<Record<string, unknown>>(node.attributes, {});
  const parts: string[] = [];
  for (const f of cardFields) {
    const val = attrs[f.key];
    if (val != null && val !== '') {
      parts.push(`${f.label}: ${val}`);
    }
  }
  return parts.join('\n');
}

/** 生成节点卡片 HTML */
function buildCardHTML(d: Record<string, unknown>): string {
  const name = String(nd(d, 'name') ?? d.id);
  const status = nd(d, 'status') as string | null;
  const isMilestone = nd(d, 'isMilestone') === 1;
  const cardText = nd(d, 'showOnCardText') as string;
  const childCount = (nd(d, 'childCount') as number) ?? 0;
  const collapsed = nd(d, 'collapsed') === true;
  const statusColor = status
    ? (STATUS_META as any)[status]?.color ?? '#d9d9d9'
    : '#d9d9d9';
  const statusLabel = status
    ? (STATUS_META as any)[status]?.label ?? ''
    : '';

  const milestoneHtml = isMilestone
    ? '<span style="color:#faad14;font-size:14px;flex-shrink:0">★</span>'
    : '';

  const statusHtml = statusLabel
    ? `<span style="color:${statusColor};font-size:11px;font-weight:500;flex-shrink:0">${statusLabel}</span>`
    : '';

  const toggleHtml = childCount > 0
    ? `<span data-toggle-id="${d.id}" title="${collapsed ? '展开子节点' : '折叠子节点'}" style="
        display:inline-flex;align-items:center;justify-content:center;
        width:18px;height:18px;border-radius:50%;
        background:#f5f5f5;color:#999;font-size:11px;
        cursor:pointer;flex-shrink:0;margin-left:auto;
        transition:background 0.15s;
      ">${collapsed ? `+${childCount}` : '−'}</span>`
    : '';

  const attrsHtml = cardText
    ? `<div style="height:1px;background:#f0f0f0;margin:0 10px"></div>
       <div style="font-size:11px;color:#333;padding:3px 10px 7px;line-height:1.6;white-space:pre-wrap">${cardText.replace(/</g, '&lt;')}</div>`
    : '';

  return `<div data-node-id="${d.id}" style="
    background:#fff;
    border-radius:6px;
    box-shadow:0 1px 4px rgba(0,0,0,0.1);
    overflow:hidden;
    min-width:120px;
    cursor:pointer;
    transition:box-shadow 0.2s;
  ">
    <div style="height:4px;background:${statusColor};border-radius:6px 6px 0 0"></div>
    <div style="display:flex;align-items:center;padding:7px 10px 5px;gap:4px">
      <span style="font-weight:600;font-size:13px;color:#333;overflow:hidden;text-overflow:ellipsis;white-space:nowrap">${name.replace(/</g, '&lt;')}</span>
      ${milestoneHtml}
      ${statusHtml}
      ${toggleHtml}
    </div>
    ${attrsHtml}
  </div>`;
}

function countVisibleChildren(n: EntityTreeNode, visible: Set<string>): number {
  return (n.children ?? []).filter((c) => visible.has(c.id)).length;
}

function buildTreeData(roots: EntityTreeNode[], visible: Set<string>, entityTemplates: EntityTemplateVO[], collapsedIds: Set<string>): TreeData {
  const conv = (n: EntityTreeNode): TreeData => {
    const visibleChildren = (n.children ?? []).filter((c) => visible.has(c.id));
    const isCollapsed = collapsedIds.has(n.id);
    return {
      id: n.id,
      name: n.name,
      status: n.status ?? null,
      templateId: n.templateId,
      isMilestone: n.isMilestone ?? 0,
      parentRelationTemplateId: n.parentRelationTemplateId ?? null,
      parentRelationRemark: n.parentRelationRemark ?? null,
      showOnCardText: getShowOnCardText(n, entityTemplates),
      childCount: visibleChildren.length,
      collapsed: isCollapsed,
      children: isCollapsed ? [] : visibleChildren.map(conv),
    };
  };
  const visibleRoots = roots.filter((r) => visible.has(r.id)).map(conv);
  return { id: VIRTUAL_ROOT, name: '', children: visibleRoots };
}

export interface GraphHandle {
  fitView: () => void;
  zoomIn: () => void;
  zoomOut: () => void;
  zoomTo: (ratio: number) => void;
  getZoom: () => number;
  expandAll: () => void;
  collapseAll: () => void;
}

const TreeGraph = forwardRef<GraphHandle, Props>(function TreeGraph({
  roots,
  relations,
  entityTemplates,
  relationTemplates,
  visibleNodeIds,
  selectedId,
  highlightIds,
  onSelectNode,
  onSelectEdge,
}, ref) {
  const containerRef = useRef<HTMLDivElement>(null);
  const graphRef = useRef<Graph | null>(null);
  const [collapsedIds, setCollapsedIds] = useState<Set<string>>(new Set());
  const collapsedRef = useRef(collapsedIds);
  collapsedRef.current = collapsedIds;

  const toggleCollapse = useCallback((nodeId: string) => {
    setCollapsedIds((prev) => {
      const next = new Set(prev);
      if (next.has(nodeId)) next.delete(nodeId);
      else next.add(nodeId);
      return next;
    });
  }, []);

  const collectAllParentIds = useCallback((): Set<string> => {
    const ids = new Set<string>();
    const walk = (nodes: EntityTreeNode[]) => {
      for (const n of nodes) {
        const visibleChildren = (n.children ?? []).filter((c) => visibleNodeIds.has(c.id));
        if (visibleChildren.length > 0) {
          ids.add(n.id);
          walk(visibleChildren);
        }
      }
    };
    walk(roots);
    return ids;
  }, [roots, visibleNodeIds]);

  const buildGraphData = (): GraphData => {
    const treeData = buildTreeData(roots, visibleNodeIds, entityTemplates, collapsedRef.current);
    const gd = treeToGraphData(treeData);

    gd.nodes = (gd.nodes ?? []).filter((n) => n.id !== VIRTUAL_ROOT);
    gd.edges = (gd.edges ?? []).filter(
      (e) => e.source !== VIRTUAL_ROOT && e.target !== VIRTUAL_ROOT,
    );

    gd.edges = (gd.edges ?? []).map((e) => {
      const targetNode = gd.nodes?.find((n) => n.id === e.target);
      const tplId = (targetNode as any)?.parentRelationTemplateId;
      const tpl = tplId ? relationTemplates.find((t) => t.id === tplId) : null;
      const ls = tpl ? safeParse<LineStyle>(tpl.lineStyle, {}) : {};
      return {
        ...e,
        data: { ...(e.data ?? {}), parentEdge: true, templateId: tplId ?? '', templateName: tpl?.name ?? '', lineColor: ls.color || '#bbb', lineDash: ls.dash, lineWidth: ls.width ?? 1.5 },
      };
    });

    const relEdges = relations
      .filter((r) => visibleNodeIds.has(r.fromEntityId) && visibleNodeIds.has(r.toEntityId))
      .map((r) => {
        const tpl = relationTemplates.find((t) => t.id === r.templateId);
        const ls = safeParse<LineStyle>(tpl?.lineStyle, {});
        return {
          id: `rel-${r.id}`,
          source: r.fromEntityId,
          target: r.toEntityId,
          data: { relation: true, relationId: r.id, templateId: r.templateId, templateName: tpl?.name ?? '', remark: r.remark ?? '', lineColor: ls.color || '#fa8c16', lineDash: ls.dash, lineWidth: ls.width ?? 2, directed: tpl?.directed !== 0 },
        };
      });
    gd.edges = [...(gd.edges ?? []), ...relEdges];
    return gd;
  };

  useEffect(() => {
    if (!containerRef.current) return;
    let graph: Graph | null = null;
    const timer = window.setTimeout(() => {
      graph = new Graph({
        container: containerRef.current!,
        autoResize: true,
        data: buildGraphData(),
        layout: { type: 'antv-dagre', rankdir: 'TB', nodesep: 40, ranksep: 40 },
        node: {
          type: 'html',
          style: {
            size: (d: Record<string, unknown>) => {
              const cardText = nd(d, 'showOnCardText') as string;
              if (!cardText) return [160, 40];
              const lines = cardText.split('\n').length;
              return [180, 44 + lines * 18 + 10];
            },
            innerHTML: (d: Record<string, unknown>) => buildCardHTML(d),
          },
        },
        edge: {
          type: 'cubic-vertical',
          style: (d: Record<string, unknown>) => {
            const data = (d as any)?.data ?? {};
            const color = data.lineColor || '#bbb';
            return {
              stroke: color,
              lineWidth: data.lineWidth ?? 1.5,
              lineDash: data.lineDash ? [4, 4] : undefined,
              endArrow: data.directed ?? false,
              labelText: data.templateName ?? '',
              labelFontSize: 10,
              labelFill: color,
            };
          },
          state: {
            highlight: {
              stroke: HIGHLIGHT_EDGE_COLOR,
              lineWidth: 3,
              shadowColor: HIGHLIGHT_SHADOW,
              shadowBlur: 6,
            },
          },
        },
        behaviors: ['zoom-canvas', 'drag-canvas'],
      });
      graphRef.current = graph;
      graph.on('node:click', (evt: IElementEvent) => {
        const id = evt.target?.id as string | undefined;
        if (id) onSelectNode(id);
      });
      graph.on('edge:click', (evt: IElementEvent) => {
        if (!onSelectEdge) return;
        const edgeId = evt.target?.id as string | undefined;
        if (!edgeId) return;
        const edgeData = graph!.getEdgeData(edgeId);
        if (!edgeData) return;
        const sourceId = String(edgeData.source);
        const targetId = String(edgeData.target);
        const sourceNode = graph!.getNodeData(sourceId);
        const targetNode = graph!.getNodeData(targetId);
        const data = edgeData.data as any;
        if (data?.relation) {
          onSelectEdge({
            type: 'relation',
            sourceId,
            targetId,
            sourceName: String((sourceNode as any)?.name ?? sourceId),
            targetName: String((targetNode as any)?.name ?? targetId),
            templateId: data.templateId ?? '',
            templateName: data.templateName ?? '',
            templateColor: data.lineColor ?? '#fa8c16',
            relationId: data.relationId ?? '',
            remark: data.remark ?? '',
          });
        } else {
          const remark = (targetNode as any)?.parentRelationRemark ?? '';
          onSelectEdge({
            type: 'parent',
            sourceId,
            targetId,
            sourceName: String((sourceNode as any)?.name ?? sourceId),
            targetName: String((targetNode as any)?.name ?? targetId),
            templateId: data?.templateId ?? '',
            templateName: data?.templateName ?? '',
            templateColor: data?.lineColor ?? '#bbb',
            remark,
          });
        }
      });
      graph.render().catch(() => undefined);
    }, 0);
    return () => {
      window.clearTimeout(timer);
      graph?.destroy();
      graphRef.current = null;
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  // 数据变化时更新。
  useEffect(() => {
    const graph = graphRef.current;
    if (!graph || !graph.rendered) return;
    graph.setData(buildGraphData());
    graph.render().catch(() => undefined);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [roots, relations, entityTemplates, relationTemplates, visibleNodeIds, collapsedIds]);

  // 选中/高亮状态更新。
  useEffect(() => {
    const graph = graphRef.current;
    if (!graph || !graph.rendered) return;
    const container = containerRef.current;
    if (!container) return;

    const highlightSet = new Set(highlightIds);

    // 节点高亮：通过 DOM 操作设置 box-shadow
    const allCards = container.querySelectorAll<HTMLElement>('[data-node-id]');
    allCards.forEach((card) => {
      const nodeId = card.getAttribute('data-node-id');
      if (nodeId === selectedId) {
        card.style.boxShadow = '0 0 0 2px #1890ff, 0 0 12px rgba(24,144,255,0.35)';
      } else if (nodeId && highlightSet.has(nodeId)) {
        card.style.boxShadow = '0 0 0 2px #faad14, 0 0 10px rgba(250,173,20,0.4)';
      } else {
        card.style.boxShadow = '0 1px 4px rgba(0,0,0,0.1)';
      }
    });

    // 边高亮：两端都在高亮集合中
    const edgeStateMap: Record<string, string[]> = {};
    graph.getEdgeData().forEach((e) => {
      const src = String(e.source);
      const tgt = String(e.target);
      const states: string[] = [];
      if (highlightSet.has(src) && highlightSet.has(tgt)) {
        states.push('highlight');
      }
      edgeStateMap[e.id as string] = states;
    });
    graph.setElementState(edgeStateMap);

    if (selectedId) {
      graph.focusElement(selectedId).catch(() => undefined);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [selectedId, highlightIds]);

  // 折叠/展开按钮点击：事件委托
  useEffect(() => {
    const container = containerRef.current;
    if (!container) return;
    const handler = (e: MouseEvent) => {
      const target = (e.target as HTMLElement).closest<HTMLElement>('[data-toggle-id]');
      if (!target) return;
      e.stopPropagation();
      const nodeId = target.getAttribute('data-toggle-id');
      if (nodeId) toggleCollapse(nodeId);
    };
    container.addEventListener('click', handler, true);
    return () => container.removeEventListener('click', handler, true);
  }, [toggleCollapse]);

  useImperativeHandle(ref, () => ({
    fitView: () => { graphRef.current?.fitView().catch(() => undefined); },
    zoomIn: () => { graphRef.current?.zoomBy(1.25).catch(() => undefined); },
    zoomOut: () => { graphRef.current?.zoomBy(0.8).catch(() => undefined); },
    zoomTo: (ratio: number) => { graphRef.current?.zoomTo(ratio).catch(() => undefined); },
    getZoom: () => graphRef.current?.getZoom() ?? 1,
    expandAll: () => { setCollapsedIds(new Set()); },
    collapseAll: () => { setCollapsedIds(collectAllParentIds()); },
  }));

  return <div ref={containerRef} style={{ width: '100%', height: '100%' }} />;
});

export default TreeGraph;
