package com.labmind.business.chat.knowledge.indexing.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.labmind.business.chat.knowledge.indexing.data.KnowledgeDocumentChunkData;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface KnowledgeDocumentChunkMapper extends BaseMapper<KnowledgeDocumentChunkData> {
}
