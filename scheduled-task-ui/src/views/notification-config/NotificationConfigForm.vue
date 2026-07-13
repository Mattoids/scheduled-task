<script setup lang="ts">
import { ref, watch, computed, onUnmounted } from "vue";
import { useRouter } from "vue-router";
import { ElMessage } from "element-plus";
import { Loading, CircleCheck } from "@element-plus/icons-vue";
import {
  createNotificationConfig,
  getNotificationConfig,
  updateNotificationConfig,
  testNotificationConfig,
} from "@/api/notificationConfig";
import { generateQrCode, checkLoginStatus, checkCookieValid } from "@/api/wecomIpSync";
import { listEnabledWeComAdminAccounts } from "@/api/wecomAdminAccount";
import type { NotificationConfig, WeComAdminAccount } from "@/types/entity";

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

const router = useRouter();
const goIpSyncLogs = () => {
  router.push("/notification/ip-sync-log");
};

// ==================== 企业微信管理账户下拉 ====================
const wecomAccounts = ref<WeComAdminAccount[]>([]);
const loadingAccounts = ref(false);

const loadWecomAccounts = async () => {
  loadingAccounts.value = true;
  try {
    wecomAccounts.value = await listEnabledWeComAdminAccounts();
  } finally {
    loadingAccounts.value = false;
  }
};

const goWecomAdmin = () => {
  router.push("/wecom-admin");
};

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
        autoSyncIp: false,
        adminCookie: "",
        adminAccountId: null,
        syncIntervalMinutes: 10,
        ipDetectionUrl: "",
        appManageUrl: "",
      };
    case "WECOM_BOT":
      return { webhookKey: "" };
    case "WECOM_INTELLIGENT_BOT":
      return { mode: "LONGCHAIN", corpId: "", botId: "", botSecret: "" };
    case "DINGTALK":
      return { webhookUrl: "", secret: "", atMobiles: "", atAll: 0 };
    case "FEISHU":
      return { webhookUrl: "", secret: "" };
    case "SLACK":
      return { webhookUrl: "", channel: "", username: "" };
    case "WEBHOOK":
      return { url: "", method: "POST", headers: "", bodyTemplate: "", timeoutSeconds: 30 };
    default:
      return {};
  }
};

const form = ref<NotificationConfig>({
  configName: "",
  configCode: "",
  configType: "EMAIL",
  configJson: defaultConfigJson("EMAIL"),
  status: 1,
});

const typeOptions = [
  { label: "邮箱", value: "EMAIL" },
  { label: "企业微信应用", value: "WECOM_APP" },
  { label: "企业微信群机器人", value: "WECOM_BOT" },
  { label: "企业微信智能机器人", value: "WECOM_INTELLIGENT_BOT" },
  { label: "钉钉群机器人", value: "DINGTALK" },
  { label: "飞书群机器人", value: "FEISHU" },
  { label: "Slack", value: "SLACK" },
  { label: "Webhook", value: "WEBHOOK" },
];

const rules = {
  configName: [{ required: true, message: "请输入配置名称", trigger: "blur" }],
  configCode: [{ required: true, message: "请输入配置编码", trigger: "blur" }],
  configType: [
    { required: true, message: "请选择配置类型", trigger: "change" },
  ],
};

const isEdit = computed(() => !!props.id);
const title = computed(() =>
  isEdit.value ? "编辑通知配置" : "新增通知配置",
);

// ==================== IP 检测源 ====================
const CUSTOM_IP_SOURCE = "__custom__";

const ipSourceOptions = [
  { label: "ipip.net", value: "https://myip.ipip.net" },
  { label: "3322.net", value: "https://ip.3322.net" },
  { label: "ifconfig.me", value: "https://ifconfig.me/ip" },
  { label: "ipify.org", value: "https://api.ipify.org" },
  { label: "ipinfo.io", value: "https://ipinfo.io/ip" },
  { label: "icanhazip.com", value: "https://icanhazip.com" },
  { label: "oray（花生壳）", value: "https://ddns.oray.com/checkip" },
  { label: "AWS checkip", value: "https://checkip.amazonaws.com" },
  { label: "自定义", value: CUSTOM_IP_SOURCE },
];

const ipSourceSelect = ref("");
const customIpSourceUrl = ref("");
// 防止 watcher 循环：内部更新 ipDetectionUrl 时设置此标记，ipDetectionUrl watcher 跳过本次
let _ipSourceInternal = false;

