import { Tabs, Empty } from 'antd';
import { useProjectStore } from '@/stores/projectStore';
import MemberTab from './MemberTab';
import PortTab from './PortTab';
import LogTab from './LogTab';

export default function SettingsPage() {
  const currentProject = useProjectStore((s) => s.currentProject);
  const isAdmin = useProjectStore((s) => s.hasRole('ADMIN'));

  if (!currentProject) {
    return <Empty description="请先在顶部选择一个项目" />;
  }

  const items = [{ key: 'members', label: '成员权限', children: <MemberTab /> }];
  // 导入导出、操作日志仅 ADMIN 可见。
  if (isAdmin) {
    items.push({ key: 'port', label: '导入导出', children: <PortTab /> });
    items.push({ key: 'logs', label: '操作日志', children: <LogTab /> });
  }

  return <Tabs items={items} />;
}
