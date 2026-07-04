<script setup lang="ts">
import { ref, watch, computed } from "vue";
import { ElMessage } from "element-plus";
import {
  createNotificationConfig,
  getNotificationConfig,
  updateNotificationConfig,
  testNotificationConfig,
} from "@/api/notificationConfig";
import type { NotificationConfig } from "@/types/entity";

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

const defaultConfigJson = (type: string) => {
  switch (type) {
    case "EMAIL":
      return {
        smtpHost: "",
        smtpPort: 587,
        username: "",
        password: "",
        fromAddress: "",
        fromName: "",
        auth: 1,
        starttls: 1,
        ssl: 0,
      };
    case "WECOM_APP":
      return {
        corpId: "",
        agentId: 0,
        secret: "",
        token: "",
        aesKey: "",
        proxyUrl: "",
        menuJson: "",
      };
    case "WECOM_BOT":
    case "WECOM_INTELLIGENT_BOT":
      return { webhookKey: "" };
    default:
      return {};
  }
};

const form = ref<NotificationConfig>({
  configName: "",
  configType: "EMAIL",
  configJson: defaultConfigJson("EMAIL"),
  status: 1,
});

const typeOptions = [
  { label: "邮箱", value: "EMAIL" },
  { label: "企业微信应用", value: "WECOM_APP" },
  { label: "企业微信群机器人", value: "WECOM_BOT" },
  { label: "企业微信智能机器人", value: "WECOM_INTELLIGENT_BOT" },
];

const rules = {
  configName: [{ required: true, message: "请输入配置名称", trigger: "blur" }],
  configType: [
    { required: true, message: "请选择配置类型", trigger: "change" },
  ],
};

const isEdit = computed(() => !!props.id);
const title = computed(() =>
  isEdit.value ? "编辑通知配置" : "新增通知配置",
);

const resetForm = () => {
  form.value = {
    configName: "",
    configType: "EMAIL",
    configJson: defaultConfigJson("EMAIL"),
    status: 1,
  };
};

const parseConfigJson = (value: any) => {
  if (!value) return {};
  if (typeof value === "string") {
    try {
      return JSON.parse(value);
    } catch {
      return {};
    }
  }
  return value;
};

const loadDetail = async () => {
  if (!props.id) return;
  loading.value = true;
  try {
    const res = await getNotificationConfig(props.id);
    form.value = {
      ...res,
      configJson: parseConfigJson(res.configJson),
    };
  } finally {
    loading.value = false;
  }
};

watch(
  () => props.visible,
  (val) => {
    if (val) {
      resetForm();
      if (props.id) loadDetail();
    }
  },
);

watch(
  () => form.value.configType,
  (type) => {
    if (!isEdit.value && type) {
      form.value.configJson = defaultConfigJson(type);
    }
  },
);

const buildSubmitData = () => {
  const data = { ...form.value };
  data.configJson =
    typeof data.configJson === "string"
      ? data.configJson
      : JSON.stringify(data.configJson);
  return data;
};

const handleSubmit = async () => {
  const valid = await formRef.value?.validate().catch(() => false);
  if (!valid) return;
  loading.value = true;
  try {
    const data = buildSubmitData();
    if (isEdit.value) {
      await updateNotificationConfig(props.id!, data);
    } else {
      await createNotificationConfig(data);
    }
    ElMessage.success(isEdit.value ? "修改成功" : "新增成功");
    emit("success");
  } finally {
    loading.value = false;
  }
};

const handleTest = async () => {
  const valid = await formRef.value?.validate().catch(() => false);
  if (!valid) return;
  loading.value = true;
  try {
    const res = await testNotificationConfig(buildSubmitData());
    if (res.success) {
      ElMessage.success("连接成功");
    } else {
      ElMessage.error(res.message || "连接失败");
    }
  } finally {
    loading.value = false;
  }
};
</script>

