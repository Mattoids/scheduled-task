export interface TaskConfig {
  id?: number
  taskName: string
  taskCode: string
  description?: string
  triggerType: 'CRON' | 'ONCE'
  triggerConfig: string
  status: 'ENABLE' | 'DISABLE'
  createTime?: string
  updateTime?: string
}

export interface TaskSqlConfig {
  id?: number
  sqlName: string
  sqlCode: string
  datasourceId: number
  sqlContent: string
  templateId?: number
  groupId?: number
  outputFormat?: string
  chartEnabled?: number
  chartType?: string
  chartTitle?: string
  fileSuffix?: string
  fileNamePattern?: string
  description?: string
  status?: number
  sortOrder?: number
  taskSqlGroup?: TaskSqlGroup
  groupName?: string
  createTime?: string
  updateTime?: string
}

export interface TaskSqlGroup {
  id?: number
  groupName: string
  groupCode: string
  fileNamePattern?: string
  description?: string
  status?: number
  createTime?: string
  updateTime?: string
}

export type NotificationChannel =
  | 'EMAIL'
  | 'WECOM_APP'
  | 'WECOM_BOT'
  | 'WECOM_INTELLIGENT_BOT'
  | 'DINGTALK'
  | 'FEISHU'
  | 'SLACK'
  | 'WEBHOOK'

export interface NotificationRule {
  id?: number
  eventType: 'TASK_SUCCESS' | 'TASK_FAILURE' | 'TASK_COMPLETED'
  channel: NotificationChannel
  configId?: number
  taskId?: number
  recipientIds?: string
  recipientGroupIds?: string
  wecomToUser?: string
  subject?: string
  body?: string
  content?: string
  aiOptimizeNotify?: number
  aiConfigId?: number
  storageConfigId?: number
  enabled?: number
  createTime?: string
  updateTime?: string
}

export interface TaskConfigRequest {
  task: TaskConfig
  sqlIds?: number[]
}

export interface TaskLog {
  id?: number
  taskId: number
  triggerMode?: 'MANUAL' | 'AUTO'
  startTime?: string
  endTime?: string
  status?: 'RUNNING' | 'SUCCESS' | 'FAILED'
  resultMessage?: string
  errorMessage?: string
  filePath?: string
  createTime?: string
}

export interface DatasourceConfig {
  id?: number
  name: string
  dbType: string
  host: string
  port: number
  databaseName: string
  username: string
  password?: string
  driverClass?: string
  jdbcUrlParams?: string
  sshEnabled?: number
  sshHost?: string
  sshPort?: number
  sshUsername?: string
  sshPassword?: string
  sshPrivateKey?: string
  sshPassphrase?: string
  sshLocalPort?: number
  sshAuthType?: 'password' | 'key'
  remark?: string
  status?: number
  createTime?: string
}

export interface EmailConfig {
  id?: number
  configName: string
  smtpHost: string
  smtpPort: number
  username: string
  password?: string
  fromAddress: string
  fromName?: string
  auth?: number
  starttls?: number
  ssl?: number
  status?: number
  createTime?: string
}

export interface EmailRecipientGroup {
  id?: number
  groupName: string
  groupCode: string
  description?: string
  status?: number
  createTime?: string
}

export interface EmailRecipient {
  id?: number
  recipientName?: string
  email: string
  groupIds?: number[]
  status?: number
  createTime?: string
}

export interface ReportTemplate {
  id?: number
  templateName: string
  templateCode: string
  templateType: string
  filePath?: string
  fileName?: string
  description?: string
  status?: number
  createTime?: string
}

export interface WeComAppConfig {
  id?: number
  configName: string
  corpId: string
  agentId: number
  secret?: string
  token?: string
  aesKey?: string
  proxyUrl?: string
  status?: number
  menuJson?: string
  createTime?: string
  updateTime?: string
}

export interface WeComBotConfig {
  id?: number
  configName: string
  webhookKey: string
  status?: number
  createTime?: string
  updateTime?: string
}

export interface WeComIntelligentBotConfig {
  id?: number
  configName: string
  mode?: 'LONGCHAIN' | 'CALLBACK'
  corpId?: string
  agentId?: number
  secret?: string
  botId?: string
  botSecret?: string
  token?: string
  aesKey?: string
  status?: number
  createTime?: string
  updateTime?: string
}

export interface DingTalkConfig {
  id?: number
  configName: string
  webhookUrl: string
  secret?: string
  atMobiles?: string
  atAll?: number
  status?: number
  createTime?: string
  updateTime?: string
}

export interface FeishuConfig {
  id?: number
  configName: string
  webhookUrl: string
  secret?: string
  status?: number
  createTime?: string
  updateTime?: string
}

export interface SlackConfig {
  id?: number
  configName: string
  webhookUrl: string
  channel?: string
  username?: string
  status?: number
  createTime?: string
  updateTime?: string
}

export interface WebhookConfig {
  id?: number
  configName: string
  url: string
  method?: 'GET' | 'POST' | 'PUT'
  headers?: Record<string, string>
  bodyTemplate?: string
  timeoutSeconds?: number
  status?: number
  createTime?: string
  updateTime?: string
}

export interface NotificationConfig {
  id?: number
  configName: string
  configType: NotificationChannel
  configJson: any
  status?: number
  createTime?: string
  updateTime?: string
}

export interface AiConfig {
  id?: number
  configName: string
  provider: 'OPENAI' | 'ANTHROPIC' | 'AZURE_OPENAI' | 'OLLAMA' | 'CUSTOM'
  apiKey?: string
  baseUrl?: string
  model?: string
  temperature?: number
  maxTokens?: number
  timeoutSeconds?: number
  isDefault?: number
  status?: number
  systemPrompt?: string
  remark?: string
  createTime?: string
  updateTime?: string
}

export interface StorageConfig {
  id?: number
  configName: string
  storageType: 'LOCAL' | 'OSS' | 'S3' | 'WEBDAV'
  configJson: any
  status?: number
  isDefault?: number
  createTime?: string
  updateTime?: string
}

export interface SysUser {
  id?: number
  username: string
  password?: string
  nickname?: string
  email?: string
  phone?: string
  status?: number
  createTime?: string
}

export interface SysRole {
  id?: number
  roleCode: string
  roleName: string
  description?: string
  status?: number
  createTime?: string
}

export interface SysPermission {
  id?: number
  permissionCode: string
  permissionName: string
  resourceType?: string
  parentId?: number
  sortOrder?: number
  path?: string
  status?: number
  createTime?: string
}
