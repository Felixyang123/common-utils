package com.lezai.threadpool.dao.rep;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lezai.threadpool.dao.entity.ApiKeyEntity;
import com.lezai.threadpool.dao.mapper.ApiKeyMapper;
import org.springframework.stereotype.Repository;

@Repository
public class ApiKeyRep extends ServiceImpl<ApiKeyMapper, ApiKeyEntity> {
}
