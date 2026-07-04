<script setup lang="ts">
import { ref, watch, computed, onMounted, nextTick } from "vue";
import { ElMessage } from "element-plus";
import { createTask, getTask, updateTask } from "@/api/task";
import { listTaskSql } from "@/api/taskSql";
import type {
  TaskConfig,
  TaskConfigRequest,
  TaskSqlConfig,
} from "@/types/entity";

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
const form = ref<TaskConfig>({
  taskName: "",
  taskCode: "",
  triggerType: "CRON",
  triggerConfig: "",
  status: "ENABLE",
});

const sqlOptions = ref<TaskSqlConfig[]>([]);
const selectedSqlIds = ref<number[]>([]);

const rules = {
  taskName: [{ required: true, message: "请输入任务名称", trigger: "blur" }],
  taskCode: [{ required: true, message: "请输入任务编码", trigger: "blur" }],
  triggerType: [
    { required: true, message: "请选择触发类型", trigger: "change" },
  ],
  triggerConfig: [
    { required: true, message: "请输入触发配置", trigger: "blur" },
  ],
};

const isEdit = computed(() => !!props.id);
const title = computed(() => (isEdit.value ? "编辑任务" : "新增任务"));

const selectedSqlList = computed(() => {
  return selectedSqlIds.value
    .map((id) => sqlOptions.value.find((sql) => sql.id === id))
    .filter((sql): sql is TaskSqlConfig => !!sql);
});

const groupedSqlOptions = computed(() => {
  const groups = new Map<string, TaskSqlConfig[]>();
  const noGroup: TaskSqlConfig[] = [];
  sqlOptions.value.forEach((sql) => {
    if (sql.groupName) {
      if (!groups.has(sql.groupName)) {
        groups.set(sql.groupName, []);
      }
      groups.get(sql.groupName)!.push(sql);
    } else {
      noGroup.push(sql);
    }
  });
  const result: { label: string; options: TaskSqlConfig[] }[] = [];
  groups.forEach((options, label) => result.push({ label, options }));
  if (noGroup.length > 0) {
    result.push({ label: "未分组", options: noGroup });
  }
  return result;
});

const sqlTreeData = computed(() => {
  return groupedSqlOptions.value.map((group) => ({
    id: `group_${group.label}`,
    label: group.label,
    children: group.options.map((item) => ({
      id: item.id!,
      label: `${item.sqlName} (${item.sqlCode})`,
    })),
  }));
});

const treeSelectRef = ref();

const handleTreeChange = () => {
  nextTick(() => {
    const ts = treeSelectRef.value as any;
    if (ts?.treeRef) {
      ts.treeRef.filter("");
    }
    if (ts?.selectRef) {
      ts.selectRef.query = "";
    }
  });
};

const treeSelectedKeys = computed({
  get: () => selectedSqlIds.value.map((id) => id!),
  set: (keys) => {
    selectedSqlIds.value = keys
      .filter((k) => typeof k === "number" || !String(k).startsWith("group_"))
      .map((k) => Number(k));
  },
});

const loadOptions = async () => {
  const sqlRes = await listTaskSql().catch(() => []);
  sqlOptions.value = sqlRes || [];
};

const resetForm = () => {
  form.value = {
    taskName: "",
    taskCode: "",
    triggerType: "CRON",
    triggerConfig: "",
    status: "ENABLE",
  };
  selectedSqlIds.value = [];
};

