import { useState, useEffect } from 'react';
import { Table, Button, Space, Modal, message, Typography, Spin, Tag, Drawer, Form, Input, Card, Descriptions, Select, Empty } from 'antd';
import { PlusOutlined, DeleteOutlined, EyeOutlined } from '@ant-design/icons';
import type { ColumnsType } from 'antd/es/table';
import api from '../utils/api';
import { useTranslation } from 'react-i18next';

const { Title } = Typography;

interface ServiceInstance {
  ip: string;
  port: number;
  weight?: number;
  healthy?: boolean;
  enabled?: boolean;
}

interface Service {
  name: string;
  description?: string;
  loadBalancer?: string;
  instances?: ServiceInstance[];
  metadata?: any;
}

interface CreateServiceForm {
  name: string;
  loadBalancer?: string;
  instances?: ServiceInstance[];
}

// IP address validation regex (supports IPv4)
const IPV4_REGEX = /^(\d{1,3}\.){3}\d{1,3}$/;

/**
 * Validate IP address format
 */
const isValidIP = (ip: string): boolean => {
  if (!ip || !IPV4_REGEX.test(ip)) {
    return false;
  }
  // Check each octet is between 0-255
  const octets = ip.split('.').map(Number);
  return octets.every(octet => octet >= 0 && octet <= 255);
};

/**
 * Validate port number
 */
const isValidPort = (port: number): boolean => {
  return Number.isInteger(port) && port > 0 && port < 65536;
};

