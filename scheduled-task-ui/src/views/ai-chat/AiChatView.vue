<script setup lang="ts">
import { ref, onMounted, nextTick } from "vue";
import { ElMessage } from "element-plus";
import { marked } from "marked";
import DOMPurify from "dompurify";
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
  datasourceOptions.value = (res.records || []).filter((d) => d.status === 1);
  // 默认选中第一个可用数据源，使对话自动基于该数据源的数据字典生成 SQL、执行并返回结果
  if (selectedDatasourceId.value === undefined && datasourceOptions.value.length > 0) {
    const firstId = datasourceOptions.value[0].id;
    if (firstId != null) {
      selectedDatasourceId.value = firstId;
    }
  }
};

const scrollToBottom = () => {
  nextTick(() => {
    if (messageContainer.value) {
      messageContainer.value.scrollTop = messageContainer.value.scrollHeight;
    }
  });
};

const renderMarkdown = (content: string) => {
  const raw = marked.parse(content || "", { breaks: true, gfm: true }) as string;
  return DOMPurify.sanitize(raw, { ADD_TAGS: ["details", "summary"] });
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
          placeholder="选择数据源（将基于其数据字典自动生成 SQL）"
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
            <pre v-if="msg.role === 'user'">{{ msg.content }}</pre>
            <div
              v-else
              class="markdown-body"
              v-html="renderMarkdown(msg.content)"
            />
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
  border: 2px solid var(--el-border-color);
  border-radius: 8px;
  padding: 20px 24px;
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
  max-width: 80%;
  min-width: 60px;
  padding: 10px 14px;
  border-radius: 8px;
  background: #fff;
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.05);
  overflow-wrap: break-word;
  word-break: break-word;
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

.markdown-body {
  font-size: 14px;
  line-height: 1.6;
  color: var(--el-text-color-primary);
  overflow-wrap: break-word;
  word-break: break-word;
}

.markdown-body :first-child {
  margin-top: 0;
}

.markdown-body :last-child {
  margin-bottom: 0;
}

.markdown-body p {
  margin: 0 0 8px;
}

.markdown-body h1,
.markdown-body h2,
.markdown-body h3,
.markdown-body h4,
.markdown-body h5,
.markdown-body h6 {
  margin: 12px 0 8px;
  font-weight: 600;
  line-height: 1.4;
}

.markdown-body h1 { font-size: 20px; }
.markdown-body h2 { font-size: 18px; }
.markdown-body h3 { font-size: 16px; }
.markdown-body h4,
.markdown-body h5,
.markdown-body h6 { font-size: 14px; }

.markdown-body ul,
.markdown-body ol {
  margin: 8px 0;
  padding-left: 18px;
  list-style-position: inside;
}

.markdown-body li {
  margin: 4px 0;
}

.markdown-body code {
  padding: 2px 6px;
  background: var(--el-fill-color-light);
  border-radius: 4px;
  font-family: "SFMono-Regular", Consolas, "Liberation Mono", Menlo, monospace;
  font-size: 13px;
  word-break: break-word;
}

.markdown-body pre {
  padding: 12px;
  background: #f4f4f5;
  border-radius: 6px;
  overflow-x: auto;
  margin: 8px 0;
  max-width: 100%;
}

.markdown-body pre code {
  padding: 0;
  background: transparent;
  word-break: break-all;
}

.markdown-body img {
  max-width: 100%;
  height: auto;
  border-radius: 6px;
  margin: 8px 0;
}

.markdown-body blockquote {
  margin: 8px 0;
  padding: 4px 12px;
  border-left: 4px solid var(--el-border-color);
  color: var(--el-text-color-secondary);
  background: var(--el-fill-color-light);
  border-radius: 0 4px 4px 0;
}

.markdown-body table {
  width: 100%;
  max-width: 100%;
  border-collapse: collapse;
  margin: 8px 0;
  font-size: 13px;
  display: block;
  overflow-x: auto;
}

.markdown-body th,
.markdown-body td {
  border: 1px solid var(--el-border-color);
  padding: 6px 10px;
  text-align: left;
  white-space: nowrap;
}

.markdown-body th {
  background: var(--el-fill-color-light);
  font-weight: 600;
}

.markdown-body a {
  color: var(--el-color-primary);
  text-decoration: none;
  word-break: break-all;
}

.markdown-body a:hover {
  text-decoration: underline;
}

.markdown-body details.sql-block {
  margin: 14px 0 2px;
}

.markdown-body details.sql-block summary {
  display: flex;
  align-items: center;
  justify-content: space-between;
  cursor: pointer;
  padding-top: 10px;
  margin-top: 12px;
  border-top: 1px solid var(--el-border-color);
  font-size: 13px;
  color: var(--el-text-color-secondary);
  user-select: none;
  list-style: none;
}

.markdown-body details.sql-block summary::-webkit-details-marker {
  display: none;
}

.markdown-body details.sql-block summary::after {
  content: "▾";
  font-size: 12px;
  color: var(--el-text-color-placeholder);
  transition: transform 0.15s ease;
}

.markdown-body details.sql-block[open] summary::after {
  transform: rotate(180deg);
}

.markdown-body details.sql-block pre {
  margin: 10px 0 0;
  padding: 12px;
  border: 1px solid var(--el-border-color);
  border-radius: 6px;
  background: #f6f8fa;
  overflow-x: auto;
}

.markdown-body details.sql-block pre code {
  font-family: "SFMono-Regular", Consolas, "Liberation Mono", Menlo, monospace;
  font-size: 13px;
  line-height: 1.6;
  background: transparent;
  white-space: pre;
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
