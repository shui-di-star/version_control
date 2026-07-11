import { useRef } from 'react';
import { Button, Input, Select, Checkbox, Space } from 'antd';
import { DeleteOutlined, PlusOutlined, ArrowUpOutlined, ArrowDownOutlined } from '@ant-design/icons';
import type { SchemaField } from '@/types/api';
import { FIELD_TYPES, FIELD_TYPE_LABEL } from '@/utils/constants';

interface Props {
  value: SchemaField[];
  onChange: (fields: SchemaField[]) => void;
}

let uidSeq = 0;

/** 带稳定 _rid 的行数据，用于 Table rowKey。 */
interface Row extends SchemaField {
  _rid: string;
}

export default function FieldSchemaEditor({ value, onChange }: Props) {
  // 每个条目分配一个稳定的行 id，跟随增删同步
  const rids = useRef<string[]>([]);
  while (rids.current.length < value.length) rids.current.push(`r${++uidSeq}`);
  if (rids.current.length > value.length) rids.current.length = value.length;

  // 合并 _rid 用于渲染（不回传给 onChange）
  const rows: Row[] = value.map((f, i) => ({ ...f, _rid: rids.current[i] }));

  const update = (idx: number, patch: Partial<SchemaField>) => {
    const next = value.map((f, i) => {
      if (i !== idx) return f;
      const merged = { ...f, ...patch };
      if ('label' in patch && (!f.key || f.key === f.label)) {
        merged.key = patch.label ?? '';
      }
      if ('type' in patch && patch.type === 'IMAGE') {
        merged.showOnCard = false;
        merged.compareInCard = false;
        merged.keyMetric = false;
      }
      // NUMBER/ENUM 新切换时默认勾选关键指标、展示在卡片上、参与对比
      if ('type' in patch && (patch.type === 'NUMBER' || patch.type === 'ENUM')) {
        if (f.keyMetric === undefined) merged.keyMetric = true;
        if (f.showOnCard === undefined) merged.showOnCard = true;
        if (f.compareInCard === undefined) merged.compareInCard = true;
      }
      return merged;
    });
    onChange(next);
  };

  const add = () => {
    rids.current.push(`r${++uidSeq}`);
    onChange([...value, { key: '', label: '', type: 'TEXT', required: false }]);
  };

  const remove = (idx: number) => {
    rids.current.splice(idx, 1);
    onChange(value.filter((_, i) => i !== idx));
  };

  const moveUp = (idx: number) => {
    if (idx <= 0) return;
    const next = [...value];
    [next[idx - 1], next[idx]] = [next[idx], next[idx - 1]];
    const r = rids.current;
    [r[idx - 1], r[idx]] = [r[idx], r[idx - 1]];
    onChange(next);
  };

  const moveDown = (idx: number) => {
    if (idx >= value.length - 1) return;
    const next = [...value];
    [next[idx], next[idx + 1]] = [next[idx + 1], next[idx]];
    const r = rids.current;
    [r[idx], r[idx + 1]] = [r[idx + 1], r[idx]];
    onChange(next);
  };

  return (
    <Space direction="vertical" style={{ width: '100%' }}>
      <div style={{ overflowX: 'auto' }}>
      <table style={{ minWidth: 980, borderCollapse: 'collapse', fontSize: 13 }}>
        <thead>
          <tr style={{ textAlign: 'left', color: 'var(--muted)', fontSize: 12 }}>
            <th style={{ padding: '6px 4px', minWidth: 120 }}>显示名</th>
            <th style={{ padding: '6px 4px', width: 100 }}>类型</th>
            <th style={{ padding: '6px 4px', width: 70 }}>单位</th>
            <th style={{ padding: '6px 4px', width: 44 }}>必填</th>
            <th style={{ padding: '6px 4px', width: 80 }}>展示在卡片上</th>
            <th style={{ padding: '6px 4px', width: 70 }}>参与对比</th>
            <th style={{ padding: '6px 4px', width: 70 }}>重要指标</th>
            <th style={{ padding: '6px 4px', minWidth: 120 }}>枚举选项（逗号分隔）</th>
            <th style={{ padding: '6px 4px', width: 36 }} />
            <th style={{ padding: '6px 4px', width: 70 }}>排序</th>
          </tr>
        </thead>
        <tbody>
          {rows.map((row, i) => (
            <tr key={row._rid} style={{ borderTop: '1px solid var(--line, #f0f0f0)' }}>
              <td style={{ padding: '4px' }}>
                <Input
                  value={row.label}
                  placeholder="标签"
                  size="small"
                  onChange={(e) => update(i, { label: e.target.value })}
                />
              </td>
              <td style={{ padding: '4px' }}>
                <Select
                  style={{ width: '100%' }}
                  size="small"
                  value={row.type}
                  options={FIELD_TYPES.map((t) => ({ value: t, label: FIELD_TYPE_LABEL[t] }))}
                  onChange={(t) => update(i, { type: t })}
                />
              </td>
              <td style={{ padding: '4px' }}>
                {row.type === 'NUMBER' ? (
                  <Input
                    value={row.unit ?? ''}
                    placeholder="如 kg"
                    size="small"
                    onChange={(e) => update(i, { unit: e.target.value || undefined })}
                  />
                ) : (
                  <span style={{ color: '#bbb' }}>—</span>
                )}
              </td>
              <td style={{ padding: '4px', textAlign: 'center' }}>
                <Checkbox
                  checked={row.required}
                  disabled={row.key === 'card_name'}
                  onChange={(e) => update(i, { required: e.target.checked })}
                />
              </td>
              <td style={{ padding: '4px', textAlign: 'center' }}>
                <Checkbox
                  checked={row.showOnCard}
                  disabled={row.type === 'IMAGE' || row.key === 'card_name'}
                  onChange={(e) => update(i, { showOnCard: e.target.checked })}
                />
              </td>
              <td style={{ padding: '4px', textAlign: 'center' }}>
                <Checkbox
                  checked={row.compareInCard}
                  disabled={row.type === 'IMAGE' || row.key === 'card_name'}
                  onChange={(e) => update(i, { compareInCard: e.target.checked })}
                />
              </td>
              <td style={{ padding: '4px', textAlign: 'center' }}>
                <Checkbox
                  checked={row.keyMetric}
                  disabled={row.type === 'IMAGE' || row.type === 'DATE' || row.type === 'TEXT'}
                  onChange={(e) => update(i, { keyMetric: e.target.checked })}
                />
              </td>
              <td style={{ padding: '4px' }}>
                {row.type === 'ENUM' ? (
                  <Input
                    value={(row.options ?? []).join('，')}
                    placeholder="选项A，选项B"
                    size="small"
                    onChange={(e) =>
                      update(i, {
                        options: e.target.value
                          .split(/[,，]/),
                      })
                    }
                    onBlur={(e) =>
                      update(i, {
                        options: e.target.value
                          .split(/[,，]/)
                          .map((s) => s.trim())
                          .filter(Boolean),
                      })
                    }
                  />
                ) : (
                  <span style={{ color: '#bbb' }}>—</span>
                )}
              </td>
              <td style={{ padding: '4px' }}>
                <Button type="text" danger size="small" icon={<DeleteOutlined />} onClick={() => remove(i)} />
              </td>
              <td style={{ padding: '4px', whiteSpace: 'nowrap' }}>
                <Button type="text" size="small" icon={<ArrowUpOutlined />} disabled={i === 0} onClick={() => moveUp(i)} />
                <Button type="text" size="small" icon={<ArrowDownOutlined />} disabled={i === rows.length - 1} onClick={() => moveDown(i)} />
              </td>
            </tr>
          ))}
        </tbody>
      </table>
      </div>
      <Button icon={<PlusOutlined />} onClick={add} block>
        添加字段
      </Button>
    </Space>
  );
}
