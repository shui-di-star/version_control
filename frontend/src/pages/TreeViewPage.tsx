import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { App, Empty, Radio, Typography, Space, Select } from 'antd';
import {
  LeftOutlined,
  RightOutlined,
} from '@ant-design/icons';
import { entityApi } from '@/api/entity';
import { relationApi } from '@/api/misc';
import { entityTemplateApi, relationTemplateApi } from '@/api/template';
import { useProjectStore } from '@/stores/projectStore';
import { useTreeStore } from '@/stores/treeStore';
import { computeVisibleIds, computeMatchedIds } from '@/utils/tree';
import TreeGraph from '@/components/TreeGraph';
import type { EdgeInfo, GraphHandle } from '@/components/TreeGraph';
import GraphToolbar from '@/components/GraphToolbar';
import DetailPanel from '@/components/DetailPanel';
import EdgeDetailPanel from '@/components/EdgeDetailPanel';
import CreateEntityModal from '@/components/CreateEntityModal';
import CompareBar from '@/components/CompareBar';
import NodeActions from '@/components/NodeActions';
import LeftSidebar from '@/components/LeftSidebar';
import type { ChildStrategy } from '@/types/api';

const LEFT_DEFAULT = 220;
const LEFT_MIN = 160;
const LEFT_MAX = 400;
const RIGHT_DEFAULT = 340;
const RIGHT_MIN = 260;
const RIGHT_MAX = 600;

/** 可拖拽分隔条样式 */
const resizerStyle: React.CSSProperties = {
  width: 5,
  height: '100%',
  cursor: 'col-resize',
  background: 'transparent',
  flexShrink: 0,
  zIndex: 5,
  transition: 'background 0.15s',
};
const resizerHoverBg = '#d0d0d0';
const resizerActiveBg = '#1890ff';

/** 折叠按钮样式 */
const collapseToggleStyle: React.CSSProperties = {
  position: 'absolute',
  top: '50%',
  transform: 'translateY(-50%)',
  width: 16,
  height: 48,
  display: 'flex',
  alignItems: 'center',
  justifyContent: 'center',
  background: '#fff',
  border: '1px solid #e8e8e8',
  borderRadius: 4,
  cursor: 'pointer',
  zIndex: 10,
  fontSize: 10,
  color: '#999',
  boxShadow: '0 1px 4px rgba(0,0,0,0.08)',
};

function useResizer(
  defaultWidth: number,
  minWidth: number,
  maxWidth: number,
  direction: 'left' | 'right',
): {
  width: number;
  collapsed: boolean;
  setCollapsed: (v: boolean) => void;
  onMouseDown: (e: React.MouseEvent) => void;
  resizerHover: boolean;
  setResizerHover: (v: boolean) => void;
  dragging: boolean;
} {
  const [width, setWidth] = useState(defaultWidth);
  const [collapsed, setCollapsed] = useState(false);
  const [dragging, setDragging] = useState(false);
  const [resizerHover, setResizerHover] = useState(false);
  const startRef = useRef({ startX: 0, startW: 0 });

  const onMouseDown = useCallback(
    (e: React.MouseEvent) => {
      e.preventDefault();
      startRef.current = { startX: e.clientX, startW: width };
      setDragging(true);

      const onMove = (ev: MouseEvent) => {
        const delta = ev.clientX - startRef.current.startX;
        const newW = direction === 'left'
          ? startRef.current.startW + delta
          : startRef.current.startW - delta;
        setWidth(Math.max(minWidth, Math.min(maxWidth, newW)));
      };
      const onUp = () => {
        setDragging(false);
        window.removeEventListener('mousemove', onMove);
        window.removeEventListener('mouseup', onUp);
      };
      window.addEventListener('mousemove', onMove);
      window.addEventListener('mouseup', onUp);
    },
    [width, minWidth, maxWidth, direction],
  );

  return { width, collapsed, setCollapsed, onMouseDown, resizerHover, setResizerHover, dragging };
}

