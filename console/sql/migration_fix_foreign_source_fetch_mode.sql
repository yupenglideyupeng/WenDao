-- ============================================================
-- 修复：将国外来源的 fetch_mode 改回 PRIMARY，使其能被定时任务抓取
-- 原因：之前的迁移 SQL 将所有 RSS/API 源（除36氪快讯、IT之家外）都标记为 SUPPLEMENTARY
--       导致 5 个国外来源（Hacker News/TechCrunch/Ars Technica/The Verge/Dev.to）不再被定时抓取
-- ============================================================

UPDATE news_source SET fetch_mode='PRIMARY', priority='medium', max_articles_per_fetch=10
  WHERE type = '1' AND status = '0';
