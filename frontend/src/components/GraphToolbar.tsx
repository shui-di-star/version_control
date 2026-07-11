import { useState } from 'react';
import { Button, Tooltip, Dropdown, Checkbox, Select, Radio } from 'antd';
import {
  ZoomInOutlined,
  ZoomOutOutlined,
  ExpandOutlined,
  CompressOutlined,
  NodeExpandOutlined,
  NodeCollapseOutlined,
  FilterOutlined,
  DragOutlined,
  ApiOutlined,
  ReloadOutlined,
  AimOutlined,
  CloseCircleOutlined,
} from '@ant-design/icons';
import type { GraphHandle } from '@/components/TreeGraph';
import { useTreeStore } from '@/stores/treeStore';
import { useProjectStore } from '@/stores/projectStore';
import { STATUS_OPTIONS } from '@/utils/constants';
import type { EntityStatus } from '@/types/api';
import type { GraphMode } from '@/stores/treeStore';

interface Props {
  graphRef: React.RefObject<GraphHandle | null>;
  selectedId: string | null;
  onTracePath?: () => void;
  onClearPath?: () => void;
  hasPathHighlight?: boolean;
}

export default function GraphToolbar({ graphRef, selectedId, onTracePath, onClearPath, hasPathHighlight }: Props) {
  const [zoom, setZoom] = useState(100);
  const [filterOpen, setFilterOpen] = useState(false);
  const filter = useTreeStore((s) => s.filter);
  const setFilter = useTreeStore((s) => s.setFilter);
  const relationTemplates = useTreeStore((s) => s.relationTemplates);
  const connectRelationTemplateId = useTreeStore((s) => s.connectRelationTemplateId);
  const setConnectRelationTemplateId = useTreeStore((s) => s.setConnectRelationTemplateId);
  const graphMode = useTreeStore((s) => s.graphMode);
  const setGraphMode = useTreeStore((s) => s.setGraphMode);
  const canWrite = useProjectStore((s) => s.hasRole('EDITOR'));

  const refreshZoom = () => {
    const z = graphRef.current?.getZoom() ?? 1;
    setZoom(Math.round(z * 100));
  };

  const fitView = () => {
    graphRef.current?.fitView();
    setTimeout(refreshZoom, 300);
  };

  const zoomIn = () => {
    graphRef.current?.zoomIn();
    setTimeout(refreshZoom, 200);
  };

  const zoomOut = () => {
    graphRef.current?.zoomOut();
    setTimeout(refreshZoom, 200);
  };

  const resetZoom = () => {
    graphRef.current?.zoomTo(1);
    setTimeout(refreshZoom, 200);
  };

  const activeStatuses = filter.statuses ?? [];
  const statusLabel = activeStatuses.length === 0
    ? '全部状态'
    : activeStatuses.length === 1
      ? STATUS_OPTIONS.find((o) => o.value === activeStatuses[0])?.label ?? '已筛选'
      : `${activeStatuses.length}个状态`;

  const statusMenuItems = [
    {
      key: '__ALL__',
      label: (
        <Checkbox
          checked={activeStatuses.length === 0}
          onChange={() => setFilter({ statuses: [] })}
        >
          全部状态
        </Checkbox>
      ),
    },
    ...STATUS_OPTIONS.map((o) => ({
      key: o.value,
      label: (
        <Checkbox
          checked={activeStatuses.includes(o.value)}
          onChange={(e) => {
            const checked = e.target.checked;
            const next = checked
              ? [...activeStatuses, o.value]
              : activeStatuses.filter((s) => s !== o.value);
            setFilter({ statuses: next as EntityStatus[] });
          }}
        >
          {o.label}
        </Checkbox>
      ),
    })),
  ];

  const handleModeChange = (mode: GraphMode) => {
    setGraphMode(mode);
    // 切回移动模式时清除连线关系模板选择
    if (mode === 'move') {
      setConnectRelationTemplateId(null);
    }
  };

  return (
    <div
      style={{
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'space-between',
        padding: '8px 12px',
        borderBottom: '1px solid var(--line)',
        background: 'var(--panel)',
        flexShrink: 0,
      }}
    >
      {/* 左侧：标题 + 模式切换 + 状态过滤 */}
      <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
        <span style={{ fontSize: 14, fontWeight: 700, color: 'var(--text)' }}>
          仿真结果图谱
        </span>
        {canWrite && (
          <Radio.Group
            size="small"
            value={graphMode}
            onChange={(e) => handleModeChange(e.target.value)}
            optionType="button"
            buttonStyle="solid"
          >
            <Radio.Button value="move">
              <DragOutlined style={{ marginRight: 4 }} />移动
            </Radio.Button>
            <Radio.Button value="connect">
              <ApiOutlined style={{ marginRight: 4 }} />连线
            </Radio.Button>
          </Radio.Group>
        )}
        {canWrite && graphMode === 'connect' && (
          <Select
            size="small"
            placeholder="选择关系类型"
            allowClear
            style={{ width: 140 }}
            value={connectRelationTemplateId ?? undefined}
            onChange={(v) => setConnectRelationTemplateId(v ?? null)}
            options={relationTemplates.map((t) => ({ value: t.id, label: t.name }))}
          />
        )}
        <div style={{ width: 1, height: 18, background: 'var(--line)', margin: '0 4px' }} />
        <Dropdown
          menu={{ items: statusMenuItems }}
          trigger={['click']}
          open={filterOpen}
          onOpenChange={setFilterOpen}
        >
          <Button size="small" icon={<FilterOutlined />}>
            {statusLabel}
          </Button>
        </Dropdown>
        {activeStatuses.length > 0 && (
          <Button
            size="small"
            type="link"
            onClick={() => setFilter({ statuses: [] })}
          >
            清除
          </Button>
        )}
        <div style={{ width: 1, height: 18, background: 'var(--line)', margin: '0 4px' }} />
        <Tooltip title="路径追溯" placement="top">
          <Button
            size="small"
            icon={<AimOutlined />}
            disabled={!selectedId}
            onClick={onTracePath}
          >
            路径追溯
          </Button>
        </Tooltip>
        {hasPathHighlight && (
          <Button
            size="small"
            icon={<CloseCircleOutlined />}
            onClick={onClearPath}
          >
            清除追溯
          </Button>
        )}
      </div>

      {/* 右侧：缩放/布局控制 */}
      <div style={{ display: 'flex', alignItems: 'center', gap: 2 }}>
        <Tooltip title="缩小" placement="top">
          <Button type="text" size="small" icon={<ZoomOutOutlined />} onClick={zoomOut} />
        </Tooltip>
        <span
          style={{
            fontSize: 12,
            color: 'var(--muted)',
            minWidth: 40,
            textAlign: 'center',
            cursor: 'pointer',
            userSelect: 'none',
          }}
          onClick={resetZoom}
          title="点击重置为 100%"
        >
          {zoom}%
        </span>
        <Tooltip title="放大" placement="top">
          <Button type="text" size="small" icon={<ZoomInOutlined />} onClick={zoomIn} />
        </Tooltip>
        <div style={{ width: 1, height: 18, background: 'var(--line)', margin: '0 4px' }} />
        <Tooltip title="自适应窗口" placement="top">
          <Button type="text" size="small" icon={<CompressOutlined />} onClick={fitView} />
        </Tooltip>
        <Tooltip title="1:1 原始大小" placement="top">
          <Button type="text" size="small" icon={<ExpandOutlined />} onClick={resetZoom} />
        </Tooltip>
        <Tooltip title="重置视图" placement="top">
          <Button type="text" size="small" icon={<ReloadOutlined />} onClick={() => graphRef.current?.resetLayout()} />
        </Tooltip>
        <div style={{ width: 1, height: 18, background: 'var(--line)', margin: '0 4px' }} />
        <Tooltip title="展开全部" placement="top">
          <Button type="text" size="small" icon={<NodeExpandOutlined />} onClick={() => graphRef.current?.expandAll()} />
        </Tooltip>
        <Tooltip title="折叠全部" placement="top">
          <Button type="text" size="small" icon={<NodeCollapseOutlined />} onClick={() => graphRef.current?.collapseAll()} />
        </Tooltip>
      </div>
    </div>
  );
}
