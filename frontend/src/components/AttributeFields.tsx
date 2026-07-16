import { useState } from 'react';
import { Form, Input, InputNumber, Select, DatePicker, Upload, Button, App, Image } from 'antd';
import { DeleteOutlined, PlusOutlined } from '@ant-design/icons';
import dayjs from 'dayjs';
import type { SchemaField } from '@/types/api';
import { attrImageApi } from '@/api/misc';

// 按模板 field_schema 动态渲染 Form.Item。
// IMAGE 类型：上传图片到独立的属性图片接口，存 objectKey 数组到 attributes JSON。
// 与产出物（Asset）完全独立。
export default function AttributeFields({
  fields,
  disabled,
  projectId,
}: {
  fields: SchemaField[];
  disabled?: boolean;
  projectId?: string;
}) {
  return (
    <>
      {fields.map((f) => {
        const name = ['attrs', f.key];
        const rules = f.required ? [{ required: true, message: `${f.label}必填` }] : [];
        let control;
        switch (f.type) {
          case 'NUMBER':
            control = <InputNumber style={{ width: '100%' }} disabled={disabled} />;
            break;
          case 'ENUM':
            control = (
              <Select
                disabled={disabled}
                allowClear
                options={(f.options ?? []).map((o) => ({ value: o, label: o }))}
              />
            );
            break;
          case 'DATE':
            control = <DatePicker style={{ width: '100%' }} disabled={disabled} />;
            break;
          case 'IMAGE':
            control = (
              <MultiImageAttrField
                disabled={disabled}
                projectId={projectId}
              />
            );
            break;
          default:
            control = <Input disabled={disabled} />;
        }
        return (
          <Form.Item key={f.key} name={name} label={f.label} rules={rules}>
            {control}
          </Form.Item>
        );
      })}
    </>
  );
}

/** 将旧格式单字符串或新格式数组统一转为 string[] */
function normalizeImageValue(value: unknown): string[] {
  if (Array.isArray(value)) return value.filter((v) => typeof v === 'string' && v.trim());
  if (typeof value === 'string' && value.trim()) return [value];
  return [];
}

/** 多图 IMAGE 属性字段：受控组件，value/onChange 支持 string[] | string。 */
function MultiImageAttrField({
  value,
  onChange,
  disabled,
  projectId,
}: {
  value?: string[] | string;
  onChange?: (v: string[]) => void;
  disabled?: boolean;
  projectId?: string;
}) {
  const { message } = App.useApp();
  const [uploading, setUploading] = useState(false);

  const images = normalizeImageValue(value);

  const handleUpload = async (file: File) => {
    if (!projectId) return;
    setUploading(true);
    try {
      const res = await attrImageApi.upload(projectId, file);
      onChange?.([...images, res.objectKey]);
      message.success('图片已上传');
    } catch {
      message.error('图片上传失败');
    } finally {
      setUploading(false);
    }
  };

  const handleRemove = (idx: number) => {
    const next = images.filter((_, i) => i !== idx);
    onChange?.(next);
  };

  return (
    <div>
      {images.length > 0 && (
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(100px, 1fr))', gap: 8, marginBottom: 8 }}>
          {images.map((key, idx) => (
            <div key={key} style={{ position: 'relative', border: '1px solid #e8e8e8', borderRadius: 6, overflow: 'hidden' }}>
              <Image
                src={projectId ? attrImageApi.previewUrl(projectId, key) : undefined}
                alt={`图片${idx + 1}`}
                style={{ width: '100%', aspectRatio: '1', objectFit: 'cover' }}
                preview={{ mask: '预览' }}
              />
              {!disabled && (
                <Button
                  type="text"
                  danger
                  size="small"
                  icon={<DeleteOutlined />}
                  onClick={() => handleRemove(idx)}
                  style={{
                    position: 'absolute', top: 2, right: 2,
                    background: 'rgba(255,255,255,0.85)', borderRadius: 4,
                    width: 22, height: 22, padding: 0,
                  }}
                />
              )}
            </div>
          ))}
        </div>
      )}
      {!disabled && projectId ? (
        <Upload
          showUploadList={false}
          accept="image/*"
          multiple
          beforeUpload={(file) => {
            handleUpload(file);
            return false;
          }}
        >
          <Button icon={<PlusOutlined />} loading={uploading} size="small">
            添加图片
          </Button>
        </Upload>
      ) : !projectId ? (
        <span style={{ color: 'var(--muted)', fontSize: 12 }}>创建实体后可上传图片</span>
      ) : null}
    </div>
  );
}

/** 把后端 attributes 对象转为表单值（DATE 转 dayjs）。 */
export function attrsToForm(
  attrs: Record<string, unknown>,
  fields: SchemaField[],
): Record<string, unknown> {
  const out: Record<string, unknown> = {};
  for (const f of fields) {
    const v = attrs[f.key];
    if (v == null) continue;
    if (f.type === 'DATE') {
      out[f.key] = dayjs(String(v));
    } else if (f.type === 'IMAGE') {
      // 兼容旧数据：单字符串转数组
      out[f.key] = normalizeImageValue(v);
    } else {
      out[f.key] = v;
    }
  }
  return out;
}

/** 把表单值转回后端 attributes 对象（DATE 转 YYYY-MM-DD）。 */
export function formToAttrs(
  formAttrs: Record<string, unknown>,
  fields: SchemaField[],
): Record<string, unknown> {
  const out: Record<string, unknown> = {};
  for (const f of fields) {
    const v = formAttrs?.[f.key];
    if (v == null || v === '') continue;
    if (f.type === 'DATE' && dayjs.isDayjs(v)) {
      out[f.key] = (v as dayjs.Dayjs).format('YYYY-MM-DD');
    } else if (f.type === 'IMAGE') {
      // IMAGE 字段存为数组；空数组不存
      const arr = normalizeImageValue(v);
      if (arr.length > 0) out[f.key] = arr;
    } else {
      out[f.key] = v;
    }
  }
  return out;
}
