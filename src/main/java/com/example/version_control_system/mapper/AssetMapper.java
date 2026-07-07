package com.example.version_control_system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.version_control_system.entity.Asset;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AssetMapper extends BaseMapper<Asset> {
}
