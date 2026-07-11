import { Tabs, Empty } from 'antd';
import { useProjectStore } from '@/stores/projectStore';
import EntityTemplateTab from './EntityTemplateTab';
import RelationTemplateTab from './RelationTemplateTab';

export default function TemplatePage() {
  const currentProject = useProjectStore((s) => s.currentProject);
  if (!currentProject) {
    return <Empty description="请先在顶部选择一个项目" />;
  }
  return (
    <div className="theme-panel" style={{ padding: '16px 20px', maxWidth: 1100, margin: '0 auto', minHeight: 'calc(100vh - 80px)' }}>
      <h2 style={{ margin: '0 0 12px', fontSize: 18, fontWeight: 700, color: 'var(--text)' }}>模板管理</h2>
      <Tabs
        items={[
          { key: 'entity', label: '实体模板', children: <EntityTemplateTab /> },
          { key: 'relation', label: '关系模板', children: <RelationTemplateTab /> },
        ]}
      />
    </div>
  );
}
