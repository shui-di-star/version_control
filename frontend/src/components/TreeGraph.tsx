import { forwardRef, useCallback, useEffect, useImperativeHandle, useRef, useState } from 'react';
import { Graph, treeToGraphData } from '@antv/g6';
import type { GraphData, TreeData, IElementEvent, EdgeData } from '@antv/g6';
import type { EntityTemplateVO, EntityTreeNode, RelationTemplateVO, RelationVO, FieldSchema } from '@/types/api';
import type { GraphMode } from '@/stores/treeStore';
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
  dimmedNodeIds?: Set<string>;
  selectedId: string | null;
  highlightIds: string[];
  graphMode: GraphMode;
  onSelectNode: (id: string) => void;
  onSelectEdge?: (edgeData: EdgeInfo) => void;
  /** 连线模式回调：从 sourceId 拖到 targetId，请求建立父子关系 */
  onConnect?: (sourceId: string, targetId: string) => void;
  /** 参与对比的节点 id 列表 */
  compareIds?: string[];
  /** 切换对比选中 */
  onToggleCompare?: (id: string) => void;
  /** 外部拖放创建：sourceEntityId, dropTargetNodeId(null=根节点) */
  onDropCreate?: (data: string, dropTargetNodeId: string | null) => void;
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
  const cardFields = schema.fields?.filter((f) => f.showOnCard && f.key !== 'card_name') ?? [];
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

/** 生成节点卡片 HTML（对齐示例项目的 .node 风格） */
function buildCardHTML(d: Record<string, unknown>): string {
  const name = String(nd(d, 'name') ?? d.id);
  const status = nd(d, 'status') as string | null;
  const isMilestone = nd(d, 'isMilestone') === 1;
  const cardText = nd(d, 'showOnCardText') as string;
  const childCount = (nd(d, 'childCount') as number) ?? 0;
  const collapsed = nd(d, 'collapsed') === true;
  const cardNumber = nd(d, 'cardNumber') as string;
  const dimmed = nd(d, 'dimmed') === true;
  const isCompared = nd(d, 'isCompared') === true;

  // 状态色条颜色
  const statusColors: Record<string, string> = {
    RECOMMENDED: '#c73b3b',
    DEPRECATED: '#7c8794',
    COMPLETED: '#168a4a',
    SIMULATING: '#b26a00',
  };
  const borderColor = status ? (statusColors[status] ?? '#276ef1') : '#276ef1';
  const statusLabel = status
    ? (STATUS_META as any)[status]?.label ?? ''
    : '';
  const statusColor = status
    ? (STATUS_META as any)[status]?.color ?? '#7c8794'
    : '';

  const toggleHtml = childCount > 0
    ? `<span data-toggle-id="${d.id}" title="${collapsed ? '展开子节点' : '折叠子节点'}" style="
        display:inline-flex;align-items:center;justify-content:center;
        width:18px;height:18px;border-radius:50%;
        background:#f0f2f5;color:#666;font-size:10px;
        cursor:pointer;flex-shrink:0;
        transition:background 0.15s;
      ">${collapsed ? `+${childCount}` : '−'}</span>`
    : '';

  const compareHtml = `<span data-compare-id="${d.id}" title="参与对比" style="
      display:inline-flex;align-items:center;justify-content:center;
      width:16px;height:16px;border-radius:3px;
      border:1.5px solid ${isCompared ? '#1890ff' : '#c0c0c0'};
      background:${isCompared ? '#1890ff' : '#fff'};
      cursor:pointer;flex-shrink:0;
      transition:all 0.15s;font-size:10px;color:#fff;
    ">${isCompared ? '✓' : ''}</span>`;

  // 指标区（showOnCard 属性）
  const metricsHtml = cardText
    ? `<div style="display:grid;grid-template-columns:1fr 1fr;gap:2px 8px;padding:4px 0;margin-top:4px">
        ${cardText.split('\n').map((line: string) => {
          const [label, ...valParts] = line.split(': ');
          const val = valParts.join(': ');
          return `<div><div style="font-size:11px;color:#657386">${(label ?? '').replace(/</g, '&lt;')}</div><div style="font-size:13px;font-weight:600;color:#17202a">${(val ?? '').replace(/</g, '&lt;')}</div></div>`;
        }).join('')}
       </div>`
    : '';

  // 状态 badge
  const badgeHtml = statusLabel
    ? `<div style="margin-top:6px"><span style="
        display:inline-block;padding:2px 10px;border-radius:12px;
        font-size:11px;font-weight:600;
        background:${statusColor}20;color:${statusColor};
      ">${statusLabel}</span></div>`
    : '';

  return `<div data-node-id="${d.id}" style="
    width:206px;
    background:#fff;
    border-radius:8px;
    box-shadow:0 12px 26px rgba(24,39,75,0.1);
    overflow:hidden;
    cursor:pointer;
    border-left:5px solid ${borderColor};
    transition:box-shadow 0.2s, opacity 0.3s;
    opacity:${dimmed ? '0.3' : '1'};
  ">
    <div style="padding:11px 11px 14px">
      <div style="display:flex;align-items:center;justify-content:space-between;margin-bottom:4px">
        <span style="font-size:11px;font-weight:700;color:#657386">${(cardNumber ?? '').replace(/</g, '&lt;')}</span>
        <div style="display:flex;align-items:center;gap:4px">
          ${compareHtml}
          ${isMilestone ? '<span style="color:#b26a00;font-size:12px">★</span>' : ''}
          ${toggleHtml}
        </div>
      </div>
      <div style="font-size:13px;font-weight:700;color:#17202a;overflow:hidden;text-overflow:ellipsis;white-space:nowrap">${name.replace(/</g, '&lt;')}</div>
      ${metricsHtml}
      ${badgeHtml}
    </div>
  </div>`;
}

