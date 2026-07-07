<script setup lang="ts">
import { ref, watch, computed } from "vue";
import { ElMessage } from "element-plus";
import { createTaskSql, getTaskSql, updateTaskSql } from "@/api/taskSql";
import type { TaskSqlConfig } from "@/types/entity";

interface Props {
  visible: boolean;
  id?: number;
  datasourceOptions: { label: string; value: number }[];
  templateOptions: { label: string; value: string }[];
  groupOptions: { label: string; value: string; fileNamePattern?: string }[];
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
  templateCode: undefined,
  groupCode: undefined,
  outputFormat: "CSV",
  chartEnabled: 0,
  chartType: "BAR",
  chartTitle: "",
  chartAutoMerge: 1,
  chartLabelRotation: "AUTO",
  excelMergeGroup: "",
  excelSheetName: "",
  fileSuffix: "",
  fileNamePattern: "",
  customParams: "",
  description: "",
  status: 1,
});

const defaultTime = ref("");
const defaultTimeDays = ref(7);
const defaultTimeOptions = [
  { label: "无", value: "" },
  { label: "昨天", value: "YESTERDAY" },
  { label: "N 天前", value: "DAYS_AGO" },
  { label: "上周", value: "LAST_WEEK" },
  { label: "上月", value: "LAST_MONTH" },
  { label: "本月", value: "CURRENT_MONTH" },
  { label: "今年", value: "CURRENT_YEAR" },
  { label: "去年", value: "LAST_YEAR" },
];

const selectedGroupPattern = computed(() => {
  const group = props.groupOptions.find((g) => g.value === form.value.groupCode);
  return group?.fileNamePattern;
});

const isInlineOutput = computed(() => form.value.outputFormat === "INLINE");

watch(
  () => form.value.groupCode,
  (groupCode) => {
    if (isInlineOutput.value) {
      return;
    }
    const group = props.groupOptions.find((g) => g.value === groupCode);
    if (group?.fileNamePattern) {
      form.value.fileNamePattern = group.fileNamePattern;
    }
  },
);

watch(
  () => form.value.outputFormat,
  (format) => {
    if (format === "INLINE") {
      form.value.templateCode = undefined;
      form.value.fileSuffix = "";
      form.value.fileNamePattern = "";
    } else {
      form.value.chartEnabled = 0;
    }
  },
  { immediate: true }
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

const chartLabelRotationOptions = [
  { label: "自动", value: "AUTO" },
  { label: "0°", value: "0" },
  { label: "45°", value: "45" },
  { label: "90°", value: "90" },
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
    templateCode: undefined,
    groupCode: undefined,
    outputFormat: "CSV",
    chartEnabled: 0,
    chartType: "BAR",
    chartTitle: "",
    chartAutoMerge: 1,
    chartLabelRotation: "AUTO",
    excelMergeGroup: "",
    excelSheetName: "",
    fileSuffix: "",
    fileNamePattern: "",
    customParams: "",
    description: "",
    status: 1,
  };
  defaultTime.value = "";
  defaultTimeDays.value = 7;
};

const pad = (n: number) => String(n).padStart(2, "0");
const formatDateTime = (date: Date) =>
  `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`;
const formatDateEnd = (date: Date) =>
  `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} 23:59:59`;

const getTimeRange = (type: string, days: number): { startTime: string; endTime: string } | null => {
  const now = new Date();
  const today = new Date(now.getFullYear(), now.getMonth(), now.getDate());
  switch (type) {
    case "YESTERDAY": {
      const start = new Date(today);
      start.setDate(start.getDate() - 1);
      return { startTime: formatDateTime(start), endTime: formatDateEnd(start) };
    }
    case "DAYS_AGO": {
      const start = new Date(today);
      start.setDate(start.getDate() - days);
      return { startTime: formatDateTime(start), endTime: formatDateEnd(start) };
    }
    case "LAST_WEEK": {
      const day = today.getDay() || 7;
      const start = new Date(today);
      start.setDate(today.getDate() - day - 6);
      const end = new Date(today);
      end.setDate(today.getDate() - day);
      return { startTime: formatDateTime(start), endTime: formatDateEnd(end) };
    }
    case "LAST_MONTH": {
      const start = new Date(today.getFullYear(), today.getMonth() - 1, 1);
      const end = new Date(today.getFullYear(), today.getMonth(), 0);
      return { startTime: formatDateTime(start), endTime: formatDateEnd(end) };
    }
    case "CURRENT_MONTH": {
      const start = new Date(today.getFullYear(), today.getMonth(), 1);
      const end = new Date(today.getFullYear(), today.getMonth() + 1, 0);
      return { startTime: formatDateTime(start), endTime: formatDateEnd(end) };
    }
    case "CURRENT_YEAR": {
      const start = new Date(today.getFullYear(), 0, 1);
      const end = new Date(today.getFullYear(), 11, 31);
      return { startTime: formatDateTime(start), endTime: formatDateEnd(end) };
    }
    case "LAST_YEAR": {
      const start = new Date(today.getFullYear() - 1, 0, 1);
      const end = new Date(today.getFullYear() - 1, 11, 31);
      return { startTime: formatDateTime(start), endTime: formatDateEnd(end) };
    }
    default:
      return null;
  }
};

const parseCustomParams = (): Record<string, any> => {
  const raw = form.value.customParams?.trim();
  if (!raw) return {};
  try {
    return JSON.parse(raw);
  } catch {
    return {};
  }
};

const setCustomParams = (patch: Record<string, any>) => {
  const current = parseCustomParams();
  const next = { ...current, ...patch };
  form.value.customParams = JSON.stringify(next, null, 2);
};

