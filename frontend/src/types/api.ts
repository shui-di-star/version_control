// 与后端 DTO/VO 一一对应的类型定义。
// 红线：雪花 id（后端 Long）全局序列化为 JSON 字符串 → 前端一律 string；
// 统计计数（后端基本型 long）仍为 JSON number → 前端 number。

/** 统一响应结构。 */
export interface Result<T> {
  code: number;
  message: string;
  data: T;
}

/** 后端 ResultCode 错误码。 */
export const ResultCode = {
  SUCCESS: 0,
  BAD_REQUEST: 1000,
  VALIDATION_ERROR: 1001,
  INTERNAL_ERROR: 1002,
  UNAUTHORIZED: 2000,
  FORBIDDEN: 2001,
  NOT_FOUND: 3000,
  CONFLICT: 3001,
  BUSINESS_ERROR: 3002,
} as const;

// ---------- 认证 ----------
export interface RegisterRequest {
  username: string;
  password: string;
  email?: string;
  displayName?: string;
}

export interface LoginRequest {
  username: string;
  password: string;
}

export interface LoginResponse {
  token: string;
  userId: string;
  username: string;
  systemRole: string;
}

export interface UserInfo {
  id: string;
  username: string;
  email?: string;
  displayName?: string;
  systemRole: string;
}

// ---------- 项目与成员 ----------
export type ProjectRoleName = 'ADMIN' | 'EDITOR' | 'VIEWER';

export interface ProjectVO {
  id: string;
  name: string;
  description?: string;
  ownerId: string;
  ownerName?: string;
  myRole: ProjectRoleName;
}

export interface ProjectCreateRequest {
  name: string;
  description?: string;
}

export type ProjectUpdateRequest = ProjectCreateRequest;

export interface MemberVO {
  userId: string;
  username?: string;
  displayName?: string;
  role: ProjectRoleName;
}

export interface MemberAddRequest {
  username: string;
  role: ProjectRoleName;
}

// ---------- 模板 ----------
export type FieldType = 'TEXT' | 'NUMBER' | 'ENUM' | 'DATE' | 'IMAGE';

/** field_schema 中单个字段定义。 */
export interface SchemaField {
  key: string;
  label: string;
  type: FieldType;
  required?: boolean;
  options?: string[];
  showOnCard?: boolean;
  unit?: string;
  compareInCard?: boolean;
  keyMetric?: boolean;
  /** 卡片上属性名字体大小 (默认11) */
  cardLabelFontSize?: number;
  /** 卡片上属性名颜色 (默认#657386) */
  cardLabelColor?: string;
  /** 卡片上属性名加粗 */
  cardLabelBold?: boolean;
  /** 卡片上属性值字体大小 (默认13) */
  cardValueFontSize?: number;
  /** 卡片上属性值颜色 (默认#17202a) */
  cardValueColor?: string;
  /** 卡片上属性值加粗 */
  cardValueBold?: boolean;
}

/** field_schema JSON 顶层结构。 */
export interface FieldSchema {
  fields: SchemaField[];
}

export interface EntityTemplateVO {
  id: string;
  projectId: string;
  name: string;
  fieldSchema?: string; // JSON 字符串
}

export interface EntityTemplateRequest {
  name: string;
  fieldSchema?: string;
}

export interface RelationTemplateVO {
  id: string;
  projectId: string;
  name: string;
  directed?: number; // 0/1
  lineStyle?: string; // JSON 字符串
  allowedFrom?: string; // JSON 数组字符串
  allowedTo?: string;
}

export interface RelationTemplateRequest {
  name: string;
  directed?: number;
  lineStyle?: string;
  allowedFrom?: string;
  allowedTo?: string;
}

// ---------- 实体 ----------
export type EntityStatus = 'RECOMMENDED' | 'DEPRECATED' | 'SIMULATING' | 'COMPLETED';

export interface EntityTreeNode {
  id: string;
  parentId?: string;
  name: string;
  templateId: string;
  status?: EntityStatus | null;
  isMilestone?: number; // 0/1
  parentRelationTemplateId?: string;
  parentRelationRemark?: string;
  attributes?: string;
  children: EntityTreeNode[];
}

export interface EntityVO {
  id: string;
  projectId: string;
  templateId: string;
  parentId?: string;
  name: string;
  status?: EntityStatus | null;
  isMilestone?: number;
  remark?: string;
  attributes?: string; // JSON 字符串
  parentRelationTemplateId?: string;
  parentRelationRemark?: string;
}

export interface EntityCreateRequest {
  templateId: string;
  parentId?: string | null;
  name: string;
  remark?: string;
  attributes?: string;
  parentRelationTemplateId?: string;
  parentRelationRemark?: string;
}

export interface EntityUpdateRequest {
  name: string;
  remark?: string;
  attributes?: string;
}

export type ChildStrategy = 'CASCADE' | 'PROMOTE';

// ---------- 关系 ----------
export interface RelationVO {
  id: string;
  projectId: string;
  templateId: string;
  fromEntityId: string;
  toEntityId: string;
  remark?: string;
}

export interface RelationCreateRequest {
  templateId: string;
  fromEntityId: string;
  toEntityId: string;
  remark?: string;
}

export interface RelationUpdateRequest {
  templateId?: string;
  remark?: string;
}

// ---------- 产出物 ----------
export interface AssetVO {
  id: string;
  entityId: string;
  assetType: string;
  fileName?: string;
  objectKey?: string;
  contentText?: string;
  size?: number;
  mimeType?: string;
}

// ---------- 统计 ----------
export interface ProjectStatsVO {
  totalNodes: number;
  completedSim: number;
  simulating: number;
  recommended: number;
  maxNumberValue: number | null;
  cardCount: number;
  assetCount: number;
}

export interface GlobalStatsVO {
  projectCount: number;
  cardCount: number;
  assetCount: number;
}

// ---------- 搜索 ----------
export interface SearchHit {
  sourceType: 'ENTITY' | 'RELATION' | 'PARENT_RELATION';
  entityId?: string | null;
  relationId?: string | null;
  fromEntityId?: string | null;
  toEntityId?: string | null;
  field: string;
  snippet: string;
}

// ---------- 操作日志 ----------
export interface OperationLogVO {
  id: string;
  projectId: string;
  userId: string;
  action: string;
  targetType?: string;
  targetId?: string;
  detail?: string;
  createdAt: string;
}

// ---------- 导入导出 ----------
export interface ProjectExport {
  entityTemplates: unknown[];
  relationTemplates: unknown[];
  entities: unknown[];
  relations: unknown[];
  assets: unknown[];
  edgeRemarks: unknown[];
  edgeRemarkImages: unknown[];
}

// ---------- 连线备注 ----------
export interface EdgeRemarkImageVO {
  id: string;
  fileName: string;
  objectKey: string;
  size?: number;
  mimeType?: string;
}

export interface EdgeRemarkVO {
  id: string;
  entityId: string;
  content: string;
  sortOrder: number;
  images: EdgeRemarkImageVO[];
  createdAt: string;
}
