import { apiClient } from '../../api/client'

export interface LoginRequest {
  email: string
  password: string
}

export interface AuthResponse {
  accessToken: string
  tokenType: string
  expiresIn: number
}

export interface RegisterRequest extends LoginRequest {
  displayName: string
}

export interface RegisterResponse {
  id: number
  email: string
  displayName: string
  role: 'CUSTOMER'
  createdAt: string
}

export async function login(request: LoginRequest): Promise<AuthResponse> {
  return (await apiClient.post<AuthResponse>('/auth/login', request)).data
}

export async function register(request: RegisterRequest): Promise<RegisterResponse> {
  return (await apiClient.post<RegisterResponse>('/auth/register', request)).data
}
