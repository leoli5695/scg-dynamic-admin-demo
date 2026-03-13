import { useState } from 'react';
import { Layout, Menu, theme } from 'antd';
import type { MenuProps } from 'antd';
import { 
  AppstoreOutlined,      // Services - 应用图标
  SafetyOutlined,        // Strategies - 安全/策略图标  
  DeploymentUnitOutlined // Routes - 路由/部署图标
} from '@ant-design/icons';
import RoutesPage from './pages/RoutesPage';
import ServicesPage from './pages/ServicesPage';
import StrategiesPage from './pages/StrategiesPage';
import LanguageSwitcher from './components/LanguageSwitcher';
import './App.css';

const { Header, Sider, Content } = Layout;

type MenuItem = Required<MenuProps>['items'][number];

function getItem(
  label: React.ReactNode,
  key: React.Key,
  icon?: React.ReactNode,
  children?: MenuItem[],
): MenuItem {
  return {
    key,
    icon,
    children,
    label,
  } as MenuItem;
}

const menuItems: MenuItem[] = [
  getItem('Services', 'services', <AppstoreOutlined />),
  getItem('Routes', 'routes', <DeploymentUnitOutlined />),
  getItem('Strategies', 'strategies', <SafetyOutlined />),
];

const App: React.FC = () => {
  const [current, setCurrent] = useState('services');
  const {
    token: { colorBgContainer },
  } = theme.useToken();

  const onClick: MenuProps['onClick'] = (e) => {
    setCurrent(e.key);
  };

  const renderContent = () => {
    switch (current) {
      case 'services':
        return <ServicesPage />;
      case 'routes':
        return <RoutesPage />;
      case 'strategies':
        return <StrategiesPage />;
      default:
        return <ServicesPage />;
    }
  };

  return (
    <Layout style={{ minHeight: '100vh' }}>
      {/* Left Sidebar */}
      <Sider width={200} theme="dark" className="sidebar">
        <div className="logo">
          Gateway Admin
        </div>
        <Menu
          theme="dark"
          mode="inline"
          selectedKeys={[current]}
          items={menuItems}
          onClick={onClick}
          style={{ borderRight: 'none', fontSize: '14px' }}
        />
      </Sider>
      
      {/* Main Content Area */}
      <Layout>
        <Header style={{ 
          background: colorBgContainer,
          display: 'flex', 
          alignItems: 'center', 
          justifyContent: 'flex-end',
          padding: '0 24px',
          borderBottom: '1px solid #f0f0f0'
        }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: '16px' }}>
            <LanguageSwitcher />
          </div>
        </Header>
        <Content style={{ 
          padding: '24px', 
          background: colorBgContainer,
          overflow: 'auto'
        }}>
          {renderContent()}
        </Content>
      </Layout>
    </Layout>
  );
};

export default App;
