<script setup lang="ts">
import { computed } from "vue";

interface Props {
  total: number;
  current: number;
  size: number;
}

const props = defineProps<Props>();
const emit = defineEmits<{
  change: [current: number, size: number];
}>();

const currentPage = computed({
  get: () => props.current,
  set: (val) => emit("change", val, props.size),
});

const pageSize = computed({
  get: () => props.size,
  set: (val) => emit("change", 1, val),
});
</script>

<template>
  <div class="pagination-wrapper">
    <el-pagination
      v-model:current-page="currentPage"
      v-model:page-size="pageSize"
      :total="total"
      :page-sizes="[10, 20, 50, 100]"
      layout="total, sizes, prev, pager, next, jumper"
    />
  </div>
</template>
