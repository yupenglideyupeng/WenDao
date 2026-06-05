-- =============================================
-- WenDao 关键词驱动改造 - 数据库变更脚本
-- =============================================

-- 1.1 news_source 新增字段：优先级、配额、抓取模式
ALTER TABLE news_source
  ADD COLUMN priority VARCHAR(20) DEFAULT 'medium' COMMENT '优先级: high/medium/low',
  ADD COLUMN max_articles_per_fetch INT DEFAULT 10 COMMENT '每次抓取最大文章数',
  ADD COLUMN fetch_mode VARCHAR(20) DEFAULT 'PRIMARY' COMMENT 'PRIMARY=主力/SUPPLEMENTARY=辅助';

-- 1.2 news_keyword 新增字段：相关性阈值、扩展查询词
ALTER TABLE news_keyword
  ADD COLUMN relevance_threshold INT DEFAULT 40 COMMENT '相关性阈值,低于此值自动下架',
  ADD COLUMN expand_queries JSON DEFAULT NULL COMMENT '扩展查询词列表';

-- 1.3 news_article 新增字段：来源方式标记
ALTER TABLE news_article
  ADD COLUMN fetch_origin VARCHAR(20) DEFAULT 'SOURCE' COMMENT '来源方式: KEYWORD/SOURCE';

-- 1.4 回填历史数据
UPDATE news_article SET fetch_origin = 'KEYWORD' WHERE keyword_id IS NOT NULL;

-- 1.5 初始来源配置：高质量源标记为 PRIMARY + high 优先级
UPDATE news_source SET priority='high', fetch_mode='PRIMARY', max_articles_per_fetch=15
  WHERE name IN ('36氪快讯','IT之家');

-- 其他来源降级为 SUPPLEMENTARY
UPDATE news_source SET priority='medium', fetch_mode='SUPPLEMENTARY', max_articles_per_fetch=10
  WHERE fetch_type IN ('RSS','API') AND name NOT IN ('36氪快讯','IT之家');

-- 1.6 新增查询扩展表
CREATE TABLE IF NOT EXISTS news_query_expansion (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  keyword_id BIGINT COMMENT '关联 news_keyword.id',
  expanded_term VARCHAR(200) NOT NULL COMMENT '扩展词',
  expansion_type VARCHAR(20) DEFAULT 'RELATED' COMMENT 'SYNONYM/RELATED/AI_GENERATED',
  is_active TINYINT(1) DEFAULT 1,
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_keyword_id (keyword_id),
  INDEX idx_is_active (is_active)
) COMMENT='查询扩展词表';
