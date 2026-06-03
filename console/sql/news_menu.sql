-- =====================================================
-- AI新闻模块 - 菜单和权限初始化SQL
-- 数据库: wendao (对应配置中的 jdbc:mysql://localhost:3306/wendao)
-- =====================================================

-- 一级目录：AI新闻 (menu_id=2000, parent_id=0)
INSERT INTO `sys_menu` VALUES ('2000', 'AI新闻', '0', '5', 'news', NULL, '', 'News', 1, 0, 'M', '0', '0', '', 'monitor', 'admin', sysdate(), '', NULL, 'AI新闻模块目录');

-- 二级菜单：新闻大屏 (menu_id=2001, parent_id=2000)
INSERT INTO `sys_menu` VALUES ('2001', '新闻大屏', '2000', '1', 'dashboard', 'news/dashboard/index', '', 'NewsDashboard', 1, 0, 'C', '0', '0', '', 'dashboard', 'admin', sysdate(), '', NULL, '新闻大屏菜单');

-- 二级菜单：新闻源管理 (menu_id=2002, parent_id=2000)
INSERT INTO `sys_menu` VALUES ('2002', '新闻源管理', '2000', '2', 'source', 'news/source/index', '', 'NewsSource', 1, 0, 'C', '0', '0', 'news:source:list', 'tree', 'admin', sysdate(), '', NULL, '新闻源管理菜单');

-- 二级菜单：文章管理 (menu_id=2003, parent_id=2000)
INSERT INTO `sys_menu` VALUES ('2003', '文章管理', '2000', '3', 'article', 'news/article/index', '', 'NewsArticle', 1, 0, 'C', '0', '0', 'news:article:list', 'list', 'admin', sysdate(), '', NULL, '文章管理菜单');

-- =====================================================
-- 新闻源管理 - 按钮权限 (parent_id=2002)
-- =====================================================
INSERT INTO `sys_menu` VALUES ('2004', '新闻源查询', '2002', '1', '', '', '', '', 1, 0, 'F', '0', '0', 'news:source:query', '#', 'admin', sysdate(), '', NULL, '');
INSERT INTO `sys_menu` VALUES ('2005', '新闻源新增', '2002', '2', '', '', '', '', 1, 0, 'F', '0', '0', 'news:source:add',   '#', 'admin', sysdate(), '', NULL, '');
INSERT INTO `sys_menu` VALUES ('2006', '新闻源修改', '2002', '3', '', '', '', '', 1, 0, 'F', '0', '0', 'news:source:edit',  '#', 'admin', sysdate(), '', NULL, '');
INSERT INTO `sys_menu` VALUES ('2007', '新闻源删除', '2002', '4', '', '', '', '', 1, 0, 'F', '0', '0', 'news:source:remove','#', 'admin', sysdate(), '', NULL, '');

-- =====================================================
-- 文章管理 - 按钮权限 (parent_id=2003)
-- =====================================================
INSERT INTO `sys_menu` VALUES ('2008', '文章查询', '2003', '1', '', '', '', '', 1, 0, 'F', '0', '0', 'news:article:query', '#', 'admin', sysdate(), '', NULL, '');
INSERT INTO `sys_menu` VALUES ('2009', '文章修改', '2003', '2', '', '', '', '', 1, 0, 'F', '0', '0', 'news:article:edit',  '#', 'admin', sysdate(), '', NULL, '');
INSERT INTO `sys_menu` VALUES ('2010', '文章删除', '2003', '3', '', '', '', '', 1, 0, 'F', '0', '0', 'news:article:remove','#', 'admin', sysdate(), '', NULL, '');
INSERT INTO `sys_menu` VALUES ('2011', '文章推送', '2003', '4', '', '', '', '', 1, 0, 'F', '0', '0', 'news:article:push',  '#', 'admin', sysdate(), '', NULL, '');

-- =====================================================
-- 分配菜单权限给普通角色 (role_id=2)
-- 超级管理员 (role_id=1) 拥有 *:*:* 通配符权限，无需显式分配
-- =====================================================
INSERT INTO `sys_role_menu` VALUES ('2', '2000');
INSERT INTO `sys_role_menu` VALUES ('2', '2001');
INSERT INTO `sys_role_menu` VALUES ('2', '2002');
INSERT INTO `sys_role_menu` VALUES ('2', '2003');
INSERT INTO `sys_role_menu` VALUES ('2', '2004');
INSERT INTO `sys_role_menu` VALUES ('2', '2005');
INSERT INTO `sys_role_menu` VALUES ('2', '2006');
INSERT INTO `sys_role_menu` VALUES ('2', '2007');
INSERT INTO `sys_role_menu` VALUES ('2', '2008');
INSERT INTO `sys_role_menu` VALUES ('2', '2009');
INSERT INTO `sys_role_menu` VALUES ('2', '2010');
INSERT INTO `sys_role_menu` VALUES ('2', '2011');
