import { Form, Input, InputNumber, Select, DatePicker } from 'antd';
import dayjs from 'dayjs';
import type { SchemaField } from '@/types/api';

// 按模板 field_schema 动态渲染 Form.Item。FILE 类型此处以文本 id 承载（指向本实体产出物）。
// 用于 Form 内：字段名前缀 attrs.<key>。
export default function AttributeFields({
  fields,
  disabled,
}: {
  fields: SchemaField[];
  disabled?: boolean;
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
          case 'FILE':
            control = <Input placeholder="产出物 id" disabled={disabled} />;
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
