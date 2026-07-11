import type { MessageInstance } from 'antd/es/message/interface';

/** 全局 message 实例，由 App 组件初始化，供非组件代码（如 http 拦截器）调用。 */
let msg: MessageInstance | null = null;

export function setGlobalMessage(instance: MessageInstance) {
  msg = instance;
}

export function getGlobalMessage(): MessageInstance | null {
  return msg;
}
