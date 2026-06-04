-- =====================================================
-- 提示词配置改造：多对多关联 + 去model字段
-- =====================================================

-- 1. 创建中间表（提示词-新闻类型多对多）
CREATE TABLE IF NOT EXISTS `news_prompt_type_relation` (
  `prompt_config_id` BIGINT(20) NOT NULL COMMENT '提示词配置ID',
  `type_config_id`   BIGINT(20) NOT NULL COMMENT '新闻类型ID',
  PRIMARY KEY (`prompt_config_id`, `type_config_id`),
  INDEX `idx_type_config_id` (`type_config_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='提示词-新闻类型关联表';

-- 2. 迁移现有数据到中间表（如果有type_config_id不为空的记录）
INSERT INTO `news_prompt_type_relation` (prompt_config_id, type_config_id)
SELECT id, type_config_id FROM `news_prompt_config` WHERE type_config_id IS NOT NULL;

-- 3. 删除 news_prompt_config 的 type_config_id 和 model 列
ALTER TABLE `news_prompt_config`
  DROP COLUMN `type_config_id`,
  DROP COLUMN `model`;
