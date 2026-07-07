import { Button, Input, Select, Checkbox, Space, Table } from 'antd';
import { DeleteOutlined, PlusOutlined } from '@ant-design/icons';
import type { SchemaField } from '@/types/api';
import { FIELD_TYPES, FIELD_TYPE_LABEL } from '@/utils/constants';

// field_schema 可视化编辑器：增删字段行（key/label/type/required/options）。
// 受控组件：value 为字段数组，onChange 回传新数组。
interface Props {
  value: SchemaField[];
  onChange: (fields: SchemaField[]) => void;
}

export default function FieldSchemaEditor({ value, onChange }: Props) {
  const update = (idx: number, patch: Partial<SchemaField>) => {
    const next = value.map((f, i) => (i === idx ? { ...f, ...patch } : f));
    onChange(next);
  };

  const add = () => onChange([...value, { key: '', label: '', type: 'TEXT', required: false }]);
  const remove = (idx: number) => onChange(value.filter((_, i) => i !== idx));

  const columns = [
    {
      title: 'key',
      dataIndex: 'key',
      render: (_: unknown, _f: SchemaField, i: number) => (
        <Input
          value={value[i].key}
          placeholder="字段键"
          onChange={(e) => update(i, { key: e.target.value })}
        />
      ),
    },
    {
      title: '显示名',
      dataIndex: 'label',
      render: (_: unknown, _f: SchemaField, i: number) => (
        <Input
          value={value[i].label}
          placeholder="标签"
          onChange={(e) => update(i, { label: e.target.value })}
        />
      ),
    },
    {
      title: '类型',
      dataIndex: 'type',
      width: 120,
      render: (_: unknown, _f: SchemaField, i: number) => (
        <Select
          style={{ width: '100%' }}
          value={value[i].type}
          options={FIELD_TYPES.map((t) => ({ value: t, label: FIELD_TYPE_LABEL[t] }))}
          onChange={(t) => update(i, { type: t })}
        />
      ),
    },
    {
      title: '必填',
      dataIndex: 'required',
      width: 60,
      render: (_: unknown, _f: SchemaField, i: number) => (
        <Checkbox
          checked={value[i].required}
          onChange={(e) => update(i, { required: e.target.checked })}
        />
      ),
    },
    {
      title: '展示在仿真卡',
      dataIndex: 'showOnCard',
      width: 100,
      render: (_: unknown, _f: SchemaField, i: number) => (
        <Checkbox
          checked={value[i].showOnCard}
          onChange={(e) => update(i, { showOnCard: e.target.checked })}
        />
      ),
    },
    {
      title: '枚举选项（逗号分隔）',
      dataIndex: 'options',
      render: (_: unknown, _f: SchemaField, i: number) =>
        value[i].type === 'ENUM' ? (
          <Input
            value={(value[i].options ?? []).join(',')}
            placeholder="选项A,选项B"
            onChange={(e) =>
              update(i, {
                options: e.target.value
                  .split(',')
                  .map((s) => s.trim())
                  .filter(Boolean),
              })
            }
          />
        ) : (
          <span style={{ color: '#bbb' }}>—</span>
        ),
    },
    {
      title: '',
      key: 'op',
      width: 40,
      render: (_: unknown, _f: SchemaField, i: number) => (
        <Button type="text" danger icon={<DeleteOutlined />} onClick={() => remove(i)} />
      ),
    },
  ];

  return (
    <Space direction="vertical" style={{ width: '100%' }}>
      <Table
        rowKey={(record) => record.key || Math.random().toString()}
        size="small"
        pagination={false}
        columns={columns}
        dataSource={value}
      />
      <Button icon={<PlusOutlined />} onClick={add} block>
        添加字段
      </Button>
    </Space>
  );
}