// 根据 configJson.ipDetectionUrl 初始化下拉选中值
watch(
  () => form.value.configJson?.ipDetectionUrl,
  (url) => {
    if (_ipSourceInternal) {
      _ipSourceInternal = false;
      return;
    }
    if (!url) {
      ipSourceSelect.value = ipSourceOptions[0].value;
      customIpSourceUrl.value = "";
      return;
    }
    const preset = ipSourceOptions.find((o) => o.value === url);
    if (preset) {
      ipSourceSelect.value = url;
      customIpSourceUrl.value = "";
    } else {
      ipSourceSelect.value = CUSTOM_IP_SOURCE;
      customIpSourceUrl.value = url;
    }
  },
  { immediate: true },
);

// 下拉变化时同步到 configJson
watch(ipSourceSelect, (val) => {
  _ipSourceInternal = true;
  if (val === CUSTOM_IP_SOURCE) {
    // 选自定义时保留已有 URL（编辑场景），不清空
    if (!customIpSourceUrl.value) {
      form.value.configJson.ipDetectionUrl = "";
    } else {
      form.value.configJson.ipDetectionUrl = customIpSourceUrl.value;
    }
  } else {
    form.value.configJson.ipDetectionUrl = val;
    customIpSourceUrl.value = "";
  }
});

// 自定义 URL 输入时同步
watch(customIpSourceUrl, (val) => {
  if (ipSourceSelect.value === CUSTOM_IP_SOURCE) {
    _ipSourceInternal = true;
    form.value.configJson.ipDetectionUrl = val;
  }
});

const headersText = computed({
  get: () => {
    const headers = form.value.configJson?.headers;
    if (!headers) return "";
    if (typeof headers === "string") return headers;
    try {
      return JSON.stringify(headers, null, 2);
    } catch {
      return "";
    }
  },
  set: (val) => {
    form.value.configJson.headers = val;
  },
});

