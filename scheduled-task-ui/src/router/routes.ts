import type { RouteRecordRaw } from 'vue-router'

export const menuRoutes: RouteRecordRaw[] = [
  {
    path: '/dashboard',
    name: 'Dashboard',
    component: () => import('@/views/dashboard/DashboardView.vue'),
    meta: { title: '首页', icon: 'HomeFilled', permission: null },
  },
  {
    path: '/task-sql',
    name: 'TaskSql',
    component: () => import('@/views/task-sql/TaskSqlList.vue'),
    meta: { title: 'SQL 管理', icon: 'DocumentCopy', permission: 'task:view' },
  },
  {
    path: '/task-crawl',
    name: 'TaskCrawl',
    component: () => import('@/views/task-crawl/TaskCrawlList.vue'),
    meta: { title: '网页爬取', icon: 'Globe', permission: 'taskCrawl:view' },
  },
  {
    path: '/task',
    name: 'Task',
    component: () => import('@/views/task/TaskList.vue'),
    meta: { title: '任务管理', icon: 'Timer', permission: 'task:view' },
  },
  {
    path: '/task-log',
    name: 'TaskLog',
    component: () => import('@/views/task-log/TaskLogList.vue'),
    meta: { title: '任务日志', icon: 'Document', permission: 'log:view' },
  },
  {
    path: '/notification',
    name: 'Notification',
    redirect: '/notification/rule',
    meta: { title: '通知管理', icon: 'Bell', permission: null },
    children: [
      {
        path: '/notification/rule',
        name: 'NotificationRule',
        component: () => import('@/views/notification-rule/NotificationRuleList.vue'),
        meta: { title: '通知规则', permission: 'notificationRule:view' },
      },
      {
        path: '/notification/config',
        name: 'NotificationConfig',
        component: () => import('@/views/notification-config/NotificationConfigList.vue'),
        meta: { title: '通知配置', permission: 'notificationConfig:view' },
      },
    ],
  },
  {
    path: '/datasource',
    name: 'Datasource',
    component: () => import('@/views/datasource/DatasourceList.vue'),
    meta: { title: '数据源管理', icon: 'Coin', permission: 'datasource:view' },
  },
  {
    path: '/email-recipient',
    name: 'EmailRecipient',
    component: () => import('@/views/email-recipient/RecipientList.vue'),
    meta: { title: '收件人管理', icon: 'UserFilled', permission: 'email:view' },
  },
  {
    path: '/template',
    name: 'Template',
    component: () => import('@/views/template/TemplateList.vue'),
    meta: { title: '报表模板', icon: 'Files', permission: 'template:view' },
  },
  {
    path: '/ai-config',
    name: 'AiConfig',
    component: () => import('@/views/ai-config/AiConfigList.vue'),
    meta: { title: 'AI 配置', icon: 'Cpu', permission: 'system:user' },
  },
  {
    path: '/storage-config',
    name: 'StorageConfig',
    component: () => import('@/views/storage-config/StorageConfigList.vue'),
    meta: { title: '存储配置', icon: 'FolderOpened', permission: 'storageConfig:view' },
  },
  {
    path: '/system',
    name: 'System',
    redirect: '/system/user',
    meta: { title: '系统管理', icon: 'Setting', permission: null },
    children: [
      {
        path: '/system/user',
        name: 'SystemUser',
        component: () => import('@/views/system/UserList.vue'),
        meta: { title: '用户管理', permission: 'system:user' },
      },
      {
        path: '/system/role',
        name: 'SystemRole',
        component: () => import('@/views/system/RoleList.vue'),
        meta: { title: '角色管理', permission: 'system:role' },
      },
    ],
  },
]

const routes: RouteRecordRaw[] = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/login/LoginView.vue'),
  },
  {
    path: '/',
    component: () => import('@/layouts/AdminLayout.vue'),
    redirect: '/dashboard',
    children: menuRoutes,
  },
  {
    path: '/:pathMatch(.*)*',
    redirect: '/',
  },
]

export default routes
