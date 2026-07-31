import { useMemo } from 'react'
import { useLocation } from 'react-router-dom'
import { useAuth } from './authContextCore.js'
import { MenuContext } from './menuContextCore.js'
import {
  findMenuPathForRoute,
  flattenMenuTree,
  getMenuBreadcrumbs,
  getRootMenus,
} from '../utils/menuUtils.js'

export function MenuProvider({ children }) {
  const { menus } = useAuth()
  const location = useLocation()
  const rootMenus = useMemo(() => getRootMenus(menus), [menus])
  const flatMenus = useMemo(() => flattenMenuTree(rootMenus), [rootMenus])
  const activePath = useMemo(() => findMenuPathForRoute(rootMenus, location.pathname), [location.pathname, rootMenus])
  const breadcrumbs = useMemo(() => getMenuBreadcrumbs(rootMenus, location.pathname), [location.pathname, rootMenus])
  const activeRoot = activePath[0] || rootMenus[0]
  const activeSecondary = activePath[1] || null
  const activeMenu = activePath[activePath.length - 1] || activeRoot

  const value = useMemo(
    () => ({
      activeMenu,
      activePath,
      activeRoot,
      activeSecondary,
      breadcrumbs,
      flatMenus,
      rootMenus,
    }),
    [activeMenu, activePath, activeRoot, activeSecondary, breadcrumbs, flatMenus, rootMenus],
  )

  return <MenuContext.Provider value={value}>{children}</MenuContext.Provider>
}
