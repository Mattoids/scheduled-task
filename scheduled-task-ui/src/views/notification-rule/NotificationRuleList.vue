<script setup lang="ts">
import { ref, reactive, onMounted, computed, watch } from "vue";
import { ElMessage, ElMessageBox } from "element-plus";
import { usePagination } from "@/composables/usePagination";
import {
  pageNotificationRule,
  createNotificationRule,
  updateNotificationRule,
  updateNotificationRuleEnabled,
  deleteNotificationRule,
} from "@/api/notificationRule";
import { listNotificationConfig } from "@/api/notificationConfig";
import { listRecipient, listGroup } from "@/api/emailRecipient";
import { pageTask } from "@/api/task";
import { listAiConfig } from "@/api/aiConfig";
import { listStorageConfig } from "@/api/storageConfig";
import type { NotificationRule, TaskConfig, AiConfig } from "@/types/entity";
import { useAppStore } from "@/stores/app";
import RichTextEditor from "@/components/RichTextEditor.vue";

const appStore = useAppStore();
appStore.setBreadcrumb([{ title: "通知规则" }]);

const { current, size, total, records, buildQuery, setPageResult, reset } =
  usePagination();

const queryForm = reactive({
  eventType: "",
  channel: "",
  taskCode: undefined as string | undefined,
});

const loading = ref(false);
const formVisible = ref(false);
const formId = ref<number | undefined>(undefined);

const notificationConfigOptions = ref<{ label: string; value: string; configType: string }[]>([]);
const recipientOptions = ref<{ label: string; value: number }[]>([]);
const recipientGroupOptions = ref<{ label: string; value: number }[]>([]);
const taskOptions = ref<{ label: string; value: string }[]>([]);
const aiConfigOptions = ref<{ label: string; value: number }[]>([]);
const storageConfigOptions = ref<{ label: string; value: number }[]>([]);

const eventTypeOptions = [
  { label: "任务执行完成", value: "TASK_COMPLETED" },
  { label: "任务执行成功", value: "TASK_SUCCESS" },
  { label: "任务执行失败", value: "TASK_FAILURE" },
];

const channelOptions = [
  { label: "邮件", value: "EMAIL" },
  { label: "企业微信应用", value: "WECOM_APP" },
  { label: "企业微信机器人", value: "WECOM_BOT" },
  { label: "企业微信智能机器人", value: "WECOM_INTELLIGENT_BOT" },
  { label: "钉钉群机器人", value: "DINGTALK" },
  { label: "飞书群机器人", value: "FEISHU" },
  { label: "Slack", value: "SLACK" },
  { label: "Webhook", value: "WEBHOOK" },
];

const configOptions = computed(() => {
  const channel = form.value.channel;
  if (!channel) return [];
  return notificationConfigOptions.value.filter(
    (item) => item.configType === channel
  );
});

const configPlaceholder = computed(() => {
  const channel = form.value.channel;
  switch (channel) {
    case "EMAIL":
      return "请选择邮箱配置";
    case "WECOM_APP":
      return "请选择企业微信应用配置";
    case "WECOM_BOT":
      return "请选择企业微信群机器人配置";
    case "WECOM_INTELLIGENT_BOT":
      return "请选择企业微信智能机器人配置";
    case "DINGTALK":
      return "请选择钉钉群机器人配置";
    case "FEISHU":
      return "请选择飞书群机器人配置";
    case "SLACK":
      return "请选择 Slack 配置";
    case "WEBHOOK":
      return "请选择 Webhook 配置";
    default:
      return "请选择配置";
  }
});

const form = ref<NotificationRule>({
  eventType: "TASK_COMPLETED",
  channel: "EMAIL",
  configCode: undefined,
  taskCode: undefined,
  recipientIds: undefined,
  recipientGroupIds: undefined,
  wecomToUser: "",
  subject: "",
  body: "",
  content: "",
  aiOptimizeNotify: 0,
  aiConfigId: undefined,
  storageConfigId: undefined,
  enabled: 1,
});

