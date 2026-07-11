import { useEffect, useMemo, useState } from 'react';
import { App, Button, Select } from 'antd';
import {
  PlusOutlined,
  CopyOutlined,
  AppstoreOutlined,
  CloseOutlined,
  HolderOutlined,
} from '@ant-design/icons';
import { useProjectStore } from '@/stores/projectStore';
import { useTreeStore } from '@/stores/treeStore';
import { entityApi } from '@/api/entity';
import { statsApi } from '@/api/misc';
import { parseSchemaFields, parseAttributes } from '@/utils/json';
import type { GlobalStatsVO, ProjectStatsVO, ProjectVO, EntityTreeNode, EntityVO } from '@/types/api';
import './LeftSidebar.css';

interface NumberStat {
  max: number;
  maxName: string;
  min: number;
  minName: string;
}

/** 从整棵树中提取指定字段的数值及对应卡片名称，计算最大/最小值。 */
function collectNumberStats(roots: EntityTreeNode[], fieldKey: string): NumberStat | null {
  let max = -Infinity;
  let min = Infinity;
  let maxName = '';
  let minName = '';
  let found = false;
  const walk = (nodes: EntityTreeNode[]) => {
    for (const n of nodes) {
      const attrs = parseAttributes(n.attributes);
      const v = attrs[fieldKey];
      if (v !== undefined && v !== null && v !== '') {
        const num = Number(v);
        if (!isNaN(num)) {
          found = true;
          if (num >= max) { max = num; maxName = n.name; }
          if (num <= min) { min = num; minName = n.name; }
        }
      }
      walk(n.children ?? []);
    }
  };
  walk(roots);
  return found ? { max, maxName, min, minName } : null;
}

interface Props {
  onCreateEntity: (parentId: string | null) => void;
  refreshKey: number;
}

