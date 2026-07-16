import { useEffect, useState } from 'react';
import { App, Button, Card, Image, Input, Popconfirm, Space, Spin, Upload } from 'antd';
import { DeleteOutlined, PlusOutlined, UploadOutlined } from '@ant-design/icons';
import { edgeRemarkApi } from '@/api/edgeRemark';
import { useProjectStore } from '@/stores/projectStore';
import type { EdgeRemarkVO } from '@/types/api';

interface Props {
  entityId: string;  // 子节点 ID
}

export default function EdgeRemarkPanel({ entityId }: Props) {
  const { message } = App.useApp();
  const pid = useProjectStore((s) => s.currentProject?.id);
  const canWrite = useProjectStore((s) => s.hasRole('EDITOR'));

  const [remarks, setRemarks] = useState<EdgeRemarkVO[]>([]);
  const [loading, setLoading] = useState(false);
  const [addingContent, setAddingContent] = useState('');
  const [showAdd, setShowAdd] = useState(false);
  const [editingId, setEditingId] = useState<string | null>(null);
  const [editingContent, setEditingContent] = useState('');

  const load = async () => {
    if (!pid) return;
    setLoading(true);
    try {
      const list = await edgeRemarkApi.list(pid, entityId);
      setRemarks(list);
    } catch {
      // silent
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => { load(); }, [pid, entityId]);

  const handleAdd = async () => {
    if (!pid || !addingContent.trim()) return;
    try {
      await edgeRemarkApi.create(pid, entityId, addingContent.trim());
      setAddingContent('');
      setShowAdd(false);
      message.success('备注已添加');
      load();
    } catch {
      message.error('添加失败');
    }
  };

  const handleUpdate = async (remarkId: string) => {
    if (!pid || !editingContent.trim()) return;
    try {
      await edgeRemarkApi.update(pid, remarkId, editingContent.trim());
      setEditingId(null);
      message.success('已更新');
      load();
    } catch {
      message.error('更新失败');
    }
  };

  const handleDelete = async (remarkId: string) => {
    if (!pid) return;
    try {
      await edgeRemarkApi.remove(pid, remarkId);
      message.success('备注已删除');
      load();
    } catch {
      message.error('删除失败');
    }
  };

  const handleUploadImage = async (remarkId: string, file: File) => {
    if (!pid) return;
    try {
      await edgeRemarkApi.uploadImage(pid, remarkId, file);
      message.success('图片已上传');
      load();
    } catch {
      message.error('上传失败');
    }
  };

  const handleDeleteImage = async (imageId: string) => {
    if (!pid) return;
    try {
      await edgeRemarkApi.deleteImage(pid, imageId);
      message.success('图片已删除');
      load();
    } catch {
      message.error('删除失败');
    }
  };

  if (loading && remarks.length === 0) {
    return <Spin size="small" />;
  }

  return (
    <div style={{ marginTop: 12 }}>
      <div style={{ display: 'flex', alignItems: 'center', marginBottom: 8 }}>
        <label style={{ fontWeight: 500 }}>连线备注</label>
        {canWrite && (
          <Button
            type="link"
            size="small"
            icon={<PlusOutlined />}
            onClick={() => setShowAdd(true)}
            style={{ marginLeft: 'auto' }}
          >
            添加备注
          </Button>
        )}
      </div>

      {showAdd && (
        <Card size="small" style={{ marginBottom: 8 }}>
          <Input.TextArea
            rows={2}
            value={addingContent}
            placeholder="输入备注内容"
            onChange={(e) => setAddingContent(e.target.value)}
          />
          <Space style={{ marginTop: 8 }}>
            <Button type="primary" size="small" onClick={handleAdd}>
              保存
            </Button>
            <Button size="small" onClick={() => { setShowAdd(false); setAddingContent(''); }}>
              取消
            </Button>
          </Space>
        </Card>
      )}

      {remarks.length === 0 && !showAdd && (
        <span style={{ color: '#999', fontSize: 12 }}>暂无备注</span>
      )}

      {remarks.map((r) => (
        <Card
          key={r.id}
          size="small"
          style={{ marginBottom: 8 }}
          styles={{ body: { padding: '8px 12px' } }}
        >
          {editingId === r.id ? (
            <>
              <Input.TextArea
                rows={2}
                value={editingContent}
                onChange={(e) => setEditingContent(e.target.value)}
              />
              <Space style={{ marginTop: 4 }}>
                <Button type="primary" size="small" onClick={() => handleUpdate(r.id)}>
                  保存
                </Button>
                <Button size="small" onClick={() => setEditingId(null)}>
                  取消
                </Button>
              </Space>
            </>
          ) : (
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start' }}>
              <span style={{ whiteSpace: 'pre-wrap', flex: 1 }}>{r.content}</span>
              {canWrite && (
                <Space size={4}>
                  <Button
                    type="link"
                    size="small"
                    onClick={() => { setEditingId(r.id); setEditingContent(r.content); }}
                  >
                    编辑
                  </Button>
                  <Popconfirm title="确定删除此备注？" onConfirm={() => handleDelete(r.id)}>
                    <Button type="link" size="small" danger icon={<DeleteOutlined />} />
                  </Popconfirm>
                </Space>
              )}
            </div>
          )}

          {/* Images */}
          {r.images.length > 0 && (
            <div style={{ marginTop: 8 }}>
              <Image.PreviewGroup>
                {r.images.map((img) => (
                  <div key={img.id} style={{ display: 'inline-block', marginRight: 8, marginBottom: 8, position: 'relative' }}>
                    <Image
                      width={80}
                      height={80}
                      style={{ objectFit: 'cover', borderRadius: 4 }}
                      src={edgeRemarkApi.imagePreviewUrl(pid!, img.id)}
                      alt={img.fileName}
                    />
                    {canWrite && (
                      <Popconfirm title="删除图片？" onConfirm={() => handleDeleteImage(img.id)}>
                        <Button
                          type="link"
                          size="small"
                          danger
                          icon={<DeleteOutlined />}
                          style={{ position: 'absolute', top: -4, right: -4, background: '#fff', borderRadius: '50%', padding: 2, lineHeight: 1 }}
                        />
                      </Popconfirm>
                    )}
                  </div>
                ))}
              </Image.PreviewGroup>
            </div>
          )}

          {/* Upload image button */}
          {canWrite && (
            <Upload
              showUploadList={false}
              accept="image/*"
              multiple
              beforeUpload={(file) => {
                handleUploadImage(r.id, file);
                return false;
              }}
            >
              <Button size="small" icon={<UploadOutlined />} style={{ marginTop: 4 }}>
                上传图片
              </Button>
            </Upload>
          )}
        </Card>
      ))}
    </div>
  );
}
