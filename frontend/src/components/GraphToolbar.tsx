import { useState } from 'react';
import { Button, Tooltip } from 'antd';
import {
  ZoomInOutlined,
  ZoomOutOutlined,
  ExpandOutlined,
  CompressOutlined,
  AimOutlined,
  NodeExpandOutlined,
  NodeCollapseOutlined,
} from '@ant-design/icons';
import type { GraphHandle } from '@/components/TreeGraph';

interface Props {
  graphRef: React.RefObject<GraphHandle | null>;
  selectedId: string | null;
}

export default function GraphToolbar({ graphRef, selectedId }: Props) {
  const [zoom, setZoom] = useState(100);

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

  const focusSelected = () => {
    if (!selectedId) return;
    graphRef.current?.fitView();
    setTimeout(refreshZoom, 300);
  };

  return (
    <div
      style={{
        position: 'absolute',
        bottom: 12,
        right: 12,
        display: 'flex',
        alignItems: 'center',
        gap: 2,
        background: '#fff',
        borderRadius: 6,
        boxShadow: '0 2px 8px rgba(0,0,0,0.12)',
        padding: '4px 6px',
        zIndex: 10,
      }}
    >
      <Tooltip title="缩小" placement="top">
        <Button type="text" size="small" icon={<ZoomOutOutlined />} onClick={zoomOut} />
      </Tooltip>
      <span
        style={{
          fontSize: 12,
          color: '#666',
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
      <div style={{ width: 1, height: 18, background: '#e8e8e8', margin: '0 4px' }} />
      <Tooltip title="自适应窗口" placement="top">
        <Button type="text" size="small" icon={<CompressOutlined />} onClick={fitView} />
      </Tooltip>
      <Tooltip title="1:1 原始大小" placement="top">
        <Button type="text" size="small" icon={<ExpandOutlined />} onClick={resetZoom} />
      </Tooltip>
      {selectedId && (
        <Tooltip title="定位到选中节点" placement="top">
          <Button type="text" size="small" icon={<AimOutlined />} onClick={focusSelected} />
        </Tooltip>
      )}
      <div style={{ width: 1, height: 18, background: '#e8e8e8', margin: '0 4px' }} />
      <Tooltip title="展开全部" placement="top">
        <Button type="text" size="small" icon={<NodeExpandOutlined />} onClick={() => graphRef.current?.expandAll()} />
      </Tooltip>
      <Tooltip title="折叠全部" placement="top">
        <Button type="text" size="small" icon={<NodeCollapseOutlined />} onClick={() => graphRef.current?.collapseAll()} />
      </Tooltip>
    </div>
  );
}
