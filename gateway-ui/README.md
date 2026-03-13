# Gateway Admin UI

A modern React + TypeScript frontend for Gateway Admin with internationalization support.

## 🚀 Tech Stack

- **React 18** - UI framework
- **TypeScript** - Type safety
- **Vite** - Fast build tool
- **Ant Design** - UI component library
- **i18next** - Internationalization
- **React Router** - Navigation
- **Axios** - HTTP client

## 🌍 Internationalization (i18n)

Supported languages:
- 🇺🇸 English (default)
- 🇨🇳 中文 (Chinese)

Language can be switched using the language switcher in the header.

## 📦 Features

### Route Management
- View all routes
- Create new routes
- Edit existing routes
- Delete routes (using route_id UUID)
- Real-time data synchronization

### Service Management
- View all services
- Create new services
- Edit existing services
- Delete services

### Strategy Management
- View all strategies
- Create new strategies
- Edit existing strategies
- Delete strategies

## 🛠️ Development

### Prerequisites
- Node.js >= 16
- npm >= 8

### Installation

```bash
# Install dependencies
npm install

# Start development server
npm run dev

# Build for production
npm run build

# Preview production build
npm run preview
```

### Project Structure

```
gateway-ui/
├── src/
│   ├── components/          # Reusable components
│   │   └── LanguageSwitcher.tsx
│   ├── pages/              # Page components
│   │   ├── RoutesPage.tsx
│   │   ├── ServicesPage.tsx
│   │   └── StrategiesPage.tsx
│   ├── utils/              # Utilities
│   │   └── api.ts
│   ├── App.tsx            # Main app component
│   ├── i18n.ts           # i18n configuration
│   ├── main.tsx          # Entry point
│   └── index.css         # Global styles
├── index.html
├── package.json
├── tsconfig.json
└── vite.config.ts
```

## 🔑 Important Notes

### Route Deletion Uses UUID

When deleting a route, the frontend uses `routeId` (UUID) instead of route name:

```typescript
// ✅ Correct - Uses UUID
const routeIdToDelete = record.routeId || record.id;
await api.delete(`/api/routes/${routeIdToDelete}`);

// ❌ Wrong - Don't use route name
await api.delete(`/api/routes/user-route`);
```

### API Proxy

The Vite dev server proxies `/api` requests to the backend:

```typescript
// vite.config.ts
server: {
  port: 3000,
  proxy: {
    '/api': {
      target: 'http://localhost:8080',
      changeOrigin: true,
    },
  },
}
```

Make sure the gateway-admin backend is running on port 8080.

## 🎨 UI Components

### Language Switcher
Located in the header, allows users to switch between English and Chinese.

### Navigation Menu
- Routes (Cloud icon)
- Services (Appstore icon)
- Strategies (Setting icon)

### Data Tables
All data is displayed in Ant Design tables with:
- Pagination
- Sorting
- Search
- Actions (Edit/Delete)

## 📝 Translation Keys

Common translation keys:

```typescript
'common.submit'     // Submit button
'common.cancel'     // Cancel button
'common.delete'     // Delete button
'common.save'       // Save button
'nav.routes'        // Routes menu
'nav.services'      // Services menu
'nav.strategies'    // Strategies menu
```

## 🔗 Backend Integration

Backend API endpoints:

```
GET    /api/routes           - Get all routes
POST   /api/routes           - Create route
DELETE /api/routes/:id       - Delete route (use route_id UUID)
GET    /api/services         - Get all services
POST   /api/services         - Create service
DELETE /api/services/:id     - Delete service
```

## 🎯 Next Steps

1. ✅ Set up project structure
2. ✅ Configure i18n
3. ✅ Create basic pages
4. ⏳ Implement create/edit forms
5. ⏳ Add authentication
6. ⏳ Implement real-time updates
7. ⏳ Add error handling
8. ⏳ Write unit tests

## 📄 License

MIT
