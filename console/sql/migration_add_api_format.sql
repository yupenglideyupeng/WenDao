-- ============================================================
-- 迁移脚本：新增 Anthropic Messages API 格式支持
-- 日期：2026-06-11
-- 说明：在 news_model_config 表增加 api_format 列，支持 OPENAI / ANTHROPIC 两种格式
-- ============================================================

-- 1. news_model_config 表新增 api_format 列
ALTER TABLE `news_model_config`
    ADD COLUMN `api_format` VARCHAR(20) NOT NULL DEFAULT 'OPENAI'
    COMMENT 'API格式：OPENAI=OpenAI Chat Completions / ANTHROPIC=Anthropic Messages'
    AFTER `usage_type`;

-- 2. 新增字典类型：API格式 (dict_type_id=12)
INSERT INTO `sys_dict_type` VALUES (12, 'API格式', 'news_api_format', '0', 'admin', sysdate(), '', NULL, 'AI模型API格式列表');

-- 3. 新增字典数据
INSERT INTO `sys_dict_data` VALUES (36, 1, 'OpenAI格式', 'OPENAI', 'news_api_format', '', 'primary', 'Y', '0', 'admin', sysdate(), '', NULL, 'OpenAI Chat Completions 格式');
INSERT INTO `sys_dict_data` VALUES (37, 2, 'Anthropic格式', 'ANTHROPIC', 'news_api_format', '', 'success', 'N', '0', 'admin', sysdate(), '', NULL, 'Anthropic Messages API 格式');
