-- =====================================================
-- AI新闻模块 - 关键词监控升级SQL
-- =====================================================

-- 1. 新闻关键词表
CREATE TABLE IF NOT EXISTS `news_keyword` (
  `id`              BIGINT(20)   NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `text`            VARCHAR(200) NOT NULL COMMENT '关键词',
  `category`        VARCHAR(100) DEFAULT NULL COMMENT '分类',
  `is_active`       TINYINT(1)   NOT NULL DEFAULT 1 COMMENT '是否启用：1=启用 0=停用',
  `fetch_interval`  INT(11)      NOT NULL DEFAULT 30 COMMENT '抓取间隔(分钟)',
  `last_fetch_time` DATETIME     DEFAULT NULL COMMENT '上次抓取时间',
  `create_by`       VARCHAR(64)  DEFAULT '' COMMENT '创建者',
  `create_time`     DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by`       VARCHAR(64)  DEFAULT '' COMMENT '更新者',
  `update_time`     DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `remark`          VARCHAR(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`id`),
  INDEX `idx_is_active` (`is_active`)
) ENGINE=InnoDB AUTO_INCREMENT=100 DEFAULT CHARSET=utf8mb4 COMMENT='新闻关键词表';

-- 2. news_article 新增字段
ALTER TABLE `news_article`
  ADD COLUMN `keyword_id` BIGINT(20) DEFAULT NULL COMMENT '关联关键词ID(NULL=来源抓取)',
  ADD COLUMN `is_real` TINYINT(1) DEFAULT 1 COMMENT 'AI判断是否真实：1=真实 0=虚假',
  ADD COLUMN `relevance` INT(11) DEFAULT 0 COMMENT '相关性评分0-100',
  ADD COLUMN `relevance_reason` VARCHAR(500) DEFAULT NULL COMMENT 'AI相关性理由',
  ADD COLUMN `keyword_mentioned` TINYINT(1) DEFAULT 0 COMMENT '是否直接提及关键词',
  ADD COLUMN `importance` VARCHAR(20) DEFAULT 'low' COMMENT '重要性：low/medium/high/urgent';

ALTER TABLE `news_article`
  ADD INDEX `idx_keyword_id` (`keyword_id`),
  ADD INDEX `idx_importance` (`importance`);

-- 3. 新增搜索源
INSERT IGNORE INTO `news_source` (`name`, `type`, `url`, `fetch_type`, `fetch_interval`, `fetch_config`, `status`, `create_by`, `create_time`)
VALUES
('Bing搜索', '0', 'https://www.bing.com/search?q=', 'SEARCH', 30, '{"headers":{"User-Agent":"Mozilla/5.0"}}', '0', 'admin', NOW()),
('搜狗搜索', '0', 'https://www.sogou.com/web?query=', 'SEARCH', 30, '{"headers":{"User-Agent":"Mozilla/5.0"}}', '0', 'admin', NOW()),
('B站搜索', '0', 'https://api.bilibili.com/x/web-interface/search/type?search_type=video&keyword=', 'SEARCH', 30, '{"headers":{"User-Agent":"Mozilla/5.0","Referer":"https://search.bilibili.com/"}}', '0', 'admin', NOW()),
('微博热搜', '0', 'https://weibo.com/ajax/side/hotSearch', 'SEARCH', 15, '{"headers":{"User-Agent":"Mozilla/5.0","Referer":"https://weibo.com/"}}', '0', 'admin', NOW());
