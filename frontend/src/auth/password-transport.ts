import { getJson } from '../api/axios-client'

interface PasswordPublicKeyResponse {
  enabled: boolean
  keyId: string | null
  algorithm: 'RSA-OAEP-256'
  publicKey: string | null
}

export type PasswordPayload =
  | { password: string }
  | { passwordCiphertext: string; passwordKeyId: string }

let publicKeyPromise: Promise<PasswordPublicKeyResponse> | null = null
const textEncoder = new TextEncoder()

export async function encryptPasswordIfAvailable(password: string): Promise<PasswordPayload> {
  const passwordKey = await getPasswordKey()
  if (!passwordKey.enabled) return { password }
  if (!globalThis.crypto?.subtle) {
    throw new Error('当前浏览器不支持密码加密，请升级浏览器后再登录')
  }
  if (!passwordKey.publicKey || !passwordKey.keyId) {
    throw new Error('服务器登录密码公钥配置不完整')
  }

  try {
    const key = await globalThis.crypto.subtle.importKey(
      'spki',
      fromBase64(passwordKey.publicKey),
      { name: 'RSA-OAEP', hash: 'SHA-256' },
      false,
      ['encrypt'],
    )
    const encrypted = await globalThis.crypto.subtle.encrypt(
      { name: 'RSA-OAEP' },
      key,
      textEncoder.encode(password),
    )
    return {
      passwordCiphertext: toBase64Url(new Uint8Array(encrypted)),
      passwordKeyId: passwordKey.keyId,
    }
  } catch (error) {
    publicKeyPromise = null
    throw error
  }
}

async function getPasswordKey() {
  publicKeyPromise ??= getJson<PasswordPublicKeyResponse>('/api/v1/auth/password-key').catch(() => ({
    enabled: false,
    keyId: null,
    algorithm: 'RSA-OAEP-256',
    publicKey: null,
  }))
  return publicKeyPromise
}

export function clearPasswordPublicKeyCache() {
  publicKeyPromise = null
}

function fromBase64(value: string) {
  return Uint8Array.from(atob(value), (char) => char.charCodeAt(0))
}

function toBase64Url(bytes: Uint8Array) {
  const binary = Array.from(bytes, (byte) => String.fromCharCode(byte)).join('')
  return btoa(binary).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/u, '')
}
