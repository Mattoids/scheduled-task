<script setup lang="ts">
import { ref, watch, computed } from "vue";
import { ElMessage } from "element-plus";
import {
  createTaskSqlGroup,
  getTaskSqlGroup,
  updateTaskSqlGroup,
} from "@/api/taskSqlGroup";
import type { TaskSqlGroup } from "@/types/entity";

interface Props {
  visible: boolean;
  id?: number;
}

const props = defineProps<Props>();
const emit = defineEmits<{
  "update:visible": [value: boolean];
  success: [];
}>();

const dialogVisible = computed({
  get: () => props.visible,
  set: (val) => emit("update:visible", val),
});

const loading = ref(false);
const formRef = ref();
const form = ref<TaskSqlGroup>({
  groupName: "",
  groupCode: "",
  fileNamePattern: "",
  description: "",
  status: 1,
});

const rules = {
  groupName: [{ required: true, message: "请输入分组名称", trigger: "blur" }],
  groupCode: [{ required: true, message: "请输入分组编码", trigger: "blur" }],
};

const isEdit = computed(() => !!props.id);
const title = computed(() =>
  isEdit.value ? "编辑 SQL 分组" : "新增 SQL 分组",
);

const resetForm = () => {
  form.value = {
    groupName: "",
    groupCode: "",
    fileNamePattern: "",
    description: "",
    status: 1,
  };
};

const loadDetail = async () => {
  if (!props.id) return;
  loading.value = true;
  try {
    const res = await getTaskSqlGroup(props.id);
    form.value = res;
  } finally {
    loading.value = false;
  }
};

watch(
  () => props.visible,
  (val) => {
    if (val) {
      resetForm();
      if (props.id) {
        loadDetail();
      }
    }
  },
);

const handleSubmit = async () => {
  const valid = await formRef.value?.validate().catch(() => false);
  if (!valid) return;

  loading.value = true;
  try {
    if (isEdit.value) {
      await updateTaskSqlGroup(props.id!, form.value);
    } else {
      await createTaskSqlGroup(form.value);
    }
    ElMessage.success(isEdit.value ? "修改成功" : "新增成功");
    emit("success");
  } finally {
    loading.value = false;
  }
};

const handleClose = () => {
  emit("update:visible", false);
};
</script>

<template>
  <el-dialog
    v-model="dialogVisible"
    :title="title"
    width="560px"
    @close="handleClose"
  >
    <el-form
      ref="formRef"
      :model="form"
      :rules="rules"
      class="dialog-form"
      label-width="100px"
      v-loading="loading"
    >
      <el-form-item label="分组名称" prop="groupName">
        <el-input v-model="form.groupName" placeholder="分组名称" />
      </el-form-item>
      <el-form-item label="分组编码" prop="groupCode">
        <el-input v-model="form.groupCode" placeholder="分组编码" />
      </el-form-item>
      <el-form-item label="文件名格式">
        <el-input
          v-model="form.fileNamePattern"
          placeholder="report_{yyyyMMddHHmmss}（可选）"
        />
      </el-form-item>
      <el-form-item label="描述">
        <el-input
          v-model="form.description"
          type="textarea"
          :rows="2"
          placeholder="描述（可选）"
        />
      </el-form-item>
      <el-form-item label="状态">
        <el-switch
          v-model="form.status"
          :active-value="1"
          :inactive-value="0"
          active-text="启用"
          inactive-text="禁用"
        />
      </el-form-item>
    </el-form>

    <template #footer>
      <el-button @click="handleClose">取消</el-button>
      <el-button type="primary" :loading="loading" @click="handleSubmit"
        >确定</el-button
      >
    </template>
  </el-dialog>
</template>
