import { useEffect, useState } from 'react';
import { App, Form, Input, Select, Switch, Divider, Space, Button, Image } from 'antd';
import { DeleteOutlined, SaveOutlined } from '@ant-design/icons';
import { entityApi } from '@/api/entity';
import { attrImageApi } from '@/api/misc';
import { useProjectStore } from '@/stores/projectStore';
import { useTreeStore } from '@/stores/treeStore';
import { parseAttributes, parseSchemaFields } from '@/utils/json';
import { STATUS_OPTIONS, STATUS_META } from '@/utils/constants';
import AttributeFields, { attrsToForm, formToAttrs } from '@/components/AttributeFields';
import AssetSection from '@/components/AssetSection';
import type { EntityVO, SchemaField } from '@/types/api';

interface Props {
  entityId: string;
  onChanged: () => void;
  onSaveState: (s: 'saving' | 'saved' | null) => void;
  onDelete?: () => void;
}

/** 详情只读视图：分区式布局，模仿示例项目 */
function DetailView({
  entity,
  fields,
  attrs,
  pid,
}: {
  entity: EntityVO;
  fields: SchemaField[];
  attrs: Record<string, unknown>;
  pid: string;
}) {
  const statusMeta = entity.status ? STATUS_META[entity.status] : null;

  // 分类字段
  const keyMetricFields = fields.filter((f) => f.keyMetric);
  const imageFields = fields.filter((f) => f.type === 'IMAGE');
  const otherFields = fields.filter((f) => !f.keyMetric && f.type !== 'IMAGE');

  return (
    <div>
      {/* 标题与状态 */}
      <div className="detail-heading">
        <div>
          <h3 className="detail-title">{entity.name}</h3>
        </div>
        {statusMeta && (
          <span className="status-badge" style={{ background: `${statusMeta.color}20`, color: statusMeta.color }}>
            {statusMeta.label}
          </span>
        )}
      </div>

      {/* 关键数值摘要 */}
      {keyMetricFields.length > 0 && (
        <section className="detail-section">
          <h4>关键数值</h4>
          <div className="stats-grid">
            {keyMetricFields.slice(0, 6).map((f) => {
              const val = attrs[f.key];
              return (
                <div className="stat-card" key={f.key}>
                  <span className="stat-label">{f.label}{f.unit ? ` (${f.unit})` : ''}</span>
                  <strong className="stat-value">{val != null && val !== '' ? String(val) : '—'}</strong>
                </div>
              );
            })}
          </div>
        </section>
      )}

      {/* 常规属性 */}
      {otherFields.length > 0 && (
        <section className="detail-section">
          <h4>属性详情</h4>
          <div className="kv">
            <div className="kv-row">
              <span>里程碑</span>
              <strong>{entity.isMilestone === 1 ? '★ 是' : '—'}</strong>
            </div>
            {otherFields.map((f) => {
              const val = attrs[f.key];
              const displayVal = val != null && val !== '' ? String(val) : '—';
              return (
                <div className="kv-row" key={f.key}>
                  <span>{f.label}{f.unit ? ` (${f.unit})` : ''}</span>
                  <strong>{displayVal}</strong>
                </div>
              );
            })}
            {entity.remark && (
              <div className="kv-row">
                <span>备注</span>
                <strong>{entity.remark}</strong>
              </div>
            )}
          </div>
        </section>
      )}

      {/* 图片字段 - 支持点击放大 */}
      {imageFields.length > 0 && (
        <section className="detail-section">
          <h4>图片</h4>
          <div className="image-grid">
            {imageFields.map((f) => {
              const val = attrs[f.key];
              const hasImage = val != null && val !== '' && String(val).trim() !== '';
              if (!hasImage) return (
                <div className="image-card" key={f.key}>
                  <div className="image-meta">{f.label}</div>
                  <div className="image-thumb-empty">暂无图片</div>
                </div>
              );
              return (
                <div className="image-card" key={f.key}>
                  <div className="image-meta">{f.label}</div>
                  <Image
                    src={attrImageApi.previewUrl(pid, String(val))}
                    alt={f.label}
                    style={{ width: '100%', aspectRatio: '4/3', objectFit: 'contain', borderRadius: 6, background: '#fff' }}
                  />
                </div>
              );
            })}
          </div>
        </section>
      )}

      {/* 产出物 */}
      <section className="detail-section">
        <h4>产出物</h4>
        <AssetSection entityId={entity.id} />
      </section>
    </div>
  );
}