<template>
  <el-dialog v-model="dialogVisible" :title="title" width="700px">
    <el-form
      ref="formRef"
      :model="form"
      :rules="rules"
      class="dialog-form"
      label-width="110px"
      v-loading="loading"
    >
      <el-form-item label="配置名称" prop="configName">
        <el-input v-model="form.configName" placeholder="配置名称" />
      </el-form-item>

      <el-form-item label="配置类型" prop="configType">
        <el-select
          v-model="form.configType"
          placeholder="请选择配置类型"
          :disabled="isEdit"
          style="width: 100%"
        >
          <el-option
            v-for="item in typeOptions"
            :key="item.value"
            :label="item.label"
            :value="item.value"
          />
        </el-select>
      </el-form-item>

      <template v-if="form.configType === 'EMAIL'">
        <el-row :gutter="16">
          <el-col :span="16">
            <el-form-item label="SMTP 主机" required>
              <el-input v-model="form.configJson.smtpHost" placeholder="smtp.example.com" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="SMTP 端口" required>
              <el-input-number
                v-model="form.configJson.smtpPort"
                :min="1"
                :max="65535"
                controls-position="right"
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="用户名" required>
          <el-input v-model="form.configJson.username" placeholder="邮箱账号" />
        </el-form-item>
        <el-form-item label="密码">
          <el-input
            v-model="form.configJson.password"
            type="password"
            placeholder="留空表示不修改"
            show-password
          />
        </el-form-item>
        <el-form-item label="发件地址" required>
          <el-input v-model="form.configJson.fromAddress" placeholder="sender@example.com" />
        </el-form-item>
        <el-form-item label="发件人名称">
          <el-input v-model="form.configJson.fromName" placeholder="发件人显示名称" />
        </el-form-item>
        <el-row :gutter="16">
          <el-col :span="8">
            <el-form-item label="认证">
              <el-switch
                v-model="form.configJson.auth"
                :active-value="1"
                :inactive-value="0"
              />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="STARTTLS">
              <el-switch
                v-model="form.configJson.starttls"
                :active-value="1"
                :inactive-value="0"
              />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="SSL">
              <el-switch
                v-model="form.configJson.ssl"
                :active-value="1"
                :inactive-value="0"
              />
            </el-form-item>
          </el-col>
        </el-row>
      </template>

      <template v-if="form.configType === 'WECOM_APP'">
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="企业 ID" required>
              <el-input v-model="form.configJson.corpId" placeholder="企业微信 CorpID" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="应用 ID" required>
              <el-input-number
                v-model="form.configJson.agentId"
                :min="0"
                controls-position="right"
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="Secret">
          <el-input
            v-model="form.configJson.secret"
            type="password"
            placeholder="留空表示不修改"
            show-password
          />
        </el-form-item>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="Token">
              <el-input v-model="form.configJson.token" placeholder="回调 Token（可选）" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="AES Key">
              <el-input v-model="form.configJson.aesKey" placeholder="回调 AES Key（可选）" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="代理地址">
          <el-input
            v-model="form.configJson.proxyUrl"
            placeholder="企业微信 API 代理地址（https://proxy.example.com/qyapi）"
          />
        </el-form-item>
        <el-form-item label="菜单 JSON">
          <el-input
            v-model="form.configJson.menuJson"
            type="textarea"
            :rows="4"
            placeholder='{"button":[{"type":"click","name":"查询任务","key":"QUERY_TASKS"}]}'
          />
        </el-form-item>
      </template>

      <template
        v-if="
          form.configType === 'WECOM_BOT' ||
          form.configType === 'WECOM_INTELLIGENT_BOT'
        "
      >
        <el-form-item label="Webhook Key" required>
          <el-input
            v-model="form.configJson.webhookKey"
            placeholder="机器人 Webhook Key"
          />
        </el-form-item>
      </template>

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
      <el-button @click="dialogVisible = false">取消</el-button>
      <el-button :loading="loading" @click="handleTest">测试连接</el-button>
      <el-button type="primary" :loading="loading" @click="handleSubmit"
        >确定</el-button
      >
    </template>
  </el-dialog>
</template>
