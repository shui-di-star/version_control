import type { EntityStatus, FieldType } from '@/types/api';

/** 实体状态：值 → 中文标签 + 展示色。 */
export const STATUS_META: Record<EntityStatus, { label: string; color: string }> = {
  RECOMMENDED: { label: '推荐', color: '#f5222d' },
  DEPRECATED: { label: '淘汰', color: '#8c8c8c' },
  SIMULATING: { label: '仿真中', color: '#faad14' },
  COMPLETED: { label: '已完成', color: '#52c41a' },
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
  FILE: '文件',
};

export const FIELD_TYPES: FieldType[] = ['TEXT', 'NUMBER', 'ENUM', 'DATE', 'FILE'];

/** 产出物大小上限：100MB（与后端一致）。 */
export const MAX_ASSET_SIZE = 100 * 1024 * 1024;