const validateRecipient = (_rule: any, _value: any, callback: any) => {
  if (form.value.channel !== "EMAIL") return callback();

  const hasRecipient = Array.isArray(form.value.recipientIds)
    ? form.value.recipientIds.length > 0
    : !!form.value.recipientIds;
  const hasGroup = Array.isArray(form.value.recipientGroupIds)
    ? form.value.recipientGroupIds.length > 0
    : !!form.value.recipientGroupIds;

  if (!hasRecipient && !hasGroup) {
    callback(new Error("请至少选择收件人或收件人群组"));
  } else if (hasRecipient && hasGroup) {
    callback(new Error("收件人和收件人群组只能二选一"));
  } else {
    callback();
  }
};

const rules = {
  eventType: [{ required: true, message: "请选择事件类型", trigger: "change" }],
  channel: [{ required: true, message: "请选择通知渠道", trigger: "change" }],
  configCode: [{ required: true, message: "请选择关联配置", trigger: "change" }],
  recipientIds: [{ validator: validateRecipient, trigger: "change" }],
  recipientGroupIds: [{ validator: validateRecipient, trigger: "change" }],
};

const isEdit = computed(() => !!formId.value);
const title = computed(() => (isEdit.value ? "编辑通知规则" : "新增通知规则"));

const loadOptions = async () => {
  const [configs, rec, grp, taskRes, aiRes, storageRes] = await Promise.all([
    listNotificationConfig().catch(() => ({ records: [] })),
    listRecipient().catch(() => []),
    listGroup().catch(() => []),
    pageTask({ current: 1, size: 1000 }).catch(() => ({ records: [] })),
    listAiConfig().catch(() => ({ records: [] })),
    listStorageConfig().catch(() => []),
  ]);
  notificationConfigOptions.value = (configs.records || [])
    .filter((item: any) => item.configCode)
    .map((item: any) => ({
      label: `${item.configName} (${item.configCode})`,
      value: String(item.configCode),
      configType: item.configType,
    }));
  recipientOptions.value = (rec || []).map((item: any) => ({
    label: `${item.recipientName || item.email} (${item.email})`,
    value: item.id,
  }));
  recipientGroupOptions.value = (grp || []).map((item: any) => ({
    label: item.groupName,
    value: item.id,
  }));
  taskOptions.value = (taskRes.records || [])
    .filter((item: TaskConfig) => item.taskCode)
    .map((item: TaskConfig) => ({
      label: `${item.taskName} (${item.taskCode})`,
      value: String(item.taskCode),
    }));
  aiConfigOptions.value = (aiRes.records || []).map((item: AiConfig) => ({
    label: item.configName,
    value: item.id!,
  }));
  storageConfigOptions.value = (storageRes || []).map((item: any) => ({
    label: item.configName,
    value: item.id!,
  }));
};

const resetForm = () => {
  form.value = {
    eventType: "TASK_COMPLETED",
    channel: "EMAIL",
    configCode: undefined,
    taskCode: undefined,
    recipientIds: undefined,
    recipientGroupIds: undefined,
    wecomToUser: "",
    subject: "",
    body: "",
    content: "",
    aiOptimizeNotify: 0,
    aiConfigId: undefined,
    storageConfigId: undefined,
    enabled: 1,
  };
};

const loadDetail = async () => {
  if (!formId.value) return;
  const row = records.value.find((r) => r.id === formId.value);
  if (row) {
    form.value = {
      ...row,
      aiOptimizeNotify: row.aiOptimizeNotify ?? 0,
      recipientIds: row.recipientIds
        ? row.recipientIds.split(",").map((id: string) => id.trim())
        : undefined,
      recipientGroupIds: row.recipientGroupIds
        ? row.recipientGroupIds.split(",").map((id: string) => id.trim())
        : undefined,
    };
  }
};

const loadPage = async () => {
  loading.value = true;
  try {
    const res = await pageNotificationRule(buildQuery(queryForm));
    setPageResult(res);
  } finally {
    loading.value = false;
  }
};

const handleSearch = () => {
  current.value = 1;
  loadPage();
};

const handleReset = () => {
  queryForm.eventType = "";
  queryForm.channel = "";
  queryForm.taskCode = undefined;
  reset();
  loadPage();
};

const handleCreate = () => {
  formId.value = undefined;
  resetForm();
  formVisible.value = true;
};

const handleEdit = (row: NotificationRule) => {
  formId.value = row.id;
  loadDetail();
  formVisible.value = true;
};

const handleDelete = async (row: NotificationRule) => {
  await ElMessageBox.confirm("确认删除该通知规则？", "提示", {
    type: "warning",
  });
  await deleteNotificationRule(row.id!);
  ElMessage.success("删除成功");
  loadPage();
};

