# Frontend Documentation

## Overview

This document provides guidelines and information for frontend development in the LogStream project. LogStream is a log aggregation and analytics platform with a comprehensive REST API backend.

## Technology Stack

- **Framework**: React 18+
- **State Management**: Redux Toolkit or Context API
- **HTTP Client**: Axios
- **Styling**: Tailwind CSS or Material-UI
- **Build Tool**: Vite or Create React App
- **Testing**: Jest + React Testing Library
- **Linting**: ESLint
- **Code Formatting**: Prettier

## Project Structure

```
frontend/
├── public/
│   └── index.html
├── src/
│   ├── components/
│   │   ├── Analytics/
│   │   ├── Health/
│   │   ├── Logs/
│   │   └── Common/
│   ├── pages/
│   │   ├── Dashboard.jsx
│   │   ├── Analytics.jsx
│   │   └── Health.jsx
│   ├── services/
│   │   ├── api.js
│   │   ├── analyticsService.js
│   │   └── healthService.js
│   ├── hooks/
│   │   ├── useAnalytics.js
│   │   └── useHealth.js
│   ├── store/
│   │   ├── slices/
│   │   └── store.js
│   ├── utils/
│   │   ├── formatters.js
│   │   └── validators.js
│   ├── App.jsx
│   └── main.jsx
├── tests/
│   ├── components/
│   └── services/
├── package.json
└── vite.config.js
```

## Setup and Installation

### Prerequisites

- Node.js 16+ and npm 8+
- Backend API running on `http://localhost:8080`

### Installation Steps

1. Clone the repository:
```bash
git clone <repository-url>
cd frontend
```

2. Install dependencies:
```bash
npm install
```

3. Create `.env` file:
```
VITE_API_URL=http://localhost:8080/api
VITE_APP_NAME=LogStream
```

4. Start development server:
```bash
npm run dev
```

5. Open browser at `http://localhost:5173`

## Development Guidelines

### Code Style

- Follow ESLint configuration
- Use Prettier for code formatting
- Use camelCase for variables and functions
- Use PascalCase for components and classes
- Keep components under 300 lines
- Extract reusable logic into custom hooks

### Component Structure

```jsx
// Functional component with hooks
import { useState, useEffect } from 'react';

export function ComponentName({ prop1, prop2 }) {
  const [state, setState] = useState(null);

  useEffect(() => {
    // Side effects
  }, []);

  return (
    <div>
      {/* JSX */}
    </div>
  );
}
```

### API Integration

Use centralized API service:

```javascript
// services/api.js
import axios from 'axios';

const API_URL = import.meta.env.VITE_API_URL;

export const api = axios.create({
  baseURL: API_URL,
  timeout: 10000,
});

// Add request/response interceptors
api.interceptors.response.use(
  response => response.data,
  error => Promise.reject(error)
);
```

### State Management

Use Redux Toolkit for complex state:

```javascript
// store/slices/analyticsSlice.js
import { createSlice, createAsyncThunk } from '@reduxjs/toolkit';

export const fetchAnalytics = createAsyncThunk(
  'analytics/fetchAnalytics',
  async (params) => {
    // API call
  }
);

const analyticsSlice = createSlice({
  name: 'analytics',
  initialState: { data: [], loading: false },
  extraReducers: (builder) => {
    builder.addCase(fetchAnalytics.fulfilled, (state, action) => {
      state.data = action.payload;
    });
  },
});

export default analyticsSlice.reducer;
```

## Testing

### Unit Tests

```bash
npm run test
```

### Test Structure

```javascript
// components/Analytics.test.jsx
import { render, screen } from '@testing-library/react';
import Analytics from './Analytics';

describe('Analytics Component', () => {
  it('should render analytics data', () => {
    render(<Analytics />);
    expect(screen.getByText(/analytics/i)).toBeInTheDocument();
  });
});
```

### Coverage

Run coverage report:
```bash
npm run test:coverage
```

Target: 80%+ coverage

## Building for Production

### Build

```bash
npm run build
```

Output: `dist/` directory

### Preview

```bash
npm run preview
```

### Deployment

1. Build the project
2. Deploy `dist/` folder to hosting service
3. Configure environment variables
4. Set up reverse proxy for API calls

## API Endpoints Reference

### Analytics Endpoints

- `GET /api/analytics/error-rate` - Error rate per service
- `GET /api/analytics/common-errors` - Top error messages
- `GET /api/analytics/volume` - Log volume by time

### Health Endpoints

- `GET /api/health/dashboard` - Service health status

### Log Endpoints

- `GET /api/logs` - Search logs
- `POST /api/logs` - Ingest logs

## Performance Optimization

- Use React.memo for expensive components
- Implement code splitting with React.lazy
- Optimize images and assets
- Use virtual scrolling for large lists
- Implement request debouncing/throttling

## Troubleshooting

### API Connection Issues

1. Verify backend is running on correct port
2. Check VITE_API_URL environment variable
3. Check browser console for CORS errors
4. Verify API response format

### Build Issues

1. Clear node_modules: `rm -rf node_modules && npm install`
2. Clear cache: `npm cache clean --force`
3. Check Node.js version: `node --version`

## Contributing

1. Create feature branch: `git checkout -b feature/feature-name`
2. Follow code style guidelines
3. Write tests for new features
4. Submit pull request with description
5. Ensure CI/CD passes

## Resources

- [React Documentation](https://react.dev)
- [Vite Documentation](https://vitejs.dev)
- [Redux Toolkit Documentation](https://redux-toolkit.js.org)
- [Tailwind CSS Documentation](https://tailwindcss.com)

## Support

For issues or questions, contact the development team or create an issue in the repository.
