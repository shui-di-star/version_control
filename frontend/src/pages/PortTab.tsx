import { useState } from 'react';
import { App, Button, Space, Upload, Typography, Card, Modal } from 'antd';
import { DownloadOutlined, UploadOutlined } from '@ant-design/icons';
import type { UploadProps } from 'antd';
import { portApi } from '@/api/misc';
import { entityApi } from '@/api/entity';
import { useProjectStore } from '@/stores/projectStore';
import type { ProjectExport } from '@/types/api';

// 导入导出：导出下载 JSON；导入上传 JSON（后端一律分配新 id）。仅 ADMIN。
export default function PortTab() {
  const { message } = App.useApp();
  const currentProject = useProjectStore((s) => s.currentProject);
  const pid = currentProject?.id;
  const [exporting, setExporting] = useState(false);
  const [importing, setImporting] = useState(false);

  const onExport = async () => {
    if (!pid) return;
    setExporting(true);
    try {
      const data = await portApi.export(pid);
      const blob = new Blob([JSON.stringify(data, null, 2)], { type: 'application/json' });
      const url = URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = `${currentProject?.name || 'project'}-export.json`;
      a.click();
      URL.revokeObjectURL(url);
      message.success('已导出');
    } finally {
      setExporting(false);
    }
  };

  const beforeUpload: UploadProps['beforeUpload'] = async (file) => {
    if (!pid) return Upload.LIST_IGNORE;
    setImporting(true);
    try {
      // 检查项目是否为空
      const tree = await entityApi.tree(pid);
      if (tree && tree.length > 0) {
        Modal.warning({
          title: '无法导入',
          content: '只能向空项目导入数据。当前项目已有实体，请先清空或新建一个空项目再导入。',
        });
        return Upload.LIST_IGNORE;
      }
      const text = await file.text();
      const data = JSON.parse(text) as ProjectExport;
      await portApi.import(pid, data);
      message.success('已导入到当前项目');
    } catch (e: any) {
      const msg = e?.response?.data?.message || e?.message || '文件格式错误或后端拒绝';
      message.error('导入失败：' + msg);
    } finally {
      setImporting(false);
    }
    return Upload.LIST_IGNORE; // 阻止 antd 自动上传
  };

  return (
    <Space direction="vertical" size="large" style={{ width: '100%' }}>
      <Card size="small" title="导出项目">
        <Typography.Paragraph type="secondary">
          导出当前项目的全量模板/实体/关系/产出物元信息为 JSON 文件。
        </Typography.Paragraph>
        <Button icon={<DownloadOutlined />} loading={exporting} onClick={onExport}>
          导出 JSON
        </Button>
      </Card>
      <Card size="small" title="导入项目">
        <Typography.Paragraph type="secondary">
          选择一个导出的 JSON 文件导入到<strong>当前项目</strong>。只能向空项目导入，导入后会覆盖预设模板。
        </Typography.Paragraph>
        <Upload accept=".json" beforeUpload={beforeUpload} showUploadList={false}>
          <Button icon={<UploadOutlined />} loading={importing}>
            选择 JSON 导入
          </Button>
        </Upload>
      </Card>
    </Space>
  );
}
