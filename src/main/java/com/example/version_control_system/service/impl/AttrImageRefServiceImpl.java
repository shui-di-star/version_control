package com.example.version_control_system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.version_control_system.entity.AttrImageRef;
import com.example.version_control_system.mapper.AttrImageRefMapper;
import com.example.version_control_system.service.AttrImageRefService;
import com.example.version_control_system.service.StorageService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class AttrImageRefServiceImpl implements AttrImageRefService {

    private final AttrImageRefMapper refMapper;
    private final StorageService storageService;

    public AttrImageRefServiceImpl(AttrImageRefMapper refMapper, StorageService storageService) {
        this.refMapper = refMapper;
        this.storageService = storageService;
    }

    @Override
    @Transactional
    public void addRef(Long projectId, String objectKey) {
        AttrImageRef existing = refMapper.selectOne(new LambdaQueryWrapper<AttrImageRef>()
                .eq(AttrImageRef::getProjectId, projectId)
                .eq(AttrImageRef::getObjectKey, objectKey));
        if (existing != null) {
            existing.setRefCount(existing.getRefCount() + 1);
            refMapper.updateById(existing);
        } else {
            AttrImageRef ref = new AttrImageRef();
            ref.setProjectId(projectId);
            ref.setObjectKey(objectKey);
            ref.setRefCount(1);
            refMapper.insert(ref);
        }
    }

    @Override
    @Transactional
    public void addRefs(Long projectId, List<String> objectKeys) {
        for (String key : objectKeys) {
            addRef(projectId, key);
        }
    }

    @Override
    @Transactional
    public void releaseRef(String objectKey) {
        AttrImageRef existing = refMapper.selectOne(new LambdaQueryWrapper<AttrImageRef>()
                .eq(AttrImageRef::getObjectKey, objectKey));
        if (existing == null) {
            // No ref record — delete directly (legacy data uploaded before ref counting)
            try { storageService.delete(objectKey); } catch (Exception ignored) {}
            return;
        }
        existing.setRefCount(existing.getRefCount() - 1);
        if (existing.getRefCount() <= 0) {
            refMapper.deleteById(existing.getId());
            try { storageService.delete(objectKey); } catch (Exception ignored) {}
        } else {
            refMapper.updateById(existing);
        }
    }

    @Override
    @Transactional
    public void releaseRefs(List<String> objectKeys) {
        for (String key : objectKeys) {
            releaseRef(key);
        }
    }
}
