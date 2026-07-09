<script setup lang="ts">
import { ref, watch, computed, onMounted, nextTick } from "vue";
import { ElMessage } from "element-plus";
import { createTask, getTask, updateTask } from "@/api/task";
import { listTaskSql } from "@/api/taskSql";
import { listTaskCrawl } from "@/api/taskCrawl";
import type {
  TaskConfig,
  TaskConfigRequest,
  TaskSqlConfig,
  TaskWebCrawlConfig,
} from "@/types/entity";
import { validateCron, getNextExecutions } from "@/utils/cron";

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
  taskType: "SQL",
});

const sqlOptions = ref<TaskSqlConfig[]>([]);
const crawlOptions = ref<TaskWebCrawlConfig[]>([]);
const selectedSqlCodes = ref<string[]>([]);
const selectedCrawlCodes = ref<string[]>([]);
const treeSelectedKeys = ref<string[]>([]);

watch(
  treeSelectedKeys,
  (keys) => {
    selectedSqlCodes.value = keys.filter((k) => !k.startsWith("group_"));
  },
  { deep: true },
);

const handleTaskTypeChange = () => {
  selectedSqlCodes.value = [];
  selectedCrawlCodes.value = [];
  treeSelectedKeys.value = [];
};

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
  return selectedSqlCodes.value
    .map((code) => sqlOptions.value.find((sql) => sql.sqlCode === code))
    .filter((sql): sql is TaskSqlConfig => !!sql);
});

const selectedCrawlList = computed(() => {
  return selectedCrawlCodes.value
    .map((code) => crawlOptions.value.find((crawl) => crawl.crawlCode === code))
    .filter((crawl): crawl is TaskWebCrawlConfig => !!crawl);
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
      id: item.sqlCode,
      label: `${item.sqlName} (${item.sqlCode})`,
    })),
  }));
});

const cronPreviewVisible = ref(false);
const cronPreviewResult = ref<string[]>([]);
const cronPreviewValid = ref(false);
const cronPreviewMessage = ref("");

const handlePreviewCron = () => {
  if (form.value.triggerType !== "CRON") {
    ElMessage.warning("仅 CRON 触发类型支持预览");
    return;
  }
  const cron = form.value.triggerConfig;
  if (!cron || cron.trim() === "") {
    ElMessage.warning("请先填写 Cron 表达式");
    return;
  }

  const validation = validateCron(cron);
  cronPreviewValid.value = validation.valid;
  cronPreviewMessage.value = validation.message;
  if (!validation.valid) {
    cronPreviewResult.value = [];
    cronPreviewVisible.value = true;
    return;
  }

  const executions = getNextExecutions(cron, 10);
  if (!executions) {
    cronPreviewValid.value = false;
    cronPreviewMessage.value = "Cron 表达式格式无效";
    cronPreviewResult.value = [];
    cronPreviewVisible.value = true;
    return;
  }
  cronPreviewResult.value = executions;
  cronPreviewVisible.value = true;
};

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

const loadOptions = async () => {
  const [sqlRes, crawlRes] = await Promise.all([
    listTaskSql().catch(() => []),
    listTaskCrawl().catch(() => []),
  ]);
  sqlOptions.value = sqlRes || [];
  crawlOptions.value = crawlRes || [];
};

const resetForm = () => {
  form.value = {
    taskName: "",
    taskCode: "",
    triggerType: "CRON",
    triggerConfig: "",
    status: "ENABLE",
    taskType: "SQL",
  };
  selectedSqlCodes.value = [];
  selectedCrawlCodes.value = [];
  treeSelectedKeys.value = [];
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
      taskType: "SQL",
    };
    selectedSqlCodes.value = res.sqlCodes || [];
    selectedCrawlCodes.value = res.crawlCodes || [];
    treeSelectedKeys.value = [...selectedSqlCodes.value];
  } finally {
    loading.value = false;
  }
};

watch(
  () => props.visible,
  async (val) => {
    if (val) {
      resetForm();
      await loadOptions();
      if (props.id) {
        loadDetail();
      }
    }
  },
);

const moveSqlUp = (index: number) => {
  if (index <= 0) return;
  const arr = [...selectedSqlCodes.value];
  const temp = arr[index];
  arr[index] = arr[index - 1];
  arr[index - 1] = temp;
  selectedSqlCodes.value = arr;
};

const moveSqlDown = (index: number) => {
  if (index >= selectedSqlCodes.value.length - 1) return;
  const arr = [...selectedSqlCodes.value];
  const temp = arr[index];
  arr[index] = arr[index + 1];
  arr[index + 1] = temp;
  selectedSqlCodes.value = arr;
};

const removeSql = (index: number) => {
  const arr = [...selectedSqlCodes.value];
  arr.splice(index, 1);
  selectedSqlCodes.value = arr;
};

const moveCrawlUp = (index: number) => {
  if (index <= 0) return;
  const arr = [...selectedCrawlCodes.value];
  const temp = arr[index];
  arr[index] = arr[index - 1];
  arr[index - 1] = temp;
  selectedCrawlCodes.value = arr;
};

const moveCrawlDown = (index: number) => {
  if (index >= selectedCrawlCodes.value.length - 1) return;
  const arr = [...selectedCrawlCodes.value];
  const temp = arr[index];
  arr[index] = arr[index + 1];
  arr[index + 1] = temp;
  selectedCrawlCodes.value = arr;
};

