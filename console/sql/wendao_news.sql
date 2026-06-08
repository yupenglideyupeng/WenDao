-- =====================================================
-- AI 新闻资讯平台 - 核心业务表
-- 数据库: wendao
-- MySQL 版本: 8.0+
-- =====================================================

-- 创建数据库（如已存在则跳过）
CREATE DATABASE IF NOT EXISTS `wendao_news`
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_general_ci;

USE `wendao_news`;

-- =====================================================
-- 1. 新闻来源表
-- =====================================================
DROP TABLE IF EXISTS `news_source`;
CREATE TABLE `news_source`
(
    `id`              BIGINT(20)   NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `name`            VARCHAR(100) NOT NULL COMMENT '来源名称（如 Hacker News、知乎热榜）',
    `type`            CHAR(1)      NOT NULL DEFAULT '0' COMMENT '来源类型：0=国内 1=国外',
    `url`             VARCHAR(500) NOT NULL COMMENT '新闻源地址（API/RSS/网页）',
    `fetch_type`      VARCHAR(20)  NOT NULL DEFAULT 'RSS' COMMENT '抓取方式：RSS / API / CRAWL',
    `fetch_interval`  INT(11)      NOT NULL DEFAULT 30 COMMENT '抓取间隔（分钟）',
    `fetch_config`    JSON                  DEFAULT NULL COMMENT '额外配置（请求头、参数等）',
    `status`          CHAR(1)      NOT NULL DEFAULT '0' COMMENT '状态：0=启用 1=停用',
    `create_by`       VARCHAR(64)           DEFAULT '' COMMENT '创建者',
    `create_time`     DATETIME              DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`       VARCHAR(64)           DEFAULT '' COMMENT '更新者',
    `update_time`     DATETIME              DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `remark`          VARCHAR(500)          DEFAULT NULL COMMENT '备注',
    PRIMARY KEY (`id`),
    INDEX `idx_type_status` (`type`, `status`)
) ENGINE = InnoDB
  AUTO_INCREMENT = 100
  DEFAULT CHARSET = utf8mb4 COMMENT ='新闻来源表';

-- =====================================================
-- 2. 新闻文章表
-- =====================================================
DROP TABLE IF EXISTS `news_article`;
CREATE TABLE `news_article`
(
    `id`              BIGINT(20)   NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `source_id`       BIGINT(20)            DEFAULT NULL COMMENT '新闻来源ID（关键词搜索时为NULL）',
    `source_name`     VARCHAR(100)          DEFAULT NULL COMMENT '来源名称（冗余，方便查询）',
    `title`           VARCHAR(500) NOT NULL COMMENT '文章标题',
    `summary`         TEXT                  DEFAULT NULL COMMENT 'AI 生成的摘要',
    `content`         LONGTEXT              DEFAULT NULL COMMENT '原始内容/正文',
    `original_url`    VARCHAR(500)          DEFAULT NULL COMMENT '原始链接',
    `language`        CHAR(2)      NOT NULL DEFAULT 'zh' COMMENT '语言：zh=中文 en=英文 ja=日文 等',
    `tags`            JSON                  DEFAULT NULL COMMENT 'AI 提取的标签，JSON数组，如 ["AI","科技"]',
    `sentiment`       VARCHAR(20)           DEFAULT NULL COMMENT '情感分析：positive / negative / neutral',
    `keywords`        VARCHAR(500)          DEFAULT NULL COMMENT 'AI 提取的关键词，逗号分隔',
    `publish_time`    DATETIME              DEFAULT NULL COMMENT '新闻发布时间',
    `fetch_time`      DATETIME              DEFAULT CURRENT_TIMESTAMP COMMENT '抓取入库时间',
    `is_pushed`       TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '是否已推送：0=未推送 1=已推送',
    `push_time`       DATETIME              DEFAULT NULL COMMENT '推送时间',
    `read_count`      INT(11)      NOT NULL DEFAULT 0 COMMENT '阅读次数',
    `status`          CHAR(1)      NOT NULL DEFAULT '0' COMMENT '状态：0=正常 1=下架',
    `create_by`       VARCHAR(64)           DEFAULT '' COMMENT '创建者',
    `create_time`     DATETIME              DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`       VARCHAR(64)           DEFAULT '' COMMENT '更新者',
    `update_time`     DATETIME              DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `remark`          VARCHAR(500)          DEFAULT NULL COMMENT '备注',
    PRIMARY KEY (`id`),
    INDEX `idx_source_id` (`source_id`),
    INDEX `idx_language` (`language`),
    INDEX `idx_sentiment` (`sentiment`),
    INDEX `idx_publish_time` (`publish_time`),
    INDEX `idx_fetch_time` (`fetch_time`),
    INDEX `idx_is_pushed` (`is_pushed`),
    FULLTEXT INDEX `ft_title_summary` (`title`, `summary`) WITH PARSER ngram
) ENGINE = InnoDB
  AUTO_INCREMENT = 1000
  DEFAULT CHARSET = utf8mb4 COMMENT ='新闻文章表';

