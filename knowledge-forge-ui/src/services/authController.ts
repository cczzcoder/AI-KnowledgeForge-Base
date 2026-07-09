import request from './request';
import type { ApiResponse } from './typings.d';

export interface UserLoginDTO {
  username: string;
  password: string;
}

export interface AuthVO {
  username?: string;
  token?: string;
  roles?: string[];
}

export async function login(
  body: UserLoginDTO,
  options?: Record<string, unknown>,
) {
  return request.post<ApiResponse<AuthVO>>('/auth/login', body, options);
}

export async function userInfo(options?: Record<string, unknown>) {
  return request.get<ApiResponse<AuthVO>>('/auth/userInfo', options);
}

export default { login, userInfo };
