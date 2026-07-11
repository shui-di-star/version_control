import { useEffect, useState } from 'react';
import { App, List, Button, Upload, Space, Popconfirm, Input, Modal, Image } from 'antd';
import { UploadOutlined, DownloadOutlined, DeleteOutlined, FileTextOutlined } from '@ant-design/icons';
import type { UploadProps } from 'antd';
import { assetApi } from '@/api/misc';
import { useProjectStore } from '@/stores/projectStore';
import { MAX_ASSET_SIZE } from '@/utils/constants';
import type { AssetVO } from '@/types/api';

// 产出物区：列表/上传/TEXT内联/下载/删除。EDITOR+ 可写。
// 图片类型产出物直接内联展示，可点击放大。

function isImageAsset(a: AssetVO): boolean {
  if (a.mimeType?.startsWith('image/')) return true;
  const name = (a.fileName || '').toLowerCase();
  return /\.(png|jpe?g|gif|webp|bmp|svg)$/.test(name);
}

export default function AssetSection({ entityId }: { entityId: string }) {
  const { message, modal } = App.useApp();
  const currentProject = useProjectStore((s) => s.currentProject);
  const canWrite = useProjectStore((s) => s.hasRole('EDITOR'));
  const pid = currentProject?.id;
  const [assets, setAssets] = useState<AssetVO[]>([]);
  const [textOpen, setTextOpen] = useState(false);
  const [textForm, setTextForm] = useState({ fileName: '', contentText: '' });

  const reload = () => {
    if (!pid) return;
    assetApi.list(pid, entityId).then(setAssets).catch(() => undefined);
  };

  useEffect(() => {
    reload();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [pid, entityId]);

  const beforeUpload: UploadProps['beforeUpload'] = async (file) => {
    if (!pid) return Upload.LIST_IGNORE;
    if (file.size > MAX_ASSET_SIZE) {
      message.error('文件超过 100MB 上限');
      return Upload.LIST_IGNORE;
    }
    await assetApi.upload(pid, entityId, 'FILE', file as File);
    message.success('已上传');
    reload();
    return Upload.LIST_IGNORE;
  };

  const onText = async () => {
    if (!pid) return;
    if (!textForm.fileName.trim()) {
      message.error('请填写文件名');
      return;
    }
    await assetApi.uploadText(pid, entityId, textForm.fileName, textForm.contentText);
    message.success('已保存文本');
    setTextOpen(false);
    setTextForm({ fileName: '', contentText: '' });
    reload();
  };

  const onDownload = async (a: AssetVO) => {
    if (!pid) return;
    if (a.assetType === 'TEXT') {
      modal.info({ title: a.fileName, content: <pre style={{ whiteSpace: 'pre-wrap' }}>{a.contentText}</pre>, width: 640 });
      return;
    }
    const resp = await assetApi.download(pid, a.id);
    const url = URL.createObjectURL(resp.data as Blob);
    const link = document.createElement('a');
    link.href = url;
    link.download = a.fileName || 'download';
    link.click();
    URL.revokeObjectURL(url);
  };

  const onRemove = async (a: AssetVO) => {
    if (!pid) return;
    await assetApi.remove(pid, a.id);
    message.success('已删除');
    reload();
  };

  return (
    <div>
      {canWrite && (
        <Space style={{ marginBottom: 8 }}>
          <Upload beforeUpload={beforeUpload} showUploadList={false}>
            <Button size="small" icon={<UploadOutlined />}>
              上传文件
            </Button>
          </Upload>
          <Button size="small" icon={<FileTextOutlined />} onClick={() => setTextOpen(true)}>
            添加文本
          </Button>
        </Space>
      )}
      <List
        size="small"
        dataSource={assets}
        locale={{ emptyText: '暂无产出物' }}
        renderItem={(a) => (
          <List.Item
            actions={[
              <Button key="dl" type="link" size="small" icon={<DownloadOutlined />} onClick={() => onDownload(a)}>
                {a.assetType === 'TEXT' ? '查看' : '下载'}
              </Button>,
              canWrite ? (
                <Popconfirm key="del" title="删除该产出物？" onConfirm={() => onRemove(a)}>
                  <Button type="link" size="small" danger icon={<DeleteOutlined />} />
                </Popconfirm>
              ) : null,
            ]}
          >
            <List.Item.Meta
              title={a.fileName}
              description={
                isImageAsset(a) && pid ? (
                  <Image
                    src={assetApi.previewUrl(pid, a.id)}
                    alt={a.fileName}
                    style={{ maxWidth: 200, maxHeight: 150, marginTop: 4 }}
                    placeholder
                  />
                ) : (
                  `${a.assetType}${a.size ? ` · ${(a.size / 1024).toFixed(1)}KB` : ''}`
                )
              }
            />
          </List.Item>
        )}
      />
      <Modal title="添加文本产出物" open={textOpen} onOk={onText} onCancel={() => setTextOpen(false)} destroyOnHidden>
        <Input
          placeholder="文件名"
          style={{ marginBottom: 8 }}
          value={textForm.fileName}
          onChange={(e) => setTextForm((s) => ({ ...s, fileName: e.target.value }))}
        />
        <Input.TextArea
          rows={6}
          placeholder="文本内容"
          value={textForm.contentText}
          onChange={(e) => setTextForm((s) => ({ ...s, contentText: e.target.value }))}
        />
      </Modal>
    </div>
  );
}