watch(defaultTime, (type) => {
  if (!type) {
    const current = parseCustomParams();
    const { startTime, endTime, ...rest } = current;
    form.value.customParams =
      Object.keys(rest).length === 0 ? "" : JSON.stringify(rest, null, 2);
    return;
  }
  const range = getTimeRange(type, defaultTimeDays.value);
  if (range) {
    setCustomParams(range);
  }
});

watch(defaultTimeDays, (days) => {
  if (defaultTime.value === "DAYS_AGO") {
    const range = getTimeRange("DAYS_AGO", days);
    if (range) {
      setCustomParams(range);
    }
  }
});

watch(
  () => form.value.customParams,
  (raw) => {
    if (!raw) {
      defaultTime.value = "";
      return;
    }
    try {
      const parsed = JSON.parse(raw);
      if (!parsed.startTime || !parsed.endTime) {
        defaultTime.value = "";
      }
    } catch {
      // 非法 JSON 不影响默认时间选择
    }
  },
);

const loadDetail = async () => {
  if (!props.id) return;
  loading.value = true;
  try {
    const res = await getTaskSql(props.id);
    form.value = {
      ...res,
      customParams: res.customParams || "",
    };
    defaultTime.value = "";
    if (res.outputFormat === "INLINE") {
      form.value.templateCode = undefined;
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

  const raw = form.value.customParams?.trim();
  if (raw) {
    try {
      JSON.parse(raw);
    } catch {
      ElMessage.error("自定义参数必须是合法的 JSON 对象");
      return;
    }
  }

  loading.value = true;
  try {
    const payload = {
      ...form.value,
      customParams: raw || undefined,
    };
    if (isEdit.value) {
      await updateTaskSql(props.id!, payload);
    } else {
      await createTaskSql(payload);
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
        <span class="form-tip"
          >支持通过 <code>${参数名}</code> 引用自定义参数，例如
          <code>${startTime}</code>、<code>${endTime}</code></span
        >
      </el-form-item>

      <el-row :gutter="16">
        <el-col :span="12">
          <el-form-item label="默认时间">
            <el-select v-model="defaultTime" placeholder="快捷填充时间范围" clearable>
              <el-option
                v-for="item in defaultTimeOptions"
                :key="item.value"
                :label="item.label"
                :value="item.value"
              />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="12" v-if="defaultTime === 'DAYS_AGO'">
          <el-form-item label="天数">
            <el-input-number v-model="defaultTimeDays" :min="1" :max="365" style="width: 100%" />
          </el-form-item>
        </el-col>
      </el-row>

      <el-form-item label="自定义参数" prop="customParams">
        <el-input
          v-model="form.customParams"
          type="textarea"
          :rows="4"
          placeholder='{"startTime": "2026-07-01 00:00:00", "endTime": "2026-07-01 23:59:59"}'
        />
        <span class="form-tip"
          >JSON 对象，key 对应 SQL 中的 <code>${xxx}</code>
          占位符，选择默认时间后会自动填充 startTime 和 endTime</span
        >
      </el-form-item>

      <el-row :gutter="16">
        <el-col :span="12">
          <el-form-item label="报表模板">
            <el-select
              v-model="form.templateCode"
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

      <el-form-item v-if="form.outputFormat === 'EXCEL'">
        <span class="form-tip"
          >Excel 输出时，若 SQL 结果包含 <code>_sheet_name</code> 列，系统会自动按该列值分 sheet 生成；输出后该列不会写入单元格</span
        >
      </el-form-item>

      <el-row :gutter="16" v-if="form.outputFormat === 'EXCEL'">
        <el-col :span="12">
          <el-form-item label="Excel 合并组">
            <el-input
              v-model="form.excelMergeGroup"
              placeholder="同组合并到同一 Excel 文件（可选）"
            />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="Sheet 名称">
            <el-input
              v-model="form.excelSheetName"
              placeholder="默认使用 SQL 名称（可选）"
            />
          </el-form-item>
        </el-col>
      </el-row>
      <el-form-item v-if="form.outputFormat === 'EXCEL' && form.excelMergeGroup">
        <span class="form-tip"
          >同一合并组内：相同 Sheet 名称的 SQL 会追加到同一页，不同 Sheet 名称会分到多页</span
        >
      </el-form-item>

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
              v-model="form.groupCode"
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

      <template v-if="form.outputFormat === 'INLINE'">
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
          <el-row :gutter="16">
            <el-col :span="12">
              <el-form-item label="自动合并">
                <el-switch
                  v-model="form.chartAutoMerge"
                  :active-value="1"
                  :inactive-value="0"
                  active-text="开启"
                  inactive-text="关闭"
                />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="标签旋转">
                <el-select v-model="form.chartLabelRotation" placeholder="请选择标签旋转角度" style="width: 100%">
                  <el-option
                    v-for="item in chartLabelRotationOptions"
                    :key="item.value"
                    :label="item.label"
                    :value="item.value"
                  />
                </el-select>
              </el-form-item>
            </el-col>
          </el-row>
          <el-form-item>
            <span class="form-tip"
              >在通知内容中可通过占位符 <code>${chart:{{ form.sqlCode || 'sql编码' }}}</code> 插入该图表</span
            >
          </el-form-item>
        </template>
      </template>
    </el-form>

    <template #footer>
      <el-button @click="handleClose">取消</el-button>
      <el-button type="primary" :loading="loading" @click="handleSubmit"
        >确定</el-button
      >
    </template>
  </el-dialog>
</template>
