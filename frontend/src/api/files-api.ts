import { apiClient, getJson } from './axios-client'

export interface UploadedFile {
  id: string
  customerId: string | null
  filename: string
  contentType: string
  sizeBytes: number
  sha256: string
  createdAt: string
}

export async function getCustomerFiles(customerId: string) {
  return getJson<UploadedFile[]>('/api/v1/files', { params: { customerId } })
}

export async function uploadCustomerFile(customerId: string, file: File) {
  const form = new FormData()
  form.append('customerId', customerId)
  form.append('file', file)
  const response = await apiClient.post<UploadedFile>('/api/v1/files', form)
  return response.data
}