const ServicesPage: React.FC = () => {
  const [services, setServices] = useState<Service[]>([]);
  const [loading, setLoading] = useState(false);
  const [createDrawerVisible, setCreateDrawerVisible] = useState(false);
  const [editDrawerVisible, setEditDrawerVisible] = useState(false);
  const [detailDrawerVisible, setDetailDrawerVisible] = useState(false);
  const [selectedService, setSelectedService] = useState<Service | null>(null);
  const [editingService, setEditingService] = useState<Service | null>(null);
  const [searchTerm, setSearchTerm] = useState('');
  const [statusFilter, setStatusFilter] = useState<string>('all');
  const [createForm] = Form.useForm();
  const [editForm] = Form.useForm();
  const [instances, setInstances] = useState<ServiceInstance[]>([]);
  const [editInstances, setEditInstances] = useState<ServiceInstance[]>([]);
  const { t } = useTranslation();

  useEffect(() => {
    loadServices();
  }, []);

  const loadServices = async () => {
    try {
      setLoading(true);
      const response = await api.get('/api/services');
      if (response.data.code === 200) {
        setServices(response.data.data || []);
      }
    } catch (error: any) {
      message.error(t('message.load_services_failed', { error: error.message }));
    } finally {
      setLoading(false);
    }
  };

  const filteredServices = services.filter(service => {
    const matchesSearch = !searchTerm || 
      service.name.toLowerCase().includes(searchTerm.toLowerCase());
    
    // For status filter, we'll consider health based on instances
    if (statusFilter === 'healthy') {
      return service.instances && service.instances.some(instance => instance.healthy !== false);
    } else if (statusFilter === 'unhealthy') {
      return service.instances && service.instances.some(instance => instance.healthy === false);
    } else if (statusFilter === 'no_instances') {
      return !(service.instances && service.instances.length > 0);
    }
    // If statusFilter is 'all', return all matching search results
    return matchesSearch;
  });

  const handleCreate = (values: CreateServiceForm) => {
    // ✅ Validate instances before submission
    for (let i = 0; i < instances.length; i++) {
      const instance = instances[i];
      if (!isValidIP(instance.ip)) {
        message.error(t('message.invalid_ip_format', { index: i + 1, ip: instance.ip }));
        return;
      }
      if (!isValidPort(instance.port)) {
        message.error(t('message.invalid_port', { index: i + 1, port: instance.port }));
        return;
      }
    }

    const serviceData = {
      ...values,
      instances: instances.length > 0 ? instances : [],
      loadBalancer: values.loadBalancer || 'weighted',
    };
    
    api.post('/api/services', serviceData)
      .then(response => {
        if (response.data.code === 200) {
          message.success(t('message.create_success'));
          createForm.resetFields();
          setInstances([]);
          setCreateDrawerVisible(false);
          loadServices();
        } else {
          message.error(t('message.create_failed', { msg: response.data.message }));
        }
      })
      .catch(error => {
        message.error(t('message.create_failed', { msg: error.response?.data?.message || error.message }));
      });
  };

  const showServiceDetail = (record: Service) => {
    setSelectedService(record);
    setDetailDrawerVisible(true);
  };

  const handleEdit = (record: Service) => {
    setEditingService(record);
    setEditInstances(record.instances || []);
    editForm.setFieldsValue({
      name: record.name,
      loadBalancer: record.loadBalancer,
      description: record.description,
    });
    setDetailDrawerVisible(false);  // ✅ 关闭详情抽屉
    setEditDrawerVisible(true);     // ✅ 打开编辑抽屉
  };

  const handleUpdate = async (values: CreateServiceForm) => {
    if (!editingService) return;

    // ✅ Validate instances before submission
    for (let i = 0; i < editInstances.length; i++) {
      const instance = editInstances[i];
      if (!isValidIP(instance.ip)) {
        message.error(t('message.invalid_ip_format', { index: i + 1, ip: instance.ip }));
        return;
      }
      if (!isValidPort(instance.port)) {
        message.error(t('message.invalid_port', { index: i + 1, port: instance.port }));
        return;
      }
    }

    const serviceData = {
      ...values,
      instances: editInstances.length > 0 ? editInstances : [],
      loadBalancer: values.loadBalancer || 'weighted',
    };

    try {
      const response = await api.put(`/api/services/${editingService.name}`, serviceData);
      
      if (response.data.code === 200) {
        message.success(t('message.update_success'));
        editForm.resetFields();
        setEditInstances([]);
        setEditDrawerVisible(false);
        loadServices();
      } else {
        message.error(t('message.update_failed', { msg: response.data.message }));
      }
    } catch (error: any) {
      message.error(t('message.update_failed', { msg: error.response?.data?.message || error.message }));
    }
  };

  const updateEditInstance = (index: number, field: string, value: any) => {
    const newInstances = [...editInstances];
    newInstances[index] = { ...newInstances[index], [field]: value };
    setEditInstances(newInstances);
  };

  const addEditInstance = () => {
    setEditInstances([...editInstances, { ip: '', port: 8080, weight: 1 }]);
  };

  const removeEditInstance = (index: number) => {
    const newInstances = editInstances.filter((_, i) => i !== index);
    setEditInstances(newInstances);
  };

  /**
   * Edit instance weight in detail page
   */
  const handleEditWeight = async (instanceIndex: number) => {
    if (!selectedService) return;
    
    const instance = selectedService.instances?.[instanceIndex];
    if (!instance) return;

    // Create a simple modal with input
    Modal.confirm({
      title: t('modal.edit_weight_title', { ip: instance.ip, port: instance.port }),
      content: (
        <Input 
          defaultValue={String(instance.weight || 1)}
          placeholder={t('modal.weight_placeholder')}
          id="weight-input"
          onPressEnter={() => {
            const input = document.getElementById('weight-input') as HTMLInputElement;
            if (input) {
              handleWeightSubmit(selectedService.name, instanceIndex, parseInt(input.value));
            }
          }}
        />
      ),
      okText: t('common.confirm'),
      cancelText: t('common.cancel'),
      onOk: () => {
        const input = document.getElementById('weight-input') as HTMLInputElement;
        if (input) {
          return handleWeightSubmit(selectedService.name!, instanceIndex, parseInt(input.value));
        }
      },
    });
  };

  const handleWeightSubmit = async (serviceName: string, instanceIndex: number, newWeight: number) => {
    if (isNaN(newWeight) || newWeight < 1 || newWeight > 100) {
      message.error(t('message.weight_range_error'));
      return;
    }

    try {
      // Get current service data
      const service = { ...selectedService! };
      if (service.instances) {
        service.instances[instanceIndex].weight = newWeight;
      }

      // Update via API
      const response = await api.put(`/api/services/${serviceName}`, service);
      
      if (response.data.code === 200) {
        message.success(t('message.weight_update_success'));
        loadServices(); // Refresh list
        // Update selected service data
        setSelectedService(service);
      } else {
        message.error(t('message.update_failed', { msg: response.data.message }));
      }
    } catch (error: any) {
      message.error(t('message.update_failed', { msg: error.response?.data?.message || error.message }));
    }
  };

  /**
   * Toggle instance online/offline status
   */
  const handleToggleOnline = async (instanceIndex: number) => {
    if (!selectedService) return;
    
    const instance = selectedService.instances?.[instanceIndex];
    if (!instance) return;

    const newEnabled = instance.enabled !== false;
    const action = newEnabled ? t('common.offline') : t('common.online');
    
    Modal.confirm({
      title: t('modal.confirm_toggle_title', { action }),
      content: t('modal.confirm_toggle_content', { action, ip: instance.ip, port: instance.port }),
      okText: t('common.confirm'),
      cancelText: t('common.cancel'),
      onOk: async () => {
        try {
          // Get current service data
          const service = { ...selectedService! };
          if (service.instances) {
            service.instances[instanceIndex].enabled = !newEnabled;
          }

          // Update via API
          const response = await api.put(`/api/services/${selectedService.name}`, service);
          
          if (response.data.code === 200) {
            message.success(t('message.toggle_success', { action }));
            loadServices(); // Refresh list
            // Update selected service data
            setSelectedService(service);
          } else {
            message.error(t('message.update_failed', { msg: response.data.message }));
          }
        } catch (error: any) {
          message.error(t('message.update_failed', { msg: error.response?.data?.message || error.message }));
        }
      },
    });
  };

  /**
   * Remove instance from service
   */
  const handleRemoveInstance = async (instanceIndex: number) => {
    if (!selectedService) return;
    
    const instance = selectedService.instances?.[instanceIndex];
    if (!instance) return;

    Modal.confirm({
      title: t('modal.confirm_remove_title'),
      content: t('modal.confirm_remove_content', { ip: instance.ip, port: instance.port }),
      okText: t('common.confirm'),
      cancelText: t('common.cancel'),
      onOk: async () => {
        try {
          // Get current service data
          const service = { ...selectedService! };
          if (service.instances) {
            service.instances.splice(instanceIndex, 1);
          }

          // Update via API
          const response = await api.put(`/api/services/${selectedService.name}`, service);
          
          if (response.data.code === 200) {
            message.success(t('message.remove_success'));
            loadServices(); // Refresh list
            // Update selected service data
            setSelectedService(service);
          } else {
            message.error(t('message.update_failed', { msg: response.data.message }));
          }
        } catch (error: any) {
          message.error(t('message.update_failed', { msg: error.response?.data?.message || error.message }));
        }
      },
    });
  };

  const addInstance = () => {
    const newInstance: ServiceInstance = {
      ip: '',
      port: 8080,
      weight: 1,
      healthy: true,
      enabled: true
    };
    setInstances([...instances, newInstance]);
  };

  const removeInstance = (index: number) => {
    const newInstances = instances.filter((_, i) => i !== index);
    setInstances(newInstances);
  };

  const updateInstance = (index: number, field: keyof ServiceInstance, value: any) => {
    const newInstances = [...instances];
    newInstances[index] = { ...newInstances[index], [field]: value };
    setInstances(newInstances);
  };

  const handleDelete = (record: Service) => {
    Modal.confirm({
      title: t('common.confirm'),
      content: t('message.confirm_delete_service', { name: record.name }),
      okText: t('common.delete'),
      okType: 'danger',
      cancelText: t('common.cancel'),
      onOk: async () => {
        try {
          // First check if service is referenced by any route
          const usageResponse = await api.get(`/api/services/${record.name}/usage`);
          const referencingRoutes = usageResponse.data.data;
          
          if (referencingRoutes && referencingRoutes.length > 0) {
            // Service is in use, show error and prevent deletion
            Modal.error({
              title: t('common.error'),
              content: (
                <div>
                  <p>{t('message.service_in_use')}</p>
                  <ul style={{ marginTop: '8px', paddingLeft: '20px' }}>
                    {referencingRoutes.map((route: string) => (
                      <li key={route}>{route}</li>
                    ))}
                  </ul>
                  <p style={{ marginTop: '12px', color: '#999', fontSize: '13px' }}>
                    {t('message.please_remove_routes_first')}
                  </p>
                </div>
              ),
              width: 400,
            });
            return;
          }
          
          // Not in use, proceed with deletion
          const response = await api.delete(`/api/services/${record.name}`);
          
          if (response.data.code === 200) {
            message.success(t('message.delete_success'));
            loadServices();
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

  const columns: ColumnsType<Service> = [
    {
      title: t('services.name'),
      dataIndex: 'name',
      key: 'name',
      width: 200,
      render: (text) => (
        <Space>
          <span style={{ fontWeight: 500 }}>{text}</span>
        </Space>
      ),
    },
    {
      title: t('services.instances'),
      key: 'instances',
      width: 250,
      render: (_, record) => {
        const instances = record.instances || [];
        if (instances.length === 0) {
          return <span style={{ color: '#999' }}>{t('services.no_instances')}</span>;
        }
        const showWeight = instances.length > 1; // ✅ 只有多实例才显示权重
        return (
          <div style={{ display: 'flex', flexDirection: 'column', gap: '4px' }}>
            {instances.map((instance, index) => (
              <div 
                key={index} 
                style={{ 
                  display: 'flex', 
                  alignItems: 'center', 
                  gap: '8px',
                  padding: '2px 6px',
                  borderRadius: '4px',
                  backgroundColor: instance.healthy !== false ? '#f6ffed' : '#fff2e8'
                }}
              >
                <Tag color={instance.healthy !== false ? 'green' : 'red'} style={{ minWidth: '32px' }}>
                  {instance.healthy !== false ? '✓' : '✗'}
                </Tag>
                <span style={{ fontSize: '13px', color: '#333' }}>
                  {instance.ip}:{instance.port}
                  {showWeight && (
                    <span style={{ marginLeft: '8px', color: '#999', fontSize: '12px' }}>
                      ({t('services.weight_label')} {instance.weight || 1})
                    </span>
                  )}
                </span>
              </div>
            ))}
          </div>
        );
      },
    },
    {
      title: t('services.loadBalancer'),
      dataIndex: 'loadBalancer',
      key: 'loadBalancer',
      width: 150,
      render: (text) => text ? <Tag color="blue">{t(`services.${text}`)}</Tag> : <Tag>-</Tag>,
    },
    {
      title: t('services.healthCheck'),
      key: 'healthCheck',
      width: 150,
      render: (_, record) => {
        const total = record.instances?.length || 0;
        const healthy = record.instances?.filter(instance => instance.healthy !== false).length || 0;
        
        if (total === 0) {
          return <Tag color="default">{t('common.disabled')}</Tag>;
        } else if (healthy === total) {
          return <Tag color="success">✅ {t('common.normal')}</Tag>;
        } else if (healthy === 0) {
          return <Tag color="error">❌ {t('common.offline')}</Tag>;
        } else {
          return <Tag color="warning">⚠️ {t('common.abnormal')}</Tag>;
        }
      },
    },
    {
      title: t('services.actions'),
      key: 'actions',
      width: 250,
      fixed: 'right',
      render: (_, record) => (
        <Space size="small">
          <Button 
            icon={<EyeOutlined />} 
            size="small"
            onClick={() => showServiceDetail(record)}
          >
            {t('common.view_instances')}
          </Button>
          <Button 
            type="primary"
            size="small"
            onClick={() => handleEdit(record)}
          >
            {t('common.edit_service')}
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
    <Card 
      title={
        <div>
          <Title level={4} style={{ margin: 0, display: 'inline-block' }}>{t('services.title')}</Title>
          <div style={{ display: 'inline-block', marginLeft: 16, color: '#999', fontSize: '14px' }}>
            {t('services.description_helper')}
          </div>
        </div>
      }
      extra={
        <Space>
          <Input.Search
            placeholder={t('services.search_placeholder')}
            value={searchTerm}
            onChange={(e) => setSearchTerm(e.target.value)}
            style={{ width: 250 }}
          />
          <Select
            placeholder={t('services.status_filter_placeholder')}
            value={statusFilter}
            onChange={setStatusFilter}
            style={{ width: 150 }}
            allowClear
          >
            <Select.Option value="all">{t('common.all')}</Select.Option>
            <Select.Option value="healthy">{t('common.healthy')}</Select.Option>
            <Select.Option value="unhealthy">{t('common.unhealthy')}</Select.Option>
            <Select.Option value="no_instances">{t('services.no_instances')}</Select.Option>
          </Select>
          <Button 
            type="primary" 
            icon={<PlusOutlined />}
            onClick={() => setCreateDrawerVisible(true)}
          >
            {t('services.create')}
          </Button>
        </Space>
      }
    >
      <Spin spinning={loading}>
        <Table 
          columns={columns} 
          dataSource={filteredServices} 
          rowKey={(record) => record.name}
          scroll={{ x: 1000 }}
          locale={{
            emptyText: (
              <Empty 
                description={t('services.empty_description')}
                image={Empty.PRESENTED_IMAGE_SIMPLE}
              >
                <Button 
                  type="primary" 
                  icon={<PlusOutlined />}
                  onClick={() => setCreateDrawerVisible(true)}
                >
                  {t('services.create_first')}
                </Button>
              </Empty>
            )
          }}
          pagination={{ pageSize: 10, showSizeChanger: true }}
        />
      </Spin>

      {/* Create Service Drawer */}
      <Drawer
        title={t('services.create')}
        placement="right"
        width={600}
        open={createDrawerVisible}
        onClose={() => {
          setCreateDrawerVisible(false);
          createForm.resetFields();
          setInstances([]);
        }}
      >
        <Form
          form={createForm}
          layout="vertical"
          onFinish={handleCreate}
        >
          <Form.Item
            name="name"
            label={t('services.name')}
            rules={[{ required: true, message: t('services.name_required') }]}
            extra={t('services.name_helper')}
          >
            <Input placeholder={t('services.name_placeholder')} />
          </Form.Item>

          <Form.Item
            name="description"
            label={t('services.description')}
          >
            <Input.TextArea 
              rows={3} 
              placeholder={t('services.description_placeholder')} 
            />
          </Form.Item>

          <Form.Item
            name="loadBalancer"
            label={t('services.loadBalancer')}
            initialValue="weighted"
          >
            <Select placeholder={t('services.loadBalancer_placeholder')}>
              <Select.Option value="round-robin">{t('services.round_robin')}</Select.Option>
              <Select.Option value="random">{t('services.random')}</Select.Option>
              <Select.Option value="weighted">{t('services.weighted')}</Select.Option>
              <Select.Option value="least-connections">{t('services.least_connections')}</Select.Option>
            </Select>
          </Form.Item>

          <Form.Item label={t('services.service_instances')}>
            <div style={{ border: '1px solid #e8e8e8', borderRadius: '6px', padding: '12px' }}>
              {instances.map((instance, index) => (
                <div key={index} style={{ display: 'flex', gap: '8px', marginBottom: '8px', alignItems: 'center' }}>
                  <Input
                    placeholder={t('services.placeholder_ip')}
                    value={instance.ip}
                    onChange={(e) => updateInstance(index, 'ip', e.target.value)}
                    style={{ width: '140px' }}
                  />
                  <Input
                    type="number"
                    placeholder={t('services.placeholder_port')}
                    value={instance.port}
                    onChange={(e) => updateInstance(index, 'port', parseInt(e.target.value) || 8080)}
                    style={{ width: '80px' }}
                  />
                  <Input
                    type="number"
                    placeholder={t('services.placeholder_weight')}
                    value={instance.weight}
                    onChange={(e) => updateInstance(index, 'weight', parseInt(e.target.value) || 1)}
                    style={{ width: '70px' }}
                  />
                  <Select
                    value={instance.enabled !== false ? 'enabled' : 'disabled'}
                    onChange={(value) => updateInstance(index, 'enabled', value === 'enabled')}
                    style={{ width: '90px' }}
                  >
                    <Select.Option value="enabled">{t('common.enabled')}</Select.Option>
                    <Select.Option value="disabled">{t('common.disabled')}</Select.Option>
                  </Select>
                  <Button
                    danger
                    size="small"
                    onClick={() => removeInstance(index)}
                  >
                    ×
                  </Button>
                </div>
              ))}
              <Button
                type="dashed"
                onClick={addInstance}
                style={{ marginTop: '8px', width: '100%' }}
              >
                + {t('services.add_instance')}
              </Button>
              {/* 创建服务时，如果移除了所有实例 */}
              {instances.length === 0 && (
                <div style={{ marginTop: '12px', padding: '8px 12px', backgroundColor: '#fff7e6', border: '1px solid #ffd591', borderRadius: '4px' }}>
                  <span style={{ color: '#d46b08', fontSize: '13px' }}>
                    {t('services.warning_no_instances')}
                  </span>
                </div>
              )}
              {instances.length > 0 && instances.every(inst => inst.enabled === false) && (
                <div style={{ marginTop: '12px', padding: '8px 12px', backgroundColor: '#fff7e6', border: '1px solid #ffd591', borderRadius: '4px' }}>
                  <span style={{ color: '#d46b08', fontSize: '13px' }}>
                    {t('services.warning_all_disabled')}
                  </span>
                </div>
              )}
            </div>
          </Form.Item>

          <Form.Item>
            <Space>
              <Button type="primary" htmlType="submit">
                {t('common.create')}
              </Button>
              <Button onClick={() => setCreateDrawerVisible(false)}>
                {t('common.cancel')}
              </Button>
            </Space>
          </Form.Item>
        </Form>
      </Drawer>

      {/* Edit Service Drawer */}
      <Drawer
        title={t('services.edit')}
        placement="right"
        width={600}
        open={editDrawerVisible}
        onClose={() => {
          setEditDrawerVisible(false);
          editForm.resetFields();
          setEditInstances([]);
        }}
      >
        <Form
          form={editForm}
          layout="vertical"
          onFinish={handleUpdate}
        >
          <Form.Item
            name="name"
            label={t('services.name')}
            rules={[{ required: true, message: t('services.name_required') }]}
            extra={t('services.name_helper')}
          >
            <Input placeholder={t('services.name_placeholder')} />
          </Form.Item>

          <Form.Item
            name="loadBalancer"
            label={t('services.loadBalancer')}
            initialValue="weighted"
          >
            <Select placeholder={t('services.loadBalancer_placeholder')}>
              <Select.Option value="round-robin">{t('services.round_robin')}</Select.Option>
              <Select.Option value="random">{t('services.random')}</Select.Option>
              <Select.Option value="weighted">{t('services.weighted')}</Select.Option>
              <Select.Option value="least-connections">{t('services.least_connections')}</Select.Option>
            </Select>
          </Form.Item>

          <Form.Item
            name="description"
            label={t('services.description')}
          >
            <Input.TextArea 
              rows={3} 
              placeholder={t('services.description_placeholder')} 
            />
          </Form.Item>

          <Form.Item label={t('services.service_instances')}>
            <div style={{ border: '1px solid #e8e8e8', borderRadius: '6px', padding: '12px' }}>
              {editInstances.map((instance, index) => (
                <div key={index} style={{ display: 'flex', gap: '8px', marginBottom: '8px', alignItems: 'center' }}>
                  <Input
                    placeholder={t('services.placeholder_ip')}
                    value={instance.ip}
                    onChange={(e) => updateEditInstance(index, 'ip', e.target.value)}
                    style={{ width: '140px' }}
                  />
                  <Input
                    type="number"
                    placeholder={t('services.placeholder_port')}
                    value={instance.port}
                    onChange={(e) => updateEditInstance(index, 'port', parseInt(e.target.value) || 8080)}
                    style={{ width: '80px' }}
                  />
                  <Input
                    type="number"
                    placeholder={t('services.placeholder_weight')}
                    value={instance.weight}
                    onChange={(e) => updateEditInstance(index, 'weight', parseInt(e.target.value) || 1)}
                    style={{ width: '70px' }}
                  />
                  <Select
                    value={instance.enabled !== false ? 'enabled' : 'disabled'}
                    onChange={(value) => updateEditInstance(index, 'enabled', value === 'enabled')}
                    style={{ width: '90px' }}
                  >
                    <Select.Option value="enabled">{t('common.enabled')}</Select.Option>
                    <Select.Option value="disabled">{t('common.disabled')}</Select.Option>
                  </Select>
                  <Button
                    danger
                    size="small"
                    onClick={() => removeEditInstance(index)}
                  >
                    ×
                  </Button>
                </div>
              ))}
              <Button
                type="dashed"
                onClick={addEditInstance}
                style={{ marginTop: '8px', width: '100%' }}
              >
                + {t('services.add_instance')}
              </Button>
              {/* 编辑服务时，如果移除了所有实例 */}
              {editInstances.length === 0 && (
                <div style={{ marginTop: '12px', padding: '8px 12px', backgroundColor: '#fff7e6', border: '1px solid #ffd591', borderRadius: '4px' }}>
                  <span style={{ color: '#d46b08', fontSize: '13px' }}>
                    {t('services.warning_no_instances')}
                  </span>
                </div>
              )}
              {editInstances.length > 0 && editInstances.every(inst => inst.enabled === false) && (
                <div style={{ marginTop: '12px', padding: '8px 12px', backgroundColor: '#fff7e6', border: '1px solid #ffd591', borderRadius: '4px' }}>
                  <span style={{ color: '#d46b08', fontSize: '13px' }}>
                    {t('services.warning_all_disabled')}
                  </span>
                </div>
              )}
            </div>
          </Form.Item>

          <Form.Item>
            <Space>
              <Button type="primary" htmlType="submit">
                {t('common.update')}
              </Button>
              <Button onClick={() => setEditDrawerVisible(false)}>
                {t('common.cancel')}
              </Button>
            </Space>
          </Form.Item>
        </Form>
      </Drawer>

      {/* Service Detail Drawer */}
      <Drawer
        title={t('services.detail_title')}
        placement="right"
        width={700}
        open={detailDrawerVisible}
        onClose={() => setDetailDrawerVisible(false)}
      >
        {selectedService && (
          <div>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 16 }}>
              <Title level={5}>{selectedService.name}</Title>
              <Button 
                type="primary"
                onClick={() => handleEdit(selectedService)}
              >
                {t('common.edit_service')}
              </Button>
            </div>
            
            <Descriptions bordered column={1} size="small">
              <Descriptions.Item label={t('services.name')}>
                {selectedService.name}
              </Descriptions.Item>
              <Descriptions.Item label={t('services.loadBalancer')}>
                {selectedService.loadBalancer ? t(`services.${selectedService.loadBalancer}`) : '-'}
              </Descriptions.Item>
            </Descriptions>

            <Card title={t('services.instances')} size="small" style={{ marginTop: 16 }}>
              {selectedService.instances && selectedService.instances.length > 0 ? (
                <Table
                    dataSource={selectedService.instances}
                    columns={[
                      {
                        title: t('services.ip_port'),
                        dataIndex: 'ip',
                        key: 'ip',
                        render: (ip, record) => `${ip}:${record.port}`
                      },
                      {
                        title: t('services.weight'),
                        dataIndex: 'weight',
                        key: 'weight',
                        render: (weight) => weight || 1
                      },
                      {
                        title: t('services.healthy'),
                        dataIndex: 'healthy',
                        key: 'healthy',
                        render: (healthy) => healthy !== false ? t('common.yes') : t('common.no')
                      },
                      {
                        title: t('services.enabled'),
                        dataIndex: 'enabled',
                        key: 'enabled',
                        render: (enabled) => enabled !== false ? t('common.yes') : t('common.no')
                      },
                      {
                        title: t('common.actions'),
                        key: 'actions',
                        render: (_, __, index) => (
                          <Space size="small">
                            <Button 
                              size="small"
                              onClick={() => handleEditWeight(index)}
                            >
                              {t('common.edit_weight')}
                            </Button>
                            <Button 
                              size="small"
                              onClick={() => handleToggleOnline(index)}
                            >
                              {t('common.toggle_online')}
                            </Button>
                            <Button 
                              danger 
                              size="small"
                              onClick={() => handleRemoveInstance(index)}
                            >
                              {t('common.remove_instance')}
                            </Button>
                          </Space>
                        )
                      }
                    ]}
                    pagination={false}
                    rowKey={(record, index) => `${record.ip}-${record.port}-${index}`}
                  />
              ) : (
                <p>{t('services.no_instances')}</p>
              )}
            </Card>
          </div>
        )}
      </Drawer>
    </Card>
  );
};

export default ServicesPage;
