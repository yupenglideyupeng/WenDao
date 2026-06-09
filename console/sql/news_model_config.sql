-- =====================================================
-- AI模型配置表 + 菜单权限
-- =====================================================

DROP TABLE IF EXISTS `news_model_config`;
CREATE TABLE `news_model_config` (
    `id`                 BIGINT(20)   NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `name`               VARCHAR(100)  NOT NULL COMMENT '配置名称（如"DeepSeek主模型"）',
    `provider`           VARCHAR(50)   NOT NULL DEFAULT 'DEEPSEEK' COMMENT '提供商：DEEPSEEK/SILICONFLOW/BAILIAN/ZHIPU/VOLCENGINE/CUSTOM',
    `api_url`            VARCHAR(500)  NOT NULL COMMENT 'API地址',
    `api_key`            VARCHAR(500)  NOT NULL COMMENT 'API密钥（AES加密存储）',
    `model_name`         VARCHAR(100)  NOT NULL COMMENT '模型名称（如 deepseek-chat）',
    `priority`           INT(11)       NOT NULL DEFAULT 1 COMMENT '优先级，数字越小越优先，0=禁用',
    `max_tokens`         INT(11)       DEFAULT 2000 COMMENT '默认最大输出token数',
    `temperature`        DECIMAL(3,2)  DEFAULT 0.30 COMMENT '默认温度参数',
    `support_json_mode`  TINYINT(1)    NOT NULL DEFAULT 1 COMMENT '是否支持JSON结构化输出',
    `support_stream`     TINYINT(1)    NOT NULL DEFAULT 1 COMMENT '是否支持流式输出(SSE)',
    `usage_type`         VARCHAR(100)  NOT NULL DEFAULT 'ALL' COMMENT '适用场景：INTERPRET/ANALYSIS/EXPANSION/ALL（逗号分隔）',
    `timeout_ms`         INT(11)       DEFAULT 30000 COMMENT '请求超时（毫秒）',
    `retry_count`        INT(11)       DEFAULT 1 COMMENT '失败重试次数',
    `is_active`          TINYINT(1)    NOT NULL DEFAULT 1 COMMENT '0=停用 1=启用',
    `remark`             VARCHAR(500)  DEFAULT NULL COMMENT '备注',
    `create_by`          VARCHAR(64)   DEFAULT '' COMMENT '创建者',
    `create_time`        DATETIME      DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`          VARCHAR(64)   DEFAULT '' COMMENT '更新者',
    `update_time`        DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    INDEX `idx_priority` (`priority`),
    INDEX `idx_active_usage` (`is_active`, `usage_type`)
) ENGINE=InnoDB AUTO_INCREMENT=100 DEFAULT CHARSET=utf8mb4 COMMENT='AI模型配置表';

-- =====================================================
-- 菜单：模型管理 (menu_id=2017, parent_id=2000 AI新闻)
-- =====================================================
INSERT INTO `sys_menu` VALUES ('2027', '模型管理', '2000', '5', 'modelConfig', 'news/modelConfig/index', '', 'NewsModelConfig', 1, 0, 'C', '0', '0', 'news:model:list', 'setting', 'admin', sysdate(), '', NULL, 'AI模型配置管理');

-- 按钮权限 (parent_id=2027)
INSERT INTO `sys_menu` VALUES ('2028', '模型查询', '2027', '1', '', '', '', '', 1, 0, 'F', '0', '0', 'news:model:query', '#', 'admin', sysdate(), '', NULL, '');
INSERT INTO `sys_menu` VALUES ('2029', '模型新增', '2027', '2', '', '', '', '', 1, 0, 'F', '0', '0', 'news:model:add',   '#', 'admin', sysdate(), '', NULL, '');
INSERT INTO `sys_menu` VALUES ('2030', '模型修改', '2027', '3', '', '', '', '', 1, 0, 'F', '0', '0', 'news:model:edit',  '#', 'admin', sysdate(), '', NULL, '');
INSERT INTO `sys_menu` VALUES ('2031', '模型删除', '2027', '4', '', '', '', '', 1, 0, 'F', '0', '0', 'news:model:remove','#', 'admin', sysdate(), '', NULL, '');

-- 分配权限给普通角色 (role_id=2)
INSERT INTO `sys_role_menu` VALUES ('2', '2027');
INSERT INTO `sys_role_menu` VALUES ('2', '2028');
INSERT INTO `sys_role_menu` VALUES ('2', '2029');
INSERT INTO `sys_role_menu` VALUES ('2', '2030');
INSERT INTO `sys_role_menu` VALUES ('2', '2031');

-- =====================================================
-- 字典：模型提供商 (dict_type_id=11)
-- =====================================================
INSERT INTO `sys_dict_type` VALUES (11, '模型提供商', 'news_model_provider', '0', 'admin', sysdate(), '', NULL, 'AI模型提供商列表');

INSERT INTO `sys_dict_data` VALUES (30, 1, 'DeepSeek',    'DEEPSEEK',    'news_model_provider', '', 'success', 'Y', '0', 'admin', sysdate(), '', NULL, 'DeepSeek大模型');
INSERT INTO `sys_dict_data` VALUES (31, 2, '硅基流动',    'SILICONFLOW', 'news_model_provider', '', 'warning', 'N', '0', 'admin', sysdate(), '', NULL, '硅基流动 SiliconFlow');
INSERT INTO `sys_dict_data` VALUES (32, 3, '阿里百炼',    'BAILIAN',     'news_model_provider', '', '',        'N', '0', 'admin', sysdate(), '', NULL, '阿里百炼大模型平台');
INSERT INTO `sys_dict_data` VALUES (33, 4, '智谱GLM',     'ZHIPU',       'news_model_provider', '', 'danger',  'N', '0', 'admin', sysdate(), '', NULL, '智谱AI GLM系列');
INSERT INTO `sys_dict_data` VALUES (34, 5, '火山引擎',    'VOLCENGINE',  'news_model_provider', '', 'info',    'N', '0', 'admin', sysdate(), '', NULL, '字节跳动火山引擎');
INSERT INTO `sys_dict_data` VALUES (35, 6, '自定义',      'CUSTOM',      'news_model_provider', '', '',        'N', '0', 'admin', sysdate(), '', NULL, '自定义API地址');