-- =====================================================
-- 3. 新闻推送记录表
-- =====================================================
DROP TABLE IF EXISTS `news_push_log`;
CREATE TABLE `news_push_log`
(
    `id`              BIGINT(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `article_id`      BIGINT(20) NOT NULL COMMENT '文章ID',
    `push_time`       DATETIME            DEFAULT CURRENT_TIMESTAMP COMMENT '推送时间',
    `push_status`     CHAR(1)   NOT NULL DEFAULT '0' COMMENT '推送状态：0=成功 1=失败',
    `push_type`       VARCHAR(20)         DEFAULT 'WEBSOCKET' COMMENT '推送方式：WEBSOCKET / EMAIL / WECHAT',
    `error_msg`       VARCHAR(2000)       DEFAULT NULL COMMENT '失败原因',
    `create_time`     DATETIME            DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    INDEX `idx_article_id` (`article_id`),
    INDEX `idx_push_time` (`push_time`)
) ENGINE = InnoDB
  AUTO_INCREMENT = 1000
  DEFAULT CHARSET = utf8mb4 COMMENT ='新闻推送记录表';

-- =====================================================
-- 初始化示例数据（国内 + 国外新闻源）
-- =====================================================
INSERT INTO `news_source` (`name`, `type`, `url`, `fetch_type`, `fetch_interval`, `fetch_config`, `status`)
VALUES
-- 国内新闻源
('知乎热榜',    '0', 'https://www.zhihu.com/api/v3/feed/topstory/hot-lists/total?limit=20', 'API',  15, '{"headers":{"User-Agent":"Mozilla/5.0"}}', '0'),
('微博热搜',    '0', 'https://weibo.com/ajax/side/hotSearch',                                   'API',  15, '{"headers":{"User-Agent":"Mozilla/5.0"}}', '0'),
('36氪快讯',    '0', 'https://36kr.com/newsflashes',                                            'CRAWL',20, '{}', '0'),
('IT之家',      '0', 'https://www.ithome.com/rss/',                                              'RSS',  20, '{}', '0'),
('机器之心',    '0', 'https://www.jiqizhixin.com/rss',                                          'RSS',  30, '{}', '0'),

-- 国外新闻源
('Hacker News', '1', 'https://hacker-news.firebaseio.com/v0/topstories.json',                    'API',  10, '{}', '0'),
('TechCrunch',  '1', 'https://techcrunch.com/feed/',                                             'RSS',  30, '{}', '0'),
('Ars Technica','1', 'https://feeds.arstechnica.com/arstechnica/index',                          'RSS',  30, '{}', '0'),
('The Verge',   '1', 'https://www.theverge.com/rss/index.xml',                                   'RSS',  30, '{}', '0'),
('Dev.to',      '1', 'https://dev.to/feed',                                                      'RSS',  30, '{}', '0');
