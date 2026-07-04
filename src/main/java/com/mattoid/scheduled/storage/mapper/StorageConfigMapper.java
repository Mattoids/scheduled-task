package com.mattoid.scheduled.storage.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mattoid.scheduled.entity.StorageConfig;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface StorageConfigMapper extends BaseMapper<StorageConfig> {
}