export default function DetailPanel({ entityId, onChanged, onSaveState, onDelete }: Props) {
  const { message } = App.useApp();
  const currentProject = useProjectStore((s) => s.currentProject);
  const myRole = currentProject?.myRole;
  const canWrite = !!myRole && ({ ADMIN: 3, EDITOR: 2, VIEWER: 1 }[myRole] ?? 0) >= 2;
  const entityTemplates = useTreeStore((s) => s.entityTemplates);
  const pid = currentProject?.id;

  const [entity, setEntity] = useState<EntityVO | null>(null);
  const [form] = Form.useForm();
  const [dirty, setDirty] = useState(false);
  const [saving, setSaving] = useState(false);
  const [activeTab, setActiveTab] = useState<'detail' | 'edit'>('detail');

  const fields = entity
    ? parseSchemaFields(entityTemplates.find((t) => t.id === entity.templateId)?.fieldSchema)
    : [];
  const attrs = entity ? parseAttributes(entity.attributes) : {};

  useEffect(() => {
    if (!pid) return;
    let cancelled = false;
    entityApi.getSilent(pid, entityId).then((e) => {
      if (cancelled) return;
      setEntity(e);
      setDirty(false);
      setActiveTab('detail');
    }).catch(() => {
      if (!cancelled) setEntity(null);
    });
    return () => { cancelled = true; };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [pid, entityId]);

  useEffect(() => {
    if (activeTab !== 'edit' || !entity) return;
    const f = parseSchemaFields(entityTemplates.find((t) => t.id === entity.templateId)?.fieldSchema);
    form.setFieldsValue({
      name: entity.name,
      remark: entity.remark,
      attrs: attrsToForm(parseAttributes(entity.attributes), f),
    });
    setDirty(false);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [activeTab, entity]);

  const handleSave = async () => {
    if (!pid || !entity) return;
    const v = form.getFieldsValue();
    const attrValues = formToAttrs(v.attrs ?? {}, fields);
    const name = String(attrValues['card_name'] ?? v.attrs?.['card_name'] ?? entity.name);
    setSaving(true);
    onSaveState('saving');
    try {
      await entityApi.update(pid, entity.id, {
        name,
        remark: v.remark,
        attributes: JSON.stringify(attrValues),
      });
      onSaveState('saved');
      setDirty(false);
      onChanged();
      const e = await entityApi.get(pid, entity.id);
      setEntity(e);
      setActiveTab('detail');
    } catch {
      onSaveState(null);
      message.error('保存失败');
    } finally {
      setSaving(false);
    }
  };

  const onValuesChange = () => {
    if (canWrite) setDirty(true);
  };

  const toggleMilestone = async () => {
    if (!pid || !entity) return;
    const e = await entityApi.toggleMilestone(pid, entity.id);
    setEntity(e);
    onChanged();
  };

  const setStatus = async (status: string | null) => {
    if (!pid || !entity) return;
    const e = await entityApi.setStatus(pid, entity.id, status ?? null);
    setEntity(e);
    onChanged();
  };

  if (!entity) return null;

  const roleHint = canWrite ? '当前角色可编辑图谱和卡片内容。' : '当前角色只读，不可编辑。';

  return (
    <div style={{ display: 'flex', flexDirection: 'column', height: '100%' }}>
      {/* Panel head */}
      <div style={{ padding: '16px 16px 12px', borderBottom: '1px solid var(--line)', flexShrink: 0 }}>
        <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
          <span style={{ fontSize: 16, fontWeight: 700, color: 'var(--text)' }}>卡片详情</span>
        </div>
        <div style={{ fontSize: 12, color: 'var(--muted)', marginTop: 4 }}>{roleHint}</div>
      </div>

      {/* Tab bar */}
      <div style={{ display: 'flex', borderBottom: '1px solid var(--line)', flexShrink: 0 }}>
        <button
          onClick={() => setActiveTab('detail')}
          style={{
            flex: 1, padding: '8px 0', border: 'none', background: 'none',
            cursor: 'pointer', fontSize: 13, fontWeight: 600,
            color: activeTab === 'detail' ? 'var(--blue)' : 'var(--muted)',
            borderBottom: activeTab === 'detail' ? '2px solid var(--blue)' : '2px solid transparent',
          }}
        >
          详情
        </button>
        <button
          onClick={() => setActiveTab('edit')}
          style={{
            flex: 1, padding: '8px 0', border: 'none', background: 'none',
            cursor: 'pointer', fontSize: 13, fontWeight: 600,
            color: activeTab === 'edit' ? 'var(--blue)' : 'var(--muted)',
            borderBottom: activeTab === 'edit' ? '2px solid var(--blue)' : '2px solid transparent',
          }}
        >
          编辑
        </button>
      </div>

      {/* Content */}
      <div style={{ flex: 1, overflowY: 'auto', padding: 16 }}>
        {activeTab === 'detail' ? (
          <DetailView entity={entity} fields={fields} attrs={attrs} pid={pid!} />
        ) : (
          <Form form={form} layout="vertical" onValuesChange={onValuesChange} disabled={!canWrite}>
            <Space size="large" style={{ marginBottom: 8 }}>
              <span>
                里程碑：
                <Switch
                  checkedChildren="★"
                  checked={entity.isMilestone === 1}
                  onChange={toggleMilestone}
                  disabled={!canWrite}
                />
              </span>
            </Space>
            <Form.Item label="状态">
              <Select
                allowClear
                placeholder="无状态"
                value={entity.status ?? undefined}
                options={STATUS_OPTIONS}
                onChange={(v) => setStatus(v ?? null)}
                disabled={!canWrite}
              />
            </Form.Item>
            <Divider orientation="left" plain>动态属性</Divider>
            <AttributeFields fields={fields} disabled={!canWrite} projectId={pid} />
            <Form.Item name="remark" label="备注" rules={[{ max: 512 }]}>
              <Input.TextArea rows={3} />
            </Form.Item>
            {dirty && (
              <Button type="primary" icon={<SaveOutlined />} loading={saving} onClick={handleSave} block>
                保存
              </Button>
            )}
            {canWrite && onDelete && (
              <Button danger icon={<DeleteOutlined />} onClick={onDelete} block style={{ marginTop: 12 }}>
                删除卡片
              </Button>
            )}
          </Form>
        )}
      </div>

      <style>{`
        .detail-heading {
          display: flex;
          align-items: flex-start;
          justify-content: space-between;
          gap: 12px;
          margin-bottom: 12px;
        }
        .detail-title {
          margin: 0;
          font-size: 15px;
          font-weight: 700;
          color: var(--text);
        }
        .status-badge {
          display: inline-block;
          padding: 2px 10px;
          border-radius: 12px;
          font-size: 11px;
          font-weight: 600;
          flex-shrink: 0;
        }
        .detail-section {
          padding: 12px 0;
          border-top: 1px solid var(--line);
        }
        .detail-section:first-of-type {
          border-top: 0;
          padding-top: 0;
        }
        .detail-section h4 {
          margin: 0 0 10px;
          color: #344054;
          font-size: 13px;
          font-weight: 600;
        }
        .stats-grid {
          display: grid;
          grid-template-columns: repeat(2, 1fr);
          gap: 9px;
        }
        .stat-card {
          min-height: 56px;
          padding: 10px;
          border: 1px solid var(--line);
          border-radius: 8px;
          background: #fbfcfe;
        }
        .stat-label {
          font-size: 12px;
          color: var(--muted);
        }
        .stat-value {
          display: block;
          margin-top: 6px;
          font-size: 16px;
          line-height: 1.1;
          overflow-wrap: anywhere;
        }
        .kv {
          display: grid;
          gap: 8px;
        }
        .kv-row {
          display: grid;
          grid-template-columns: 80px 1fr;
          gap: 12px;
          font-size: 13px;
          line-height: 1.55;
        }
        .kv-row span {
          color: var(--muted);
        }
        .kv-row strong {
          font-weight: 500;
          color: var(--text);
          overflow-wrap: anywhere;
        }
        .image-grid {
          display: grid;
          grid-template-columns: repeat(auto-fill, minmax(130px, 1fr));
          gap: 10px;
        }
        .image-card {
          padding: 8px;
          border: 1px solid var(--line);
          border-radius: 8px;
          background: #fbfcfe;
        }
        .image-meta {
          color: var(--muted);
          font-size: 12px;
          margin-bottom: 6px;
          overflow-wrap: anywhere;
        }
        .image-thumb-empty {
          width: 100%;
          aspect-ratio: 4/3;
          display: grid;
          place-items: center;
          border: 1px dashed var(--line);
          border-radius: 6px;
          color: var(--muted);
          font-size: 12px;
        }
      `}</style>
    </div>
  );
}
