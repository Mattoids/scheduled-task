<script setup lang="ts">
import { ref, onMounted } from 'vue'
import {
  Timer,
  Coin,
  Message,
  DocumentCopy,
  TrendCharts,
  CircleCheck,
  CircleClose,
} from '@element-plus/icons-vue'
import { useAppStore } from '@/stores/app'
import { getDashboardStats } from '@/api/dashboard'
import type { DashboardStats } from '@/types'

const appStore = useAppStore()
appStore.setBreadcrumb([{ title: '首页' }])

const stats = ref<DashboardStats>({
  taskCount: 0,
  datasourceCount: 0,
  emailConfigCount: 0,
  templateCount: 0,
  todayLogCount: 0,
  successLogCount: 0,
  failedLogCount: 0,
})

const loading = ref(false)

const statCards = [
  {
    title: '任务总数',
    key: 'taskCount' as keyof DashboardStats,
    icon: Timer,
    color: 'blue',
  },
  {
    title: '数据源',
    key: 'datasourceCount' as keyof DashboardStats,
    icon: Coin,
    color: 'green',
  },
  {
    title: '邮箱配置',
    key: 'emailConfigCount' as keyof DashboardStats,
    icon: Message,
    color: 'orange',
  },
  {
    title: '报表模板',
    key: 'templateCount' as keyof DashboardStats,
    icon: DocumentCopy,
    color: 'red',
  },
]

const logCards = [
  {
    title: '今日执行',
    key: 'todayLogCount' as keyof DashboardStats,
    icon: TrendCharts,
    color: 'purple',
  },
  {
    title: '累计成功',
    key: 'successLogCount' as keyof DashboardStats,
    icon: CircleCheck,
    color: 'success',
  },
  {
    title: '累计失败',
    key: 'failedLogCount' as keyof DashboardStats,
    icon: CircleClose,
    color: 'danger',
  },
]

const colorMap: Record<string, string> = {
  blue: '#3b82f6',
  green: '#10b981',
  orange: '#f59e0b',
  red: '#ef4444',
  purple: '#8b5cf6',
  success: '#10b981',
  danger: '#ef4444',
}

const loadStats = async () => {
  loading.value = true
  try {
    stats.value = await getDashboardStats()
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  loadStats()
})
</script>

<template>
  <div v-loading="loading" class="page-card dashboard-page">
    <div class="dashboard-welcome">
      <h1 class="welcome-title">欢迎使用定时任务报表系统</h1>
      <p class="welcome-desc">自动化报表生成、定时调度与邮件分发一站式平台</p>
    </div>

    <div class="section-title">资源概览</div>
    <el-row :gutter="20">
      <el-col v-for="item in statCards" :key="item.title" :span="6" :xs="24" :sm="12" :md="6">
        <el-card class="stat-card" shadow="never">
          <div class="stat-content">
            <div class="stat-icon" :style="{ backgroundColor: colorMap[item.color] + '15', color: colorMap[item.color] }">
              <el-icon :size="28"
                ><component :is="item.icon" /></el-icon>
            </div>
            <div class="stat-info">
              <div class="stat-value">{{ stats[item.key] ?? 0 }}</div>
              <div class="stat-label">{{ item.title }}</div>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <div class="section-title" style="margin-top: 24px">执行统计</div>
    <el-row :gutter="20">
      <el-col v-for="item in logCards" :key="item.title" :span="8" :xs="24" :sm="24" :md="8">
        <el-card class="stat-card log-stat-card" shadow="never">
          <div class="stat-content">
            <div class="stat-icon" :style="{ backgroundColor: colorMap[item.color] + '15', color: colorMap[item.color] }">
              <el-icon :size="28"
                ><component :is="item.icon" /></el-icon>
            </div>
            <div class="stat-info">
              <div class="stat-value">{{ stats[item.key] ?? 0 }}</div>
              <div class="stat-label">{{ item.title }}</div>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <el-card class="dashboard-info-card" shadow="never">
      <template #header>
        <div class="info-header">
          <span class="info-title">系统说明</span>
        </div>
      </template>
      <div class="info-content">
        <p>本系统支持配置定时任务，连接多种数据源执行 SQL，将结果生成 Excel / Word / PPT / CSV / TXT 报表，并通过邮件发送给指定收件人。</p>
        <el-divider />
        <el-row :gutter="24">
          <el-col :span="12" :xs="24">
            <h4>快速入口</h4>
            <ul class="feature-list">
              <li><router-link to="/task">任务管理</router-link> — 创建、编辑、触发定时任务</li>
              <li><router-link to="/task-sql">SQL 管理</router-link> — 维护 SQL 模块与模板绑定</li>
              <li><router-link to="/template">模板管理</router-link> — 上传 Word / Excel / PPT 模板</li>
            </ul>
          </el-col>
          <el-col :span="12" :xs="24">
            <h4>核心能力</h4>
            <ul class="feature-list">
              <li>CRON / 单次触发</li>
              <li>多数据源与 SSH 代理</li>
              <li>邮件自动分发与模板变量</li>
            </ul>
          </el-col>
        </el-row>
      </div>
    </el-card>
  </div>
</template>

<style scoped>
.dashboard-page {
  padding: 28px;
}

.dashboard-welcome {
  margin-bottom: 28px;
  padding-bottom: 20px;
  border-bottom: 1px solid #e5e7eb;
}

.welcome-title {
  font-size: 26px;
  font-weight: 700;
  color: #111827;
  margin-bottom: 8px;
}

.welcome-desc {
  color: #6b7280;
  font-size: 15px;
}

.section-title {
  font-size: 16px;
  font-weight: 600;
  color: #374151;
  margin-bottom: 16px;
  padding-left: 10px;
  border-left: 4px solid #4f46e5;
}

.stat-card {
  border-radius: 12px;
  border: 1px solid #e5e7eb;
  background: linear-gradient(135deg, #ffffff 0%, #f9fafb 100%);
  transition: all 0.25s ease;
  margin-bottom: 20px;
}

.stat-card:hover {
  transform: translateY(-3px);
  box-shadow: 0 10px 25px -5px rgba(0, 0, 0, 0.1);
  border-color: #c7d2fe;
}

.stat-card :deep(.el-card__body) {
  padding: 20px;
}

.stat-content {
  display: flex;
  align-items: center;
  gap: 16px;
}

.stat-icon {
  width: 56px;
  height: 56px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.stat-value {
  font-size: 28px;
  font-weight: 700;
  color: #111827;
  line-height: 1.2;
}

.stat-label {
  font-size: 14px;
  color: #6b7280;
  margin-top: 4px;
}

.log-stat-card {
  background: linear-gradient(135deg, #ffffff 0%, #faf5ff 100%);
}

.dashboard-info-card {
  margin-top: 12px;
  border-radius: 12px;
  border: 1px solid #e5e7eb;
}

.info-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.info-title {
  font-size: 16px;
  font-weight: 600;
  color: #374151;
}

.info-content {
  color: #4b5563;
  line-height: 1.8;
}

.info-content h4 {
  color: #111827;
  margin: 16px 0 10px;
  font-size: 15px;
}

.feature-list {
  list-style: none;
  padding: 0;
}

.feature-list li {
  padding: 6px 0;
  color: #4b5563;
}

.feature-list a {
  color: #4f46e5;
  text-decoration: none;
  font-weight: 500;
}

.feature-list a:hover {
  text-decoration: underline;
}

@media (max-width: 768px) {
  .stat-card {
    margin-bottom: 16px;
  }
}
</style>
