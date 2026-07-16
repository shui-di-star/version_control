import type { EntityStatus, FieldType, SchemaField } from '@/types/api';

/** 实体状态：值 → 中文标签 + 展示色（对齐示例项目）。 */
export const STATUS_META: Record<EntityStatus, { label: string; color: string }> = {
  RECOMMENDED: { label: '推荐', color: '#c73b3b' },
  DEPRECATED: { label: '淘汰', color: '#7c8794' },
  SIMULATING: { label: '仿真中', color: '#b26a00' },
  COMPLETED: { label: '已完成', color: '#168a4a' },
};

export const STATUS_OPTIONS: { value: EntityStatus; label: string }[] = (
  Object.keys(STATUS_META) as EntityStatus[]
).map((v) => ({ value: v, label: STATUS_META[v].label }));

/** 字段类型 → 中文标签。 */
export const FIELD_TYPE_LABEL: Record<FieldType, string> = {
  TEXT: '文本',
  NUMBER: '数字',
  ENUM: '枚举',
  DATE: '日期',
  IMAGE: '图片',
};

export const FIELD_TYPES: FieldType[] = ['TEXT', 'NUMBER', 'ENUM', 'DATE', 'IMAGE'];

/** 产出物大小上限：100MB（与后端一致）。 */
export const MAX_ASSET_SIZE = 100 * 1024 * 1024;

/** 新建实体模板时的预设字段（4个，与后端 EntityTemplateServiceImpl.DEFAULT_FIELD_SCHEMA 一致）。 */
export const DEFAULT_ENTITY_FIELDS: SchemaField[] = [
  { key: 'card_name', label: '卡片名称', type: 'TEXT', required: true, showOnCard: true, compareInCard: true },
  { key: 'time', label: '时间', type: 'DATE', required: false, showOnCard: false },
  { key: 'owner', label: '负责人', type: 'TEXT', required: false, showOnCard: true, compareInCard: true },
  { key: 'conclusion_suggestion', label: '结论及建议', type: 'TEXT', required: false, showOnCard: false },
];
