import type { RouteRecordRaw } from 'vue-router'

export const menuRoutes: RouteRecordRaw[] = [
  {
    path: '/dashboard',
    name: 'Dashboard',
    component: () => import('@/views/dashboard/DashboardView.vue'),
    meta: { title: '首页', icon: 'HomeFilled', permission: null },
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
    path: '/datasource',
    name: 'Datasource',
    component: () => import('@/views/datasource/DatasourceList.vue'),
    meta: { title: '数据源管理', icon: 'Coin', permission: 'datasource:view' },
  },
  {
    path: '/email-config',
    name: 'EmailConfig',
    component: () => import('@/views/email-config/EmailConfigList.vue'),
    meta: { title: '邮箱配置', icon: 'Message', permission: 'email:view' },
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
