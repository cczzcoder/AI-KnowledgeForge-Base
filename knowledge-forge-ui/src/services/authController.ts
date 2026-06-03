import request from './request';

export async function login(body: API.UserLoginVO, options?: Record<string, unknown>) {
  return request.post<API.BaseResponseAuthVO>('/auth/login', body, options);
}

export async function userInfo(options?: Record<string, unknown>) {
  return request.get<API.BaseResponseAuthVO>('/auth/userInfo', options);
}

export default { login, userInfo };