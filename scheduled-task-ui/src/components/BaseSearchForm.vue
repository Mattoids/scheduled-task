<script setup lang="ts">
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
    <el-form-item>
      <el-button type="primary" @click="handleSearch">查询</el-button>
      <el-button @click="handleReset">重置</el-button>
    </el-form-item>
  </el-form>
</template>

<style scoped>
.search-form {
  margin-bottom: 16px;
}
</style>
