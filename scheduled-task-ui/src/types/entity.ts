export interface TaskConfig {
  id?: number
  taskName: string
  taskCode: string
  description?: string
  triggerType: 'CRON' | 'ONCE'
  triggerConfig: string
  status: 'ENABLE' | 'DISABLE'
  taskType?: 'SQL' | 'CRAWL'
  sortOrder?: number
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
  templateCode?: string
  groupId?: number
  groupCode?: string
  outputFormat?: string
  chartEnabled?: number
  chartType?: string
  chartTitle?: string
  chartAutoMerge?: number
  chartLabelRotation?: string
  chartBackgroundColor?: string
  excelMergeGroup?: string
  excelSheetName?: string
  excelLoopEnabled?: number
  excelLoopConfig?: string
  excelAppendMode?: number
  excelBaseFilePath?: string
  excelAppendUpdateSameSheet?: number
  excelAppendPosition?: number
  fileSuffix?: string
  fileNamePattern?: string
  customParams?: string
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
  configCode?: string
  taskId?: number
  taskCode?: string
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

export interface TaskWebCrawlConfig {
  id?: number
  crawlName: string
  crawlCode: string
  requestUrl: string
  requestMethod?: string
  requestHeaders?: string
  requestParams?: string
  requestBody?: string
  requestContentType?: string
  cookies?: string
  authType?: 'NONE' | 'BASIC' | 'FORM' | 'TOKEN' | 'OAUTH2'
  authConfig?: string
  sshEnabled?: number
  sshHost?: string
  sshPort?: number
  sshUsername?: string
  sshPassword?: string
  sshPrivateKey?: string
  sshPassphrase?: string
  sshAuthType?: 'PASSWORD' | 'KEY'
  sshRemoteHost?: string
  sshRemotePort?: number
  sshLocalPort?: number
  sshJumpHostEnabled?: number
  sshHops?: SshHopConfig[]
  proxyEnabled?: number
  proxyHost?: string
  proxyPort?: number
  proxyUsername?: string
  proxyPassword?: string
  renderType?: 'STATIC' | 'DYNAMIC'
  driverConfig?: string
  outputFormat?: string
  templateId?: number
  templateCode?: string
  fileSuffix?: string
  fileNamePattern?: string
  excelSheetName?: string
  description?: string
  customParams?: string
  status?: number
  paginationEnabled?: number
  paginationType?: string
  paginationSelector?: string
  paginationUrlTemplate?: string
  paginationMaxPages?: number
  mediaEnabled?: number
  mediaSelector?: string
  mediaFileTypes?: string
  mediaStorageConfigId?: number
  mediaOutputMode?: 'ATTACH' | 'ZIP' | 'STORE_ONLY' | 'ATTACH_ZIP'
  mediaZipNamePattern?: string
  mediaFilterConfig?: string
  chartEnabled?: number
  chartType?: string
  chartTitle?: string
  chartAutoMerge?: number
  chartLabelRotation?: string
  chartBackgroundColor?: string
  previewSelectorEnabled?: number
  selectors?: TaskWebCrawlSelector[]
  sortOrder?: number
  createTime?: string
  updateTime?: string
}

export interface SshHopConfig {
  host: string
  port?: number
  username: string
  password?: string
  privateKey?: string
  passphrase?: string
  authType?: 'PASSWORD' | 'KEY'
}

export interface TaskWebCrawlSelector {
  id?: number
  crawlConfigId?: number
  crawlCode?: string
  fieldName?: string
  isRowSelector?: number
  selectorType: 'CSS' | 'XPATH' | 'REGEX'
  selectorValue: string
  attribute?: string
  dataType?: 'STRING' | 'NUMBER' | 'DATE' | 'LINK' | 'HTML'
  defaultValue?: string
  sortOrder?: number
  createTime?: string
  updateTime?: string
}

export interface TaskConfigRequest {
  task: TaskConfig
  sqlCodes?: string[]
  crawlCodes?: string[]
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
  configCode?: string
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
