import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import { ConfigProvider, Layout, Menu, Typography, theme, App as AntApp, Avatar, Dropdown } from 'antd';
import {
  RobotOutlined,
  BookOutlined,
  UserOutlined,
  MenuFoldOutlined,
  MenuUnfoldOutlined,
  LogoutOutlined,
  SettingOutlined,
  CompassOutlined,
  IdcardOutlined,
} from '@ant-design/icons';
import { useNavigate, useLocation } from 'react-router-dom';
import { useState, useCallback, useEffect } from 'react';
import zhCN from 'antd/locale/zh_CN';
import ThemeSwitcher from '@/component/ThemeSwitcher';
import ServiceUnavailableBanner from '@/component/ServiceUnavailableBanner/ServiceUnavailableBanner';
import { onServiceStatusChange, checkServiceHealth } from '@/services/request';
import ChatPage from '@/pages/Chat';
import KnowledgeBasePage from '@/pages/KnowledgeBase';
import DocumentPage from '@/pages/Document';
import GraphPage from '@/pages/Graph';
import DiscoveryPage from '@/pages/Discovery';
import KnowledgeCardPage from '@/pages/KnowledgeCard';
import LoginPage from '@/pages/Login';
import NotFoundPage from '@/pages/404';
import logoWhite from '@/assets/images/logo-white.jpg';
import logoBlack from '@/assets/images/logo-black.jpg';
import './index.css';

const { Header, Content } = Layout;
const { darkAlgorithm, defaultAlgorithm } = theme;

interface AppLayoutProps {
  themeMode: 'light' | 'dark';
  onThemeChange: (mode: 'light' | 'dark') => void;
}