const handleEnabledChange = async (row: NotificationRule) => {
  const enabled = row.enabled === 1 ? 0 : 1;
  await updateNotificationRuleEnabled(row.id!, enabled);
  ElMessage.success("启用状态更新成功");
  loadPage();
};

const handleSubmit = async () => {
  const valid = await formRef.value?.validate().catch(() => false);
  if (!valid) return;

  loading.value = true;
  try {
    const data: NotificationRule = { ...form.value };
    if (data.channel === "EMAIL") {
      data.recipientIds = Array.isArray(data.recipientIds)
        ? data.recipientIds.join(",")
        : data.recipientIds;
      data.recipientGroupIds = Array.isArray(data.recipientGroupIds)
        ? data.recipientGroupIds.join(",")
        : data.recipientGroupIds;
    } else {
      data.recipientIds = undefined;
      data.recipientGroupIds = undefined;
      data.subject = undefined;
      data.body = undefined;
    }
    if (data.channel === "WEBHOOK" || data.channel === "SLACK") {
      data.wecomToUser = undefined;
    }
    if (isEdit.value) {
      await updateNotificationRule(formId.value!, data);
    } else {
      await createNotificationRule(data);
    }
    ElMessage.success(isEdit.value ? "修改成功" : "新增成功");
    formVisible.value = false;
    loadPage();
  } finally {
    loading.value = false;
  }
};

const handleClose = () => {
  formVisible.value = false;
  resetForm();
};

const handlePageChange = (c: number, s: number) => {
  current.value = c;
  size.value = s;
  loadPage();
};

const formatEventType = (value?: string) => {
  return (
    eventTypeOptions.find((item) => item.value === value)?.label || value || "-"
  );
};

const formatChannel = (value?: string) => {
  return (
    channelOptions.find((item) => item.value === value)?.label || value || "-"
  );
};

const formatConfigName = (row: NotificationRule) => {
  return (
    notificationConfigOptions.value.find((item) => item.value === row.configCode)
      ?.label || row.configCode || "-"
  );
};

const formatTaskName = (row: NotificationRule) => {
  if (!row.taskCode) return "全部任务";
  return (
    taskOptions.value.find((item) => item.value === row.taskCode)?.label ||
    row.taskCode
  );
};

const formRef = ref();

watch(
  () => form.value.recipientIds,
  (val) => {
    if (Array.isArray(val) && val.length > 0) {
      form.value.recipientGroupIds = [] as any;
    }
    formRef.value?.validateField("recipientGroupIds");
  }
);

watch(
  () => form.value.recipientGroupIds,
  (val) => {
    if (Array.isArray(val) && val.length > 0) {
      form.value.recipientIds = [] as any;
    }
    formRef.value?.validateField("recipientIds");
  }
);

watch(
  () => form.value.aiOptimizeNotify,
  (val) => {
    if (val !== 1) {
      form.value.aiConfigId = undefined;
    }
  }
);

onMounted(() => {
  loadOptions();
  loadPage();
});
</script>

