import axios from 'axios';
import type { ApiResponse } from './typings.d';

// 服务不可用状态回调管理
type ServiceStatusCallback = (unavailable: boolean) => void;
const serviceStatusCallbacks: Set<ServiceStatusCallback> = new Set();

/** 注册服务状态变化回调，返回取消注册函数 */
export function onServiceStatusChange(
  callback: ServiceStatusCallback,
): () => void {
  serviceStatusCallbacks.add(callback);
  return () => serviceStatusCallbacks.delete(callback);
}

/** 手动触发健康检查（使用轻量端点避免不必要的数据库查询） */
export async function checkServiceHealth(): Promise<boolean> {
  try {
    const resp = await axios.get('/api/health', { timeout: 5000 });
    const available = resp.data?.status === 'UP';
    notifyServiceStatus(!available);
    return available;
  } catch {
    notifyServiceStatus(true);
    return false;
  }
}

function notifyServiceStatus(unavailable: boolean) {
  serviceStatusCallbacks.forEach((cb) => cb(unavailable));
}

const request = axios.create({
  baseURL: '/api/v1',
  timeout: 60000,
  headers: {
    'Content-Type': 'application/json',
  },
});

request.interceptors.response.use(
  (response) => {
    if (response.config.responseType === 'blob') {
      return response;
    }
    const res = response.data as ApiResponse<unknown>;
    if (res.code === 200) {
      // 每次成功请求都确认服务可用
      notifyServiceStatus(false);
      return response;
    }
    return Promise.reject(new Error(res.message || '请求失败'));
  },
  (error) => {
    // 网络错误 / 超时 / 5xx 服务端错误 → 标记服务不可用
    if (
      !error.response ||
      error.code === 'ERR_NETWORK' ||
      error.code === 'ECONNABORTED' ||
      (error.response && error.response.status >= 500)
    ) {
      notifyServiceStatus(true);
    }
    return Promise.reject(error);
  },
);

export default request;
