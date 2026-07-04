<script setup lang="ts">
import type { StageResult } from "@/types/index";

interface Props {
  stages: StageResult[];
  sshEnabled: boolean;
}

const props = defineProps<Props>();

const getStatus = (stages: StageResult[], key: string) => {
  const found = stages.find((s) => s.stage === key);
  if (!found) return "pending";
  return found.success ? "success" : "error";
};

const clientStatus = () => {
  return props.stages.length > 0 ? "success" : "pending";
};
</script>

<template>
  <div class="connection-test-progress">
    <div class="progress-track">
      <div class="progress-node" :class="clientStatus()">
        <span class="node-dot"></span>
      </div>

      <div
        class="progress-line"
        :class="
          sshEnabled ? getStatus(stages, 'SSH') : getStatus(stages, 'DATABASE')
        "
      ></div>

      <template v-if="sshEnabled">
        <div class="progress-node" :class="getStatus(stages, 'SSH')">
          <span class="node-dot"></span>
        </div>
        <div class="progress-line" :class="getStatus(stages, 'DATABASE')"></div>
      </template>

      <div class="progress-node" :class="getStatus(stages, 'DATABASE')">
        <span class="node-dot"></span>
      </div>
    </div>

    <div class="progress-labels">
      <span class="label">客户端</span>
      <span
        v-if="sshEnabled"
        class="label"
        :class="{ 'is-error': getStatus(stages, 'SSH') === 'error' }"
        >SSH 服务器</span
      >
      <span
        class="label"
        :class="{ 'is-error': getStatus(stages, 'DATABASE') === 'error' }"
        >数据库</span
      >
    </div>

    <div v-if="stages.length > 0" class="progress-messages">
      <div
        v-for="stage in stages"
        :key="stage.stage"
        class="stage-message"
        :class="stage.success ? 'is-success' : 'is-error'"
      >
        {{ stage.stage === "SSH" ? "SSH 服务器" : "数据库" }}：
        {{ stage.success ? "连接成功" : stage.message || "连接失败" }}
      </div>
    </div>
  </div>
</template>

<style scoped>
.connection-test-progress {
  margin: 16px 0;
  padding: 16px;
  background-color: #f5f7fa;
  border-radius: 4px;
}

.progress-track {
  display: flex;
  align-items: center;
  justify-content: space-between;
  position: relative;
  height: 24px;
}

.progress-line {
  flex: 1;
  height: 4px;
  background-color: #dcdfe6;
  border-radius: 2px;
  transition: background-color 0.3s;
  margin: 0 4px;
}

.progress-line.success {
  background-color: #67c23a;
}

.progress-line.error {
  background-color: #f56c6c;
}

.progress-node {
  width: 24px;
  height: 24px;
  border-radius: 50%;
  background-color: #fff;
  border: 2px solid #dcdfe6;
  display: flex;
  align-items: center;
  justify-content: center;
  transition:
    border-color 0.3s,
    background-color 0.3s;
  flex-shrink: 0;
}

.progress-node.success {
  border-color: #67c23a;
  background-color: #67c23a;
}

.progress-node.error {
  border-color: #f56c6c;
  background-color: #f56c6c;
}

.node-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background-color: #fff;
}

.progress-labels {
  display: flex;
  justify-content: space-between;
  margin-top: 8px;
  color: #606266;
  font-size: 13px;
}

.progress-labels .label.is-error {
  color: #f56c6c;
}

.progress-messages {
  margin-top: 12px;
  padding-top: 12px;
  border-top: 1px solid #e4e7ed;
}

.stage-message {
  font-size: 13px;
  line-height: 1.8;
}

.stage-message.is-success {
  color: #67c23a;
}

.stage-message.is-error {
  color: #f56c6c;
}
</style>
