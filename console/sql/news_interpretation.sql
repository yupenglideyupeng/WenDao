-- ============================================================
-- 新闻解读记录表
-- 创建时间：2026-06-05
-- 说明：存储新闻一键解读的结果，支持多次解读历史追踪
-- ============================================================

CREATE TABLE IF NOT EXISTS `news_interpretation` (
  `id`               bigint(20)   NOT NULL AUTO_INCREMENT           COMMENT '主键ID',
  `article_id`       bigint(20)   NOT NULL                          COMMENT '关联新闻文章ID（news_article.id）',
  `prompt_config_id` bigint(20)   DEFAULT NULL                      COMMENT '使用的提示词配置ID（news_prompt_config.id）',
  `prompt_snapshot`  text         DEFAULT NULL                      COMMENT '解读时提示词快照（防止提示词修改后无法追溯）',
  `content`          longtext     DEFAULT NULL                      COMMENT '解读结果内容（Markdown格式，含mermaid流程图）',
  `status`           char(1)      NOT NULL DEFAULT '0'              COMMENT '解读状态：0=进行中 1=完成 2=失败',
  `error_msg`        varchar(500) DEFAULT NULL                      COMMENT '失败原因描述',
  `tokens_used`      int(11)      DEFAULT NULL                      COMMENT '本次解读消耗的token数',
  `model_name`       varchar(100) DEFAULT NULL                      COMMENT '使用的AI模型名称（如deepseek-chat）',
  `interpret_count`  int(11)      NOT NULL DEFAULT 1                COMMENT '第几次解读（从1开始递增，用于版本追踪）',
  `create_by`        varchar(64)  NOT NULL DEFAULT ''               COMMENT '创建人（操作用户名）',
  `create_time`      datetime     DEFAULT NULL                      COMMENT '创建时间（发起解读的时间）',
  `update_time`      datetime     DEFAULT NULL                      COMMENT '更新时间（完成/失败的时间）',
  PRIMARY KEY (`id`),
  KEY `idx_article_id` (`article_id`),
  KEY `idx_status`     (`status`),
  KEY `idx_create_time`(`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='新闻解读记录表';

-- ============================================================
-- 说明：
--   1. 同一 article_id 可以有多条记录（每次"重新解读"新增一条）
--   2. 查询最新解读：SELECT * FROM news_interpretation
--        WHERE article_id = ? ORDER BY id DESC LIMIT 1
--   3. status=0 表示SSE流式推送进行中，不允许同时发起第二次
--   4. prompt_snapshot 在发起解读时保存当时的系统提示词，
--      避免提示词被修改后历史记录显示的提示词与实际不符
--   5. interpret_count = 上一条记录的 interpret_count + 1
-- ============================================================
