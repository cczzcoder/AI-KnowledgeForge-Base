import { ExclamationCircleOutlined } from '@ant-design/icons';
import { App } from 'antd';
import React from 'react';

interface OperationConfirmProps {
  title: string;
  content: string;
  onOk: () => void;
  children: React.ReactNode;
}

const OperationConfirm: React.FC<OperationConfirmProps> = ({
  title,
  content,
  onOk,
  children,
}) => {
  const { modal } = App.useApp();

  const showConfirm = () => {
    modal.confirm({
      title,
      icon: <ExclamationCircleOutlined />,
      content,
      onOk,
    });
  };

  return <span onClick={showConfirm}>{children}</span>;
};

export default OperationConfirm;