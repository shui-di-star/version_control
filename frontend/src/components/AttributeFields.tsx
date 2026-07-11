import { useState } from 'react';
import { Form, Input, InputNumber, Select, DatePicker, Upload, Button, App } from 'antd';
import { UploadOutlined } from '@ant-design/icons';
import dayjs from 'dayjs';
import type { SchemaField } from '@/types/api';
import { attrImageApi } from '@/api/misc';

// 按模板 field_schema 动态渲染 Form.Item。
// IMAGE 类型：上传图片到独立的属性图片接口，存 objectKey 到 attributes JSON。
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
              <ImageAttrField
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

/** IMAGE 属性字段：受控组件，value/onChange 为 objectKey 字符串。 */
function ImageAttrField({
  value,
  onChange,
  disabled,
  projectId,
}: {
  value?: string;
  onChange?: (v: string | undefined) => void;
  disabled?: boolean;
  projectId?: string;
}) {
  const { message } = App.useApp();
  const [uploading, setUploading] = useState(false);

  const handleUpload = async (file: File) => {
    if (!projectId) return;
    setUploading(true);
    try {
      const res = await attrImageApi.upload(projectId, file);
      onChange?.(res.objectKey);
      message.success('图片已上传');
    } catch {
      message.error('图片上传失败');
    } finally {
      setUploading(false);
    }
  };

  return (
    <div>
      {value && projectId && (
        <img
          src={attrImageApi.previewUrl(projectId, value)}
          alt="预览"
          style={{ maxWidth: '100%', maxHeight: 200, borderRadius: 6, marginBottom: 8, display: 'block' }}
        />
      )}
      {!disabled && projectId ? (
        <Upload
          showUploadList={false}
          accept="image/*"
          beforeUpload={(file) => {
            handleUpload(file);
            return false;
          }}
        >
          <Button icon={<UploadOutlined />} loading={uploading} size="small">
            {value ? '更换图片' : '上传图片'}
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
    out[f.key] = f.type === 'DATE' ? dayjs(String(v)) : v;
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
    } else {
      out[f.key] = v;
    }
  }
  return out;
}
