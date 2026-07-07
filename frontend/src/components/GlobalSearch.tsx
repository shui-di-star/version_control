import { useState } from 'react';
import { AutoComplete, Input } from 'antd';
import { useNavigate } from 'react-router-dom';
import { searchApi } from '@/api/misc';
import { useProjectStore } from '@/stores/projectStore';
import { useTreeStore } from '@/stores/treeStore';
import type { SearchHit } from '@/types/api';

const SOURCE_LABEL: Record<string, string> = {
  ENTITY: '实体',
  RELATION: '语义关系',
  PARENT_RELATION: '父子关系',
};

// 全局搜索框：调 /search，结果下拉；点击结果跳树视图并定位。
export default function GlobalSearch() {
  const navigate = useNavigate();
  const currentProject = useProjectStore((s) => s.currentProject);
  const setSearchHits = useTreeStore((s) => s.setSearchHits);
  const select = useTreeStore((s) => s.select);
  const setPathHighlight = useTreeStore((s) => s.setPathHighlight);
  const setSelectedEdgeFromSearch = useTreeStore((s) => s.setSelectedEdgeFromSearch);
  const [options, setOptions] = useState<{ value: string; label: string; hit: SearchHit }[]>([]);

  const onSearch = async (keyword: string) => {
    if (!currentProject || !keyword.trim()) {
      setOptions([]);
      return;
    }
    const hits = await searchApi.search(currentProject.id, keyword.trim());
    setSearchHits(hits);
    setOptions(
      hits.map((h, i) => ({
        value: `${h.sourceType}-${h.entityId ?? h.relationId}-${i}`,
        label: `[${SOURCE_LABEL[h.sourceType] ?? h.sourceType}·${h.field}] ${h.snippet}`,
        hit: h,
      })),
    );
  };

  const onSelect = (_: string, opt: { hit: SearchHit }) => {
    const { hit } = opt;
    if (hit.sourceType === 'ENTITY' && hit.entityId) {
      // 实体命中：选中该实体
      select(hit.entityId);
      setSelectedEdgeFromSearch(null);
      setPathHighlight([]);
    } else if ((hit.sourceType === 'RELATION' || hit.sourceType === 'PARENT_RELATION') && hit.fromEntityId && hit.toEntityId) {
      // 关系命中：高亮两端节点 + 通知树视图选中该连线
      select(null);
      setPathHighlight([hit.fromEntityId, hit.toEntityId]);
      setSelectedEdgeFromSearch({
        sourceType: hit.sourceType,
        fromEntityId: hit.fromEntityId,
        toEntityId: hit.toEntityId,
        relationId: hit.relationId ?? undefined,
      });
    }
    navigate('/tree');
  };

  return (
    <AutoComplete
      style={{ width: 260 }}
      options={options}
      onSearch={onSearch}
      onSelect={onSelect}
      disabled={!currentProject}
    >
      <Input.Search placeholder="全局搜索（名称/备注）" allowClear />
    </AutoComplete>
  );
}