function buildTreeData(roots: EntityTreeNode[], visible: Set<string>, dimmed: Set<string>, entityTemplates: EntityTemplateVO[], collapsedIds: Set<string>, compareIds: Set<string>): TreeData {
  const conv = (n: EntityTreeNode): TreeData => {
    const visibleChildren = (n.children ?? []).filter((c) => visible.has(c.id));
    const isCollapsed = collapsedIds.has(n.id);
    // 提取 card_number 属性
    const attrs = safeParse<Record<string, unknown>>(n.attributes, {});
    return {
      id: n.id,
      name: n.name,
      status: n.status ?? null,
      templateId: n.templateId,
      isMilestone: n.isMilestone ?? 0,
      parentRelationTemplateId: n.parentRelationTemplateId ?? null,
      parentRelationRemark: n.parentRelationRemark ?? null,
      showOnCardText: getShowOnCardText(n, entityTemplates),
      cardNumber: String(attrs.card_number ?? ''),
      childCount: visibleChildren.length,
      collapsed: isCollapsed,
      dimmed: dimmed.has(n.id),
      isCompared: compareIds.has(n.id),
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
  /** 重置布局：重新运行 layout 算法，恢复整齐排列 */
  resetLayout: () => void;
}

const TreeGraph = forwardRef<GraphHandle, Props>(function TreeGraph({
  roots,
  relations,
  entityTemplates,
  relationTemplates,
  visibleNodeIds,
  dimmedNodeIds,
  selectedId,
  highlightIds,
  graphMode,
  onSelectNode,
  onSelectEdge,
  onConnect,
  compareIds,
  onToggleCompare,
  onDropCreate,
}, ref) {
  const containerRef = useRef<HTMLDivElement>(null);
  const graphRef = useRef<Graph | null>(null);
  const [collapsedIds, setCollapsedIds] = useState<Set<string>>(new Set());
  const collapsedRef = useRef(collapsedIds);
  collapsedRef.current = collapsedIds;

  // 使用 ref 保持最新回调，避免重建图
  const onConnectRef = useRef(onConnect);
  onConnectRef.current = onConnect;
  const onToggleCompareRef = useRef(onToggleCompare);
  onToggleCompareRef.current = onToggleCompare;
  const onDropCreateRef = useRef(onDropCreate);
  onDropCreateRef.current = onDropCreate;
  const onSelectNodeRef = useRef(onSelectNode);
  onSelectNodeRef.current = onSelectNode;
  const onSelectEdgeRef = useRef(onSelectEdge);
  onSelectEdgeRef.current = onSelectEdge;

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
    const treeData = buildTreeData(roots, visibleNodeIds, dimmedNodeIds ?? new Set(), entityTemplates, collapsedRef.current, new Set(compareIds ?? []));
    const gd = treeToGraphData(treeData);

    gd.nodes = (gd.nodes ?? []).filter((n) => n.id !== VIRTUAL_ROOT);
    gd.edges = (gd.edges ?? []).filter(
      (e) => e.source !== VIRTUAL_ROOT && e.target !== VIRTUAL_ROOT,
    );

    gd.edges = (gd.edges ?? []).map((e) => {
      const targetNode = gd.nodes?.find((n) => n.id === e.target);
      const sourceNode = gd.nodes?.find((n) => n.id === e.source);
      const tplId = (targetNode as any)?.parentRelationTemplateId;
      const tpl = tplId ? relationTemplates.find((t) => t.id === tplId) : null;
      const ls = tpl ? safeParse<LineStyle>(tpl.lineStyle, {}) : {};
      const targetStatus = (targetNode as any)?.status ?? null;
      const edgeDimmed = (targetNode as any)?.dimmed || (sourceNode as any)?.dimmed;
      return {
        ...e,
        data: { ...(e.data ?? {}), parentEdge: true, templateId: tplId ?? '', templateName: tpl?.name ?? '', lineColor: ls.color || '#bbb', lineDash: ls.dash, lineWidth: ls.width ?? 1.5, targetStatus, dimmed: edgeDimmed },
      };
    });

    const relEdges = relations
      .filter((r) => visibleNodeIds.has(r.fromEntityId) && visibleNodeIds.has(r.toEntityId))
      .map((r) => {
        const tpl = relationTemplates.find((t) => t.id === r.templateId);
        const ls = safeParse<LineStyle>(tpl?.lineStyle, {});
        const dim = dimmedNodeIds ?? new Set();
        const edgeDimmed = dim.has(r.fromEntityId) || dim.has(r.toEntityId);
        return {
          id: `rel-${r.id}`,
          source: r.fromEntityId,
          target: r.toEntityId,
          data: { relation: true, relationId: r.id, templateId: r.templateId, templateName: tpl?.name ?? '', remark: r.remark ?? '', lineColor: ls.color || '#fa8c16', lineDash: ls.dash, lineWidth: ls.width ?? 2, directed: tpl?.directed !== 0, dimmed: edgeDimmed },
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
        layout: { type: 'antv-dagre', rankdir: 'LR', nodesep: 40, ranksep: 60 },
        node: {
          type: 'html',
          style: {
            size: (d: Record<string, unknown>) => {
              const cardText = nd(d, 'showOnCardText') as string;
              const lines = cardText ? cardText.split('\n').length : 0;
              const baseHeight = 80; // top + title + badge
              const metricsHeight = lines > 0 ? Math.ceil(lines / 2) * 44 + 8 : 0;
              return [216, baseHeight + metricsHeight];
            },
            innerHTML: (d: Record<string, unknown>) => buildCardHTML(d),
          },
        },
        edge: {
          type: 'cubic-horizontal',
          style: (d: Record<string, unknown>) => {
            const data = (d as any)?.data ?? {};
            const color = data.lineColor || '#bbb';
            const isRecommended = data.targetStatus === 'RECOMMENDED';
            const baseWidth = data.lineWidth ?? 1.5;
            const edgeDimmed = data.dimmed === true;
            return {
              stroke: color,
              lineWidth: isRecommended ? Math.max(baseWidth, 2.6) : baseWidth,
              lineDash: data.lineDash ? [4, 4] : undefined,
              endArrow: data.directed ?? false,
              opacity: edgeDimmed ? 0.2 : 1,
              labelText: data.templateName ?? '',
              labelFontSize: 11,
              labelFontWeight: 700,
              labelFill: '#657386',
              labelBackground: true,
              labelBackgroundFill: '#fff',
              labelBackgroundRadius: 3,
              labelPadding: [2, 4],
              labelBackgroundStroke: '#e0e0e0',
              labelBackgroundLineWidth: 0.5,
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
        behaviors: ['zoom-canvas', 'drag-canvas', { type: 'drag-element', dropEffect: 'none' }],
      });
      graphRef.current = graph;
      graph.on('node:click', (evt: IElementEvent) => {
        const id = evt.target?.id as string | undefined;
        if (id) onSelectNodeRef.current(id);
      });
      graph.on('edge:click', (evt: IElementEvent) => {
        if (!onSelectEdgeRef.current) return;
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
          onSelectEdgeRef.current({
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
          onSelectEdgeRef.current({
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

  // 模式切换时更新 behaviors
  useEffect(() => {
    const graph = graphRef.current;
    if (!graph || !graph.rendered) return;

    if (graphMode === 'move') {
      graph.setBehaviors([
        'zoom-canvas',
        'drag-canvas',
        { type: 'drag-element', dropEffect: 'none' },
      ]);
    } else {
      // 连线模式
      graph.setBehaviors([
        'zoom-canvas',
        'drag-canvas',
        {
          type: 'create-edge',
          trigger: 'drag',
          style: {
            stroke: '#1890ff',
            lineWidth: 2,
            lineDash: [6, 4],
            endArrow: true,
          },
          onCreate: (edgeData: EdgeData) => {
            const src = String(edgeData.source);
            const tgt = String(edgeData.target);
            // 不真正添加到图上，由外部回调处理
            if (src && tgt && src !== tgt) {
              // 延迟执行以避免 G6 内部状态冲突
              setTimeout(() => onConnectRef.current?.(src, tgt), 0);
            }
            return undefined;
          },
        },
      ]);
    }
  }, [graphMode]);

  // 数据变化时更新。
  useEffect(() => {
    const graph = graphRef.current;
    if (!graph || !graph.rendered) return;
    graph.setData(buildGraphData());
    graph.render().catch(() => undefined);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [roots, relations, entityTemplates, relationTemplates, visibleNodeIds, dimmedNodeIds, collapsedIds, compareIds]);

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
        card.style.boxShadow = '0 0 0 2px #276ef1, 0 0 12px rgba(39,110,241,0.35)';
      } else if (nodeId && highlightSet.has(nodeId)) {
        card.style.boxShadow = '0 0 0 2px #faad14, 0 0 10px rgba(250,173,20,0.4)';
      } else {
        card.style.boxShadow = '0 12px 26px rgba(24,39,75,0.1)';
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

    // 不再自动平移到选中节点，避免页面频繁跳动
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

  // 对比勾选框点击：事件委托
  useEffect(() => {
    const container = containerRef.current;
    if (!container) return;
    const handler = (e: MouseEvent) => {
      const target = (e.target as HTMLElement).closest<HTMLElement>('[data-compare-id]');
      if (!target) return;
      e.stopPropagation();
      const nodeId = target.getAttribute('data-compare-id');
      if (nodeId) onToggleCompareRef.current?.(nodeId);
    };
    container.addEventListener('click', handler, true);
    return () => container.removeEventListener('click', handler, true);
  }, []);

  useImperativeHandle(ref, () => ({
    fitView: () => { graphRef.current?.fitView().catch(() => undefined); },
    zoomIn: () => { graphRef.current?.zoomBy(1.25).catch(() => undefined); },
    zoomOut: () => { graphRef.current?.zoomBy(0.8).catch(() => undefined); },
    zoomTo: (ratio: number) => { graphRef.current?.zoomTo(ratio).catch(() => undefined); },
    getZoom: () => graphRef.current?.getZoom() ?? 1,
    expandAll: () => { setCollapsedIds(new Set()); },
    collapseAll: () => { setCollapsedIds(collectAllParentIds()); },
    resetLayout: () => {
      const graph = graphRef.current;
      if (!graph || !graph.rendered) return;
      graph.layout().catch(() => undefined);
    },
  }));

  const handleDragOver = useCallback((e: React.DragEvent) => {
    if (e.dataTransfer.types.includes('application/copy-entity')) {
      e.preventDefault();
      e.dataTransfer.dropEffect = 'copy';
    }
  }, []);

  const handleDrop = useCallback((e: React.DragEvent) => {
    const raw = e.dataTransfer.getData('application/copy-entity');
    if (!raw) return;
    e.preventDefault();
    // 判断是否落在某个节点卡片上
    const target = (e.target as HTMLElement).closest<HTMLElement>('[data-node-id]');
    const dropTargetNodeId = target?.getAttribute('data-node-id') ?? null;
    onDropCreateRef.current?.(raw, dropTargetNodeId);
  }, []);

  return <div ref={containerRef} style={{
    width: '100%',
    height: '100%',
    userSelect: 'none',
    cursor: graphMode === 'connect' ? 'crosshair' : undefined,
    background: 'linear-gradient(#edf2f7 1px, transparent 1px), linear-gradient(90deg, #edf2f7 1px, transparent 1px)',
    backgroundSize: '28px 28px',
  }}
    onDragOver={handleDragOver}
    onDrop={handleDrop}
  />;
});

export default TreeGraph;
