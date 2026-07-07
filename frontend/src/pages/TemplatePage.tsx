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
    <Tabs
      items={[
        { key: 'entity', label: '实体模板', children: <EntityTemplateTab /> },
        { key: 'relation', label: '关系模板', children: <RelationTemplateTab /> },
      ]}
    />
  );
}
