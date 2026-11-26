// Production build. The Docker build replaces this URL via the API_BASE_URL build arg.
export const environment = {
  production: true,
  apiBaseUrl: 'http://localhost:8080/api',
};
