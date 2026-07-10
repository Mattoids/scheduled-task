-- AI 知识文档内容改为文件存储，数据库只保留文件路径
ALTER TABLE ai_knowledge_doc DROP COLUMN content;
ALTER TABLE ai_knowledge_doc ADD COLUMN file_path VARCHAR(512) NULL COMMENT '文档文件路径';