const removeCrawl = (index: number) => {
  const arr = [...selectedCrawlCodes.value];
  arr.splice(index, 1);
  selectedCrawlCodes.value = arr;
};

const handleSubmit = async () => {
  const valid = await formRef.value?.validate().catch(() => false);
  if (!valid) return;

  if (form.value.taskType === "SQL" && selectedSqlCodes.value.length === 0) {
    ElMessage.warning("请选择至少一条 SQL");
    return;
  }
  if (
    form.value.taskType === "CRAWL" &&
    selectedCrawlCodes.value.length === 0
  ) {
    ElMessage.warning("请选择至少一个爬取配置");
    return;
  }

  loading.value = true;
  try {
    const request: TaskConfigRequest = {
      task: form.value,
      sqlCodes: form.value.taskType === "SQL" ? selectedSqlCodes.value : [],
      crawlCodes:
        form.value.taskType === "CRAWL" ? selectedCrawlCodes.value : [],
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
            <el-row :gutter="16">
              <el-col :span="15">
                <el-input
                    v-model="form.triggerConfig"
                    :placeholder="
                form.triggerType === 'CRON'
                  ? '0 0 9 * * ?'
                  : '2026-01-01 09:00:00'
              "
                />
              </el-col>
              <el-col :span="1">
                <el-button
                    v-if="form.triggerType === 'CRON'"
                    type="primary"
                    :disabled="!form.triggerConfig || !form.triggerConfig.trim()"
                    @click="handlePreviewCron"
                    style="margin-left: 8px"
                >
                  预览
                </el-button>
              </el-col>
            </el-row>
          </el-form-item>
        </el-col>
      </el-row>

      <el-row :gutter="16">
        <el-col :span="12">
          <el-form-item label="任务类型">
            <el-radio-group v-model="form.taskType" @change="handleTaskTypeChange">
              <el-radio label="SQL">SQL</el-radio>
              <el-radio label="CRAWL">网页爬取</el-radio>
            </el-radio-group>
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="状态">
            <el-radio-group v-model="form.status">
              <el-radio label="ENABLE">启用</el-radio>
              <el-radio label="DISABLE">禁用</el-radio>
            </el-radio-group>
          </el-form-item>
        </el-col>
      </el-row>

      <el-form-item v-if="form.taskType === 'SQL'" label="选择 SQL" required>
        <el-tree-select
          ref="treeSelectRef"
          v-model="treeSelectedKeys"
          :data="sqlTreeData"
          node-key="id"
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
              {{ row.templateCode ? "有" : "无" }}
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

      <el-form-item v-if="form.taskType === 'CRAWL'" label="选择爬取配置" required>
        <el-select
          v-model="selectedCrawlCodes"
          multiple
          filterable
          placeholder="请选择要执行的爬取配置"
          style="width: 100%"
        >
          <el-option
            v-for="item in crawlOptions"
            :key="item.crawlCode"
            :label="`${item.crawlName} (${item.crawlCode})`"
            :value="item.crawlCode!"
          />
        </el-select>
      </el-form-item>

      <el-form-item v-if="form.taskType === 'CRAWL' && selectedCrawlList.length > 0" label="执行顺序">
        <el-table :data="selectedCrawlList" border size="small">
          <el-table-column
            type="index"
            label="序号"
            width="60"
            align="center"
          />
          <el-table-column
            prop="crawlName"
            label="爬取名称"
            min-width="160"
            show-overflow-tooltip
          />
          <el-table-column
            prop="crawlCode"
            label="爬取编码"
            min-width="120"
            show-overflow-tooltip
          />
          <el-table-column
            prop="outputFormat"
            label="输出格式"
            min-width="100"
            show-overflow-tooltip
          />
          <el-table-column label="模板" min-width="140" show-overflow-tooltip>
            <template #default="{ row }">
              {{ row.templateCode ? "有" : "无" }}
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
                @click="moveCrawlUp($index)"
                >上移</el-button
              >
              <el-button
                link
                type="primary"
                :disabled="$index === selectedCrawlList.length - 1"
                @click="moveCrawlDown($index)"
                >下移</el-button
              >
              <el-button link type="danger" @click="removeCrawl($index)"
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

  <!-- Cron Preview Dialog -->
  <el-dialog
    v-model="cronPreviewVisible"
    title="Cron 预览"
    width="520px"
  >
    <el-alert
      :type="cronPreviewValid ? 'success' : 'error'"
      :title="cronPreviewValid ? 'Cron 表达式有效' : 'Cron 表达式无效'"
      :description="cronPreviewMessage"
      show-icon
      closable="false"
      style="margin-bottom: 16px"
    />
    <el-table
      v-if="cronPreviewValid && cronPreviewResult.length > 0"
      :data="cronPreviewResult"
      border
      size="small"
      style="width: 100%"
    >
      <el-table-column type="index" label="序号" width="70" align="center" />
      <el-table-column label="执行时间">
        <template #default="{ row }">
          <code style="font-size: 13px">{{ row }}</code>
        </template>
      </el-table-column>
    </el-table>
    <el-empty
      v-if="cronPreviewValid && cronPreviewResult.length === 0"
      description="未找到未来执行时间"
    />
  </el-dialog>
</template>
