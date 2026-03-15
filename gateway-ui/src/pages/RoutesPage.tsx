import { useState, useEffect } from 'react';
import { Table, Button, Space, Modal, message, Typography, Spin, Tag, Drawer, Form, Input, Switch, Card, Descriptions, Select, Empty, Radio, Tooltip } from 'antd';
import { PlusOutlined, DeleteOutlined, EyeOutlined, CopyOutlined, StopOutlined, PlayCircleOutlined, EditOutlined, CompassOutlined } from '@ant-design/icons';
import type { ColumnsType } from 'antd/es/table';
import api from '../utils/api';
import { useTranslation } from 'react-i18next';
import copy from 'copy-to-clipboard';

const { Title } = Typography;
const { TextArea } = Input;

interface Route {
  id: string;              // UUID (系统标识，用于删除)
  routeName: string;       // route_name (业务名称)
  uri: string;
  predicates?: any[];
  filters?: any[];
  order?: number;
  enabled?: boolean;
  description?: string;
  metadata?: any;
}

interface Service {
  name: string;
  serviceId: string;
  loadBalancer: string;
  instances?: any[];
}

interface NacosService {
  serviceName: string;
}

interface CreateRouteForm {
  id: string;
  uri: string;
  description?: string;
  order?: number;
  enabled?: boolean;
}

// Predicate Item Component with dynamic rendering based on type
const PredicateItem: React.FC<{
  form: any;
  restField: any;
  name: number;
  t: any;
  onRemove: (index: number) => void;
}> = ({ form, restField, name, t, onRemove }) => {
  const [predicateType, setPredicateType] = useState<string>('');
  
  return (
    <div key={name} style={{ 
      display: 'flex', 
      alignItems: 'flex-start', 
      marginBottom: 16,
      padding: '12px 16px',
      backgroundColor: '#f8fafc',
      borderRadius: '8px',
      border: '1px solid #e2e8f0'
    }}>
      <Form.Item
        {...restField}
        name={[name, 'name']}
        noStyle
        style={{ marginRight: 8 }}
      >
        <Select 
          placeholder={t('routes.predicate_type')} 
          style={{ width: 150 }}
          onChange={(value) => setPredicateType(value)}
        >
          <Select.Option value="Path">Path</Select.Option>
          <Select.Option value="Host">Host</Select.Option>
          <Select.Option value="Method">Method</Select.Option>
          <Select.Option value="Header">Header</Select.Option>
        </Select>
      </Form.Item>
      <Form.Item
        {...restField}
        name={[name, 'args']}
        noStyle
      >
        {predicateType === 'Method' ? (
          <Select 
            placeholder={t('routes.arguments')} 
            style={{ width: 300 }}
            mode="multiple"
          >
            <Select.Option value="GET">GET</Select.Option>
            <Select.Option value="POST">POST</Select.Option>
            <Select.Option value="PUT">PUT</Select.Option>
            <Select.Option value="DELETE">DELETE</Select.Option>
            <Select.Option value="PATCH">PATCH</Select.Option>
            <Select.Option value="HEAD">HEAD</Select.Option>
            <Select.Option value="OPTIONS">OPTIONS</Select.Option>
          </Select>
        ) : predicateType === 'Header' ? (
          <Space.Compact style={{ width: 300 }}>
            <Input 
              placeholder="Header Name" 
              style={{ width: '45%' }}
              onChange={(e) => {
                const currentValue = form.getFieldValue(['predicates', name, 'args']);
                const newValue = `${e.target.value}:${currentValue?.split(':')[1] || ''}`;
                form.setFieldValue(['predicates', name, 'args'], newValue);
              }}
            />
            <Input 
              placeholder="Header Value" 
              style={{ width: '55%' }}
              onChange={(e) => {
                const currentValue = form.getFieldValue(['predicates', name, 'args']);
                const newValue = `${currentValue?.split(':')[0] || ''}:${e.target.value}`;
                form.setFieldValue(['predicates', name, 'args'], newValue);
              }}
            />
          </Space.Compact>
        ) : (
          <Input placeholder={t('routes.arguments')} style={{ width: 300 }} />
        )}
      </Form.Item>
      <Button type="dashed" danger onClick={() => onRemove(name)} icon={<DeleteOutlined />}>
        {t('common.delete')}
      </Button>
    </div>
  );
};