export default function TreeViewPage() {
  const { message, modal } = App.useApp();
  const currentProject = useProjectStore((s) => s.currentProject);
  const canWrite = useProjectStore((s) => s.hasRole('EDITOR'));
  const pid = currentProject?.id;
  const graphRef = useRef<GraphHandle>(null);

  const tree = useTreeStore((s) => s.tree);
  const setTree = useTreeStore((s) => s.setTree);
  const relations = useTreeStore((s) => s.relations);
  const setRelations = useTreeStore((s) => s.setRelations);
  const entityTemplates = useTreeStore((s) => s.entityTemplates);
  const setEntityTemplates = useTreeStore((s) => s.setEntityTemplates);
  const relationTemplates = useTreeStore((s) => s.relationTemplates);
  const setRelationTemplates = useTreeStore((s) => s.setRelationTemplates);
  const selectedId = useTreeStore((s) => s.selectedId);
  const select = useTreeStore((s) => s.select);
  const filter = useTreeStore((s) => s.filter);
  const pathHighlight = useTreeStore((s) => s.pathHighlight);
  const setPathHighlight = useTreeStore((s) => s.setPathHighlight);
  const searchHits = useTreeStore((s) => s.searchHits);
  const selectedEdgeFromSearch = useTreeStore((s) => s.selectedEdgeFromSearch);
  const setSelectedEdgeFromSearch = useTreeStore((s) => s.setSelectedEdgeFromSearch);
  const connectRelationTemplateId = useTreeStore((s) => s.connectRelationTemplateId);
  const setConnectRelationTemplateId = useTreeStore((s) => s.setConnectRelationTemplateId);
  const graphMode = useTreeStore((s) => s.graphMode);
  const compareIds = useTreeStore((s) => s.compareIds);
  const toggleCompare = useTreeStore((s) => s.toggleCompare);
  const setCopySourceId = useTreeStore((s) => s.setCopySourceId);

  const [createOpen, setCreateOpen] = useState(false);
  const [createParent, setCreateParent] = useState<string | null>(null);
  const [saveState, setSaveState] = useState<'saving' | 'saved' | null>(null);
  const [refreshKey, setRefreshKey] = useState(0);
  const [selectedEdge, setSelectedEdge] = useState<EdgeInfo | null>(null);

  const loadTree = useCallback(async () => {
    if (!pid) return;
    const [t, r] = await Promise.all([entityApi.tree(pid), relationApi.list(pid)]);
    setTree(t);
    setRelations(r);
  }, [pid, setTree, setRelations]);

  useEffect(() => {
    if (!pid) return;
    setPathHighlight([]);
    select(null);
    Promise.all([
      entityApi.tree(pid),
      relationApi.list(pid),
      entityTemplateApi.list(pid),
      relationTemplateApi.list(pid),
    ]).then(([t, r, et, rt]) => {
      setTree(t);
      setRelations(r);
      setEntityTemplates(et);
      setRelationTemplates(rt);
    });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [pid]);

  // 搜索命中连线后，自动构建 EdgeInfo 展示右侧详情
  useEffect(() => {
    if (!selectedEdgeFromSearch) return;
    const { sourceType, fromEntityId, toEntityId, relationId } = selectedEdgeFromSearch;
    // 查找节点名称
    const findName = (nodes: typeof tree, id: string): string => {
      for (const n of nodes) {
        if (n.id === id) return n.name;
        const found = findName(n.children ?? [], id);
        if (found) return found;
      }
      return id;
    };
    const sourceName = findName(tree, fromEntityId);
    const targetName = findName(tree, toEntityId);

    if (sourceType === 'PARENT_RELATION') {
      // 父子关系：从目标节点找 parentRelationTemplateId
      const findNode = (nodes: typeof tree, id: string): typeof tree[0] | null => {
        for (const n of nodes) {
          if (n.id === id) return n;
          const found = findNode(n.children ?? [], id);
          if (found) return found;
        }
        return null;
      };
      const targetNode = findNode(tree, toEntityId);
      const tplId = targetNode?.parentRelationTemplateId ?? '';
      const tpl = relationTemplates.find((t) => t.id === tplId);
      setSelectedEdge({
        type: 'parent',
        sourceId: fromEntityId,
        targetId: toEntityId,
        sourceName,
        targetName,
        templateId: tplId,
        templateName: tpl?.name ?? '',
        templateColor: '#bbb',
        remark: targetNode?.parentRelationRemark ?? '',
      });
    } else {
      // 语义关系
      const rel = relations.find((r) => r.id === relationId);
      const tpl = rel ? relationTemplates.find((t) => t.id === rel.templateId) : null;
      setSelectedEdge({
        type: 'relation',
        sourceId: fromEntityId,
        targetId: toEntityId,
        sourceName,
        targetName,
        templateId: tpl?.id ?? '',
        templateName: tpl?.name ?? '',
        templateColor: '#fa8c16',
        relationId,
        remark: rel?.remark ?? '',
      });
    }
    setSelectedEdgeFromSearch(null);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [selectedEdgeFromSearch]);

  const refreshAll = () => {
    loadTree();
    setRefreshKey((k) => k + 1);
  };

  const visibleNodeIds = useMemo(
    () => computeVisibleIds(tree, filter),
    [tree, filter],
  );

  const dimmedNodeIds = useMemo(
    () => {
      // Search-based dimming: when search hits exist, dim everything except matched entities
      if (searchHits.length > 0) {
        const matchedIds = new Set<string>();
        for (const hit of searchHits) {
          if (hit.entityId) matchedIds.add(hit.entityId);
          if (hit.fromEntityId) matchedIds.add(hit.fromEntityId);
          if (hit.toEntityId) matchedIds.add(hit.toEntityId);
        }
        const dimmed = new Set<string>();
        visibleNodeIds.forEach((id) => {
          if (!matchedIds.has(id)) dimmed.add(id);
        });
        return dimmed;
      }
      // Filter-based dimming
      const matched = computeMatchedIds(tree, filter);
      const dimmed = new Set<string>();
      visibleNodeIds.forEach((id) => {
        if (!matched.has(id)) dimmed.add(id);
      });
      return dimmed;
    },
    [tree, filter, visibleNodeIds, searchHits],
  );

  const highlightIds = useMemo(() => {
    const ids = new Set(pathHighlight);
    // Also highlight matched entities from search
    if (searchHits.length > 0) {
      for (const hit of searchHits) {
        if (hit.entityId) ids.add(hit.entityId);
        if (hit.fromEntityId) ids.add(hit.fromEntityId);
        if (hit.toEntityId) ids.add(hit.toEntityId);
      }
    }
    return Array.from(ids);
  }, [pathHighlight, searchHits]);

  const onDeleteEntity = (strategy: ChildStrategy) => {
    if (!pid || !selectedId) return;
    entityApi.remove(pid, selectedId, strategy).then(() => {
      message.success('已删除');
      select(null);
      refreshAll();
    });
  };

  const confirmDelete = () => {
    let strategy: ChildStrategy = 'CASCADE';
    modal.confirm({
      title: '删除节点',
      content: (
        <div>
          <p>选择子节点处理策略：</p>
          <Radio.Group defaultValue="CASCADE" onChange={(e) => (strategy = e.target.value)}>
            <Space direction="vertical">
              <Radio value="CASCADE">CASCADE：递归删除整棵子树</Radio>
              <Radio value="PROMOTE">PROMOTE：仅删本节点，子节点上提</Radio>
            </Space>
          </Radio.Group>
        </div>
      ),
      onOk: () => onDeleteEntity(strategy),
    });
  };

  /** 连线模式：从 sourceId 拖到 targetId，弹窗选关系类型后执行 reparent */
  const handleConnect = (sourceId: string, targetId: string) => {
    if (!pid || !canWrite) return;

    // 查找节点名称
    const findName = (nodes: typeof tree, id: string): string => {
      for (const n of nodes) {
        if (String(n.id) === String(id)) return n.name;
        const found = findName(n.children ?? [], id);
        if (found) return found;
      }
      return id;
    };
    const sourceName = findName(tree, sourceId);
    const targetName = findName(tree, targetId);

    // 如果已在工具栏选好了关系模板，直接执行
    if (connectRelationTemplateId) {
      entityApi.reparent(pid, sourceId, {
        parentId: targetId,
        parentRelationTemplateId: connectRelationTemplateId,
      }).then(() => {
        message.success('连线成功');
        refreshAll();
      }).catch(() => {
        message.error('连线失败');
      });
      return;
    }

    // 未选关系模板，弹窗让用户选
    let selectedTemplateId: string | undefined;
    modal.confirm({
      title: '建立父子关系',
      width: 480,
      content: (
        <div style={{ marginTop: 12 }}>
          <div style={{ marginBottom: 12, fontSize: 13, color: '#666' }}>
            将 <strong>{sourceName}</strong> 连接到 <strong>{targetName}</strong>（作为父节点）
          </div>
          <div style={{ marginBottom: 8 }}>
            <label>关系类型（必填）：</label>
            <Select
              style={{ width: '100%' }}
              placeholder="选择关系类型"
              onChange={(v: string) => { selectedTemplateId = v; }}
              options={relationTemplates.map((t) => ({ value: t.id, label: t.name }))}
            />
          </div>
        </div>
      ),
      onOk: async () => {
        if (!selectedTemplateId) {
          message.error('请选择关系类型');
          throw new Error('missing template');
        }
        await entityApi.reparent(pid, sourceId, {
          parentId: targetId,
          parentRelationTemplateId: selectedTemplateId,
        });
        message.success('连线成功');
        refreshAll();
      },
    });
  };

  const handleSelectNode = (id: string) => {
    select(id);
    setSelectedEdge(null);
  };

  const handleSelectEdge = async (edge: EdgeInfo) => {
    // 连线模式：点击父子连线时，更改其关系模板
    if (connectRelationTemplateId && edge.type === 'parent' && canWrite && pid) {
      try {
        await entityApi.reparent(pid, edge.targetId, {
          parentId: edge.sourceId,
          parentRelationTemplateId: connectRelationTemplateId,
        });
        message.success('已更改连线关系类型');
        setConnectRelationTemplateId(null);
        refreshAll();
        return;
      } catch {
        message.error('更改连线关系类型失败');
        return;
      }
    }
    setSelectedEdge(edge);
    select(null);
  };

  /** 路径追溯：高亮从根到选中节点的路径 */
  const handleTracePath = async () => {
    if (!pid || !selectedId) return;
    const path = await entityApi.path(pid, selectedId);
    setPathHighlight(path.map((p) => p.id));
    message.success(`已高亮根→当前路径（${path.length} 个节点）`);
  };

  const handleClearPath = () => {
    setPathHighlight([]);
  };

  /** 拖放复制卡片到图谱 */
  const handleDropCreate = async (rawData: string, dropTargetNodeId: string | null) => {
    if (!pid || !canWrite) return;
    try {
      const data = JSON.parse(rawData) as {
        sourceEntityId: string;
        templateId: string;
        name: string;
        attributes: string;
      };

      if (dropTargetNodeId) {
        // 拖到节点上 → 弹窗选关系模板 → 创建为子节点
        let selectedTemplateId: string | undefined;
        modal.confirm({
          title: '创建副本为子节点',
          width: 480,
          content: (
            <div style={{ marginTop: 12 }}>
              <div style={{ marginBottom: 12, fontSize: 13, color: '#666' }}>
                将 <strong>{data.name}（副本）</strong> 创建为目标节点的子节点
              </div>
              <div>
                <label>关系类型（必填）：</label>
                <Select
                  style={{ width: '100%' }}
                  placeholder="选择关系类型"
                  onChange={(v: string) => { selectedTemplateId = v; }}
                  options={relationTemplates.map((t) => ({ value: t.id, label: t.name }))}
                />
              </div>
            </div>
          ),
          onOk: async () => {
            if (!selectedTemplateId) {
              message.error('请选择关系类型');
              throw new Error('missing template');
            }
            await entityApi.create(pid, {
              templateId: data.templateId,
              parentId: dropTargetNodeId,
              name: data.name + '（副本）',
              attributes: data.attributes,
              parentRelationTemplateId: selectedTemplateId,
            });
            message.success('副本创建成功');
            setCopySourceId(null);
            refreshAll();
          },
        });
      } else {
        // 拖到空白处 → 创建为根节点
        await entityApi.create(pid, {
          templateId: data.templateId,
          name: data.name + '（副本）',
          attributes: data.attributes,
        });
        message.success('副本创建成功（单节点）');
        setCopySourceId(null);
        refreshAll();
      }
    } catch {
      // JSON parse error or other
    }
  };

  const leftResizer = useResizer(LEFT_DEFAULT, LEFT_MIN, LEFT_MAX, 'left');
  const rightResizer = useResizer(RIGHT_DEFAULT, RIGHT_MIN, RIGHT_MAX, 'right');

  const showRight = !!(selectedId || selectedEdge);

  if (!currentProject) {
    return <Empty description="请先在顶部选择一个项目" />;
  }

  return (
    <div
      style={{
        height: 'calc(100vh - 96px)',
        display: 'flex',
        background: '#fff',
        overflow: 'hidden',
        userSelect: (leftResizer.dragging || rightResizer.dragging) ? 'none' : undefined,
      }}
    >
      {/* ---- 左侧操作栏 ---- */}
      {!leftResizer.collapsed && (
        <div
          style={{
            width: leftResizer.width,
            minWidth: leftResizer.width,
            borderRight: '1px solid var(--line)',
            overflow: 'hidden',
            background: 'var(--panel)',
          }}
        >
          <LeftSidebar
            onCreateEntity={(parentId) => {
              setCreateParent(parentId);
              setCreateOpen(true);
            }}
            refreshKey={refreshKey}
          />
          {selectedId && canWrite && (
            <div style={{ padding: '8px 12px', borderTop: '1px solid var(--line)' }}>
              <NodeActions
                nodeId={selectedId}
                onCreateChild={(parentId) => {
                  setCreateParent(parentId);
                  setCreateOpen(true);
                }}
                onDelete={confirmDelete}
                onRefresh={refreshAll}
              />
            </div>
          )}
        </div>
      )}

      {/* 左侧拖拽条 + 折叠按钮 */}
      <div style={{ position: 'relative', flexShrink: 0, width: leftResizer.collapsed ? 16 : 5 }}>
        {!leftResizer.collapsed && (
          <div
            style={{
              ...resizerStyle,
              background: leftResizer.dragging ? resizerActiveBg : leftResizer.resizerHover ? resizerHoverBg : 'transparent',
            }}
            onMouseDown={leftResizer.onMouseDown}
            onMouseEnter={() => leftResizer.setResizerHover(true)}
            onMouseLeave={() => leftResizer.setResizerHover(false)}
          />
        )}
        <div
          style={{
            ...collapseToggleStyle,
            left: 0,
          }}
          onClick={() => leftResizer.setCollapsed(!leftResizer.collapsed)}
          title={leftResizer.collapsed ? '展开左侧栏' : '收起左侧栏'}
        >
          {leftResizer.collapsed ? <RightOutlined /> : <LeftOutlined />}
        </div>
      </div>

      {/* ---- 中间内容区 ---- */}
      <div style={{ flex: 1, display: 'flex', flexDirection: 'column', minWidth: 0, overflow: 'hidden' }}>
        <GraphToolbar
          graphRef={graphRef}
          selectedId={selectedId}
          onTracePath={handleTracePath}
          onClearPath={handleClearPath}
          hasPathHighlight={pathHighlight.length > 0}
        />
        <div style={{ flex: 1, position: 'relative', minHeight: 0, overflow: 'hidden' }}>
          {graphMode === 'connect' && (
            <div style={{
              position: 'absolute', top: 12, left: '50%', transform: 'translateX(-50%)',
              zIndex: 10, background: '#e6f7ff', border: '1px solid #91d5ff',
              borderRadius: 6, padding: '6px 16px', fontSize: 13, color: '#0050b3',
              boxShadow: '0 2px 8px rgba(0,0,0,0.1)', pointerEvents: 'none',
            }}>
              从子节点出发连接父节点
            </div>
          )}
          {tree.length === 0 ? (
            <Empty style={{ marginTop: 80 }} description="空项目，请新增实体" />
          ) : (
            <>
              <TreeGraph
                ref={graphRef}
                roots={tree}
                relations={relations}
                entityTemplates={entityTemplates}
                relationTemplates={relationTemplates}
                visibleNodeIds={visibleNodeIds}
                dimmedNodeIds={dimmedNodeIds}
                selectedId={selectedId}
                highlightIds={highlightIds}
                graphMode={graphMode}
                onSelectNode={handleSelectNode}
                onSelectEdge={handleSelectEdge}
                onConnect={handleConnect}
                compareIds={compareIds}
                onToggleCompare={toggleCompare}
                onDropCreate={handleDropCreate}
              />
            </>
          )}
        </div>
        <div style={{ padding: '4px 12px', borderTop: '1px solid #f0f0f0' }}>
          <Typography.Text type="secondary">
            {saveState === 'saving' ? '保存中…' : saveState === 'saved' ? '已保存' : ' '}
          </Typography.Text>
        </div>
      </div>

      {/* 右侧拖拽条 + 折叠按钮 */}
      {showRight && (
        <div style={{ position: 'relative', flexShrink: 0, width: rightResizer.collapsed ? 16 : 5 }}>
          {!rightResizer.collapsed && (
            <div
              style={{
                ...resizerStyle,
                background: rightResizer.dragging ? resizerActiveBg : rightResizer.resizerHover ? resizerHoverBg : 'transparent',
              }}
              onMouseDown={rightResizer.onMouseDown}
              onMouseEnter={() => rightResizer.setResizerHover(true)}
              onMouseLeave={() => rightResizer.setResizerHover(false)}
            />
          )}
          <div
            style={{
              ...collapseToggleStyle,
              left: 0,
            }}
            onClick={() => rightResizer.setCollapsed(!rightResizer.collapsed)}
            title={rightResizer.collapsed ? '展开右侧栏' : '收起右侧栏'}
          >
            {rightResizer.collapsed ? <LeftOutlined /> : <RightOutlined />}
          </div>
        </div>
      )}

      {/* ---- 右侧详情栏 ---- */}
      {showRight && !rightResizer.collapsed && (
        <div
          style={{
            width: rightResizer.width,
            minWidth: rightResizer.width,
            borderLeft: '1px solid #f0f0f0',
            background: '#fff',
            overflow: 'hidden',
            position: 'relative',
            zIndex: 2,
          }}
        >
          {selectedId && (
            <DetailPanel
              key={selectedId}
              entityId={selectedId}
              onChanged={refreshAll}
              onSaveState={setSaveState}
              onDelete={confirmDelete}
            />
          )}
          {selectedEdge && (
            <EdgeDetailPanel
              key={`${selectedEdge.sourceId}-${selectedEdge.targetId}`}
              edge={selectedEdge}
              onChanged={refreshAll}
            />
          )}
        </div>
      )}

      {createOpen && (
        <CreateEntityModal
          open={createOpen}
          parentId={createParent}
          onClose={() => setCreateOpen(false)}
          onCreated={refreshAll}
        />
      )}
      <CompareBar />
    </div>
  );
}
