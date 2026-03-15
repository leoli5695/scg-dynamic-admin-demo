import { useState, useEffect } from 'react';
import { Table, Button, Space, Modal, message, Typography, Spin, Tag, Drawer, Form, Input, Card, Tabs, Dropdown, Switch, Empty } from 'antd';
import { PlusOutlined, DeleteOutlined, CopyOutlined, SafetyOutlined } from '@ant-design/icons';
import type { ColumnsType } from 'antd/es/table';
import api from '../utils/api';
import { useTranslation } from 'react-i18next';
import copy from 'copy-to-clipboard';
import './StrategiesPage.premium.css';

const { Title, Text } = Typography;

interface RateLimiterConfig {
  routeId: string;
  limit: number;
  windowSize: number;
  enabled: boolean;
}

interface IPFilterConfig {
  routeId: string;
  whitelist?: string[];
  blacklist?: string[];
  enabled: boolean;
}

interface TimeoutConfig {
  routeId: string;
  connectTimeout: number;
  responseTimeout: number;
  enabled: boolean;
}

interface CircuitBreakerConfig {
  routeId: string;
  failureRateThreshold: number;
  minimumNumberOfCalls: number;
  waitDurationInOpenState: number;
  enabled: boolean;
}

interface TracingConfig {
  routeId: string;
  enabled: boolean;
  headerName: string;
  generateIfMissing: boolean;
}

interface AuthConfig {
  routeId: string;
  enabled: boolean;
  authType: 'JWT' | 'API_KEY' | 'OAUTH2' | 'BASIC';
  secretKey?: string;
  apiKey?: string;
  excludePaths?: string[];
}

interface Strategy {
  rateLimiters?: RateLimiterConfig[];
  ipFilters?: IPFilterConfig[];
  timeouts?: TimeoutConfig[];
  circuitBreakers?: CircuitBreakerConfig[];
  tracings?: TracingConfig[];
  auths?: AuthConfig[];
}

