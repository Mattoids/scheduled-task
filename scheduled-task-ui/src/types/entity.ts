export interface TaskConfig {
  id?: number
  taskName: string
  taskCode: string
  description?: string
  triggerType: 'CRON' | 'ONCE'
  triggerConfig: string
  emailConfigId: number
  recipientIds?: string
  recipientGroupIds?: string
  status: 'ENABLE' | 'DISABLE'
  fileNamePattern?: string
  emailSubject?: string
  emailBody?: string
  weComAppConfigId?: number
  weComBotConfigId?: number
  weComToUser?: string
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
  outputFormat?: string
  fileSuffix?: string
  fileNamePattern?: string
  groupName?: string
  description?: string
  status?: number
  sortOrder?: number
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
  groupId?: number
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
