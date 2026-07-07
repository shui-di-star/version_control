import { useEffect, useRef } from 'react';
import { Menu, Modal, Select, Input, message } from 'antd';
import { entityApi } from '@/api/entity';
import { useProjectStore } from '@/stores/projectStore';
import { useTreeStore } from '@/stores/treeStore';
import type { EntityTreeNode } from '@/types/api';

interface Props {
  nodeId: string;
  x: number;
  y: number;
  onClose: () => void;
  onCreateChild: (parentId: string) => void;
  onDelete: (nodeId: string) => void;
  onRefresh: () => void;
}

/** 查找节点及其父节点信息 */
function findNodeInfo(roots: EntityTreeNode[], targetId: string): { node: EntityTreeNode | null; parent: EntityTreeNode | null; siblings: EntityTreeNode[] } {
  const search = (nodes: EntityTreeNode[], parent: EntityTreeNode | null): { node: EntityTreeNode | null; parent: EntityTreeNode | null; siblings: EntityTreeNode[] } | null => {
    for (const n of nodes) {
      if (n.id === targetId) {
        return { node: n, parent, siblings: nodes };
      }
      const found = search(n.children ?? [], n);
      if (found) return found;
    }
    return null;
  };
  return search(roots, null) ?? { node: null, parent: null, siblings: [] };
}

export default function NodeContextMenu({ nodeId, x, y, onClose, onCreateChild, onDelete, onRefresh }: Props) {
  const containerRef = useRef<HTMLDivElement>(null);
  const pid = useProjectStore((s) => s.currentProject?.id);
  const canWrite = useProjectStore((s) => s.hasRole('EDITOR'));
  const tree = useTreeStore((s) => s.tree);
  const relationTemplates = useTreeStore((s) => s.relationTemplates);

  const { node, parent, siblings } = findNodeInfo(tree, nodeId);

  useEffect(() => {
    const handleClose = (e: MouseEvent) => {
      // 点击菜单内部不关闭
      if (containerRef.current && containerRef.current.contains(e.target as HTMLElement)) return;
      onClose();
    };
    // 必须在下一帧才开始监听，否则触发右键菜单的那次事件冒泡会立刻关闭
    let raf: number;
    let timer: number;
    raf = requestAnimationFrame(() => {
      timer = window.setTimeout(() => {
        document.addEventListener('mousedown', handleClose);
        document.addEventListener('contextmenu', handleClose);
      }, 0);
    });
    return () => {
      cancelAnimationFrame(raf);
      clearTimeout(timer);
      document.removeEventListener('mousedown', handleClose);
      document.removeEventListener('contextmenu', handleClose);
    };
  }, [onClose]);

  if (!node || !pid) return null;

  const isRoot = !parent;
  const otherSiblings = siblings.filter((s) => s.id !== nodeId);

  const showReparentModal = (defaultParentId: string | null, title: string) => {
    let selectedParentId: string | null = defaultParentId;
    let selectedTemplateId: string | undefined;
    let remark = '';

    Modal.confirm({
      title,
      width: 480,
      content: (
        <div style={{ marginTop: 12 }}>
          {defaultParentId === null && (
            <div style={{ marginBottom: 8 }}>将变为根节点</div>
          )}
          {defaultParentId !== null && (
            <>
              <div style={{ marginBottom: 8 }}>
                <label>关系类型（必填）：</label>
                <Select
                  style={{ width: '100%' }}
                  placeholder="选择关系类型"
                  onChange={(v) => { selectedTemplateId = v; }}
                  options={relationTemplates.map((t) => ({ value: t.id, label: t.name }))}
                />
              </div>
              <div style={{ marginBottom: 8 }}>
                <label>备注（可选）：</label>
                <Input.TextArea rows={2} onChange={(e) => { remark = e.target.value; }} />
              </div>
            </>
          )}
        </div>
      ),
      onOk: async () => {
        if (selectedParentId !== null && !selectedTemplateId) {
          message.error('请选择关系类型');
          throw new Error('missing template');
        }
        await entityApi.reparent(pid, nodeId, {
          parentId: selectedParentId,
          parentRelationTemplateId: selectedTemplateId,
          parentRelationRemark: remark || undefined,
        });
        message.success('操作成功');
        onRefresh();
      },
    });
  };

  const handleMoveUp = () => {
    // 上移 = 变为祖父的子节点
    const grandParentId = parent ? (findNodeInfo(tree, parent.id).parent?.id ?? null) : null;
    showReparentModal(grandParentId, '层级上移');
    onClose();
  };

  const handleMoveDown = () => {
    // 下移 = 变为兄弟的子节点
    let selectedSiblingId: string | undefined;
    Modal.confirm({
      title: '层级下移 — 选择目标兄弟节点',
      width: 480,
      content: (
        <div style={{ marginTop: 12 }}>
          <div style={{ marginBottom: 8 }}>
            <label>目标兄弟节点：</label>
            <Select
              style={{ width: '100%' }}
              placeholder="选择兄弟节点"
              onChange={(v) => { selectedSiblingId = v; }}
              options={otherSiblings.map((s) => ({ value: s.id, label: s.name }))}
            />
          </div>
        </div>
      ),
      onOk: () => {
        if (!selectedSiblingId) {
          message.error('请选择目标兄弟节点');
          throw new Error('missing sibling');
        }
        showReparentModal(selectedSiblingId, '层级下移 — 选择关系类型');
      },
    });
    onClose();
  };

  const handleReparent = () => {
    // 自由选择新父节点 — 这里用简单文本输入节点ID(实际可改为 tree select)
    let inputParentId: string | null = null;
    Modal.confirm({
      title: '重选父节点',
      width: 480,
      content: (
        <div style={{ marginTop: 12 }}>
          <div style={{ marginBottom: 8 }}>
            <label>新父节点 ID（留空变为根）：</label>
            <Input placeholder="输入目标节点 ID 或留空" onChange={(e) => { inputParentId = e.target.value || null; }} />
          </div>
        </div>
      ),
      onOk: () => {
        showReparentModal(inputParentId, '重选父节点 — 选择关系类型');
      },
    });
    onClose();
  };

  const menuItems = [
    { key: 'create', label: '创建子节点', disabled: !canWrite },
    { key: 'delete', label: '删除节点', danger: true, disabled: !canWrite },
    ...(!isRoot ? [{ key: 'moveUp', label: '层级上移', disabled: !canWrite }] : []),
    ...(otherSiblings.length > 0 ? [{ key: 'moveDown', label: '层级下移', disabled: !canWrite }] : []),
    { key: 'reparent', label: '重选父节点', disabled: !canWrite },
  ];

  const onClick = ({ key }: { key: string }) => {
    switch (key) {
      case 'create': onCreateChild(nodeId); break;
      case 'delete': onDelete(nodeId); break;
      case 'moveUp': handleMoveUp(); break;
      case 'moveDown': handleMoveDown(); break;
      case 'reparent': handleReparent(); break;
    }
  };

  return (
    <div
      ref={containerRef}
      style={{ position: 'fixed', left: x, top: y, zIndex: 1000 }}
    >
      <Menu
        items={menuItems}
        onClick={onClick}
        style={{ boxShadow: '0 2px 8px rgba(0,0,0,0.15)', borderRadius: 4 }}
      />
    </div>
  );
}
