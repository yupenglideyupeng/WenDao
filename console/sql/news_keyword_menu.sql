-- =====================================================
-- 关键词监控 - 菜单和权限初始化SQL
-- =====================================================

-- 二级菜单：关键词监控 (menu_id=2012, parent_id=2000)
INSERT INTO `sys_menu` VALUES ('2012', '关键词监控', '2000', '4', 'monitor', 'news/keyword/index', '', 'NewsKeyword', 1, 0, 'C', '0', '0', 'news:keyword:list', 'monitor', 'admin', sysdate(), '', NULL, '关键词监控菜单');

-- 按钮权限 (parent_id=2012)
INSERT INTO `sys_menu` VALUES ('2013', '关键词查询', '2012', '1', '', '', '', '', 1, 0, 'F', '0', '0', 'news:keyword:query', '#', 'admin', sysdate(), '', NULL, '');
INSERT INTO `sys_menu` VALUES ('2014', '关键词新增', '2012', '2', '', '', '', '', 1, 0, 'F', '0', '0', 'news:keyword:add',   '#', 'admin', sysdate(), '', NULL, '');
INSERT INTO `sys_menu` VALUES ('2015', '关键词修改', '2012', '3', '', '', '', '', 1, 0, 'F', '0', '0', 'news:keyword:edit',  '#', 'admin', sysdate(), '', NULL, '');
INSERT INTO `sys_menu` VALUES ('2016', '关键词删除', '2012', '4', '', '', '', '', 1, 0, 'F', '0', '0', 'news:keyword:remove','#', 'admin', sysdate(), '', NULL, '');

-- 分配权限给普通角色
INSERT INTO `sys_role_menu` VALUES ('2', '2012');
INSERT INTO `sys_role_menu` VALUES ('2', '2013');
INSERT INTO `sys_role_menu` VALUES ('2', '2014');
INSERT INTO `sys_role_menu` VALUES ('2', '2015');
INSERT INTO `sys_role_menu` VALUES ('2', '2016');
