<script setup lang="ts">
import { Search, RefreshRight } from '@element-plus/icons-vue'

const emit = defineEmits<{
  search: []
  reset: []
}>()

const handleSearch = () => emit('search')
const handleReset = () => emit('reset')
</script>

<template>
  <el-form inline class="search-form">
    <slot />
    <el-form-item class="search-actions">
      <el-button type="primary" :icon="Search" @click="handleSearch">查询</el-button>
      <el-button :icon="RefreshRight" @click="handleReset">重置</el-button>
    </el-form-item>
  </el-form>
</template>

<style scoped>
.search-actions {
  margin-left: auto;
}
</style>
