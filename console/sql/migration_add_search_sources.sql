-- 将4个搜索引擎变为 news_source 记录，统一由新闻源管理驱动
-- 关键词退化为纯过滤配置（不再驱动搜索）

INSERT IGNORE INTO news_source (name, type, url, fetch_type, fetch_interval, fetch_config, status, priority, max_articles_per_fetch, fetch_mode, create_by, create_time, remark)
VALUES
('Bing搜索', '0', 'https://www.bing.com/search', 'SEARCH', 30, NULL, '0', 'medium', 20, 'PRIMARY', 'admin', sysdate(), '关键词驱动的Bing搜索引擎'),
('搜狗搜索', '0', 'https://www.sogou.com/web', 'SEARCH', 30, NULL, '0', 'medium', 20, 'PRIMARY', 'admin', sysdate(), '关键词驱动的搜狗搜索引擎'),
('B站搜索', '0', 'https://api.bilibili.com/x/web-interface/search', 'SEARCH', 30, NULL, '0', 'medium', 20, 'PRIMARY', 'admin', sysdate(), '关键词驱动的B站搜索引擎'),
('微博热搜', '0', 'https://weibo.com/ajax/side/hotSearch', 'SEARCH', 30, NULL, '0', 'medium', 20, 'PRIMARY', 'admin', sysdate(), '关键词驱动的微博热搜匹配');
