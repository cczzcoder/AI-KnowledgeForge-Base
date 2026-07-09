import { Form, Input, Modal } from 'antd';
import type { KnowledgeBaseDTO } from '@/services/typings.d';

interface KnowledgeFormProps {
  open: boolean;
  title: string;
  initialValues?: KnowledgeBaseDTO;
  onOk: (values: KnowledgeBaseDTO) => void;
  onCancel: () => void;
}

function KnowledgeForm({
  open,
  title,
  initialValues,
  onOk,
  onCancel,
}: KnowledgeFormProps) {
  const [form] = Form.useForm<KnowledgeBaseDTO>();

  const handleOk = async () => {
    try {
      const values = await form.validateFields();
      onOk(values);
      form.resetFields();
    } catch {
      // 表单校验失败时由 Ant Design 在字段处展示错误，这里无需额外处理。
    }
  };

  const handleCancel = () => {
    form.resetFields();
    onCancel();
  };

  return (
    <Modal
      open={open}
      title={title}
      onOk={handleOk}
      onCancel={handleCancel}
      destroyOnHidden
      width={520}
    >
      <Form
        form={form}
        layout="vertical"
        initialValues={initialValues}
        style={{ marginTop: 16 }}
      >
        <Form.Item
          name="name"
          label="知识库名称"
          rules={[
            { required: true, message: '请输入知识库名称' },
            { max: 255, message: '名称不能超过255个字符' },
          ]}
        >
          <Input placeholder="例如：个人学习笔记" />
        </Form.Item>

        <Form.Item
          name="description"
          label="描述"
          rules={[{ max: 2000, message: '描述不能超过2000个字符' }]}
        >
          <Input.TextArea rows={3} placeholder="知识库的简要描述（选填）" />
        </Form.Item>

        <Form.Item
          name="icon"
          label="图标"
          rules={[{ max: 50, message: '图标名不能超过50个字符' }]}
        >
          <Input placeholder="图标名称（选填）" />
        </Form.Item>
      </Form>
    </Modal>
  );
}

export default KnowledgeForm;
