<script setup lang="ts">
import { useAppStore } from '@/stores/app'

const appStore = useAppStore()
appStore.setBreadcrumb([{ title: '首页' }])
</script>

<template>
  <div class="page-card">
    <el-row :gutter="16">
      <el-col :span="6">
        <el-card shadow="hover">
          <template #header>任务总数</template>
          <div style="font-size: 28px; font-weight: bold; color: #409eff">-</div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover">
          <template #header>数据源</template>
          <div style="font-size: 28px; font-weight: bold; color: #67c23a">-</div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover">
          <template #header>邮箱配置</template>
          <div style="font-size: 28px; font-weight: bold; color: #e6a23c">-</div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover">
          <template #header>报表模板</template>
          <div style="font-size: 28px; font-weight: bold; color: #f56c6c">-</div>
        </el-card>
      </el-col>
    </el-row>

    <el-card style="margin-top: 20px" shadow="hover">
      <template #header>系统说明</template>
      <p>欢迎使用定时任务报表系统。</p>
      <p>
        本系统支持配置定时任务，连接多种数据源执行 SQL，将结果生成 Excel / Word / PPT / CSV /
        TXT 报表，并通过邮件发送给指定收件人。
      </p>
    </el-card>
  </div>
</template>
