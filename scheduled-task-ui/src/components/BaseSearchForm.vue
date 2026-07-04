<script setup lang="ts">
import { Search, RefreshRight } from "@element-plus/icons-vue";

const emit = defineEmits<{
  search: [];
  reset: [];
}>();

const handleSearch = () => emit("search");
const handleReset = () => emit("reset");
</script>

<template>
  <el-form class="search-form" @submit.prevent="handleSearch">
    <div class="search-form-content">
      <slot />
    </div>
    <div class="search-actions">
      <el-button type="primary" :icon="Search" @click="handleSearch"
        >查询</el-button
      >
      <el-button :icon="RefreshRight" @click="handleReset">清空</el-button
      >
    </div>
  </el-form>
</template>

<style scoped></style>