export default function LeftSidebar({ onCreateEntity, refreshKey }: Props) {
  const { message } = App.useApp();
  const projects = useProjectStore((s) => s.projects);
  const currentProject = useProjectStore((s) => s.currentProject);
  const setCurrentProject = useProjectStore((s) => s.setCurrentProject);
  const canWrite = useProjectStore((s) => s.hasRole('EDITOR'));

  const tree = useTreeStore((s) => s.tree);
  const entityTemplates = useTreeStore((s) => s.entityTemplates);
  const selectedId = useTreeStore((s) => s.selectedId);
  const copySourceId = useTreeStore((s) => s.copySourceId);
  const setCopySourceId = useTreeStore((s) => s.setCopySourceId);

  const pid = currentProject?.id;

  const [globalStats, setGlobalStats] = useState<GlobalStatsVO | null>(null);
  const [projectStats, setProjectStats] = useState<ProjectStatsVO | null>(null);
  const [projectStatsMap, setProjectStatsMap] = useState<Map<string, ProjectStatsVO>>(new Map());
  const [projectListOpen, setProjectListOpen] = useState(true);
  const [selectedNumberKeys, setSelectedNumberKeys] = useState<string[]>([]);
  const [copyPending, setCopyPending] = useState(false);
  const [copyEntity, setCopyEntity] = useState<EntityVO | null>(null);
  // 汇总所有实体模板的 NUMBER 字段供选择
  const numberFieldOptions = useMemo(() => {
    const labelMap = new Map<string, string>();
    entityTemplates.forEach((t) => {
      parseSchemaFields(t.fieldSchema)
        .filter((f) => f.type === 'NUMBER')
        .forEach((f) => {
          if (!labelMap.has(f.key)) labelMap.set(f.key, f.label || f.key);
        });
    });
    return Array.from(labelMap.entries()).map(([key, label]) => ({ value: key, label }));
  }, [entityTemplates]);

  // 计算选中字段的统计数据
  const numberStats = useMemo(() => {
    const result = new Map<string, NumberStat>();
    for (const key of selectedNumberKeys) {
      const stat = collectNumberStats(tree, key);
      if (stat) result.set(key, stat);
    }
    return result;
  }, [selectedNumberKeys, tree]);

  // 字段 key → label 映射
  const fieldLabelMap = useMemo(() => {
    const m = new Map<string, string>();
    numberFieldOptions.forEach((o) => m.set(o.value, o.label));
    return m;
  }, [numberFieldOptions]);

  // 切换项目时重置选中的字段
  useEffect(() => {
    setSelectedNumberKeys([]);
  }, [pid]);

  // 加载全局统计
  useEffect(() => {
    statsApi.global().then(setGlobalStats).catch(() => undefined);
  }, [refreshKey]);

  // 加载项目统计（当前选中项目）
  useEffect(() => {
    if (!pid) return;
    statsApi.get(pid).then(setProjectStats).catch(() => undefined);
  }, [pid, refreshKey]);

  // 批量加载各项目统计（用于左侧列表展示）
  useEffect(() => {
    if (projects.length === 0) return;
    Promise.all(
      projects.map((p) =>
        statsApi.get(p.id).then((s) => [p.id, s] as const).catch(() => null),
      ),
    ).then((results) => {
      const m = new Map<string, ProjectStatsVO>();
      for (const r of results) {
        if (r) m.set(r[0], r[1]);
      }
      setProjectStatsMap(m);
    });
  }, [projects, refreshKey]);

  // 复制卡片：等待选择模式下，用户选中节点后自动填入
  useEffect(() => {
    if (copyPending && selectedId) {
      setCopySourceId(selectedId);
      setCopyPending(false);
    }
  }, [copyPending, selectedId, setCopySourceId]);

  // 复制卡片：加载源实体详情
  useEffect(() => {
    if (!pid || !copySourceId) {
      setCopyEntity(null);
      return;
    }
    entityApi.get(pid, copySourceId).then(setCopyEntity).catch(() => {
      setCopyEntity(null);
      setCopySourceId(null);
    });
  }, [pid, copySourceId, setCopySourceId]);

  const handleCopyClick = () => {
    if (selectedId) {
      setCopySourceId(selectedId);
    } else {
      setCopyPending(true);
      message.info('请在图谱中点击要复制的节点');
    }
  };

  const cancelCopy = () => {
    setCopySourceId(null);
    setCopyPending(false);
    setCopyEntity(null);
  };

  const handleDragStart = (e: React.DragEvent) => {
    if (!copyEntity) return;
    e.dataTransfer.setData('application/copy-entity', JSON.stringify({
      sourceEntityId: copyEntity.id,
      templateId: copyEntity.templateId,
      name: copyEntity.name,
      attributes: copyEntity.attributes,
    }));
    e.dataTransfer.effectAllowed = 'copy';
  };

  const handleSelectProject = (p: ProjectVO) => {
    setCurrentProject(p);
  };

  return (
    <div className="left-sidebar">
      <div className="sidebar-body">
        {/* ===== 第一部分：项目总览 ===== */}
        {projectStats && (
          <div className="sidebar-section">
            <div className="section-title">项目总览</div>
            <div className="kpi-grid">
              <div className="kpi">
                <div className="kpi-label">方案节点</div>
                <div className="kpi-value">{projectStats.totalNodes}</div>
              </div>
              <div className="kpi">
                <div className="kpi-label">已完成仿真</div>
                <div className="kpi-value">{projectStats.completedSim}</div>
              </div>
              <div className="kpi">
                <div className="kpi-label">仿真中</div>
                <div className="kpi-value">{projectStats.simulating}</div>
              </div>
              <div className="kpi">
                <div className="kpi-label">推荐</div>
                <div className="kpi-value">{projectStats.recommended}</div>
              </div>
            </div>
            {/* 数值属性统计 */}
            {numberFieldOptions.length > 0 && (
              <div style={{ marginTop: 10 }}>
                <Select
                  mode="multiple"
                  size="small"
                  placeholder="选择数值属性查看统计值"
                  value={selectedNumberKeys}
                  onChange={setSelectedNumberKeys}
                  options={numberFieldOptions}
                  style={{ width: '100%' }}
                  allowClear
                  maxTagCount="responsive"
                />
                {selectedNumberKeys.map((key) => {
                  const stat = numberStats.get(key);
                  const label = fieldLabelMap.get(key) || key;
                  if (!stat) return (
                    <div key={key} className="number-stat-card">
                      <div className="number-stat-title">{label}</div>
                      <div className="number-stat-empty">暂无数据</div>
                    </div>
                  );
                  return (
                    <div key={key} className="number-stat-card">
                      <div className="number-stat-title">{label}</div>
                      <div className="number-stat-row">
                        <span className="number-stat-label">最大值</span>
                        <span className="number-stat-value">{stat.max}</span>
                        <span className="number-stat-name" title={stat.maxName}>{stat.maxName}</span>
                      </div>
                      <div className="number-stat-row">
                        <span className="number-stat-label">最小值</span>
                        <span className="number-stat-value">{stat.min}</span>
                        <span className="number-stat-name" title={stat.minName}>{stat.minName}</span>
                      </div>
                    </div>
                  );
                })}
              </div>
            )}
          </div>
        )}

        {/* ===== 第二部分：我参与的所有项目 ===== */}
        <div className="sidebar-section">
          {/* 根选项：只可折叠/展开 */}
          <div
            className="project-root-item"
            onClick={() => setProjectListOpen(!projectListOpen)}
          >
            <div className="project-root-title">
              <AppstoreOutlined style={{ marginRight: 6 }} />
              我参与的所有项目
              <span className="toggle-arrow">{projectListOpen ? '▾' : '▸'}</span>
            </div>
            <div className="project-root-meta">
              共{globalStats?.projectCount ?? 0}个项目图谱·{globalStats?.cardCount ?? 0}张卡片·{globalStats?.assetCount ?? 0}个模型附件
            </div>
          </div>

          {/* 子选项：各项目 */}
          {projectListOpen && (
            <div className="part-tree">
              {projects.map((p) => {
                const ps = projectStatsMap.get(p.id);
                return (
                  <div
                    key={p.id}
                    className={`part-item ${p.id === currentProject?.id ? 'active' : ''}`}
                    onClick={() => handleSelectProject(p)}
                  >
                    <div className="part-item-content" style={{ flex: 1 }}>
                      <div className="part-item-name">{p.name}</div>
                      <div className="part-item-meta">
                        共{ps?.cardCount ?? 0}张卡片·{ps?.assetCount ?? 0}个模型附件
                      </div>
                    </div>
                    {(ps?.recommended ?? 0) > 0 && (
                      <span className="recommend-badge">{ps!.recommended}推荐</span>
                    )}
                  </div>
                );
              })}
            </div>
          )}
        </div>

        {/* ===== 第三部分：新建/复制卡片 ===== */}
        <div className="sidebar-section">
          <div className="section-title">新建卡片 / 复制卡片</div>
          <div className="library-actions">
            <Button
              className="ghost-btn"
              icon={<PlusOutlined />}
              disabled={!canWrite}
              onClick={() => onCreateEntity(null)}
              block
            >
              新建卡片
            </Button>
            <Button
              className="ghost-btn"
              icon={<CopyOutlined />}
              disabled={!canWrite}
              onClick={handleCopyClick}
              block
              type={copyPending ? 'primary' : 'default'}
              ghost={copyPending}
            >
              {copyPending ? '请点击节点…' : '复制卡片'}
            </Button>
          </div>

          {/* 复制预览卡片 */}
          {copyEntity && (
            <div
              className="copy-preview-card"
              draggable
              onDragStart={handleDragStart}
            >
              <div className="copy-preview-header">
                <HolderOutlined style={{ color: 'var(--muted)', cursor: 'grab' }} />
                <span className="copy-preview-name">{copyEntity.name}（副本）</span>
                <Button type="text" size="small" icon={<CloseOutlined />} onClick={cancelCopy} />
              </div>
              <div className="copy-preview-meta">
                {entityTemplates.find((t) => t.id === copyEntity.templateId)?.name ?? ''}
              </div>
              <div className="copy-preview-hint">拖拽到图谱中创建副本</div>
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
