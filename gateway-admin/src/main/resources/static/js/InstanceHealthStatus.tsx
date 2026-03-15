/**
 * 健康状态展示组件示例（React/TypeScript）
 * 
 * 使用方法：
 * 1. 在服务列表页面中引入此组件
 * 2. 调用 Admin API 获取健康状态
 * 3. 展示健康图标和详细信息
 */

import React, { useState, useEffect } from 'react';
import { CheckCircleOutlined, CloseCircleOutlined, SyncOutlined } from '@ant-design/icons';
import { Tag, Tooltip, Space, Table } from 'antd';

interface InstanceHealth {
  serviceId: string;
  ip: string;
  port: number;
  healthy: boolean;
  consecutiveFailures: number;
  lastRequestTime?: number;
  checkType: 'PASSIVE' | 'ACTIVE';
  unhealthyReason?: string;
}

interface HealthStatusProps {
  serviceId: string;
}

/**
 * 健康状态组件
 */
export const InstanceHealthStatus: React.FC<HealthStatusProps> = ({ serviceId }) => {
  const [healthList, setHealthList] = useState<InstanceHealth[]>([]);
  const [loading, setLoading] = useState(false);

  // 定期刷新健康状态（每 5 秒）
  useEffect(() => {
    fetchHealthStatus();
    
    const interval = setInterval(() => {
      fetchHealthStatus();
    }, 5000);

    return () => clearInterval(interval);
  }, [serviceId]);

  // 获取健康状态
  const fetchHealthStatus = async () => {
    try {
      setLoading(true);
      const response = await fetch(`/api/gateway/services/${serviceId}/instances/health`);
      if (response.ok) {
        const data = await response.json();
        setHealthList(data);
      }
    } catch (error) {
      console.error('Failed to fetch health status:', error);
    } finally {
      setLoading(false);
    }
  };

  // 渲染健康状态图标
  const renderHealthIcon = (health: InstanceHealth) => {
    if (health.healthy) {
      return <CheckCircleOutlined style={{ color: '#52c41a', fontSize: 16 }} />;
    } else {
      return <CloseCircleOutlined style={{ color: '#ff4d4f', fontSize: 16 }} />;
    }
  };

  // 渲染检查类型标签
  const renderCheckTypeTag = (checkType: string) => {
    if (checkType === 'PASSIVE') {
      return <Tag color="blue">被动检查</Tag>;
    } else {
      return <Tag color="green">主动检查</Tag>;
    }
  };

  // 表格列定义
  const columns = [
    {
      title: '健康状态',
      dataIndex: 'healthy',
      key: 'healthy',
      width: 100,
      render: (_: boolean, record: InstanceHealth) => renderHealthIcon(record),
    },
    {
      title: '实例地址',
      dataIndex: 'ip',
      key: 'address',
      width: 200,
      render: (_: string, record: InstanceHealth) => `${record.ip}:${record.port}`,
    },
    {
      title: '检查方式',
      dataIndex: 'checkType',
      key: 'checkType',
      width: 120,
      render: (checkType: string) => renderCheckTypeTag(checkType),
    },
    {
      title: '连续失败',
      dataIndex: 'consecutiveFailures',
      key: 'consecutiveFailures',
      width: 100,
      render: (failures: number) => (
        <Tag color={failures > 0 ? 'red' : 'default'}>
          {failures} 次
        </Tag>
      ),
    },
    {
      title: '不健康原因',
      dataIndex: 'unhealthyReason',
      key: 'unhealthyReason',
      ellipsis: true,
      render: (reason?: string) => (
        reason ? (
          <Tooltip title={reason}>
            <Tag color="red">{reason.substring(0, 20)}...</Tag>
          </Tooltip>
        ) : (
          <Tag color="success">健康</Tag>
        )
      ),
    },
  ];

  return (
    <div className="instance-health-status">
      <Table
        columns={columns}
        dataSource={healthList}
        loading={loading}
        rowKey={(record) => `${record.serviceId}-${record.ip}-${record.port}`}
        pagination={false}
        size="small"
        locale={{ emptyText: '暂无健康记录' }}
      />
    </div>
  );
};

/**
 * 健康概览组件
 */
export const HealthOverview: React.FC = () => {
  const [overview, setOverview] = useState<any>(null);
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    fetchOverview();
  }, []);

  const fetchOverview = async () => {
    try {
      setLoading(true);
      const response = await fetch('/api/gateway/health/overview');
      if (response.ok) {
        const data = await response.json();
        setOverview(data);
      }
    } catch (error) {
      console.error('Failed to fetch overview:', error);
    } finally {
      setLoading(false);
    }
  };

  if (!overview) return null;

  return (
    <div style={{ marginBottom: 16 }}>
      <Space size="large">
        <div>
          <strong>总实例数：</strong>{overview.totalInstances}
        </div>
        <div>
          <strong>健康：</strong>
          <span style={{ color: '#52c41a' }}>{overview.healthyCount}</span>
        </div>
        <div>
          <strong>不健康：</strong>
          <span style={{ color: '#ff4d4f' }}>{overview.unhealthyCount}</span>
        </div>
        <div>
          <strong>健康率：</strong>
          <span style={{ color: overview.healthRate === '100.00%' ? '#52c41a' : '#faad14' }}>
            {overview.healthRate}
          </span>
        </div>
      </Space>
    </div>
  );
};

export default InstanceHealthStatus;
