import axios from 'axios';

// Create axios instance without baseURL to avoid double /api prefix
const api = axios.create({
  timeout: 10000,
  headers: {
    'Content-Type': 'application/json',
  },
});

// Request interceptor
api.interceptors.request.use(
  (config) => {
    // Add auth token if needed
    const token = localStorage.getItem('token');
    if (token) {
      config.headers.Authorization = `Bearer ${token}`;
    }
    return config;
  },
  (error) => {
    console.error('Request error:', error);
    return Promise.reject(error);
  }
);

// Response interceptor
api.interceptors.response.use(
  (response) => response,
  (error) => {
    if (error.response?.status === 401) {
      // Handle unauthorized access
      console.error('Unauthorized access');
      // Optionally redirect to login page
      // window.location.href = '/login';
    } else if (error.response?.status >= 500) {
      console.error('Server error:', error.response?.data || error.message);
    } else if (error.response?.status >= 400) {
      console.error('Client error:', error.response?.data || error.message);
    }
    
    return Promise.reject(error);
  }
);

export default api;
