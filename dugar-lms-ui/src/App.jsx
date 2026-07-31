import { Navigate, Route, Routes } from 'react-router-dom'
import { useAuth } from './context/authContextCore.js'
import AuthLayout from './layouts/AuthLayout.jsx'
import MainLayout from './layouts/MainLayout.jsx'
import ContractComingSoonPage from './pages/contracts/ContractComingSoonPage.jsx'
import ContractListPage from './pages/contracts/ContractListPage.jsx'
import Dashboard from './pages/Dashboard.jsx'
import Login from './pages/Login.jsx'
import ModulePlaceholder from './pages/ModulePlaceholder.jsx'
import NotFound from './pages/NotFound.jsx'
import ProtectedRoute from './routes/ProtectedRoute.jsx'
import { flattenMenuTree, getRootMenus, isNavigableMenuPath } from './utils/menuUtils.js'

function toRoutePath(path) {
  return path.replace(/^\/+/, '')
}

function App() {
  const { menus } = useAuth()
  const routeMenus = flattenMenuTree(getRootMenus(menus)).filter(
    (menu) => isNavigableMenuPath(menu.urlPath, menu) && !['/dashboard', '/contracts', '/contracts/new', '/contracts/active'].includes(menu.urlPath),
  )

  return (
    <Routes>
      <Route path="/" element={<Navigate to="/login" replace />} />
      <Route element={<AuthLayout />}>
        <Route path="/login" element={<Login />} />
      </Route>
      <Route element={<ProtectedRoute />}>
        <Route element={<MainLayout />}>
          <Route path="/dashboard" element={<Dashboard />} />
          <Route path="/contracts" element={<ContractListPage />} />
          <Route path="/contracts/new" element={<ContractComingSoonPage />} />
          <Route path="/contracts/active" element={<Navigate to="/contracts" replace />} />
          {routeMenus.map((menu) => (
            <Route
              key={`${menu.id}-${menu.urlPath}`}
              path={toRoutePath(menu.urlPath)}
              element={<ModulePlaceholder menuId={menu.id} />}
            />
          ))}
          <Route path="*" element={<ModulePlaceholder />} />
        </Route>
      </Route>
      <Route path="*" element={<NotFound />} />
    </Routes>
  )
}

export default App
