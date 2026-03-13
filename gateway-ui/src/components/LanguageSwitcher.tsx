import { Select } from 'antd';
import { GlobalOutlined } from '@ant-design/icons';
import { useTranslation } from 'react-i18next';

const LanguageSwitcher: React.FC = () => {
  const { i18n } = useTranslation();

  const languages = [
    { value: 'en', label: 'English' },
    { value: 'zh', label: '中文' },
  ];

  const handleChange = (value: string) => {
    i18n.changeLanguage(value);
  };

  return (
    <Select
      value={i18n.language}
      onChange={handleChange}
      options={languages}
      prefixCls="language-switcher"
      style={{ width: 120 }}
      suffixIcon={<GlobalOutlined />}
    />
  );
};

export default LanguageSwitcher;
