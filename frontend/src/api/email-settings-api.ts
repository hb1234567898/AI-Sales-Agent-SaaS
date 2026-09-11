import { getJson, requestJson } from './axios-client'

export type EmailSettingsStatusCode = 'READY' | 'ENV_FALLBACK' | 'MISSING_CONFIGURATION' | 'ENCRYPTION_KEY_UNAVAILABLE'

export interface EmailSettingsStatus {
  host: string | null
  port: number | null
  username: string | null
  fromAddress: string | null
  smtpAuth: boolean
  starttlsEnabled: boolean
  starttlsRequired: boolean
  passwordConfigured: boolean
  ready: boolean
  status: EmailSettingsStatusCode
}

export interface EmailSettingsTestResult {
  status: 'CONNECTED'
  message: string
  latencyMs: number
}

export interface EmailSettingsUpdateInput {
  host: string
  port: number
  username?: string
  password?: string
  fromAddress: string
  smtpAuth: boolean
  starttlsEnabled: boolean
  starttlsRequired: boolean
}

export function getEmailSettingsStatus() {
  return getJson<EmailSettingsStatus>('/api/v1/email/settings')
}

export function saveEmailSettings(input: EmailSettingsUpdateInput) {
  return requestJson<EmailSettingsStatus>('/api/v1/email/settings', {
    method: 'PUT',
    data: input,
  })
}

export function testEmailSettingsConnection() {
  return requestJson<EmailSettingsTestResult>('/api/v1/email/settings/test', { method: 'POST' })
}
