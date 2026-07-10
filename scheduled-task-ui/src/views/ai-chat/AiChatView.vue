<script setup lang="ts">
import { ref, reactive, onMounted, nextTick } from "vue";
import { ElMessage } from "element-plus";
import {
  chatWithAssistant,
  autoConfigureByNaturalLanguage,
} from "@/api/assistant";
import { listDatasource } from "@/api/datasource";
import { useAppStore } from "@/stores/app";
import type { AiConversation, DatasourceConfig } from "@/types/entity";
import type { AiMessage } from "@/types/assistant";

const appStore = useAppStore();
appStore.setBreadcrumb([{ title: "AI 助手" }]);

const messages = ref<AiMessage[]>([]);
const inputMessage = ref("");
const loading = ref(false);
const datasourceOptions = ref<DatasourceConfig[]>([]);
const selectedDatasourceId = ref<number | undefined>(undefined);
const sessionId = ref("");
const messageContainer = ref<HTMLDivElement | null>(null);

const activeMode = ref<"chat" | "config">("chat");
const configInput = ref("");
const configLoading = ref(false);
const configResult = ref("")

const loadDatasourceOptions = async () => {
  const res = await listDatasource();
  datasourceOptions.value = res.records || [];
};

const scrollToBottom = () => {
  nextTick(() => {
    if (messageContainer.value) {
      messageContainer.value.scrollTop = messageContainer.value.scrollHeight;
    }
  });
};

const handleSend = async () => {
  const text = inputMessage.value.trim();
  if (!text) return;

  messages.value.push({ role: "user", content: text });
  inputMessage.value = "";
  loading.value = true;
  scrollToBottom();

  try {
    const res = await chatWithAssistant({
      sessionId: sessionId.value || undefined,
      datasourceId: selectedDatasourceId.value,
      message: text,
    });
    sessionId.value = res.sessionId || "";
    if (res.messages) {
      try {
        messages.value = JSON.parse(res.messages);
      } catch (e) {
        messages.value.push({ role: "assistant", content: res.messages });
      }
    }
  } catch (e: any) {
    ElMessage.error(e?.message || "请求失败");
    messages.value.push({
      role: "assistant",
      content: "请求失败，请稍后再试。",
    });
  } finally {
    loading.value = false;
    scrollToBottom();
  }
};

const handleKeydown = (e: KeyboardEvent) => {
  if (e.key === "Enter" && !e.shiftKey) {
    e.preventDefault();
    handleSend();
  }
};

const handleAutoConfigure = async () => {
  const text = configInput.value.trim();
  if (!text) return;
  configLoading.value = true;
  configResult.value = "";
  try {
    const res = await autoConfigureByNaturalLanguage(text);
    configResult.value = res.message || "操作完成";
    if (res.success) {
      ElMessage.success(res.message);
    } else {
      ElMessage.warning(res.message);
    }
  } catch (e: any) {
    ElMessage.error(e?.message || "请求失败");
    configResult.value = "请求失败，请稍后再试。";
  } finally {
    configLoading.value = false;
  }
};

onMounted(() => {
  loadDatasourceOptions();
});
</script>

