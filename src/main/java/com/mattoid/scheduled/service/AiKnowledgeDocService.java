package com.mattoid.scheduled.service;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.mattoid.scheduled.entity.AiKnowledgeDoc;
import com.mattoid.scheduled.mapper.AiKnowledgeDocMapper;
import org.springframework.stereotype.Service;

@Service
public class AiKnowledgeDocService extends ServiceImpl<AiKnowledgeDocMapper, AiKnowledgeDoc> {

    public AiKnowledgeDoc getLatestByDatasource(Long datasourceId, String docType) {
        return lambdaQuery()
                .eq(AiKnowledgeDoc::getDatasourceId, datasourceId)
                .eq(AiKnowledgeDoc::getDocType, docType)
                .orderByDesc(AiKnowledgeDoc::getCreateTime)
                .last("LIMIT 1")
                .one();
    }
}