const StrategiesPage: React.FC = () => {
  const [strategies, setStrategies] = useState<Strategy>({});
  const [loading, setLoading] = useState(false);
  const [activeTab, setActiveTab] = useState('rateLimiter');
  const [createDrawerVisible, setCreateDrawerVisible] = useState(false);
  const [createForm] = Form.useForm();
  const { t } = useTranslation();

  const copyToClipboard = (text: string, label: string) => {
    copy(text);
    message.success(t('message.copied_to_clipboard', { label }));
  };

  useEffect(() => {
    loadStrategies();
  }, []);

  const loadStrategies = async () => {
    try {
      setLoading(true);
      const response = await api.get('/api/plugins');
      if (response.data.code === 200) {
        setStrategies(response.data.data || {});
      }
    } catch (error: any) {
      message.error(t('message.load_strategies_failed', { error: error.message }));
    } finally {
      setLoading(false);
    }
  };

  const handleCreate = (values: any) => {
    let endpoint = '';
    let data = { ...values, enabled: true }; // 默认启用

    switch (activeTab) {
      case 'rateLimiter':
        endpoint = '/api/plugins/rate-limiters';
        data = { ...data, limit: parseInt(values.limit), windowSize: parseInt(values.windowSize) };
        break;
      case 'ipFilter':
        endpoint = '/api/plugins/ip-filters';
        data = { ...data, whitelist: values.whitelist?.split(',') || [], blacklist: values.blacklist?.split(',') || [] };
        break;
      case 'timeout':
        endpoint = '/api/plugins/timeouts';
        data = { ...data, connectTimeout: parseInt(values.connectTimeout), responseTimeout: parseInt(values.responseTimeout) };
        break;
      case 'circuitBreaker':
        endpoint = '/api/plugins/circuit-breakers';
        data = {
          ...data,
          failureRateThreshold: parseFloat(values.failureRateThreshold),
          minimumNumberOfCalls: parseInt(values.minimumNumberOfCalls),
          waitDurationInOpenState: parseInt(values.waitDurationInOpenState)
        };
        break;
      default:
        break;
    }

    api.post(endpoint, data)
      .then(response => {
        if (response.data.code === 200) {
          message.success(t('message.create_success'));
          createForm.resetFields();
          setCreateDrawerVisible(false);
          loadStrategies();
        } else {
          message.error(t('message.create_failed', { msg: response.data.message }));
        }
      })
      .catch(error => {
        message.error(t('message.create_failed', { msg: error.response?.data?.message || error.message }));
      });
  };

  const handleDelete = (strategyType: string, routeId: string) => {
    let endpoint = '';

    switch (strategyType) {
      case 'rateLimiter':
        endpoint = `/api/plugins/rate-limiters/${routeId}`;
        break;
      case 'ipFilter':
        endpoint = `/api/plugins/ip-filters/${routeId}`;
        break;
      case 'timeout':
        endpoint = `/api/plugins/timeouts/${routeId}`;
        break;
      case 'circuitBreaker':
        endpoint = `/api/plugins/circuit-breakers/${routeId}`;
        break;
      default:
        break;
    }

    Modal.confirm({
      title: t('common.confirm'),
      content: t('message.confirm_delete_strategy', { type: strategyType, routeId }),
      okText: t('common.delete'),
      okType: 'danger',
      cancelText: t('common.cancel'),
      onOk: async () => {
        try {
          const response = await api.delete(endpoint);
          
          if (response.data.code === 200) {
            message.success(t('message.delete_success'));
            loadStrategies();
          } else {
            message.error(t('message.delete_failed', { msg: response.data.message }));
          }
        } catch (error: any) {
          const errorMsg = error.response?.data?.message || error.message;
          message.error(t('message.delete_failed', { msg: errorMsg }));
        }
      },
    });
  };

  const getColumns = (strategyType: string) => {
    const baseColumns: ColumnsType<any> = [
      {
        title: t('strategies.routeId'),
        dataIndex: 'routeId',
        key: 'routeId',
        width: 200,
        render: (text) => (
          <Space>
            <span style={{ fontWeight: 500 }}>{text}</span>
            <Button 
              type="text" 
              size="small" 
              icon={<CopyOutlined />} 
              onClick={(e) => {
                e.stopPropagation();
                copyToClipboard(text, t('strategies.routeId'));
              }}
            />
          </Space>
        ),
      },
      {
        title: t('strategies.col_status'),
        dataIndex: 'enabled',
        key: 'enabled',
        width: 120,
        render: (enabled: boolean) => (
          <Tag color={enabled ? 'green' : 'gray'}>
            {enabled ? t('strategies.status_active') : t('strategies.status_inactive')}
          </Tag>
        ),
      },
      {
        title: t('strategies.col_actions'),
        key: 'actions',
        width: 180,
        fixed: 'right',
        render: (_, record) => (
          <Space size="middle">
            <Button type="link" size="small" onClick={() => message.info(t('common.feature_not_implemented'))}>
              {t('strategies.btn_view')}
            </Button>
            <Button type="link" size="small" onClick={() => message.info(t('common.feature_not_implemented'))}>
              {t('strategies.btn_edit')}
            </Button>
            <Button 
              danger 
              type="link" 
              size="small"
              icon={<DeleteOutlined />}
              onClick={() => handleDelete(strategyType, record.routeId)}
            >
              {t('strategies.btn_delete')}
            </Button>
          </Space>
        ),
      },
    ];

    switch (strategyType) {
      case 'rateLimiter':
        return [
          ...baseColumns.slice(0, 2),
          {
            title: t('strategies.rateLimit'),
            key: 'rateLimit',
            render: (_: any, record: RateLimiterConfig) => (
              <span>{record.limit} {t('strategies.requests_per')} {record.windowSize} {t('strategies.seconds')}</span>
            ),
          },
          ...baseColumns.slice(2)
        ];
      case 'ipFilter':
        return [
          ...baseColumns.slice(0, 2),
          {
            title: t('strategies.ipRules'),
            key: 'ipRules',
            render: (_: any, record: IPFilterConfig) => (
              <div>
                {record.whitelist && record.whitelist.length > 0 && (
                  <div><strong>{t('strategies.whitelist')}:</strong> {record.whitelist.join(', ')}</div>
                )}
                {record.blacklist && record.blacklist.length > 0 && (
                  <div><strong>{t('strategies.blacklist')}:</strong> {record.blacklist.join(', ')}</div>
                )}
              </div>
            ),
          },
          {
            title: t('strategies.type'),
            key: 'type',
            render: () => (
              <Tag color="blue">{t('strategies.whitelist')}</Tag>
            ),
          },
          ...baseColumns.slice(2)
        ];
      case 'timeout':
        return [
          ...baseColumns.slice(0, 2),
          {
            title: t('strategies.timeouts'),
            key: 'timeouts',
            render: (_: any, record: TimeoutConfig) => (
              <div>
                <div><strong>{t('strategies.connectTimeout')}:</strong> {record.connectTimeout} ms</div>
                <div><strong>{t('strategies.responseTimeout')}:</strong> {record.responseTimeout} ms</div>
              </div>
            ),
          },
          ...baseColumns.slice(2)
        ];
      case 'circuitBreaker':
        return [
          ...baseColumns.slice(0, 2),
          {
            title: t('strategies.circuitBreakerParams'),
            key: 'circuitBreakerParams',
            render: (_: any, record: CircuitBreakerConfig) => (
              <div>
                <div><strong>{t('strategies.failureRateThreshold')}:</strong> {record.failureRateThreshold}%</div>
                <div><strong>{t('strategies.minimumNumberOfCalls')}:</strong> {record.minimumNumberOfCalls}</div>
                <div><strong>{t('strategies.waitDurationInOpenState')}:</strong> {record.waitDurationInOpenState} ms</div>
              </div>
            ),
          },
          ...baseColumns.slice(2)
        ];
      default:
        return baseColumns;
    }
  };

  const getDataSource = (strategyType: string) => {
    switch (strategyType) {
      case 'rateLimiter':
        return strategies.rateLimiters || [];
      case 'ipFilter':
        return strategies.ipFilters || [];
      case 'timeout':
        return strategies.timeouts || [];
      case 'circuitBreaker':
        return strategies.circuitBreakers || [];
      default:
        return [];
    }
  };

  const getCreateFormItems = () => {
    switch (activeTab) {
      case 'rateLimiter':
        return (
          <>
            <Form.Item
              name="routeId"
              label={t('strategies.routeId')}
              rules={[{ required: true, message: t('strategies.routeId_required') }]}
            >
              <Input placeholder={t('strategies.routeId_placeholder')} />
            </Form.Item>
            <Form.Item
              name="limit"
              label={t('strategies.limit')}
              rules={[{ required: true, message: t('strategies.limit_required') }]}
            >
              <Input placeholder={t('strategies.limit_placeholder')} />
            </Form.Item>
            <Form.Item
              name="windowSize"
              label={t('strategies.windowSize')}
              rules={[{ required: true, message: t('strategies.windowSize_required') }]}
            >
              <Input placeholder={t('strategies.windowSize_placeholder')} />
            </Form.Item>
          </>
        );
      case 'ipFilter':
        return (
          <>
            <Form.Item
              name="routeId"
              label={t('strategies.routeId')}
              rules={[{ required: true, message: t('strategies.routeId_required') }]}
            >
              <Input placeholder={t('strategies.routeId_placeholder')} />
            </Form.Item>
            <Form.Item
              name="whitelist"
              label={t('strategies.whitelist')}
            >
              <Input.TextArea placeholder={t('strategies.whitelist_placeholder')} />
            </Form.Item>
            <Form.Item
              name="blacklist"
              label={t('strategies.blacklist')}
            >
              <Input.TextArea placeholder={t('strategies.blacklist_placeholder')} />
            </Form.Item>
          </>
        );
      case 'timeout':
        return (
          <>
            <Form.Item
              name="routeId"
              label={t('strategies.routeId')}
              rules={[{ required: true, message: t('strategies.routeId_required') }]}
            >
              <Input placeholder={t('strategies.routeId_placeholder')} />
            </Form.Item>
            <Form.Item
              name="connectTimeout"
              label={t('strategies.connectTimeout')}
              rules={[{ required: true, message: t('strategies.connectTimeout_required') }]}
            >
              <Input placeholder={t('strategies.connectTimeout_placeholder')} />
            </Form.Item>
            <Form.Item
              name="responseTimeout"
              label={t('strategies.responseTimeout')}
              rules={[{ required: true, message: t('strategies.responseTimeout_required') }]}
            >
              <Input placeholder={t('strategies.responseTimeout_placeholder')} />
            </Form.Item>
          </>
        );
      case 'circuitBreaker':
        return (
          <>
            <Form.Item
              name="routeId"
              label={t('strategies.routeId')}
              rules={[{ required: true, message: t('strategies.routeId_required') }]}
            >
              <Input placeholder={t('strategies.routeId_placeholder')} />
            </Form.Item>
            <Form.Item
              name="failureRateThreshold"
              label={t('strategies.failureRateThreshold')}
              rules={[{ required: true, message: t('strategies.failureRateThreshold_required') }]}
            >
              <Input placeholder={t('strategies.failureRateThreshold_placeholder')} />
            </Form.Item>
            <Form.Item
              name="minimumNumberOfCalls"
              label={t('strategies.minimumNumberOfCalls')}
              rules={[{ required: true, message: t('strategies.minimumNumberOfCalls_required') }]}
            >
              <Input placeholder={t('strategies.minimumNumberOfCalls_placeholder')} />
            </Form.Item>
            <Form.Item
              name="waitDurationInOpenState"
              label={t('strategies.waitDurationInOpenState')}
              rules={[{ required: true, message: t('strategies.waitDurationInOpenState_required') }]}
            >
              <Input placeholder={t('strategies.waitDurationInOpenState_placeholder')} />
            </Form.Item>
          </>
        );
      default:
        return null;
    }
  };

  const items = [
    {
      key: 'rateLimiter',
      label: t('strategies.rateLimiter'),
      children: (
        <Card bordered={false}>
          {loading ? (
            <div style={{ textAlign: 'center', padding: '40px' }}>
              <Spin tip={t('common.loading')} size="large" />
            </div>
          ) : (
            <Table
              columns={getColumns('rateLimiter')}
              dataSource={getDataSource('rateLimiter')}
              rowKey={(record) => record.routeId}
              pagination={{ pageSize: 10, showSizeChanger: true }}
              scroll={{ x: 800 }}
              locale={{
                emptyText: (
                  <Empty 
                    description={<span style={{ fontSize: '14px', color: '#64748B' }}>{t('strategies.empty_description')}</span>}
                    image={<SafetyOutlined style={{ fontSize: 64, color: '#CBD5E1' }} />}
                  >
                    <Button 
                      type="primary" 
                      icon={<PlusOutlined />}
                      size="large"
                      onClick={() => setCreateDrawerVisible(true)}
                      className="gradient-button"
                    >
                      {t('strategies.create_first_button')}
                    </Button>
                  </Empty>
                )
              }}
            />
          )}
        </Card>
      ),
    },
    {
      key: 'ipFilter',
      label: t('strategies.tab_ip_filter'),
      children: (
        <Card bordered={false}>
          {loading ? (
            <div style={{ textAlign: 'center', padding: '40px' }}>
              <Spin tip={t('common.loading')} size="large" />
            </div>
          ) : (
            <Table
              columns={getColumns('ipFilter')}
              dataSource={getDataSource('ipFilter')}
              rowKey={(record) => record.routeId}
              pagination={{ pageSize: 10, showSizeChanger: true }}
              scroll={{ x: 800 }}
              locale={{
                emptyText: (
                  <Empty 
                    description={<span style={{ fontSize: '14px', color: '#64748B' }}>{t('strategies.empty_description')}</span>}
                    image={<SafetyOutlined style={{ fontSize: 64, color: '#CBD5E1' }} />}
                  >
                    <Button 
                      type="primary" 
                      icon={<PlusOutlined />}
                      size="large"
                      onClick={() => setCreateDrawerVisible(true)}
                      className="gradient-button"
                    >
                      {t('strategies.create_first_button')}
                    </Button>
                  </Empty>
                )
              }}
            />
          )}
        </Card>
      ),
    },
    {
      key: 'timeout',
      label: t('strategies.tab_timeout'),
      children: (
        <Card bordered={false}>
          {loading ? (
            <div style={{ textAlign: 'center', padding: '40px' }}>
              <Spin tip={t('common.loading')} size="large" />
            </div>
          ) : (
            <Table
              columns={getColumns('timeout')}
              dataSource={getDataSource('timeout')}
              rowKey={(record) => record.routeId}
              pagination={{ pageSize: 10, showSizeChanger: true }}
              scroll={{ x: 800 }}
              locale={{
                emptyText: (
                  <Empty 
                    description={<span style={{ fontSize: '14px', color: '#64748B' }}>{t('strategies.empty_description')}</span>}
                    image={<SafetyOutlined style={{ fontSize: 64, color: '#CBD5E1' }} />}
                  >
                    <Button 
                      type="primary" 
                      icon={<PlusOutlined />}
                      size="large"
                      onClick={() => setCreateDrawerVisible(true)}
                      className="gradient-button"
                    >
                      {t('strategies.create_first_button')}
                    </Button>
                  </Empty>
                )
              }}
            />
          )}
        </Card>
      ),
    },
    {
      key: 'circuitBreaker',
      label: t('strategies.tab_circuit_breaker'),
      children: (
        <Card bordered={false}>
          {loading ? (
            <div style={{ textAlign: 'center', padding: '40px' }}>
              <Spin tip={t('common.loading')} size="large" />
            </div>
          ) : (
            <Table
              columns={getColumns('circuitBreaker')}
              dataSource={getDataSource('circuitBreaker')}
              rowKey={(record) => record.routeId}
              pagination={{ pageSize: 10, showSizeChanger: true }}
              scroll={{ x: 800 }}
              locale={{
                emptyText: (
                  <Empty 
                    description={<span style={{ fontSize: '14px', color: '#64748B' }}>{t('strategies.empty_description')}</span>}
                    image={<SafetyOutlined style={{ fontSize: 64, color: '#CBD5E1' }} />}
                  >
                    <Button 
                      type="primary" 
                      icon={<PlusOutlined />}
                      size="large"
                      onClick={() => setCreateDrawerVisible(true)}
                      className="gradient-button"
                    >
                      {t('strategies.create_first_button')}
                    </Button>
                  </Empty>
                )
              }}
            />
          )}
        </Card>
      ),
    },
    {
      key: 'tracing',
      label: t('strategies.tab_tracing'),
      children: (
        <Card bordered={false}>
          {loading ? (
            <div style={{ textAlign: 'center', padding: '40px' }}>
              <Spin tip={t('common.loading')} size="large" />
            </div>
          ) : (
            <Table
              columns={getColumns('tracing')}
              dataSource={getDataSource('tracing')}
              rowKey={(record) => record.routeId}
              pagination={{ pageSize: 10, showSizeChanger: true }}
              scroll={{ x: 800 }}
              locale={{
                emptyText: (
                  <Empty 
                    description={<span style={{ fontSize: '14px', color: '#64748B' }}>{t('strategies.empty_description')}</span>}
                    image={<SafetyOutlined style={{ fontSize: 64, color: '#CBD5E1' }} />}
                  >
                    <Button 
                      type="primary" 
                      icon={<PlusOutlined />}
                      size="large"
                      onClick={() => setCreateDrawerVisible(true)}
                      className="gradient-button"
                    >
                      {t('strategies.create_first_button')}
                    </Button>
                  </Empty>
                )
              }}
            />
          )}
        </Card>
      ),
    },
    {
      key: 'auth',
      label: t('strategies.tab_auth'),
      children: (
        <Card bordered={false}>
          {loading ? (
            <div style={{ textAlign: 'center', padding: '40px' }}>
              <Spin tip={t('common.loading')} size="large" />
            </div>
          ) : (
            <Table
              columns={getColumns('auth')}
              dataSource={getDataSource('auth')}
              rowKey={(record) => record.routeId}
              pagination={{ pageSize: 10, showSizeChanger: true }}
              scroll={{ x: 800 }}
              locale={{
                emptyText: (
                  <Empty 
                    description={<span style={{ fontSize: '14px', color: '#64748B' }}>{t('strategies.empty_description')}</span>}
                    image={<SafetyOutlined style={{ fontSize: 64, color: '#CBD5E1' }} />}
                  >
                    <Button 
                      type="primary" 
                      icon={<PlusOutlined />}
                      size="large"
                      onClick={() => setCreateDrawerVisible(true)}
                      className="gradient-button"
                    >
                      {t('strategies.create_first_button')}
                    </Button>
                  </Empty>
                )
              }}
            />
          )}
        </Card>
      ),
    },
  ];



  return (
    <div className="page-container">
      {/* Page Header */}
      <div className="page-header">
        <div className="page-title-group">
          <h2>{t('strategies.page_title')}</h2>
          <p>{t('strategies.page_subtitle')}</p>
        </div>
        <div className="page-actions">
          <Dropdown menu={{ items: [
            {
              key: 'rateLimiter',
              label: t('strategies.tab_rate_limiter'),
              onClick: () => {
                setActiveTab('rateLimiter');
                setCreateDrawerVisible(true);
              }
            },
            {
              key: 'ipFilter',
              label: t('strategies.tab_ip_filter'),
              onClick: () => {
                setActiveTab('ipFilter');
                setCreateDrawerVisible(true);
              }
            },
            {
              key: 'timeout',
              label: t('strategies.tab_timeout'),
              onClick: () => {
                setActiveTab('timeout');
                setCreateDrawerVisible(true);
              }
            },
            {
              key: 'circuitBreaker',
              label: t('strategies.tab_circuit_breaker'),
              onClick: () => {
                setActiveTab('circuitBreaker');
                setCreateDrawerVisible(true);
              }
            },
            {
              key: 'tracing',
              label: t('strategies.tab_tracing'),
              onClick: () => {
                setActiveTab('tracing');
                setCreateDrawerVisible(true);
              }
            },
            {
              key: 'auth',
              label: t('strategies.tab_auth'),
              onClick: () => {
                setActiveTab('auth');
                setCreateDrawerVisible(true);
              }
            },
          ]}}>
            <Button 
              type="primary" 
              icon={<PlusOutlined />}
              size="large"
            >
              {t('strategies.create_policy_button')}
            </Button>
          </Dropdown>
        </div>
      </div>

      {/* Tabs with Premium Style */}
      <Tabs 
        activeKey={activeTab} 
        onChange={setActiveTab}
        items={items}
        className="premium-tabs"
      />

      {/* Create Strategy Drawer */}
      <Drawer
        title={`${t('strategies.create')} - ${t(`strategies.${activeTab}`)}`}
        placement="right"
        width={600}
        open={createDrawerVisible}
        onClose={() => {
          setCreateDrawerVisible(false);
          createForm.resetFields();
        }}
      >
        <Form
          form={createForm}
          layout="vertical"
          onFinish={handleCreate}
        >
          {getCreateFormItems()}

          <Form.Item>
            <Space>
              <Button type="primary" htmlType="submit" size="large">
                {t('common.create')}
              </Button>
              <Button onClick={() => setCreateDrawerVisible(false)} size="large">
                {t('common.cancel')}
              </Button>
            </Space>
          </Form.Item>
        </Form>
      </Drawer>
    </div>
  );
};

export default StrategiesPage;
