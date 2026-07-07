<script setup lang="ts">
import { ref, watch, computed } from "vue";
import { ElMessage } from "element-plus";
import { createTaskSql, getTaskSql, updateTaskSql } from "@/api/taskSql";
import type { TaskSqlConfig } from "@/types/entity";

interface Props {
  visible: boolean;
  id?: number;
  datasourceOptions: { label: string; value: number }[];
  templateOptions: { label: string; value: number }[];
  groupOptions: { label: string; value: number; fileNamePattern?: string }[];
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
const form = ref<TaskSqlConfig>({
  sqlName: "",
  sqlCode: "",
  datasourceId: undefined as any,
  sqlContent: "",
  templateId: undefined,
  groupId: undefined,
  outputFormat: "CSV",
  chartEnabled: 0,
  chartType: "BAR",
  chartTitle: "",
  fileSuffix: "",
  fileNamePattern: "",
  description: "",
  status: 1,
});

const selectedGroupPattern = computed(() => {
  const group = props.groupOptions.find((g) => g.value === form.value.groupId);
  return group?.fileNamePattern;
});

const isInlineOutput = computed(() => form.value.outputFormat === "INLINE");

watch(
  () => form.value.groupId,
  (groupId) => {
    if (isInlineOutput.value) {
      return;
    }
    const group = props.groupOptions.find((g) => g.value === groupId);
    if (group?.fileNamePattern) {
      form.value.fileNamePattern = group.fileNamePattern;
    }
  },
);

watch(
  () => form.value.outputFormat,
  (format) => {
    if (format === "INLINE") {
      form.value.templateId = undefined;
      form.value.fileSuffix = "";
      form.value.fileNamePattern = "";
    }
  },
);

const outputFormatOptions = [
  { label: "CSV", value: "CSV" },
  { label: "Excel", value: "EXCEL" },
  { label: "Word", value: "WORD" },
  { label: "PPT", value: "PPT" },
  { label: "文本", value: "TXT" },
  { label: "内联到通知", value: "INLINE" },
];

const chartTypeOptions = [
  { label: "柱状图", value: "BAR" },
  { label: "折线图", value: "LINE" },
  { label: "饼图", value: "PIE" },
  { label: "面积图", value: "AREA" },
  { label: "散点图", value: "SCATTER" },
  { label: "堆叠柱状图", value: "STACKED_BAR" },
  { label: "环形图", value: "DOUGHNUT" },
];

const rules = {
  sqlName: [{ required: true, message: "请输入 SQL 名称", trigger: "blur" }],
  sqlCode: [{ required: true, message: "请输入 SQL 编码", trigger: "blur" }],
  datasourceId: [
    { required: true, message: "请选择数据源", trigger: "change" },
  ],
  sqlContent: [{ required: true, message: "请输入 SQL 内容", trigger: "blur" }],
};

const isEdit = computed(() => !!props.id);
const title = computed(() => (isEdit.value ? "编辑 SQL" : "新增 SQL"));

const resetForm = () => {
  form.value = {
    sqlName: "",
    sqlCode: "",
    datasourceId: undefined as any,
    sqlContent: "",
    templateId: undefined,
    groupId: undefined,
    outputFormat: "CSV",
    chartEnabled: 0,
    chartType: "BAR",
    chartTitle: "",
    fileSuffix: "",
    fileNamePattern: "",
    description: "",
    status: 1,
  };
};

const loadDetail = async () => {
  if (!props.id) return;
  loading.value = true;
  try {
    const res = await getTaskSql(props.id);
    form.value = res;
    if (res.outputFormat === "INLINE") {
      form.value.templateId = undefined;
      form.value.fileSuffix = "";
      form.value.fileNamePattern = "";
    }
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
      await updateTaskSql(props.id!, form.value);
    } else {
      await createTaskSql(form.value);
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
    width="780px"
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
          <el-form-item label="SQL 名称" prop="sqlName">
            <el-input v-model="form.sqlName" placeholder="SQL 名称" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="SQL 编码" prop="sqlCode">
            <el-input v-model="form.sqlCode" placeholder="SQL 编码" />
          </el-form-item>
        </el-col>
      </el-row>

      <el-form-item label="数据源" prop="datasourceId">
        <el-select
          v-model="form.datasourceId"
          placeholder="请选择数据源"
          style="width: 100%"
        >
          <el-option
            v-for="item in datasourceOptions"
            :key="item.value"
            :label="item.label"
            :value="item.value"
          />
        </el-select>
      </el-form-item>

      <el-form-item label="SQL 内容" prop="sqlContent">
        <el-input
          v-model="form.sqlContent"
          type="textarea"
          :rows="5"
          placeholder="请输入要执行的 SQL"
        />
      </el-form-item>

      <el-row :gutter="16">
        <el-col :span="12">
          <el-form-item label="报表模板">
            <el-select
              v-model="form.templateId"
              placeholder="请选择模板（可选）"
              clearable
              :disabled="isInlineOutput"
            >
              <el-option
                v-for="item in templateOptions"
                :key="item.value"
                :label="item.label"
                :value="item.value"
              />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="输出格式">
            <el-select v-model="form.outputFormat" placeholder="输出格式">
              <el-option
                v-for="item in outputFormatOptions"
                :key="item.value"
                :label="item.label"
                :value="item.value"
              />
            </el-select>
          </el-form-item>
        </el-col>
      </el-row>

      <el-divider content-position="left">图表配置</el-divider>
      <el-row :gutter="16">
        <el-col :span="12">
          <el-form-item label="生成图表">
            <el-switch
              v-model="form.chartEnabled"
              :active-value="1"
              :inactive-value="0"
              active-text="启用"
              inactive-text="禁用"
            />
          </el-form-item>
        </el-col>
      </el-row>

      <template v-if="form.chartEnabled === 1">
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="图表类型">
              <el-select v-model="form.chartType" placeholder="请选择图表类型" style="width: 100%">
                <el-option
                  v-for="item in chartTypeOptions"
                  :key="item.value"
                  :label="item.label"
                  :value="item.value"
                />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="图表标题">
              <el-input
                v-model="form.chartTitle"
                placeholder="留空使用 SQL 名称"
              />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item>
          <span class="form-tip"
            >在通知内容中可通过占位符 <code>${chart:{{ form.sqlCode || 'sql编码' }}}</code> 插入该图表</span
          >
        </el-form-item>
      </template>

      <el-row :gutter="16">
        <el-col :span="12">
          <el-form-item label="文件后缀">
            <el-input
              v-model="form.fileSuffix"
              placeholder="如 csv、xlsx（可选）"
              :disabled="isInlineOutput"
            />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="分组">
            <el-select
              v-model="form.groupId"
              placeholder="请选择分组（可选）"
              clearable
            >
              <el-option
                v-for="item in groupOptions"
                :key="item.value"
                :label="item.label"
                :value="item.value"
              />
            </el-select>
          </el-form-item>
        </el-col>
      </el-row>

      <el-form-item label="文件名">
        <el-input
          v-model="form.fileNamePattern"
          :disabled="isInlineOutput || !!selectedGroupPattern"
          :placeholder="
            isInlineOutput
              ? '内联到通知，无需文件名'
              : selectedGroupPattern
                ? '已使用分组文件名'
                : '如 report_{yyyyMMddHHmmss}（可选）'
          "
        />
        <span class="form-tip"
          >支持 {yyyyMMdd}、{lastMonth} 等占位符；选择分组后将自动使用分组文件名</span
        >
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
