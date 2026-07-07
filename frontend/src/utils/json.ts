import type { FieldSchema, SchemaField } from '@/types/api';

/** 安全解析 JSON 字符串，失败返回 fallback。 */
export function safeParse<T>(json: string | undefined | null, fallback: T): T {
  if (!json) return fallback;
  try {
    return JSON.parse(json) as T;
  } catch {
    return fallback;
  }
}

/** 解析模板 fieldSchema JSON 为字段数组。 */
export function parseSchemaFields(fieldSchema?: string | null): SchemaField[] {
  const parsed = safeParse<FieldSchema>(fieldSchema, { fields: [] });
  return Array.isArray(parsed.fields) ? parsed.fields : [];
}

/** 解析实体 attributes JSON 为键值对象。 */
export function parseAttributes(attributes?: string | null): Record<string, unknown> {
  return safeParse<Record<string, unknown>>(attributes, {});
}