const resetForm = () => {
  form.value = {
    configName: "",
    configCode: "",
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
      loadWecomAccounts();
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
  if (data.configType === "WEBHOOK" && typeof data.configJson.headers === "string") {
    try {
      data.configJson.headers = JSON.parse(data.configJson.headers);
    } catch {
      data.configJson.headers = {};
    }
  }
  // 使用管理账户时清除直接存储的 Cookie，避免数据冗余
  if (data.configType === "WECOM_APP" && data.configJson.adminAccountId) {
    data.configJson.adminCookie = "";
  }
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

// ==================== 二维码登录 ====================
const qrDialogVisible = ref(false);
const qrCodeImage = ref("");
const qrStatus = ref<"loading" | "waiting" | "success" | "expired" | "error">("loading");
const qrSessionId = ref("");
const qrCountdown = ref(0);
let qrPollTimer: ReturnType<typeof setInterval> | null = null;
let qrCountdownTimer: ReturnType<typeof setInterval> | null = null;

const QR_TOTAL_SECONDS = 180;

const qrCountdownText = computed(() => {
  const m = Math.floor(qrCountdown.value / 60);
  const s = qrCountdown.value % 60;
  return `${m}:${s.toString().padStart(2, "0")}`;
});

const qrCountdownColor = computed(() => {
  if (qrCountdown.value > 60) return "#67c23a";
  if (qrCountdown.value > 30) return "#e6a23c";
  return "#f56c6c";
});

const handleFetchCookie = async () => {
  qrDialogVisible.value = true;
  qrStatus.value = "loading";
  qrCodeImage.value = "";
  stopQrCountdown();
  try {
    const res = await generateQrCode();
    if (res.debug === "true" && res.debugScreenshot) {
      qrCodeImage.value = res.debugScreenshot;
      qrStatus.value = "error";
      ElMessage.warning(`未能找到登录二维码（页面: ${res.pageTitle || "未知"}），请查看截图`);
      return;
    }
    qrSessionId.value = res.sessionId;
    qrCodeImage.value = res.qrCodeBase64;
    qrStatus.value = "waiting";
    startQrPolling();
    startQrCountdown();
  } catch (e: any) {
    qrStatus.value = "error";
    ElMessage.error(e?.message || "生成二维码失败");
  }
};

const startQrPolling = () => {
  stopQrPolling();
  qrPollTimer = setInterval(async () => {
    if (!qrSessionId.value) return;
    try {
      const res = await checkLoginStatus(qrSessionId.value);
      if (res.status === "LOGGED_IN" && res.cookie) {
        form.value.configJson.adminCookie = res.cookie;
        qrStatus.value = "success";
        stopQrPolling();
        stopQrCountdown();
        setTimeout(() => {
          qrDialogVisible.value = false;
          ElMessage.success("Cookie 获取成功，已自动填入");
        }, 800);
      } else if (res.status === "EXPIRED") {
        qrStatus.value = "expired";
        qrCountdown.value = 0;
        stopQrPolling();
        stopQrCountdown();
      }
    } catch {
      // 轮询失败不中断
    }
  }, 2000);
};

const stopQrPolling = () => {
  if (qrPollTimer) {
    clearInterval(qrPollTimer);
    qrPollTimer = null;
  }
};

const startQrCountdown = () => {
  stopQrCountdown();
  qrCountdown.value = QR_TOTAL_SECONDS;
  qrCountdownTimer = setInterval(() => {
    qrCountdown.value--;
    if (qrCountdown.value <= 0) {
      qrCountdown.value = 0;
      qrStatus.value = "expired";
      stopQrCountdown();
      stopQrPolling();
    }
  }, 1000);
};

const stopQrCountdown = () => {
  if (qrCountdownTimer) {
    clearInterval(qrCountdownTimer);
    qrCountdownTimer = null;
  }
};

const handleQrDialogClose = () => {
  stopQrPolling();
  stopQrCountdown();
  qrSessionId.value = "";
};

onUnmounted(() => {
  stopQrPolling();
  stopQrCountdown();
});
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

      <el-form-item label="配置编码" prop="configCode">
        <el-input v-model="form.configCode" placeholder="配置编码，用于通知规则关联" />
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
              <el-input
                v-model="form.configJson.token"
                type="password"
                placeholder="回调 Token（可选）"
                show-password
              />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="AES Key">
              <el-input
                v-model="form.configJson.aesKey"
                type="password"
                placeholder="回调 AES Key（可选）"
                show-password
              />
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

        <el-divider content-position="left">可信 IP 自动同步</el-divider>

        <el-form-item label="自动同步 IP">
          <el-switch
            v-model="form.configJson.autoSyncIp"
            active-text="开启"
            inactive-text="关闭"
          />
          <div class="form-tip" style="margin-left: 12px; color: #909399; font-size: 12px;">
            开启后定时检测公网 IP 并同步到企业微信可信 IP 白名单
          </div>
        </el-form-item>

        <template v-if="form.configJson.autoSyncIp">
          <el-form-item label="IP 检测源">
            <el-select v-model="ipSourceSelect" style="width: 100%" placeholder="选择 IP 检测网站">
              <el-option
                v-for="item in ipSourceOptions"
                :key="item.value"
                :label="item.label"
                :value="item.value"
              />
            </el-select>
          </el-form-item>
          <el-form-item v-if="ipSourceSelect === CUSTOM_IP_SOURCE" label="自定义域名">
            <el-input
              v-model="customIpSourceUrl"
              placeholder="输入 IP 解析站域名，如 ifconfig.me 或 ip.3322.net"
            />
            <div class="form-tip" style="color: #909399; font-size: 12px; margin-top: 4px;">
              只需输入域名，系统自动补全 https:// 并从响应中解析 IPv4 地址
            </div>
          </el-form-item>
          <el-form-item label="应用管理页 URL">
            <el-input
              v-model="form.configJson.appManageUrl"
              placeholder="可选，如 https://work.weixin.qq.com/wework_admin/frame#apps/modApiApp/5629502132772163"
              clearable
            />
            <div class="form-tip" style="color: #909399; font-size: 12px; margin-top: 4px;">
              留空时按应用 ID 自动拼接（兼容旧版后台）
            </div>
          </el-form-item>
          <el-form-item label="管理账户">
            <div style="display: flex; gap: 8px; align-items: center; width: 100%;">
              <el-select
                v-model="form.configJson.adminAccountId"
                placeholder="选择企业微信管理账户"
                style="flex: 1"
                :loading="loadingAccounts"
                clearable
              >
                <el-option
                  v-for="account in wecomAccounts"
                  :key="account.id"
                  :label="account.accountName"
                  :value="account.id"
                >
                  <span>{{ account.accountName }}</span>
                  <el-tag
                    :type="account.cookieConfigured ? 'success' : 'danger'"
                    size="small"
                    style="margin-left: 8px"
                  >
                    {{ account.cookieConfigured ? "Cookie 已配置" : "Cookie 未配置" }}
                  </el-tag>
                </el-option>
              </el-select>
              <el-button link type="primary" @click="goWecomAdmin">
                管理账户
              </el-button>
            </div>
            <div class="form-tip" style="color: #909399; font-size: 12px; margin-top: 4px;">
              选择已配置 Cookie 的企业微信管理账户，用于自动同步可信 IP
            </div>
          </el-form-item>
          <el-form-item label="同步间隔">
            <el-input-number
              v-model="form.configJson.syncIntervalMinutes"
              :min="1"
              :max="1440"
              controls-position="right"
              style="width: 160px"
            />
            <span style="margin-left: 8px; color: #909399; font-size: 12px;">分钟（默认 10 分钟）</span>
          </el-form-item>
          <el-form-item label="同步日志">
            <el-button link type="primary" @click="goIpSyncLogs">查看同步日志</el-button>
            <div class="form-tip" style="margin-left: 12px; color: #909399; font-size: 12px;">
              仅在实际替换或失败时记录，相同原因的失败记录一小时内只保留一条
            </div>
          </el-form-item>
        </template>
      </template>

      <template v-if="form.configType === 'WECOM_BOT'">
        <el-form-item label="Webhook Key" required>
          <el-input
            v-model="form.configJson.webhookKey"
            type="password"
            placeholder="机器人 Webhook Key"
            show-password
          />
        </el-form-item>
      </template>

      <template v-if="form.configType === 'WECOM_INTELLIGENT_BOT'">
        <el-form-item label="连接模式" required>
          <el-radio-group v-model="form.configJson.mode">
            <el-radio value="LONGCHAIN">长链模式</el-radio>
            <el-radio value="CALLBACK">回调模式</el-radio>
          </el-radio-group>
        </el-form-item>

        <template v-if="form.configJson.mode === 'LONGCHAIN'">
          <el-form-item label="机器人 ID" required>
            <el-input v-model="form.configJson.botId" placeholder="智能机器人的 BotId" />
          </el-form-item>
          <el-form-item label="机器人 Secret">
            <el-input
              v-model="form.configJson.botSecret"
              type="password"
              placeholder="留空表示不修改"
              show-password
            />
          </el-form-item>
        </template>

        <template v-if="form.configJson.mode === 'CALLBACK'">
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
                <el-input
                  v-model="form.configJson.token"
                  type="password"
                  placeholder="回调 Token"
                  show-password
                />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="AES Key">
                <el-input
                  v-model="form.configJson.aesKey"
                  type="password"
                  placeholder="回调 AES Key"
                  show-password
                />
              </el-form-item>
            </el-col>
          </el-row>
        </template>
      </template>

      <template v-if="form.configType === 'DINGTALK'">
        <el-form-item label="Webhook 地址" required>
          <el-input
            v-model="form.configJson.webhookUrl"
            placeholder="https://oapi.dingtalk.com/robot/send?access_token=xxx"
          />
        </el-form-item>
        <el-form-item label="加签密钥">
          <el-input
            v-model="form.configJson.secret"
            type="password"
            placeholder="机器人安全设置中的加签密钥"
            show-password
          />
        </el-form-item>
        <el-form-item label="默认@手机号">
          <el-input
            v-model="form.configJson.atMobiles"
            placeholder="被@人的手机号，多个用逗号分隔（可选）"
          />
        </el-form-item>
        <el-form-item label="@所有人">
          <el-switch
            v-model="form.configJson.atAll"
            :active-value="1"
            :inactive-value="0"
          />
        </el-form-item>
      </template>

      <template v-if="form.configType === 'FEISHU'">
        <el-form-item label="Webhook 地址" required>
          <el-input
            v-model="form.configJson.webhookUrl"
            placeholder="https://open.feishu.cn/open-apis/bot/v2/hook/xxx"
          />
        </el-form-item>
        <el-form-item label="加签密钥">
          <el-input
            v-model="form.configJson.secret"
            type="password"
            placeholder="机器人安全设置中的签名校验密钥"
            show-password
          />
        </el-form-item>
      </template>

      <template v-if="form.configType === 'SLACK'">
        <el-form-item label="Webhook 地址" required>
          <el-input
            v-model="form.configJson.webhookUrl"
            placeholder="https://hooks.slack.com/services/..."
          />
        </el-form-item>
        <el-form-item label="频道">
          <el-input
            v-model="form.configJson.channel"
            placeholder="#general（留空使用 Webhook 默认频道）"
          />
        </el-form-item>
        <el-form-item label="发送者名称">
          <el-input
            v-model="form.configJson.username"
            placeholder="Scheduled Task Bot"
          />
        </el-form-item>
      </template>

      <template v-if="form.configType === 'WEBHOOK'">
        <el-form-item label="请求地址" required>
          <el-input
            v-model="form.configJson.url"
            placeholder="https://example.com/api/notify"
          />
        </el-form-item>
        <el-form-item label="请求方法" required>
          <el-select v-model="form.configJson.method" placeholder="请选择请求方法">
            <el-option label="GET" value="GET" />
            <el-option label="POST" value="POST" />
            <el-option label="PUT" value="PUT" />
          </el-select>
        </el-form-item>
        <el-form-item label="请求头">
          <el-input
            v-model="headersText"
            type="textarea"
            :rows="4"
            placeholder='{"Content-Type":"application/json"}'
          />
        </el-form-item>
        <el-form-item label="请求体模板">
          <el-input
            v-model="form.configJson.bodyTemplate"
            type="textarea"
            :rows="6"
            placeholder='{"title":"${title}","content":"${content}"}'
          />
        </el-form-item>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="超时秒数">
              <el-input-number
                v-model="form.configJson.timeoutSeconds"
                :min="1"
                :max="300"
                controls-position="right"
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
        </el-row>
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

  <!-- 二维码登录弹窗 -->
  <el-dialog
    v-model="qrDialogVisible"
    title="扫码登录企业微信"
    width="380px"
    :close-on-click-modal="false"
    @close="handleQrDialogClose"
  >
    <div style="text-align: center; padding: 10px 0;">
      <div v-if="qrStatus === 'loading'" style="padding: 40px 0;">
        <el-icon class="is-loading" :size="32"><Loading /></el-icon>
        <p style="margin-top: 12px; color: #909399;">正在生成二维码...</p>
      </div>
      <div v-else-if="qrStatus === 'error'" style="padding: 20px 0;">
        <template v-if="qrCodeImage">
          <p style="color: #e6a23c; font-size: 13px; margin-bottom: 8px;">浏览器实际渲染的页面（未找到二维码）：</p>
          <img
            :src="qrCodeImage"
            style="max-width: 100%; border: 1px solid #e4e7ed; border-radius: 4px;"
            alt="调试截图"
          />
        </template>
        <p v-else style="color: #f56c6c;">生成二维码失败，请重试</p>
        <el-button type="primary" plain style="margin-top: 12px;" @click="handleFetchCookie">
          重新获取
        </el-button>
      </div>
      <template v-else>
        <div style="position: relative; display: inline-block;">
          <img
            v-if="qrCodeImage"
            :src="qrCodeImage"
            style="width: 260px; height: 260px; border: 1px solid #e4e7ed; border-radius: 8px;"
            alt="登录二维码"
          />
          <div
            v-if="qrStatus === 'expired'"
            style="position: absolute; inset: 0; background: rgba(255,255,255,0.92); display: flex; flex-direction: column; align-items: center; justify-content: center; border-radius: 8px;"
          >
            <p style="color: #e6a23c; font-size: 15px; font-weight: 500;">二维码已过期</p>
            <el-button type="primary" plain style="margin-top: 10px;" @click="handleFetchCookie">
              重新获取
            </el-button>
          </div>
        </div>
        <div v-if="qrStatus === 'waiting'" style="margin-top: 14px;">
          <p style="color: #606266; font-size: 14px;">请使用企业微信 App 扫码登录</p>
          <div style="margin-top: 8px; display: flex; align-items: center; justify-content: center; gap: 6px;">
            <span style="color: #909399; font-size: 12px;">有效期</span>
            <span :style="{ color: qrCountdownColor, fontSize: '16px', fontWeight: 600, fontFamily: 'monospace' }">
              {{ qrCountdownText }}
            </span>
          </div>
          <p style="color: #909399; font-size: 12px; margin-top: 4px;">
            扫码成功后 Cookie 将自动填入
          </p>
        </div>
        <div v-if="qrStatus === 'success'" style="margin-top: 16px;">
          <el-icon :size="32" color="#67c23a"><CircleCheck /></el-icon>
          <p style="color: #67c23a; font-size: 14px; margin-top: 4px;">登录成功！</p>
        </div>
      </template>
    </div>
  </el-dialog>
</template>
