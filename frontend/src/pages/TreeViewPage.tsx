import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { Layout, Button, Space, Empty, Modal, Radio, message, Typography } from 'antd';
import {
  PlusOutlined,
  ExpandOutlined,
  DiffOutlined,
  LeftOutlined,
  RightOutlined,
} from '@ant-design/icons';
import { entityApi } from '@/api/entity';
import { relationApi } from '@/api/misc';
import { entityTemplateApi, relationTemplateApi } from '@/api/template';
import { useProjectStore } from '@/stores/projectStore';
import { useTreeStore, type EdgeSearchTarget } from '@/stores/treeStore';
import { computeVisibleIds } from '@/utils/tree';
import TreeGraph from '@/components/TreeGraph';
import type { EdgeInfo, GraphHandle } from '@/components/TreeGraph';
import GraphToolbar from '@/components/GraphToolbar';
import DetailPanel from '@/components/DetailPanel';
import EdgeDetailPanel from '@/components/EdgeDetailPanel';
import CreateEntityModal from '@/components/CreateEntityModal';
import CompareModal from '@/components/CompareModal';
import NodeActions from '@/components/NodeActions';
import StatsPanel from '@/components/StatsPanel';
import FilterPanel from '@/components/FilterPanel';
import type { ChildStrategy } from '@/types/api';

const { Content } = Layout;

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
  const selectedEdgeFromSearch = useTreeStore((s) => s.selectedEdgeFromSearch);
  const setSelectedEdgeFromSearch = useTreeStore((s) => s.setSelectedEdgeFromSearch);

  const [createOpen, setCreateOpen] = useState(false);
  const [createParent, setCreateParent] = useState<string | null>(null);
  const [compareOpen, setCompareOpen] = useState(false);
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

  const highlightIds = useMemo(() => {
    const ids = new Set(pathHighlight);
    return Array.from(ids);
  }, [pathHighlight]);

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
    Modal.confirm({
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

  const handleSelectNode = (id: string) => {
    select(id);
    setSelectedEdge(null);
  };

  const handleSelectEdge = (edge: EdgeInfo) => {
    setSelectedEdge(edge);
    select(null);
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
            padding: 12,
            borderRight: '1px solid #f0f0f0',
            overflowY: 'auto',
            background: '#fff',
          }}
        >
          <Space direction="vertical" style={{ width: '100%' }}>
            <Button
              type="primary"
              block
              icon={<PlusOutlined />}
              disabled={!canWrite}
              onClick={() => {
                setCreateParent(null);
                setCreateOpen(true);
              }}
            >
              新增根实体
            </Button>
            <Button
              block
              icon={<ExpandOutlined />}
              onClick={() => setPathHighlight([])}
              disabled={pathHighlight.length === 0}
            >
              清除路径高亮
            </Button>
            <Button block icon={<DiffOutlined />} onClick={() => setCompareOpen(true)}>
              迭代对比
            </Button>
            <FilterPanel />
          </Space>
          {selectedId && canWrite && (
            <NodeActions
              nodeId={selectedId}
              onCreateChild={(parentId) => {
                setCreateParent(parentId);
                setCreateOpen(true);
              }}
              onDelete={confirmDelete}
              onRefresh={refreshAll}
            />
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
      <div style={{ flex: 1, display: 'flex', flexDirection: 'column', minWidth: 0 }}>
        <div style={{ padding: 8 }}>
          <StatsPanel refreshKey={refreshKey} />
        </div>
        <div style={{ flex: 1, position: 'relative', minHeight: 0 }}>
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
                selectedId={selectedId}
                highlightIds={highlightIds}
                onSelectNode={handleSelectNode}
                onSelectEdge={handleSelectEdge}
              />
              <GraphToolbar graphRef={graphRef} selectedId={selectedId} />
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
          }}
        >
          {selectedId && (
            <DetailPanel
              key={selectedId}
              entityId={selectedId}
              onChanged={refreshAll}
              onSaveState={setSaveState}
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
      <CompareModal open={compareOpen} onClose={() => setCompareOpen(false)} />
    </div>
  );
}