const loadDetail = async () => {
  if (!props.id) return;
  loading.value = true;
  try {
    const res: TaskConfigRequest = await getTask(props.id);
    form.value = res.task || {
      taskName: "",
      taskCode: "",
      triggerType: "CRON",
      triggerConfig: "",
      status: "ENABLE",
    };
    selectedSqlIds.value = res.sqlIds || [];
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

const moveSqlUp = (index: number) => {
  if (index <= 0) return;
  const arr = [...selectedSqlIds.value];
  const temp = arr[index];
  arr[index] = arr[index - 1];
  arr[index - 1] = temp;
  selectedSqlIds.value = arr;
};

const moveSqlDown = (index: number) => {
  if (index >= selectedSqlIds.value.length - 1) return;
  const arr = [...selectedSqlIds.value];
  const temp = arr[index];
  arr[index] = arr[index + 1];
  arr[index + 1] = temp;
  selectedSqlIds.value = arr;
};

const removeSql = (index: number) => {
  const arr = [...selectedSqlIds.value];
  arr.splice(index, 1);
  selectedSqlIds.value = arr;
};

const handleSubmit = async () => {
  const valid = await formRef.value?.validate().catch(() => false);
  if (!valid) return;

  if (selectedSqlIds.value.length === 0) {
    ElMessage.warning("请选择至少一条 SQL");
    return;
  }

  loading.value = true;
  try {
    const request: TaskConfigRequest = {
      task: form.value,
      sqlIds: selectedSqlIds.value,
    };
    if (isEdit.value) {
      await updateTask(props.id!, request);
    } else {
      await createTask(request);
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

onMounted(() => {
  loadOptions();
});
</script>

<template>
  <el-dialog
    v-model="dialogVisible"
    :title="title"
    width="820px"
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
      <el-row :gutter="16">
        <el-col :span="12">
          <el-form-item label="任务名称" prop="taskName">
            <el-input v-model="form.taskName" placeholder="任务名称" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="任务编码" prop="taskCode">
            <el-input v-model="form.taskCode" placeholder="任务编码" />
          </el-form-item>
        </el-col>
      </el-row>

      <el-row :gutter="16">
        <el-col :span="12">
          <el-form-item label="触发类型" prop="triggerType">
            <el-radio-group v-model="form.triggerType">
              <el-radio label="CRON">CRON</el-radio>
              <el-radio label="ONCE">单次</el-radio>
            </el-radio-group>
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="触发配置" prop="triggerConfig">
            <el-input
              v-model="form.triggerConfig"
              :placeholder="
                form.triggerType === 'CRON'
                  ? '0 0 9 * * ?'
                  : '2026-01-01 09:00:00'
              "
            />
          </el-form-item>
        </el-col>
      </el-row>

      <el-form-item label="选择 SQL" required>
        <el-tree-select
          ref="treeSelectRef"
          v-model="treeSelectedKeys"
          :data="sqlTreeData"
          multiple
          show-checkbox
          filterable
          default-expand-all
          placeholder="请选择要执行的 SQL，支持按分组或 SQL 名称搜索"
          style="width: 100%"
          @change="handleTreeChange"
        />
      </el-form-item>

      <el-form-item v-if="selectedSqlList.length > 0" label="执行顺序">
        <el-table :data="selectedSqlList" border size="small">
          <el-table-column
            type="index"
            label="序号"
            width="60"
            align="center"
          />
          <el-table-column
            prop="sqlName"
            label="SQL 名称"
            min-width="160"
            show-overflow-tooltip
          />
          <el-table-column
            prop="sqlCode"
            label="SQL 编码"
            min-width="120"
            show-overflow-tooltip
          />
          <el-table-column
            prop="groupName"
            label="分组"
            min-width="120"
            show-overflow-tooltip
          />
          <el-table-column label="模板" min-width="140" show-overflow-tooltip>
            <template #default="{ row }">
              {{ row.templateId ? "有" : "无" }}
            </template>
          </el-table-column>
          <el-table-column
            label="操作"
            width="150"
            align="center"
            fixed="right"
          >
            <template #default="{ $index }">
              <el-button
                link
                type="primary"
                :disabled="$index === 0"
                @click="moveSqlUp($index)"
                >上移</el-button
              >
              <el-button
                link
                type="primary"
                :disabled="$index === selectedSqlList.length - 1"
                @click="moveSqlDown($index)"
                >下移</el-button
              >
              <el-button link type="danger" @click="removeSql($index)"
                >删除</el-button
              >
            </template>
          </el-table-column>
        </el-table>
      </el-form-item>

      <el-form-item label="描述">
        <el-input
          v-model="form.description"
          type="textarea"
          :rows="2"
          placeholder="任务描述（可选）"
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
