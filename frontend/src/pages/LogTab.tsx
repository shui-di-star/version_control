import { useEffect, useState } from 'react';
import { Table, Button, Modal, Typography } from 'antd';
import { logApi } from '@/api/misc';
import { useProjectStore } from '@/stores/projectStore';
import type { OperationLogVO } from '@/types/api';

// 操作日志（仅 ADMIN）：action/target/操作人/时间/detail 快照。
export default function LogTab() {
  const currentProject = useProjectStore((s) => s.currentProject);
  const pid = currentProject?.id;
  const [data, setData] = useState<OperationLogVO[]>([]);
  const [loading, setLoading] = useState(false);
  const [detail, setDetail] = useState<string | null>(null);

  useEffect(() => {
    if (!pid) return;
    setLoading(true);
    logApi
      .list(pid)
      .then(setData)
      .finally(() => setLoading(false));
  }, [pid]);

  const columns = [
    { title: '操作', dataIndex: 'action' },
    { title: '目标类型', dataIndex: 'targetType' },
    { title: '目标 id', dataIndex: 'targetId' },
    { title: '操作人 id', dataIndex: 'userId' },
    {
      title: '时间',
      dataIndex: 'createdAt',
      render: (v: string) => (v ? v.replace('T', ' ').slice(0, 19) : ''),
    },
    {
      title: '详情',
      key: 'detail',
      render: (_: unknown, l: OperationLogVO) =>
        l.detail ? (
          <Button type="link" onClick={() => setDetail(l.detail!)}>
            查看
          </Button>
        ) : null,
    },
  ];

  return (
    <>
      <Table rowKey="id" loading={loading} columns={columns} dataSource={data} />
      <Modal open={!!detail} onCancel={() => setDetail(null)} footer={null} title="操作详情快照" width={640}>
        <Typography.Paragraph>
          <pre style={{ whiteSpace: 'pre-wrap', wordBreak: 'break-all' }}>
            {detail ? tryPretty(detail) : ''}
          </pre>
        </Typography.Paragraph>
      </Modal>
    </>
  );
}

function tryPretty(s: string): string {
  try {
    return JSON.stringify(JSON.parse(s), null, 2);
  } catch {
    return s;
  }
}