// Filter Item Component with dynamic rendering based on type
const FilterItem: React.FC<{
  form: any;
  restField: any;
  name: number;
  t: any;
  onRemove: (index: number) => void;
}> = ({ form, restField, name, t, onRemove }) => {
  const [filterType, setFilterType] = useState<string>('');
  
  // Filters that need Key:Value input
  const needsKeyValueInput = [
    'AddRequestHeader', 'SetRequestHeader',
    'AddRequestParameter',
    'AddResponseHeader', 'SetResponseHeader'
  ];
  
  // Filters that need single value input
  const needsSingleInput = [
    'RemoveRequestHeader', 'RemoveRequestParameter', 'RemoveResponseHeader',
    'StripPrefix', 'PrefixPath', 'SetPath', 'SetStatus'
  ];
  
  // RewritePath needs two inputs (regex and replacement)
  const isRewritePath = filterType === 'RewritePath';
  
  return (
    <div key={name} style={{ 
      display: 'flex', 
      alignItems: 'flex-start', 
      marginBottom: 16,
      padding: '12px 16px',
      backgroundColor: '#f8fafc',
      borderRadius: '8px',
      border: '1px solid #e2e8f0'
    }}>
      <Form.Item
        {...restField}
        name={[name, 'name']}
        noStyle
        style={{ marginRight: 8 }}
      >
        <Select 
          placeholder={t('routes.filter_type')} 
          style={{ width: 200 }}
          onChange={(value) => setFilterType(value)}
        >
          {/* Request Headers */}
          <Select.OptGroup label="Request Headers">
            <Select.Option value="AddRequestHeader">Add Request Header</Select.Option>
            <Select.Option value="RemoveRequestHeader">Remove Request Header</Select.Option>
            <Select.Option value="SetRequestHeader">Set Request Header</Select.Option>
          </Select.OptGroup>
          
          {/* Request Parameters */}
          <Select.OptGroup label="Request Parameters">
            <Select.Option value="AddRequestParameter">Add Request Parameter</Select.Option>
            <Select.Option value="RemoveRequestParameter">Remove Request Parameter</Select.Option>
          </Select.OptGroup>
          
          {/* Response Headers */}
          <Select.OptGroup label="Response Headers">
            <Select.Option value="AddResponseHeader">Add Response Header</Select.Option>
            <Select.Option value="RemoveResponseHeader">Remove Response Header</Select.Option>
            <Select.Option value="SetResponseHeader">Set Response Header</Select.Option>
          </Select.OptGroup>
          
          {/* Path Modification */}
          <Select.OptGroup label="Path Modification">
            <Select.Option value="StripPrefix">Strip Prefix</Select.Option>
            <Select.Option value="PrefixPath">Prefix Path</Select.Option>
            <Select.Option value="RewritePath">Rewrite Path</Select.Option>
            <Select.Option value="SetPath">Set Path</Select.Option>
          </Select.OptGroup>
          
          {/* Status Code */}
          <Select.OptGroup label="Status Code">
            <Select.Option value="SetStatus">Set Status</Select.Option>
          </Select.OptGroup>
        </Select>
      </Form.Item>
      <Form.Item
        {...restField}
        name={[name, 'args']}
        noStyle
      >
        {needsKeyValueInput.includes(filterType) ? (
          <Space.Compact style={{ width: 320 }}>
            <Input 
              placeholder="Key" 
              style={{ width: '45%' }}
              onChange={(e) => {
                const currentValue = form.getFieldValue(['filters', name, 'args']);
                const newValue = `${e.target.value}:${currentValue?.split(':')[1] || ''}`;
                form.setFieldValue(['filters', name, 'args'], newValue);
              }}
            />
            <Input 
              placeholder="Value" 
              style={{ width: '55%' }}
              onChange={(e) => {
                const currentValue = form.getFieldValue(['filters', name, 'args']);
                const newValue = `${currentValue?.split(':')[0] || ''}:${e.target.value}`;
                form.setFieldValue(['filters', name, 'args'], newValue);
              }}
            />
          </Space.Compact>
        ) : isRewritePath ? (
          <Space.Compact style={{ width: 320 }}>
            <Input 
              placeholder="Regexp" 
              style={{ width: '50%' }}
              onChange={(e) => {
                const currentValue = form.getFieldValue(['filters', name, 'args']);
                const newValue = `${e.target.value}|${currentValue?.split('|')[1] || ''}`;
                form.setFieldValue(['filters', name, 'args'], newValue);
              }}
            />
            <Input 
              placeholder="Replacement" 
              style={{ width: '50%' }}
              onChange={(e) => {
                const currentValue = form.getFieldValue(['filters', name, 'args']);
                const newValue = `${currentValue?.split('|')[0] || ''}|${e.target.value}`;
                form.setFieldValue(['filters', name, 'args'], newValue);
              }}
            />
          </Space.Compact>
        ) : (
          <Input 
            placeholder={t('routes.arguments')} 
            style={{ width: 320 }}
            disabled={!filterType}
          />
        )}
      </Form.Item>
      {/* Plugin Description */}
      {filterType && (
        <div style={{ 
          fontSize: 12, 
          color: '#64748b', 
          marginLeft: 12,
          marginTop: -8,
          padding: '8px 12px',
          backgroundColor: '#f1f5f9',
          borderRadius: '6px',
          borderLeft: '3px solid #3b82f6'
        }}>
          <Tooltip title={t(`plugin.${filterType}.detail`)}>
            <span style={{ cursor: 'help' }}>
              ℹ️ {t(`plugin.${filterType}.desc`)}...
            </span>
          </Tooltip>
        </div>
      )}
      <Button type="dashed" danger onClick={() => onRemove(name)} icon={<DeleteOutlined />}>
        {t('common.delete')}
      </Button>
    </div>
  );
};

