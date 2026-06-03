import { Alert, Button, Space, Typography, Divider } from 'antd';
import {
  ReloadOutlined,
  WifiOutlined,
  ClockCircleOutlined,
  CustomerServiceOutlined,
  ExclamationCircleOutlined,
} from '@ant-design/icons';
import { useState, useRef, useEffect, useCallback } from 'react';
import './index.css';

const { Text, Title } = Typography;

interface ServiceUnavailableBannerProps {
  onRetry?: () => void;
}

/**
 * 服务不可用提示横幅。
 * 当后端服务无法连接时在页面顶部显示，提供：
 * - 持续的故障计时
 * - 重试连接按钮
 * - 常见故障排查建议
 * - 联系管理员的方式
 */
function ServiceUnavailableBanner({ onRetry }: ServiceUnavailableBannerProps) {
  const [retrying, setRetrying] = useState(false);
  const [visible, setVisible] = useState(false);
  const [downtimeMinutes, setDowntimeMinutes] = useState(0);
  const startTimeRef = useRef(Date.now());
  const timerRef = useRef<ReturnType<typeof setInterval> | null>(null);

  // 入场动画
  useEffect(() => {
    startTimeRef.current = Date.now();
    // 延迟一帧触发 CSS transition
    requestAnimationFrame(() => setVisible(true));

    // 每分钟更新一次已持续时长
    timerRef.current = setInterval(() => {
      setDowntimeMinutes(Math.floor((Date.now() - startTimeRef.current) / 60000));
    }, 10000);

    return () => {
      if (timerRef.current) {
        clearInterval(timerRef.current);
      }
    };
  }, []);

  const handleRetry = useCallback(() => {
    setRetrying(true);
    onRetry?.();
    // 2s 后重置按钮状态，让健康检查结果自然驱动 UI
    setTimeout(() => setRetrying(false), 2000);
  }, [onRetry]);

  const timeText =
    downtimeMinutes === 0
      ? '刚刚'
      : downtimeMinutes === 1
        ? '约 1 分钟'
        : `约 ${downtimeMinutes} 分钟`;

  return (
    <div className={`service-unavailable-wrapper ${visible ? 'visible' : ''}`}>
      <Alert
        type="error"
        showIcon
        icon={<WifiOutlined className="pulse-icon" />}
        className="service-unavailable-alert"
        message={
          <Space size={4} wrap>
            <Text strong className="banner-title">
              服务暂时不可用
            </Text>
            <Text type="secondary" className="banner-downtime">
              <ClockCircleOutlined style={{ marginRight: 3 }} />
              已持续 {timeText}
            </Text>
          </Space>
        }
        description={
          <div className="banner-description">
            <Text type="secondary">
              <ExclamationCircleOutlined style={{ marginRight: 4 }} />
              后端服务可能正在维护、重启或负载过高，您的请求暂时无法处理。
            </Text>

            <Divider style={{ margin: '8px 0' }} />

            <div className="troubleshooting">
              <Title level={5} style={{ margin: '0 0 4px', fontSize: 13 }}>
                故障排查建议
              </Title>
              <ul>
                <li>检查您的网络连接是否正常</li>
                <li>稍等片刻后点击"重试连接"按钮</li>
                <li>如长时间未恢复（超过 5 分钟），请联系系统管理员</li>
              </ul>
            </div>

            <div className="contact-support">
              <CustomerServiceOutlined style={{ marginRight: 4 }} />
              <Text type="secondary">
                联系支持：
                <Text code style={{ marginLeft: 4 }}>admin@knowledgeforge.com</Text>
              </Text>
            </div>
          </div>
        }
        action={
          <Button
            size="small"
            type="primary"
            danger
            ghost
            icon={<ReloadOutlined spin={retrying} />}
            loading={retrying}
            onClick={handleRetry}
            className="retry-button"
          >
            重试连接
          </Button>
        }
        banner
      />
    </div>
  );
}

export default ServiceUnavailableBanner;