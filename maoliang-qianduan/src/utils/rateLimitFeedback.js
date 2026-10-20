import { ElMessage } from 'element-plus';

let lastNotice = 0;
export function notifyRateLimit(message, retryAfter) {
  const now = Date.now();
  if (now - lastNotice < 2000) return;
  lastNotice = now;
  const seconds = Math.max(1, Number(retryAfter) || 1);
  ElMessage.warning(message || `操作太频繁，请在 ${seconds} 秒后重试`);
}
