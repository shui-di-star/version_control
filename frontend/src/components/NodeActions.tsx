import { App, Button, Divider, Select, Input, Space } from 'antd';
import {
  PlusOutlined,
  DeleteOutlined,
  ArrowUpOutlined,
  ArrowDownOutlined,
  SwapOutlined,
} from '@ant-design/icons';
import { entityApi } from '@/api/entity';
import { useProjectStore } from '@/stores/projectStore';
import { useTreeStore } from '@/stores/treeStore';
import type { EntityTreeNode } from '@/types/api';

interface Props {
  nodeId: string;
  onCreateChild: (parentId: string) => void;
  onDelete: () => void;
  onRefresh: () => void;
}

/** 在树中查找节点及其父节点、兄弟列表 */
function findNodeInfo(
  roots: EntityTreeNode[],
  targetId: string,
): { node: EntityTreeNode | null; parent: EntityTreeNode | null; siblings: EntityTreeNode[] } {
  const search = (
    nodes: EntityTreeNode[],
    parent: EntityTreeNode | null,
  ): { node: EntityTreeNode | null; parent: EntityTreeNode | null; siblings: EntityTreeNode[] } | null => {
    for (const n of nodes) {
      if (n.id === targetId) return { node: n, parent, siblings: nodes };
      const found = search(n.children ?? [], n);
      if (found) return found;
    }
    return null;
  };
  return search(roots, null) ?? { node: null, parent: null, siblings: [] };
}

/** 收集树中所有节点为平铺列表（用于重选父节点下拉框） */
function flattenTree(roots: EntityTreeNode[]): { id: string; name: string }[] {
  const result: { id: string; name: string }[] = [];
  const walk = (nodes: EntityTreeNode[]) => {
    for (const n of nodes) {
      result.push({ id: n.id, name: n.name });
      walk(n.children ?? []);
    }
  };
  walk(roots);
  return result;
}

export default function NodeActions({ nodeId, onCreateChild, onDelete, onRefresh }: Props) {
  const { message, modal } = App.useApp();
  const pid = useProjectStore((s) => s.currentProject?.id);
  const tree = useTreeStore((s) => s.tree);
  const relationTemplates = useTreeStore((s) => s.relationTemplates);

  const { node, parent, siblings } = findNodeInfo(tree, nodeId);
  if (!node || !pid) return null;

  const isRoot = !parent;
  const otherSiblings = siblings.filter((s) => s.id !== nodeId);

  const showReparentModal = (defaultParentId: string | null, title: string) => {
    let selectedTemplateId: string | undefined;
    let remark = '';

    modal.confirm({
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
        if (defaultParentId !== null && !selectedTemplateId) {
          message.error('请选择关系类型');
          throw new Error('missing template');
        }
        await entityApi.reparent(pid, nodeId, {
          parentId: defaultParentId,
          parentRelationTemplateId: selectedTemplateId,
          parentRelationRemark: remark || undefined,
        });
        message.success('操作成功');
        onRefresh();
      },
    });
  };

  const handleMoveUp = () => {
    const grandParentId = parent ? (findNodeInfo(tree, parent.id).parent?.id ?? null) : null;
    showReparentModal(grandParentId, '层级上移');
  };

  const handleMoveDown = () => {
    let selectedSiblingId: string | undefined;
    modal.confirm({
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
  };

  const handleReparent = () => {
    let selectedParentId: string | null = null;
    const allNodes = flattenTree(tree).filter((n) => n.id !== nodeId);
    modal.confirm({
      title: '重选父节点',
      width: 480,
      content: (
        <div style={{ marginTop: 12 }}>
          <div style={{ marginBottom: 8 }}>
            <label>新父节点（留空变为根）：</label>
            <Select
              allowClear
              showSearch
              optionFilterProp="label"
              style={{ width: '100%' }}
              placeholder="选择目标节点或留空变为根"
              onChange={(v) => { selectedParentId = v ?? null; }}
              options={allNodes.map((n) => ({ value: n.id, label: n.name }))}
            />
          </div>
        </div>
      ),
      onOk: () => {
        showReparentModal(selectedParentId, '重选父节点 — 选择关系类型');
      },
    });
  };

  return (
    <>
      <Divider style={{ margin: '8px 0', fontSize: 12 }}>节点操作</Divider>
      <Space direction="vertical" style={{ width: '100%' }}>
        <Button
          block
          size="small"
          icon={<PlusOutlined />}
          onClick={() => onCreateChild(nodeId)}
        >
          创建子节点
        </Button>
        {!isRoot && (
          <Button
            block
            size="small"
            icon={<ArrowUpOutlined />}
            onClick={handleMoveUp}
          >
            层级上移
          </Button>
        )}
        {otherSiblings.length > 0 && (
          <Button
            block
            size="small"
            icon={<ArrowDownOutlined />}
            onClick={handleMoveDown}
          >
            层级下移
          </Button>
        )}
        <Button
          block
          size="small"
          icon={<SwapOutlined />}
          onClick={handleReparent}
        >
          重选父节点
        </Button>
        <Button
          block
          size="small"
          danger
          icon={<DeleteOutlined />}
          onClick={onDelete}
        >
          删除节点
        </Button>
      </Space>
    </>
  );
}