<template>
  <div class="page-card ai-chat-page">
    <div class="chat-header">
      <div class="chat-title">AI 数据助手</div>
      <div class="header-actions">
        <el-radio-group v-model="activeMode" size="small">
          <el-radio-button label="chat">AI 对话</el-radio-button>
          <el-radio-button label="config">智能配置</el-radio-button>
        </el-radio-group>
        <el-select
          v-show="activeMode === 'chat'"
          v-model="selectedDatasourceId"
          placeholder="选择数据源（可选，选择后可基于表结构对话）"
          clearable
          style="width: 320px"
        >
          <el-option
            v-for="item in datasourceOptions"
            :key="item.id"
            :label="item.name"
            :value="item.id!"
          />
        </el-select>
      </div>
    </div>

    <!-- 对话模式 -->
    <template v-if="activeMode === 'chat'">
      <div ref="messageContainer" class="chat-messages">
        <div
          v-for="(msg, index) in messages"
          :key="index"
          :class="['message', msg.role === 'user' ? 'message-user' : 'message-assistant']"
        >
          <div class="message-avatar">{{ msg.role === "user" ? "我" : "AI" }}</div>
          <div class="message-content">
            <pre>{{ msg.content }}</pre>
          </div>
        </div>
        <div v-if="loading" class="message message-assistant">
          <div class="message-avatar">AI</div>
          <div class="message-content">
            <el-text type="info">思考中...</el-text>
          </div>
        </div>
      </div>

      <div class="chat-input">
        <el-input
          v-model="inputMessage"
          type="textarea"
          :rows="3"
          placeholder="请输入问题，例如：查询上个月销售额排名前 10 的门店"
          @keydown="handleKeydown"
        />
        <el-button
          type="primary"
          :loading="loading"
          :disabled="!inputMessage.trim()"
          @click="handleSend"
          >发送</el-button
        >
      </div>
    </template>

    <!-- 智能配置模式 -->
    <template v-else>
      <div class="config-panel">
        <div class="config-description">
          用一句话描述你想要的配置，AI 将自动创建任务、网页爬取或通知规则。
        </div>
        <el-input
          v-model="configInput"
          type="textarea"
          :rows="6"
          placeholder="例如：每天早上 8 点执行一次 SQL 查询，统计昨日新增订单数量并发送邮件通知"
        />
        <div class="config-actions">
          <el-button
            type="primary"
            :loading="configLoading"
            :disabled="!configInput.trim()"
            @click="handleAutoConfigure"
            >生成配置</el-button
          >
        </div>
        <div v-if="configResult" class="config-result">
          <pre>{{ configResult }}</pre>
        </div>
      </div>
    </template>
  </div>
</template>

<style scoped lang="scss">
.ai-chat-page {
  display: flex;
  flex-direction: column;
  height: calc(100vh - 120px);
  padding: 16px;
}

.chat-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}

.header-actions {
  display: flex;
  align-items: center;
  gap: 16px;
}

.chat-title {
  font-size: 18px;
  font-weight: 600;
}

.chat-messages {
  flex: 1;
  overflow-y: auto;
  border: 1px solid var(--el-border-color);
  border-radius: 8px;
  padding: 16px;
  background: #fafafa;
  margin-bottom: 16px;
}

.config-panel {
  flex: 1;
  overflow-y: auto;
  border: 1px solid var(--el-border-color);
  border-radius: 8px;
  padding: 16px;
  background: #fafafa;
  margin-bottom: 16px;
}

.config-description {
  margin-bottom: 12px;
  color: var(--el-text-color-regular);
  font-size: 14px;
}

.config-actions {
  margin-top: 12px;
  display: flex;
  justify-content: flex-end;
}

.config-result {
  margin-top: 16px;
  padding: 12px;
  background: #fff;
  border-radius: 8px;
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.05);
}

.config-result pre {
  margin: 0;
  white-space: pre-wrap;
  word-break: break-word;
  font-family: inherit;
  font-size: 14px;
  line-height: 1.6;
}

.message {
  display: flex;
  gap: 12px;
  margin-bottom: 16px;
}

.message-user {
  flex-direction: row-reverse;
}

.message-avatar {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  background: var(--el-color-primary);
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 12px;
  flex-shrink: 0;
}

.message-user .message-avatar {
  background: var(--el-color-success);
}

.message-content {
  max-width: 70%;
  padding: 10px 14px;
  border-radius: 8px;
  background: #fff;
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.05);
}

.message-user .message-content {
  background: var(--el-color-primary-light-9);
}

.message-content pre {
  margin: 0;
  white-space: pre-wrap;
  word-break: break-word;
  font-family: inherit;
  font-size: 14px;
  line-height: 1.6;
}

.chat-input {
  display: flex;
  gap: 12px;
  align-items: flex-end;
}

.chat-input .el-input {
  flex: 1;
}

.chat-input .el-button {
  height: 74px;
  width: 80px;
}
</style>
