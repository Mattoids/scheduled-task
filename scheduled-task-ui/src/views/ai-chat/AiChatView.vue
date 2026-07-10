<script setup lang="ts">
import { ref, reactive, onMounted, nextTick } from "vue";
import { ElMessage } from "element-plus";
import { chatWithAssistant } from "@/api/assistant";
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

onMounted(() => {
  loadDatasourceOptions();
});
</script>

<template>
  <div class="page-card ai-chat-page">
    <div class="chat-header">
      <div class="chat-title">AI 数据助手</div>
      <el-select
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