const RoutesPage: React.FC = () => {
  const [routes, setRoutes] = useState<Route[]>([]);
  const [services, setServices] = useState<Service[]>([]);
  const [nacosServices, setNacosServices] = useState<NacosService[]>([]);
  const [loading, setLoading] = useState(false);
  const [createDrawerVisible, setCreateDrawerVisible] = useState(false);
  const [editDrawerVisible, setEditDrawerVisible] = useState(false);
  const [detailDrawerVisible, setDetailDrawerVisible] = useState(false);
  const [selectedRoute, setSelectedRoute] = useState<Route | null>(null);
  const [searchTerm, setSearchTerm] = useState('');
  const [statusFilter, setStatusFilter] = useState<string>('all');
  const [selectedRows, setSelectedRows] = useState<React.Key[]>([]);
  const [createForm] = Form.useForm();
  const [editForm] = Form.useForm();
  const [targetType, setTargetType] = useState<'static' | 'discovery'>('static'); // 明确的状态
  const [editTargetType, setEditTargetType] = useState<'static' | 'discovery'>('static');
  const { t } = useTranslation();

  useEffect(() => {
    loadRoutes();
    loadServices();
  }, []);

  const loadRoutes = async () => {
    try {
      setLoading(true);
      const response = await api.get('/api/routes');
      if (response.data.code === 200) {
        setRoutes(response.data.data || []);
      } else {
        message.error(t('message.load_routes_failed', { error: response.data.message }));
      }
    } catch (error: any) {
      console.error('Load routes error:', error);
      message.error(t('message.load_routes_failed', { error: error.response?.data?.message || error.message }));
    } finally {
      setLoading(false);
    }
  };

  const loadServices = async () => {
    try {
      const response = await api.get('/api/services');
      if (response.data.code === 200) {
        const serviceList = response.data.data || [];
        setServices(serviceList);
      }
    } catch (error: any) {
      console.error('Load services error:', error);
    }
  };

  const loadNacosServices = async () => {
    try {
      const response = await api.get('/api/services/nacos-discovery');
      if (response.data.code === 200) {
        const nacosServiceList: string[] = response.data.data || [];
        setNacosServices(nacosServiceList.map(name => ({ serviceName: name })));
      }
    } catch (error: any) {
      console.error('Load Nacos services error:', error);
    }
  };

  const filteredRoutes = routes.filter(route => {
    const matchesSearch = !searchTerm || 
      route.id.toLowerCase().includes(searchTerm.toLowerCase()) ||
      route.routeName.toLowerCase().includes(searchTerm.toLowerCase()) ||
      route.uri.toLowerCase().includes(searchTerm.toLowerCase());
    
    const matchesStatus = statusFilter === 'all' || 
      (statusFilter === 'enabled' && route.enabled) ||
      (statusFilter === 'disabled' && !route.enabled);
    
    return matchesSearch && matchesStatus;
  });

  const handleBatchDelete = () => {
    if (selectedRows.length === 0) return;
    
    Modal.confirm({
      title: t('common.confirm'),
      content: t('message.confirm_delete', { count: selectedRows.length }),
      okText: t('common.delete'),
      okType: 'danger',
      cancelText: t('common.cancel'),
      onOk: async () => {
        try {
          for (const routeId of selectedRows) {
            await api.delete(`/api/routes/${routeId}`);
          }
          message.success(t('message.delete_success'));
          setSelectedRows([]);
          loadRoutes();
        } catch (error: any) {
          message.error(error.response?.data?.message || error.message);
        }
      },
    });
  };

  const handleCreate = (values: any) => {
    // Validate at least one Path predicate
    if (!values.predicates || values.predicates.length === 0) {
      message.error('At least one predicate is required');
      return;
    }

    const hasPathPredicate = values.predicates.some((p: any) => p.name === 'Path');
    if (!hasPathPredicate) {
      message.error('At least one Path predicate is required');
      return;
    }

    // URI is already auto-generated, no need to build it

    // Format predicates and filters
    const formattedPredicates = values.predicates.map((p: any) => ({
      name: p.name,
      args: parsePredicateArgs(p.name, p.args)
    }));

    const formattedFilters = (values.filters || []).map((f: any) => ({
      name: f.name,
      args: parseFilterArgs(f.name, f.args)
    }));

    const routeData = {
      id: values.id,
      uri: values.uri,  // Already in correct format
      order: values.order || 0,
      enabled: values.enabled !== false,
      description: values.description,
      predicates: formattedPredicates,
      filters: formattedFilters
    };
    
    api.post('/api/routes', routeData)
      .then(response => {
        if (response.data.code === 200) {
          message.success('Created successfully');
          createForm.resetFields();
          setCreateDrawerVisible(false);
          loadRoutes();
          loadServices();
        } else {
          message.error('Failed to create route: ' + response.data.message);
        }
      })
      .catch(error => {
        message.error('Failed to create route: ' + (error.response?.data?.message || error.message));
      });
  };

  const handleUpdate = (values: any) => {
    // Validate at least one Path predicate
    if (!values.predicates || values.predicates.length === 0) {
      message.error('At least one predicate is required');
      return;
    }

    const hasPathPredicate = values.predicates.some((p: any) => p.name === 'Path');
    if (!hasPathPredicate) {
      message.error('At least one Path predicate is required');
      return;
    }

    // Format predicates and filters
    const formattedPredicates = values.predicates.map((p: any) => ({
      name: p.name,
      args: parsePredicateArgs(p.name, p.args)
    }));

    const formattedFilters = (values.filters || []).map((f: any) => ({
      name: f.name,
      args: parseFilterArgs(f.name, f.args)
    }));

    const routeData = {
      id: values.id,
      uri: values.uri,  // Already in correct format
      order: values.order || 0,
      enabled: values.enabled !== false,
      description: values.description,
      predicates: formattedPredicates,
      filters: formattedFilters
    };
    
    api.put(`/api/routes/${values.id}`, routeData)
      .then(response => {
        if (response.data.code === 200) {
          message.success(t('message.update_success'));
          editForm.resetFields();
          setEditDrawerVisible(false);
          loadRoutes();
          loadServices();
        } else {
          message.error('Failed to update route: ' + response.data.message);
        }
      })
      .catch(error => {
        message.error('Failed to update route: ' + (error.response?.data?.message || error.message));
      });
  };

  // Helper function to parse predicate args based on type
  const parsePredicateArgs = (type: string, args: string) => {
    switch (type) {
      case 'Path':
        return { pattern: args || '/**' };
      case 'Host':
        return { pattern: args };
      case 'Method':
        return { methods: args ? args.split(',') : ['GET'] };
      case 'Header':
        const parts = args.split(':');
        return { name: parts[0], regexp: parts[1] || '.*' };
      default:
        return { value: args };
    }
  };

  // Helper function to parse filter args based on type
  const parseFilterArgs = (type: string, args: string) => {
    switch (type) {
      case 'StripPrefix':
        return { parts: parseInt(args) || 1 };
      case 'AddRequestHeader':
      case 'SetRequestHeader':
        const headerParts = args.split(':');
        return { name: headerParts[0] || '', value: headerParts[1] || '' };
      case 'AddRequestParameter':
        const paramParts = args.split(':');
        return { name: paramParts[0] || '', value: paramParts[1] || '' };
      case 'SetPath':
        return { template: args };
      default:
        return { value: args };
    }
  };

  // Helper function to convert predicate args object back to string
  const stringifyPredicateArgs = (type: string, args: any): string => {
    if (!args) return '';
    switch (type) {
      case 'Path':
        return args.pattern || '/**';
      case 'Host':
        return args.pattern || '';
      case 'Method':
        return Array.isArray(args.methods) ? args.methods.join(',') : '';
      case 'Header':
        return `${args.name || ''}:${args.regexp || ''}`;
      default:
        return args.value || '';
    }
  };

  // Helper function to convert filter args object back to string
  const stringifyFilterArgs = (type: string, args: any): string => {
    if (!args) return '';
    switch (type) {
      case 'StripPrefix':
        return String(args.parts || 1);
      case 'AddRequestHeader':
      case 'SetRequestHeader':
        return `${args.name || ''}:${args.value || ''}`;
      case 'AddRequestParameter':
        return `${args.name || ''}:${args.value || ''}`;
      case 'SetPath':
        return args.template || '';
      default:
        return args.value || '';
    }
  };

  const showRouteDetail = (record: Route) => {
    setSelectedRoute(record);
    setDetailDrawerVisible(true);
  };

  const showRouteEdit = (record: Route) => {
    setEditTargetType(record.uri?.startsWith('lb://') ? 'discovery' : 'static');
    
    // Parse URI to get service ID
    let serviceId = '';
    let nacosServiceId = '';
    if (record.uri) {
      if (record.uri.startsWith('lb://')) {
        nacosServiceId = record.uri.substring(5);
      } else if (record.uri.startsWith('static://')) {
        serviceId = record.uri.substring(9);
      }
    }
    
    // Convert predicates and filters from object format to string format
    const editPredicates = (record.predicates || []).map((p: any) => ({
      name: p.name,
      args: stringifyPredicateArgs(p.name, p.args)
    }));
    
    const editFilters = (record.filters || []).map((f: any) => ({
      name: f.name,
      args: stringifyFilterArgs(f.name, f.args)
    }));
    
    editForm.setFieldsValue({
      id: record.id,
      routeName: record.routeName,
      uri: record.uri,
      order: record.order,
      description: record.description,
      targetType: record.uri?.startsWith('lb://') ? 'discovery' : 'static',
      serviceId: serviceId || undefined,
      nacosServiceId: nacosServiceId || undefined,
      predicates: editPredicates,
      filters: editFilters,
    });
    
    setEditDrawerVisible(true);
  };

  const copyToClipboard = (text: string, label: string) => {
    copy(text);
    message.success(t('message.copied_to_clipboard', { label }));
  };

  const handleDelete = (record: Route) => {
    Modal.confirm({
      title: t('common.confirm'),
      content: t('message.confirm_delete_route', { name: record.routeName, routeId: record.id || 'N/A' }),
      okText: t('common.delete'),
      okType: 'danger',
      cancelText: t('common.cancel'),
      onOk: async () => {
        try {
          const routeIdToDelete = record.id;  // Use UUID for deletion
          
          if (!routeIdToDelete) {
            message.error(t('message.route_uuid_not_found'));
            return;
          }
          
          console.log('Deleting route with UUID:', routeIdToDelete);
          
          const response = await api.delete(`/api/routes/${routeIdToDelete}`);
          
          if (response.data.code === 200) {
            message.success(t('message.delete_success'));
            loadRoutes();
          } else {
            message.error('Failed to delete route: ' + response.data.message);
          }
        } catch (error: any) {
          const errorMsg = error.response?.data?.message || error.message;
          message.error('Failed to delete route: ' + errorMsg);
        }
      },
    });
  };

  const handleEnableRoute = async (record: Route) => {
    try {
      const routeId = record.id;  // Use UUID
      
      if (!routeId) {
        message.error('Route ID not found');
        return;
      }
      
      const response = await api.post(`/api/routes/${routeId}/enable`);
      
      if (response.data.code === 200) {
        message.success('Route enabled successfully');
        loadRoutes();
      } else {
        message.error('Failed to enable route: ' + response.data.message);
      }
    } catch (error: any) {
      const errorMsg = error.response?.data?.message || error.message;
      message.error('Failed to enable route: ' + errorMsg);
    }
  };

  const handleDisableRoute = async (record: Route) => {
    try {
      const routeId = record.id;  // Use UUID
      
      if (!routeId) {
        message.error('Route ID not found');
        return;
      }
      
      const response = await api.post(`/api/routes/${routeId}/disable`);
      
      if (response.data.code === 200) {
        message.success('Route disabled successfully');
        loadRoutes();
      } else {
        message.error('Failed to disable route: ' + response.data.message);
      }
    } catch (error: any) {
      const errorMsg = error.response?.data?.message || error.message;
      message.error('Failed to disable route: ' + errorMsg);
    }
  };

  const rowSelection = {
    selectedRowKeys: selectedRows,
    onChange: (selectedRowKeys: React.Key[]) => {
      setSelectedRows(selectedRowKeys);
    },
  };

  const columns: ColumnsType<Route> = [
    {
      title: t('routes.id'),
      dataIndex: 'id',
      key: 'id',
      width: 200,
      render: (text) => (
        <Space>
          <span style={{ fontFamily: 'monospace', fontSize: '12px' }}>{text}</span>
          <Button 
            type="text" 
            size="small" 
            icon={<CopyOutlined />} 
            onClick={(e) => {
              e.stopPropagation();
              copy(text);
              message.success(t('message.copied_to_clipboard', { label: t('routes.id') }));
            }}
          />
        </Space>
      ),
    },
    {
      title: t('routes.name'),
      dataIndex: 'routeName',
      key: 'routeName',
      width: 150,
      render: (text) => (
        <Space>
          <span style={{ fontWeight: 500 }}>{text}</span>
        </Space>
      ),
    },
    {
      title: t('routes.uri'),
      dataIndex: 'uri',
      key: 'uri',
      width: 200,
      render: (text) => (
        <Typography.Text copyable={{ text }} style={{ fontFamily: 'monospace', fontSize: '12px' }}>
          {text}
        </Typography.Text>
      ),
    },
    {
      title: t('routes.order'),
      dataIndex: 'order',
      key: 'order',
      width: 80,
      render: (text) => (
        <Typography.Text type="secondary">{text}</Typography.Text>
      ),
    },
    {
      title: t('routes.enabled'),
      dataIndex: 'enabled',
      key: 'enabled',
      width: 100,
      render: (enabled: boolean) => (
        <Tag color={enabled ? 'green' : 'default'}>
          {enabled ? t('common.enabled') : t('common.disabled')}
        </Tag>
      ),
    },
    {
      title: t('routes.description'),
      dataIndex: 'description',
      key: 'description',
      width: 200,
      ellipsis: true,
      render: (text) => (
        <span style={{ fontSize: '12px', color: '#64748b' }}>
          {text || '-'}
        </span>
      ),
    },
    {
      title: t('common.actions'),
      key: 'actions',
      width: 180,
      fixed: 'right',
      render: (_, record) => (
        <Space size="small">
          <Button 
            icon={<EditOutlined />} 
            size="small"
            onClick={() => showRouteEdit(record)}
          >
            {t('common.edit')}
          </Button>
          {record.enabled ? (
            <Button 
              icon={<StopOutlined />} 
              size="small"
              onClick={() => handleDisableRoute(record)}
            >
              {t('common.disable')}
            </Button>
          ) : (
            <Button 
              type="primary"
              icon={<PlayCircleOutlined />} 
              size="small"
              onClick={() => handleEnableRoute(record)}
            >
              {t('common.enable')}
            </Button>
          )}
          <Button 
            icon={<EyeOutlined />} 
            size="small"
            onClick={() => showRouteDetail(record)}
          >
            {t('common.detail')}
          </Button>
          <Button 
            danger
            icon={<DeleteOutlined />} 
            size="small"
            onClick={() => handleDelete(record)}
          >
            {t('common.delete')}
          </Button>
        </Space>
      ),
    },
  ];

  return (
    <div className="page-container">
      {/* Page Header */}
      <div className="page-header">
        <div className="page-title">
          <h1 className="title">{t('routes.title')}</h1>
          <span className="subtitle">{t('routes.description_helper')}</span>
        </div>
        <div className="page-actions">
          <Space>
            <Input.Search
              placeholder={t('routes.search_placeholder')}
              value={searchTerm}
              onChange={(e) => setSearchTerm(e.target.value)}
              style={{ width: 250 }}
              allowClear
            />
            <Select
              placeholder={t('routes.status_filter_placeholder')}
              value={statusFilter}
              onChange={setStatusFilter}
              style={{ width: 150 }}
              allowClear
            >
              <Select.Option value="all">{t('common.all')}</Select.Option>
              <Select.Option value="enabled">{t('common.enabled')}</Select.Option>
              <Select.Option value="disabled">{t('common.disabled')}</Select.Option>
            </Select>
            <Button 
              danger
              disabled={selectedRows.length === 0}
              onClick={handleBatchDelete}
            >
              {t('common.batch_delete')}
            </Button>
            <Button type="primary" icon={<PlusOutlined />} onClick={() => setCreateDrawerVisible(true)}>
              {t('routes.create')}
            </Button>
          </Space>
        </div>
      </div>
      
      {/* Content Card */}
      <Card className="content-card" variant="borderless">
        <Spin spinning={loading}>
          <Table 
            rowSelection={rowSelection}
            columns={columns} 
            dataSource={filteredRoutes} 
            rowKey="id"
            scroll={{ x: 1200 }}
            locale={{
              emptyText: (
                <Empty 
                  description={<span style={{ fontSize: '14px', color: '#64748B' }}>{t('routes.empty_description')}</span>}
                  image={<CompassOutlined style={{ fontSize: 64, color: '#CBD5E1' }} />}
                >
                  <Button 
                    type="primary" 
                    icon={<PlusOutlined />}
                    size="large"
                    onClick={() => setCreateDrawerVisible(true)}
                  >
                    {t('routes.create_first')}
                  </Button>
                </Empty>
              )
            }}
            pagination={{ pageSize: 10, showSizeChanger: true }}
          />
        </Spin>
      </Card>

      {/* Create Route Drawer */}
      <Drawer
        title={t('routes.create')}
        placement="right"
        size="large"
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
          initialValues={{
            order: 0,
            enabled: true,
            targetType: 'static'
          }}
        >
          {/* Basic Info Section */}
          <div style={{ marginBottom: 24 }}>
            <h3 style={{ fontSize: 16, fontWeight: 600, color: '#1e293b', marginBottom: 16 }}>{t('routes.basic_info')}</h3>
            
            <Form.Item
              name="id"
              label={t('routes.route_id_label')}
              rules={[{ required: true, message: t('routes.name_required') }]}
              extra={t('routes.route_id_helper')}
            >
              <Input placeholder={t('routes.route_id_placeholder')} />
            </Form.Item>

            <Form.Item
              name="order"
              label={t('routes.order')}
              extra={t('routes.order_helper')}
            >
              <Input type="number" placeholder="0" />
            </Form.Item>

            <Form.Item
              name="description"
              label={t('routes.description_label')}
            >
              <TextArea rows={1} placeholder={t('routes.description_placeholder')} style={{ minHeight: '38px' }} />
            </Form.Item>
          </div>

          {/* Target Section */}
          <div style={{ marginBottom: 24, borderTop: '1px solid #e2e8f0', paddingTop: 24 }}>
            <h3 style={{ fontSize: 16, fontWeight: 600, color: '#1e293b', marginBottom: 16 }}>{t('routes.target_configuration')}</h3>
            
            <Form.Item
              name="targetType"
              label={t('routes.target_type_label')}
              rules={[{ required: true }]}
              initialValue="static"
            >
              <Radio.Group buttonStyle="solid" onChange={(e) => {
                const newType = e.target.value;
                setTargetType(newType); // 更新状态
                if (newType === 'discovery') {
                  loadNacosServices();
                }
                // Clear service selection and URI when type changes
                createForm.setFieldsValue({
                  serviceId: undefined,
                  nacosServiceId: undefined,
                  uri: ''
                });
              }}>
                <Radio.Button value="static">{t('routes.static_node')}</Radio.Button>
                <Radio.Button value="discovery">{t('routes.service_discovery')}</Radio.Button>
              </Radio.Group>
            </Form.Item>

            {/* Static Node Service Selection */}
            {targetType === 'static' && (
              <Form.Item 
                name="serviceId"
                label={t('routes.target_service')}
                rules={[{ required: true, message: t('services.name_required') }]}
                extra={t('routes.select_service')}
              >
                <Select 
                  placeholder={t('routes.select_service')}
                  onChange={(value) => {
                    if (value) {
                      createForm.setFieldValue('uri', `static://${value}`);
                    } else {
                      createForm.setFieldValue('uri', '');
                    }
                  }}
                >
                  {services.map(s => (
                    <Select.Option key={s.serviceId} value={s.serviceId}>
                      {s.serviceId} ({s.name})
                    </Select.Option>
                  ))}
                </Select>
              </Form.Item>
            )}

            {/* Discovery Service Selection */}
            {targetType === 'discovery' && (
              <Form.Item 
                name="nacosServiceId"
                label={t('routes.target_service_discovery')}
                rules={[{ required: true, message: 'Please select a Nacos service' }]}
                extra={t('routes.select_nacos_service')}
              >
                <Select 
                  placeholder={t('routes.select_nacos_service')}
                  showSearch
                  onChange={(value) => {
                    if (value) {
                      createForm.setFieldValue('uri', `lb://${value}`);
                    } else {
                      createForm.setFieldValue('uri', '');
                    }
                  }}
                >
                  {nacosServices.map(s => (
                    <Select.Option key={s.serviceName} value={s.serviceName}>
                      {s.serviceName}
                    </Select.Option>
                  ))}
                </Select>
              </Form.Item>
            )}

            <Form.Item
              name="uri"
              label={t('routes.target_uri')}
              extra={t('routes.uri_auto_generated')}
            >
              <Input placeholder={t('routes.uri_placeholder')} disabled />
            </Form.Item>
          </div>

          {/* Predicates Section */}
          <div style={{ marginBottom: 24, borderTop: '1px solid #e2e8f0', paddingTop: 24 }}>
            <h3 style={{ fontSize: 16, fontWeight: 600, color: '#1e293b', marginBottom: 16 }}>
              {t('routes.predicates_section')} <span style={{ color: '#ef4444', fontSize: 12 }}>{t('routes.at_least_one_path')}</span>
            </h3>
            
            <Form.List name="predicates">
              {(fields, { add, remove }) => (
                <>
                  {fields.map(({ key, name, ...restField }) => (
                    <PredicateItem 
                      key={key}
                      form={createForm}
                      restField={restField}
                      name={name}
                      t={t}
                      onRemove={remove}
                    />
                  ))}
                  <Button type="dashed" onClick={() => add()} block icon={<PlusOutlined />}>
                    {t('routes.add_predicate')}
                  </Button>
                </>
              )}
            </Form.List>
          </div>

          {/* Route Plugins Section */}
          <div style={{ marginBottom: 24, borderTop: '1px solid #e2e8f0', paddingTop: 24 }}>
            <h3 style={{ fontSize: 16, fontWeight: 600, color: '#1e293b', marginBottom: 16 }}>{t('routes.filters_section')}</h3>
            
            <Form.List name="filters">
              {(fields, { add, remove }) => (
                <>
                  {fields.map(({ key, name, ...restField }) => (
                    <FilterItem 
                      key={key}
                      form={createForm}
                      restField={restField}
                      name={name}
                      t={t}
                      onRemove={remove}
                    />
                  ))}
                  <Button type="dashed" onClick={() => add()} block icon={<PlusOutlined />}>
                    {t('routes.add_filter')}
                  </Button>
                </>
              )}
            </Form.List>
          </div>

          {/* Enable Switch */}
          <Form.Item
            name="enabled"
            label={t('routes.enabled')}
            valuePropName="checked"
          >
            <Switch checkedChildren={t('common.enabled')} unCheckedChildren={t('common.disabled')} />
          </Form.Item>

          <Form.Item style={{ borderTop: '1px solid #e2e8f0', paddingTop: 24, marginTop: 24 }}>
            <Space>
              <Button type="primary" htmlType="submit" size="large">
                {t('routes.create')}
              </Button>
              <Button onClick={() => setCreateDrawerVisible(false)} size="large">
                {t('common.cancel')}
              </Button>
            </Space>
          </Form.Item>
        </Form>
      </Drawer>
      
      {/* Route Edit Drawer */}
      <Drawer
        title={t('routes.edit')}
        placement="right"
        size="large"
        open={editDrawerVisible}
        onClose={() => {
          setEditDrawerVisible(false);
          editForm.resetFields();
        }}
      >
        <Form
          form={editForm}
          layout="vertical"
          onFinish={handleUpdate}
        >
          {/* Route ID (hidden, for submission) */}
          <Form.Item name="id" hidden>
            <Input />
          </Form.Item>
      
          {/* Route Name */}
          <Form.Item
            name="routeName"
            label={t('routes.route_name')}
            rules={[{ required: true, message: t('routes.route_name_required') }]}
            extra={t('routes.route_name_helper')}
          >
            <Input placeholder={t('routes.route_name_placeholder')} disabled />
          </Form.Item>
      
          {/* Order */}
          <Form.Item
            name="order"
            label={t('routes.order')}
            extra={t('routes.order_helper')}
          >
            <Input type="number" placeholder="0" />
          </Form.Item>
      
          {/* Description */}
          <Form.Item
            name="description"
            label={t('routes.description_label')}
          >
            <TextArea rows={1} placeholder={t('routes.description_placeholder')} style={{ minHeight: '38px' }} />
          </Form.Item>
      
          {/* Target Section */}
          <div style={{ marginBottom: 24, borderTop: '1px solid #e2e8f0', paddingTop: 24 }}>
            <h3 style={{ fontSize: 16, fontWeight: 600, color: '#1e293b', marginBottom: 16 }}>{t('routes.target_configuration')}</h3>
                  
            <Form.Item
              name="targetType"
              label={t('routes.target_type_label')}
              rules={[{ required: true }]}
            >
              <Radio.Group buttonStyle="solid" onChange={(e) => {
                const newType = e.target.value;
                setEditTargetType(newType);
                if (newType === 'discovery') {
                  loadNacosServices();
                }
                // Clear service selection and URI when type changes
                editForm.setFieldsValue({
                  serviceId: undefined,
                  nacosServiceId: undefined,
                  uri: ''
                });
              }}>
                <Radio.Button value="static">{t('routes.static_node')}</Radio.Button>
                <Radio.Button value="discovery">{t('routes.service_discovery')}</Radio.Button>
              </Radio.Group>
            </Form.Item>
      
            {/* Static Node Service Selection */}
            {editTargetType === 'static' && (
              <Form.Item 
                name="serviceId"
                label={t('routes.target_service')}
                rules={[{ required: true, message: t('services.name_required') }]}
                extra={t('routes.select_service')}
              >
                <Select 
                  placeholder={t('routes.select_service')}
                  onChange={(value) => {
                    if (value) {
                      editForm.setFieldValue('uri', `static://${value}`);
                    } else {
                      editForm.setFieldValue('uri', '');
                    }
                  }}
                >
                  {services.map(s => (
                    <Select.Option key={s.serviceId} value={s.serviceId}>
                      {s.serviceId} ({s.name})
                    </Select.Option>
                  ))}
                </Select>
              </Form.Item>
            )}
      
            {/* Discovery Service Selection */}
            {editTargetType === 'discovery' && (
              <Form.Item 
                name="nacosServiceId"
                label={t('routes.target_service_discovery')}
                rules={[{ required: true, message: 'Please select a Nacos service' }]}
                extra={t('routes.select_nacos_service')}
              >
                <Select 
                  placeholder={t('routes.select_nacos_service')}
                  showSearch
                  onChange={(value) => {
                    if (value) {
                      editForm.setFieldValue('uri', `lb://${value}`);
                    } else {
                      editForm.setFieldValue('uri', '');
                    }
                  }}
                >
                  {nacosServices.map(s => (
                    <Select.Option key={s.serviceName} value={s.serviceName}>
                      {s.serviceName}
                    </Select.Option>
                  ))}
                </Select>
              </Form.Item>
            )}
      
            <Form.Item
              name="uri"
              label={t('routes.target_uri')}
              extra={t('routes.uri_auto_generated')}
            >
              <Input placeholder={t('routes.uri_placeholder')} disabled />
            </Form.Item>
          </div>
      
          {/* Predicates Section */}
          <div style={{ marginBottom: 24, borderTop: '1px solid #e2e8f0', paddingTop: 24 }}>
            <h3 style={{ fontSize: 16, fontWeight: 600, color: '#1e293b', marginBottom: 16 }}>
              {t('routes.predicates_section')} <span style={{ color: '#ef4444', fontSize: 12 }}>{t('routes.at_least_one_path')}</span>
            </h3>
                  
            <Form.List name="predicates">
              {(fields, { add, remove }) => (
                <>
                  {fields.map(({ key, name, ...restField }) => (
                    <PredicateItem 
                      key={key}
                      form={editForm}
                      restField={restField}
                      name={name}
                      t={t}
                      onRemove={remove}
                    />
                  ))}
                  <Button type="dashed" onClick={() => add()} block icon={<PlusOutlined />}>
                    {t('routes.add_predicate')}
                  </Button>
                </>
              )}
            </Form.List>
          </div>
      
          {/* Route Plugins Section */}
          <div style={{ marginBottom: 24, borderTop: '1px solid #e2e8f0', paddingTop: 24 }}>
            <h3 style={{ fontSize: 16, fontWeight: 600, color: '#1e293b', marginBottom: 16 }}>{t('routes.filters_section')}</h3>
                  
            <Form.List name="filters">
              {(fields, { add, remove }) => (
                <>
                  {fields.map(({ key, name, ...restField }) => (
                    <FilterItem 
                      key={key}
                      form={editForm}
                      restField={restField}
                      name={name}
                      t={t}
                      onRemove={remove}
                    />
                  ))}
                  <Button type="dashed" onClick={() => add()} block icon={<PlusOutlined />}>
                    {t('routes.add_filter')}
                  </Button>
                </>
              )}
            </Form.List>
          </div>
      
          <Form.Item style={{ borderTop: '1px solid #e2e8f0', paddingTop: 24, marginTop: 24 }}>
            <Space>
              <Button type="primary" htmlType="submit" size="large">
                {t('common.update')}
              </Button>
              <Button onClick={() => setEditDrawerVisible(false)} size="large">
                {t('common.cancel')}
              </Button>
            </Space>
          </Form.Item>
        </Form>
      </Drawer>
      
      {/* Route Detail Drawer */}
      <Drawer
        title={t('routes.detail_title')}
        placement="right"
        size="large"
        open={detailDrawerVisible}
        onClose={() => setDetailDrawerVisible(false)}
      >
        {selectedRoute && (
          <div>
            <Descriptions bordered column={1} size="small">
              <Descriptions.Item label={t('routes.name')}>
                {selectedRoute.routeName}
              </Descriptions.Item>
              <Descriptions.Item label={t('routes.id')}>
                <Space>
                  {selectedRoute.id || 'N/A'}
                  {selectedRoute.id && (
                    <Button
                      type="link"
                      icon={<CopyOutlined />}
                      size="small"
                      onClick={() => copyToClipboard(selectedRoute.id, t('routes.id'))}
                    />
                  )}
                </Space>
              </Descriptions.Item>
              <Descriptions.Item label={t('routes.uri')}>
                <span style={{ fontFamily: 'monospace', fontSize: '12px' }}>
                  {selectedRoute.uri}
                </span>
              </Descriptions.Item>
              <Descriptions.Item label={t('routes.order')}>
                {selectedRoute.order ?? '-'}
              </Descriptions.Item>
              <Descriptions.Item label={t('routes.enabled')}>
                {selectedRoute.enabled !== false ? (
                  <Tag color="success">{t('common.enabled')}</Tag>
                ) : (
                  <Tag color="default">{t('common.disabled')}</Tag>
                )}
              </Descriptions.Item>
              <Descriptions.Item label={t('routes.description')}>
                {selectedRoute.description || '-'}
              </Descriptions.Item>
            </Descriptions>

            {selectedRoute.predicates && selectedRoute.predicates.length > 0 && (
              <Card title={t('routes.predicates')} size="small" style={{ marginTop: 16 }}>
                {selectedRoute.predicates.map((predicate, index) => (
                  <Card key={index} type="inner" size="small" style={{ marginBottom: 8 }}>
                    <strong>{predicate.name}</strong>
                    <pre style={{ margin: '8px 0', fontSize: '11px' }}>
                      {JSON.stringify(predicate.args, null, 2)}
                    </pre>
                  </Card>
                ))}
              </Card>
            )}

            {selectedRoute.filters && selectedRoute.filters.length > 0 && (
              <Card title={t('routes.filters')} size="small" style={{ marginTop: 16 }}>
                {selectedRoute.filters.map((filter, index) => (
                  <Card key={index} type="inner" size="small" style={{ marginBottom: 8 }}>
                    <strong>{filter.name}</strong>
                    <pre style={{ margin: '8px 0', fontSize: '11px' }}>
                      {JSON.stringify(filter.args, null, 2)}
                    </pre>
                  </Card>
                ))}
              </Card>
            )}
          </div>
        )}
      </Drawer>
    </div>
  );
};



export default RoutesPage;
