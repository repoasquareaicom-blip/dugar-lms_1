import { Route } from 'react-router-dom'
import ModulePlaceholder from '../pages/ModulePlaceholder.jsx'
import { useAuth } from '../context/authContextCore.js'
import { flattenMenuTree, getRootMenus, isNavigableMenuPath } from '../utils/menuUtils.js'

function toRoutePath(path) {
  return path.replace(/^\/+/, '')
}

function DynamicMenuRoutes() {
  const { menus } = useAuth()
  const routeMenus = flattenMenuTree(getRootMenus(menus)).filter(
    (menu) => isNavigableMenuPath(menu.urlPath, menu) && menu.urlPath !== '/dashboard',
  )

  return routeMenus.map((menu) => (
    <Route
      key={`${menu.id}-${menu.urlPath}`}
      path={toRoutePath(menu.urlPath)}
      element={<ModulePlaceholder menuId={menu.id} />}
    />
  ))
}

export default DynamicMenuRoutes