function AppLayout({ themeMode, onThemeChange }: AppLayoutProps) {
  const navigate = useNavigate();
  const location = useLocation();
  const { message } = AntApp.useApp();
  const [sidebarCollapsed, setSidebarCollapsed] = useState(false);
  const [serviceUnavailable, setServiceUnavailable] = useState(false);

  // 订阅服务状态变化
  useEffect(() => {
    const unsubscribe = onServiceStatusChange((unavailable) => {
      setServiceUnavailable(unavailable);
    });
    return unsubscribe;
  }, []);

  // 重试连接
  const handleRetryConnection = useCallback(() => {
    checkServiceHealth();
  }, []);

  const isDark = themeMode === 'dark';
  const isChatPage = location.pathname === '/chat' || location.pathname === '/';
  const isDocumentPage = location.pathname.startsWith('/knowledge-base/');
  const isDiscoveryPage = location.pathname.startsWith('/discovery');
  const isKnowledgeCardPage = location.pathname.startsWith('/knowledge-cards');
  const isLoginPage = location.pathname === '/login';

  const menuItems = [
    {
      key: '/chat',
      icon: <RobotOutlined />,
      label: 'AI 对话',
    },
    {
      key: '/knowledge-base',
      icon: <BookOutlined />,
      label: '知识库',
    },
    {
      key: '/discovery',
      icon: <CompassOutlined />,
      label: '主动发现',
    },
    {
      key: '/knowledge-cards',
      icon: <IdcardOutlined />,
      label: '知识卡片',
    },
  ];

  const userMenuItems = [
    {
      key: 'profile',
      icon: <UserOutlined />,
      label: '个人中心',
    },
    {
      key: 'settings',
      icon: <SettingOutlined />,
      label: '设置',
    },
    { type: 'divider' as const },
    {
      key: 'login',
      icon: <LogoutOutlined />,
      label: '登录 / 切换账号',
    },
  ];

  const handleUserMenuClick = ({ key }: { key: string }) => {
    if (key === 'profile') {
      message.info('个人中心功能开发中，敬请期待');
    } else if (key === 'settings') {
      message.info('设置功能开发中，敬请期待');
    } else if (key === 'login') {
      navigate('/login');
    }
  };

  if (isLoginPage) {
    return (
      <Routes>
        <Route path="/login" element={<LoginPage />} />
        <Route path="*" element={<Navigate to="/login" replace />} />
      </Routes>
    );
  }

  return (
    <Layout className={`app-layout ${isDark ? 'dark' : 'light'}`} style={{ height: '100vh' }}>
      {serviceUnavailable && (
        <ServiceUnavailableBanner onRetry={handleRetryConnection} />
      )}
      <Header
        className="app-header"
        style={{
          display: 'flex',
          alignItems: 'center',
          background: isDark ? '#1a1a2e' : '#fff',
          borderBottom: `1px solid ${isDark ? '#2d2d3f' : '#f0f0f0'}`,
          padding: '0 20px',
          height: 52,
          zIndex: 100,
        }}
      >
        {isChatPage && (
          <div
            className="sidebar-toggle"
            onClick={() => setSidebarCollapsed(!sidebarCollapsed)}
            style={{
              fontSize: 18,
              cursor: 'pointer',
              marginRight: 16,
              color: isDark ? '#8b8b9e' : '#666',
              display: 'flex',
              alignItems: 'center',
              transition: 'color 0.2s',
            }}
          >
            {sidebarCollapsed ? <MenuUnfoldOutlined /> : <MenuFoldOutlined />}
          </div>
        )}
        <div
          className="app-logo"
          style={{
            display: 'flex',
            alignItems: 'center',
            gap: 10,
            cursor: 'pointer',
          }}
          onClick={() => navigate('/chat')}
        >
          <img
            src={isDark ? logoWhite : logoBlack}
            alt="知否"
            style={{ height: 32, width: 'auto', objectFit: 'contain' }}
          />
          <Typography.Title
            level={5}
            style={{
              margin: 0,
              whiteSpace: 'nowrap',
              color: isDark ? '#e8e8e8' : 'inherit',
            }}
          >
            知否
          </Typography.Title>
        </div>
        <Menu
          mode="horizontal"
          selectedKeys={isDocumentPage ? ['/knowledge-base'] : isKnowledgeCardPage ? ['/knowledge-cards'] : [location.pathname]}
          items={menuItems}
          onClick={({ key }) => navigate(key)}
          style={{
            flex: 1,
            minWidth: 0,
            border: 'none',
            background: 'transparent',
          }}
          theme={isDark ? 'dark' : 'light'}
        />
        <div className="app-header-right" style={{ display: 'flex', alignItems: 'center', gap: 16 }}>
          <ThemeSwitcher value={themeMode} onChange={onThemeChange} />
          <Dropdown menu={{ items: userMenuItems, onClick: handleUserMenuClick }} placement="bottomRight">
            <Avatar
              size={32}
              icon={<UserOutlined />}
              style={{ cursor: 'pointer', backgroundColor: '#1677ff' }}
            />
          </Dropdown>
        </div>
      </Header>
      <Content
        className="app-content"
        style={{
          overflow: 'hidden',
          background: isDark ? '#0f0f1a' : '#fff',
        }}
      >
        <Routes>
          <Route path="/" element={<Navigate to="/chat" replace />} />
          <Route
            path="/chat"
            element={
              <ChatPage
                themeMode={themeMode}
                sidebarCollapsed={sidebarCollapsed}
                onToggleSidebar={() => setSidebarCollapsed(!sidebarCollapsed)}
              />
            }
          />
          <Route path="/knowledge-base" element={<KnowledgeBasePage />} />
          <Route path="/knowledge-base/:knowledgeBaseId" element={<DocumentPage />} />
          <Route path="/knowledge-base/:knowledgeBaseId/graph" element={<GraphPage />} />
          <Route path="/graph" element={<GraphPage />} />
          <Route path="/discovery" element={<DiscoveryPage />} />
          <Route path="/discovery/:knowledgeBaseId" element={<DiscoveryPage />} />
          <Route path="/knowledge-cards" element={<KnowledgeCardPage />} />
          <Route path="/knowledge-cards/:knowledgeBaseId" element={<KnowledgeCardPage />} />
          <Route path="*" element={<NotFoundPage />} />
        </Routes>
      </Content>
    </Layout>
  );
}

function App() {
  const [themeMode, setThemeMode] = useState<'light' | 'dark'>(
    (localStorage.getItem('vite-ui-theme') || 'light') as 'light' | 'dark',
  );

  const handleThemeChange = useCallback((mode: 'light' | 'dark') => {
    setThemeMode(mode);
    localStorage.setItem('vite-ui-theme', mode);
    document.documentElement.setAttribute('data-theme', mode);
  }, []);

  return (
    <ConfigProvider
      locale={zhCN}
      theme={{
        algorithm: themeMode === 'dark' ? [darkAlgorithm] : [defaultAlgorithm],
        token: {
          colorPrimary: '#1677ff',
          colorBgContainer: themeMode === 'dark' ? '#0f0f1a' : '#ffffff',
          colorBgElevated: themeMode === 'dark' ? '#1a1a2e' : '#ffffff',
          colorText: themeMode === 'dark' ? '#e8e8e8' : 'rgba(0, 0, 0, 0.88)',
          colorTextSecondary: themeMode === 'dark' ? '#8b8b9e' : 'rgba(0, 0, 0, 0.65)',
          colorBorder: themeMode === 'dark' ? '#2d2d3f' : '#f0f0f0',
          borderRadius: 8,
        },
      }}
    >
      <AntApp>
        <BrowserRouter>
          <AppLayout themeMode={themeMode} onThemeChange={handleThemeChange} />
        </BrowserRouter>
      </AntApp>
    </ConfigProvider>
  );
}

export default App;