<template>
  <div class="page-card">
    <BaseSearchForm @search="handleSearch" @reset="handleReset">
      <el-row>
        <el-col :span="6">
          <el-form-item label="事件类型">
            <el-select
              v-model="queryForm.eventType"
              placeholder="全部事件"
              clearable
            >
              <el-option
                v-for="item in eventTypeOptions"
                :key="item.value"
                :label="item.label"
                :value="item.value"
              />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="6">
          <el-form-item label="通知渠道">
            <el-select v-model="queryForm.channel" placeholder="全部渠道" clearable>
              <el-option
                v-for="item in channelOptions"
                :key="item.value"
                :label="item.label"
                :value="item.value"
              />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="6">
          <el-form-item label="关联任务">
            <el-select
              v-model="queryForm.taskCode"
              placeholder="全部任务"
              clearable
            >
              <el-option
                v-for="item in taskOptions"
                :key="item.value"
                :label="item.label"
                :value="item.value"
              />
            </el-select>
          </el-form-item>
        </el-col>
      </el-row>
    </BaseSearchForm>

    <div class="table-toolbar">
      <el-button
        type="primary"
        v-permission="'notificationRule:create'"
        @click="handleCreate"
        >新增规则</el-button
      >
    </div>

    <el-table v-loading="loading" :data="records" border stripe>
      <el-table-column prop="eventType" label="事件类型" min-width="140">
        <template #default="{ row }">{{
          formatEventType(row.eventType)
        }}</template>
      </el-table-column>
      <el-table-column prop="channel" label="通知渠道" min-width="140">
        <template #default="{ row }">{{ formatChannel(row.channel) }}</template>
      </el-table-column>
      <el-table-column label="关联任务" min-width="180" show-overflow-tooltip>
        <template #default="{ row }">{{ formatTaskName(row) }}</template>
      </el-table-column>
      <el-table-column label="关联配置" min-width="160" show-overflow-tooltip>
        <template #default="{ row }">{{ formatConfigName(row) }}</template>
      </el-table-column>
      <el-table-column label="接收人" min-width="160" show-overflow-tooltip>
        <template #default="{ row }">
          <span v-if="row.channel === 'EMAIL'">
            {{ row.recipientIds || row.recipientGroupIds ? "已配置" : "-" }}
          </span>
          <span v-else-if="row.channel === 'WEBHOOK' || row.channel === 'SLACK'">-</span>
          <span v-else>{{ row.wecomToUser || "-" }}</span>
        </template>
      </el-table-column>
      <el-table-column prop="enabled" label="启用状态" width="120" align="center">
        <template #default="{ row }">
          <el-switch
            v-permission="'notificationRule:edit'"
            class="switch-inner-text"
            :model-value="row.enabled"
            :active-value="1"
            :inactive-value="0"
            active-text="启用"
            inactive-text="关闭"
            inline-prompt
            @change="handleEnabledChange(row)"
          />
        </template>
      </el-table-column>
      <el-table-column prop="createTime" label="创建时间" width="170" />
      <el-table-column label="操作" width="160" fixed="right">
        <template #default="{ row }">
          <el-button
            link
            type="primary"
            v-permission="'notificationRule:edit'"
            @click="handleEdit(row)"
            >编辑</el-button
          >
          <el-button
            link
            type="danger"
            v-permission="'notificationRule:delete'"
            @click="handleDelete(row)"
            >删除</el-button
          >
        </template>
      </el-table-column>
    </el-table>

    <BasePagination
      :total="total"
      :current="current"
      :size="size"
      @change="handlePageChange"
    />

    <el-dialog
      v-model="formVisible"
      :title="title"
      width="860px"
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
            <el-form-item label="事件类型" prop="eventType">
              <el-select v-model="form.eventType" placeholder="请选择事件类型">
                <el-option
                  v-for="item in eventTypeOptions"
                  :key="item.value"
                  :label="item.label"
                  :value="item.value"
                />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="通知渠道" prop="channel">
              <el-select v-model="form.channel" placeholder="请选择通知渠道">
                <el-option
                  v-for="item in channelOptions"
                  :key="item.value"
                  :label="item.label"
                  :value="item.value"
                />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="关联任务">
              <el-select
                v-model="form.taskCode"
                placeholder="全部任务（不指定则所有任务都通知）"
                clearable
              >
                <el-option
                  v-for="item in taskOptions"
                  :key="item.value"
                  :label="item.label"
                  :value="item.value"
                />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="关联配置" prop="configCode">
              <el-select
                v-model="form.configCode"
                :placeholder="configPlaceholder"
                clearable
              >
                <el-option
                  v-for="item in configOptions"
                  :key="item.value"
                  :label="item.label"
                  :value="item.value"
                />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>

        <template v-if="form.channel === 'EMAIL'">
          <el-row :gutter="16">
            <el-col :span="12">
              <el-form-item label="收件人" prop="recipientIds">
                <el-select
                  v-model="form.recipientIds"
                  multiple
                  collapse-tags
                  placeholder="请选择收件人（与收件人群组二选一）"
                >
                  <el-option
                    v-for="item in recipientOptions"
                    :key="item.value"
                    :label="item.label"
                    :value="String(item.value)"
                  />
                </el-select>
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="收件人群组" prop="recipientGroupIds">
                <el-select
                  v-model="form.recipientGroupIds"
                  multiple
                  collapse-tags
                  placeholder="请选择收件人群组（与收件人二选一）"
                >
                  <el-option
                    v-for="item in recipientGroupOptions"
                    :key="item.value"
                    :label="item.label"
                    :value="String(item.value)"
                  />
                </el-select>
              </el-form-item>
            </el-col>
          </el-row>

          <el-row>
            <el-col>
              <el-form-item label="邮件主题">
                <el-input
                    v-model="form.subject"
                    placeholder="支持占位符，留空使用默认主题"
                />
              </el-form-item>
            </el-col>
          </el-row>
          <el-row>
            <el-col>
              <el-form-item label="邮件正文">
                <RichTextEditor
                    v-model="form.body"
                    placeholder="支持占位符、Markdown 和 HTML 标签，可用 ${chart:sql编码} 插入 SQL 图表，留空使用默认正文"
                />
              </el-form-item>
            </el-col>
          </el-row>
        </template>

        <template
          v-if="
            form.channel === 'WECOM_APP' ||
            form.channel === 'WECOM_BOT' ||
            form.channel === 'WECOM_INTELLIGENT_BOT' ||
            form.channel === 'DINGTALK'
          "
        >
          <el-row :gutter="16">
            <el-col :span="12">
              <el-form-item :label="form.channel === 'DINGTALK' ? '被@手机号' : '接收人'">
                <el-input
                  v-model="form.wecomToUser"
                  :placeholder="
                    form.channel === 'DINGTALK'
                      ? '被@人的手机号，多个用 | 分隔，为空则不@'
                      : '企业微信用户 ID，多个用 | 分隔，为空则不指定'
                  "
                />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="存储配置">
                <el-select
                  v-model="form.storageConfigId"
                  placeholder="未选择时直接发送文件，选择后上传到存储系统并发送链接"
                  clearable
                >
                  <el-option
                    v-for="item in storageConfigOptions"
                    :key="item.value"
                    :label="item.label"
                    :value="item.value"
                  />
                </el-select>
              </el-form-item>
            </el-col>
          </el-row>

          <el-row>
            <el-col>
              <el-form-item label="消息内容">
                <RichTextEditor
                    v-model="form.content"
                    placeholder="支持占位符、Markdown 和 HTML 标签，可用 ${chart:sql编码} 插入 SQL 图表，留空使用默认内容"
                />
              </el-form-item>
            </el-col>
          </el-row>
        </template>

        <template
          v-if="
            form.channel === 'FEISHU' ||
            form.channel === 'SLACK' ||
            form.channel === 'WEBHOOK'
          "
        >
          <el-row :gutter="16">
            <el-col :span="12">
              <el-form-item label="存储配置">
                <el-select
                  v-model="form.storageConfigId"
                  placeholder="未选择时直接发送文件，选择后上传到存储系统并发送链接"
                  clearable
                >
                  <el-option
                    v-for="item in storageConfigOptions"
                    :key="item.value"
                    :label="item.label"
                    :value="item.value"
                  />
                </el-select>
              </el-form-item>
            </el-col>
          </el-row>

          <el-row>
            <el-col>
              <el-form-item label="消息内容">
                <RichTextEditor
                    v-model="form.content"
                    placeholder="支持占位符、Markdown 和 HTML 标签，可用 ${chart:sql编码} 插入 SQL 图表，留空使用默认内容"
                />
              </el-form-item>
            </el-col>
          </el-row>
        </template>

        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="AI 优化通知">
              <el-switch
                v-model="form.aiOptimizeNotify"
                :active-value="1"
                :inactive-value="0"
                active-text="启用"
                inactive-text="禁用"
              />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="启用状态">
              <el-switch
                v-model="form.enabled"
                :active-value="1"
                :inactive-value="0"
                active-text="启用"
                inactive-text="禁用"
              />
            </el-form-item>
          </el-col>
        </el-row>

        <el-row v-if="form.aiOptimizeNotify === 1">
          <el-col :span="24">
            <el-form-item label="AI 配置">
              <el-select
                v-model="form.aiConfigId"
                placeholder="未选择时使用默认 AI 配置"
                clearable
              >
                <el-option
                  v-for="item in aiConfigOptions"
                  :key="item.value"
                  :label="item.label"
                  :value="item.value"
                />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>

      <template #footer>
        <el-button @click="handleClose">取消</el-button>
        <el-button type="primary" :loading="loading" @click="handleSubmit"
          >确定</el-button
        >
      </template>
    </el-dialog>
  </div>
</template>
