<script setup lang="ts">
import { ref, reactive, computed, onMounted, onUnmounted } from "vue";
import { ElMessage, ElMessageBox } from "element-plus";
import { Loading, CircleCheck } from "@element-plus/icons-vue";
import { usePagination } from "@/composables/usePagination";
import { generateQrCode, checkLoginStatus, checkCookieValid } from "@/api/wecomIpSync";
import {
  pageWeComAdminAccounts,
  createWeComAdminAccount,
  updateWeComAdminAccount,
  deleteWeComAdminAccount,
  triggerKeepAlive,
  issueSsoTicket,
} from "@/api/wecomAdminAccount";
import type { WeComAdminAccount } from "@/types/entity";
import { useAppStore } from "@/stores/app";

const appStore = useAppStore();
appStore.setBreadcrumb([{ title: "通知管理" }, { title: "企业微信管理" }]);

const chromiumReady = computed(() => appStore.chromiumAvailable === true);

const { current, size, total, records, buildQuery, setPageResult, reset } =
  usePagination();
const loading = ref(false);
const queryForm = reactive<{ keyword?: string }>({ keyword: undefined });

const loadPage = async () => {
  loading.value = true;
  try {
    const res = await pageWeComAdminAccounts(buildQuery(queryForm));
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
  queryForm.keyword = undefined;
  reset();
  loadPage();
};
const handlePageChange = (c: number, s: number) => {
  current.value = c;
  size.value = s;
  loadPage();
};

// ==================== 新增/编辑弹窗 ====================
const dialogVisible = ref(false);
const dialogMode = ref<"create" | "rename">("create");
const editingId = ref<number | null>(null);
const accountName = ref("");
const scannedCookie = ref("");
const editCookie = ref("");
const cookieChecking = ref(false);
const submitting = ref(false);

const dialogTitle = computed(() =>
  dialogMode.value === "create" ? "新增企业微信账户" : "编辑企业微信账户"
);

/** 新增账户：打开弹窗，用户可手动粘贴 Cookie 或扫码获取 */
const handleAdd = () => {
  dialogMode.value = "create";
  editingId.value = null;
  accountName.value = "";
  scannedCookie.value = "";
  editCookie.value = "";
  dialogVisible.value = true;
};

const handleRename = (row: WeComAdminAccount) => {
  dialogMode.value = "rename";
  editingId.value = row.id!;
  accountName.value = row.accountName;
  scannedCookie.value = "";
  editCookie.value = "";
  dialogVisible.value = true;
};

const handleSubmit = async () => {
  if (!accountName.value.trim()) {
    ElMessage.warning("请输入账户名称");
    return;
  }
  const cookie = editCookie.value.trim() || scannedCookie.value;
  if (dialogMode.value === "create" && !cookie) {
    ElMessage.warning("请先粘贴 Cookie 或扫码获取");
    return;
  }
  submitting.value = true;
  try {
    if (dialogMode.value === "create") {
      await createWeComAdminAccount({
        accountName: accountName.value.trim(),
        adminCookie: cookie,
      });
      ElMessage.success("账户创建成功，已自动开启 Cookie 保活");
    } else {
      const updateData: Record<string, any> = { accountName: accountName.value.trim() };
      if (editCookie.value.trim()) {
        updateData.adminCookie = editCookie.value.trim();
      }
      await updateWeComAdminAccount(editingId.value!, updateData);
      ElMessage.success(editCookie.value.trim() ? "名称和 Cookie 已更新" : "名称已更新");
    }
    dialogVisible.value = false;
    loadPage();
  } finally {
    submitting.value = false;
  }
};

const handleCheckCookie = async () => {
  const cookie = editCookie.value.trim() || scannedCookie.value;
  if (!cookie) {
    ElMessage.warning("请先粘贴 Cookie 或扫码获取");
    return;
  }
  cookieChecking.value = true;
  try {
    const res = await checkCookieValid({ adminCookie: cookie });
    if (res.valid) {
      ElMessage.success(res.message || "Cookie 有效");
    } else {
      ElMessage.error(res.message || "Cookie 已失效");
    }
  } finally {
    cookieChecking.value = false;
  }
};

// ==================== 二维码登录 ====================
const qrDialogVisible = ref(false);
const qrCodeImage = ref("");
const qrStatus = ref<"loading" | "waiting" | "scanned" | "success" | "expired" | "error">("loading");
const qrSessionId = ref("");
const qrCountdown = ref(0);
/** 扫码成功后的回调：新增时填入弹窗，更新 Cookie 时直接提交 */
const qrSuccessAction = ref<"fill" | "update">("fill");
const qrUpdateRow = ref<WeComAdminAccount | null>(null);
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
        qrStatus.value = "success";
        stopQrPolling();
        stopQrCountdown();
        await handleQrLoginSuccess(res.cookie);
      } else if (res.status === "SCANNED") {
        qrStatus.value = "scanned";
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

const handleQrLoginSuccess = async (cookie: string) => {
  if (qrSuccessAction.value === "update" && qrUpdateRow.value) {
    try {
      await updateWeComAdminAccount(qrUpdateRow.value.id!, { adminCookie: cookie });
      ElMessage.success(`账户「${qrUpdateRow.value.accountName}」Cookie 已更新`);
      loadPage();
    } catch {
      // 错误已由拦截器提示
    }
  } else {
    scannedCookie.value = cookie;
    editCookie.value = cookie;
    ElMessage.success("Cookie 获取成功，请填写账户名称");
  }
  setTimeout(() => {
    qrDialogVisible.value = false;
  }, 800);
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

/** 更新已有账户的 Cookie（重新扫码） */
const handleUpdateCookie = (row: WeComAdminAccount) => {
  qrSuccessAction.value = "update";
  qrUpdateRow.value = row;
  handleFetchCookie();
};

// ==================== 状态/保活 ====================
const handleStatusChange = async (row: WeComAdminAccount) => {
  try {
    await updateWeComAdminAccount(row.id!, { status: row.status });
    ElMessage.success(row.status === 1 ? "已启用" : "已停用");
  } catch {
    row.status = row.status === 1 ? 0 : 1;
  }
};

const handleKeepAliveChange = async (row: WeComAdminAccount) => {
  try {
    await updateWeComAdminAccount(row.id!, { keepAliveEnabled: row.keepAliveEnabled });
    ElMessage.success(row.keepAliveEnabled ? "已开启保活" : "已关闭保活");
  } catch {
    row.keepAliveEnabled = !row.keepAliveEnabled;
  }
};

const keepAliveLoading = ref<Record<number, boolean>>({});
const handleKeepAliveNow = async (row: WeComAdminAccount) => {
  keepAliveLoading.value[row.id!] = true;
  try {
    const res = await triggerKeepAlive(row.id!);
    if (res.success) {
      ElMessage.success(res.message || "保活成功");
    } else {
      ElMessage.warning(res.message || "保活失败");
    }
    loadPage();
  } finally {
    keepAliveLoading.value[row.id!] = false;
  }
};

const keepAliveResultType = (result?: string) => {
  if (!result) return "info";
  return result.startsWith("保活成功") ? "success" : "danger";
};

// ==================== 免登录跳转 ====================
const ssoLoading = ref<Record<number, boolean>>({});
const handleSso = async (row: WeComAdminAccount) => {
  ssoLoading.value[row.id!] = true;
  try {
    const res = await issueSsoTicket(row.id!);
    window.open(res.url, "_blank");
  } finally {
    ssoLoading.value[row.id!] = false;
  }
};

// ==================== 删除 ====================
const handleDelete = async (row: WeComAdminAccount) => {
  await ElMessageBox.confirm(
    `确定删除账户「${row.accountName}」吗？删除后保活任务将一并停止。`,
    "删除确认",
    { type: "warning" }
  );
  await deleteWeComAdminAccount(row.id!);
  ElMessage.success("删除成功");
  loadPage();
};

onMounted(loadPage);
onUnmounted(() => {
  stopQrPolling();
  stopQrCountdown();
});
</script>

<template>
  <div class="page-card">
    <BaseSearchForm @search="handleSearch" @reset="handleReset">
      <el-row>
        <el-col :span="8">
          <el-form-item label="账户名称">
            <el-input
              v-model="queryForm.keyword"
              placeholder="输入账户名称搜索"
              clearable
              @keyup.enter="handleSearch"
            />
          </el-form-item>
        </el-col>
      </el-row>
    </BaseSearchForm>

    <div class="table-toolbar">
      <el-button type="primary" v-permission="'wecomAdmin:create'" @click="handleAdd">
        新增账户
      </el-button>
      <span class="toolbar-tip">
        新增时将自动弹出企业微信扫码登录；系统会随机每 1~10 分钟访问一次企业微信页面保持 Cookie 不失效
      </span>
    </div>

    <el-table v-loading="loading" :data="records" border stripe>
      <el-table-column prop="accountName" label="账户名称" show-overflow-tooltip />
      <el-table-column label="Cookie" width="100">
        <template #default="{ row }">
          <el-tag :type="row.cookieConfigured ? 'success' : 'danger'" size="small">
            {{ row.cookieConfigured ? "已配置" : "未配置" }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="90">
        <template #default="{ row }">
          <el-switch
            v-model="row.status"
            :active-value="1"
            :inactive-value="0"
            v-permission="'wecomAdmin:edit'"
            @change="handleStatusChange(row)"
          />
        </template>
      </el-table-column>
      <el-table-column label="自动保活" width="100">
        <template #default="{ row }">
          <el-switch
            v-model="row.keepAliveEnabled"
            v-permission="'wecomAdmin:edit'"
            @change="handleKeepAliveChange(row)"
          />
        </template>
      </el-table-column>
      <el-table-column label="最近保活结果" show-overflow-tooltip>
        <template #default="{ row }">
          <el-tag :type="keepAliveResultType(row.lastKeepAliveResult)" size="small" effect="plain">
            {{ row.lastKeepAliveResult || "未执行" }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="lastKeepAliveTime" label="最近保活时间" />
      <el-table-column prop="createTime" label="创建时间" />
      <el-table-column label="操作" fixed="right">
        <template #default="{ row }: { row: WeComAdminAccount }">
          <el-button
            link
            type="primary"
            size="small"
            :loading="ssoLoading[row.id!]"
            :disabled="!row.cookieConfigured"
            v-permission="'wecomAdmin:view'"
            @click="handleSso(row)"
            >免登录跳转</el-button
          >
          <el-button
            link
            type="warning"
            size="small"
            v-permission="'wecomAdmin:edit'"
            v-if="chromiumReady"
            @click="handleUpdateCookie(row)"
            >更新Cookie</el-button
          >
          <el-button
            link
            type="warning"
            size="small"
            v-permission="'wecomAdmin:edit'"
            v-else
            disabled
            title="Chromium 内核未安装，无法扫码更新 Cookie"
            >更新Cookie</el-button
          >
          <el-button
            link
            type="success"
            size="small"
            :loading="keepAliveLoading[row.id!]"
            v-permission="'wecomAdmin:edit'"
            @click="handleKeepAliveNow(row)"
            >立即保活</el-button
          >
          <el-button
            link
            type="primary"
            size="small"
            v-permission="'wecomAdmin:edit'"
            @click="handleRename(row)"
            >重命名</el-button
          >
          <el-button
            link
            type="danger"
            size="small"
            v-permission="'wecomAdmin:delete'"
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

    <!-- 新增/重命名弹窗 -->
    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="520px">
      <el-form label-width="90px">
        <el-form-item label="账户名称" required>
          <el-input v-model="accountName" placeholder="自定义名称，如：XX公司主账号" maxlength="64" />
        </el-form-item>
        <el-form-item :label="dialogMode === 'create' ? 'Cookie' : '更新 Cookie'" :required="dialogMode === 'create'">
          <el-input
            v-model="editCookie"
            type="textarea"
            :rows="3"
            :placeholder="dialogMode === 'create' ? '可直接粘贴 Cookie，或点击下方按钮扫码获取' : '留空则不修改 Cookie；粘贴新 Cookie 或扫码更新'"
          />
          <div style="margin-top: 8px; display: flex; gap: 8px; align-items: center;">
            <el-button size="small" @click="handleFetchCookie" v-if="chromiumReady">
              扫码获取 Cookie
            </el-button>
            <el-tag v-else type="danger" size="small" effect="plain">
              Chromium 内核未安装，无法扫码，请手动粘贴 Cookie
            </el-tag>
            <el-button size="small" :loading="cookieChecking" @click="handleCheckCookie">
              检测 Cookie 有效性
            </el-button>
          </div>
          <div v-if="scannedCookie && !editCookie" style="margin-top: 4px;">
            <el-tag type="success" size="small">扫码成功，Cookie 已填入</el-tag>
          </div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="handleSubmit">确定</el-button>
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
              扫码成功后 Cookie 将自动保存
            </p>
          </div>
          <div v-if="qrStatus === 'scanned'" style="margin-top: 16px;">
            <el-icon :size="32" color="#e6a23c"><CircleCheck /></el-icon>
            <p style="color: #e6a23c; font-size: 14px; margin-top: 4px;">扫码成功</p>
            <p style="color: #606266; font-size: 12px; margin-top: 4px;">请在企业微信 App 中点击「确认登录」</p>
          </div>
          <div v-if="qrStatus === 'success'" style="margin-top: 16px;">
            <el-icon :size="32" color="#67c23a"><CircleCheck /></el-icon>
            <p style="color: #67c23a; font-size: 14px; margin-top: 4px;">登录成功！</p>
          </div>
        </template>
      </div>
    </el-dialog>
  </div>
</template>

<style scoped lang="scss">
.table-toolbar {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 12px;

  .toolbar-tip {
    color: #909399;
    font-size: 12px;
  }
}
</style>
