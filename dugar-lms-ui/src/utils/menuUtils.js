function asArray(value) {
  if (Array.isArray(value)) {
    return value
  }

  if (Array.isArray(value?.menus)) {
    return value.menus
  }

  if (Array.isArray(value?.items)) {
    return value.items
  }

  if (Array.isArray(value?.data)) {
    return value.data
  }

  return []
}

function getFirstValue(source, keys, fallback = '') {
  const value = keys.map((key) => source?.[key]).find((entry) => entry !== undefined && entry !== null && entry !== '')
  return value ?? fallback
}

function normalizePath(path) {
  if (!path || typeof path !== 'string') {
    return ''
  }

  const trimmedPath = path.trim()

  if (!trimmedPath) {
    return ''
  }

  return trimmedPath.startsWith('/') ? trimmedPath : `/${trimmedPath}`
}

function isDashboardMenu(menu) {
  return String(menu?.id || menu?.menuId || '').toLowerCase() === 'dashboard'
}

export function isNavigableMenuPath(urlPath, menu) {
  if (!urlPath || typeof urlPath !== 'string') {
    return false
  }

  const trimmedPath = urlPath.trim()
  const normalizedPath = normalizePath(trimmedPath)
  const lowerPath = trimmedPath.toLowerCase()

  if (!trimmedPath || trimmedPath === '#' || normalizedPath === '/') {
    return false
  }

  if (lowerPath.startsWith('javascript:')) {
    return false
  }

  if (normalizedPath === '/dashboard' && !isDashboardMenu(menu)) {
    return false
  }

  return true
}

export function getMenuLabel(menu) {
  return menu?.label || menu?.menuName || menu?.name || menu?.title || 'Untitled'
}

export function getMenuPath(menu) {
  const rawPath = menu?.urlPath || menu?.path || menu?.url || ''
  return isNavigableMenuPath(rawPath, menu) ? normalizePath(rawPath) : ''
}

export function getMenuChildren(menu) {
  return asArray(menu?.children || menu?.childMenus || menu?.menus || menu?.items)
}

export function getMenuId(menu, fallback) {
  return String(menu?.menuId || menu?.id || menu?.code || fallback || getMenuLabel(menu))
}

export function getMenuDisplayOrder(menu) {
  return Number(getFirstValue(menu, ['displayOrder', 'display_order', 'orderNo', 'sortOrder'], 9999))
}

export function getMenuRoute(menu) {
  return getMenuPath(menu) || `/module/${getMenuId(menu, 'menu')}`
}

export function sortMenuTree(menus) {
  return [...asArray(menus)]
    .sort((first, second) => getMenuDisplayOrder(first) - getMenuDisplayOrder(second))
    .map((menu) => ({
      ...menu,
      children: sortMenuTree(getMenuChildren(menu)),
    }))
}

function normalizeMenu(menu, fallbackId, parentId = null) {
  const menuId = getMenuId(menu, fallbackId)
  const children = sortMenuTree(getMenuChildren(menu)).map((child, index) => normalizeMenu(child, `${menuId}-${index}`, menuId))

  return {
    ...menu,
    id: menuId,
    menuId,
    parentId: getFirstValue(menu, ['parentId', 'parent_id'], parentId),
    label: getMenuLabel(menu),
    urlPath: getMenuPath(menu),
    path: getMenuRoute(menu),
    icon: menu?.icon || '',
    displayOrder: getMenuDisplayOrder(menu),
    permissions: {
      ...menu?.permissions,
      canCreate: menu?.permissions?.canCreate ?? menu?.canCreate ?? menu?.createAllowed,
      canDelete: menu?.permissions?.canDelete ?? menu?.canDelete ?? menu?.deleteAllowed,
      canExport: menu?.permissions?.canExport ?? menu?.canExport ?? menu?.exportAllowed,
      canUpdate: menu?.permissions?.canUpdate ?? menu?.canUpdate ?? menu?.updateAllowed,
      canView: menu?.permissions?.canView ?? menu?.canView ?? menu?.viewAllowed,
    },
    raw: menu,
    children,
  }
}

function hasFlatParentReferences(menus) {
  return collectMenuRecords(menus).some((menu) => getFirstValue(menu, ['parentId', 'parent_id'], null) !== null)
}

function collectMenuRecords(menus) {
  return asArray(menus).flatMap((menu) => {
    const children = getMenuChildren(menu)
    return [{ ...menu, children: [] }, ...collectMenuRecords(children)]
  })
}

function buildTreeFromFlatMenus(menus) {
  const sourceMenus = collectMenuRecords(menus)
  const normalizedById = new Map()
  const roots = []

  sourceMenus.forEach((menu, index) => {
    const normalized = normalizeMenu({ ...menu, children: [] }, `menu-${index}`)
    normalizedById.set(normalized.id, normalized)
  })

  normalizedById.forEach((menu) => {
    if (menu.parentId && normalizedById.has(String(menu.parentId))) {
      normalizedById.get(String(menu.parentId)).children.push(menu)
    } else {
      roots.push(menu)
    }
  })

  return sortMenuTree(roots)
}

export function buildMenuTree(menus) {
  const safeMenus = asArray(menus)

  if (safeMenus.length === 0) {
    return []
  }

  if (hasFlatParentReferences(safeMenus)) {
    return buildTreeFromFlatMenus(safeMenus)
  }

  return sortMenuTree(safeMenus).map((menu, index) => normalizeMenu(menu, `menu-${index}`))
}

export function getDashboardMenu() {
  return {
    id: 'dashboard',
    menuId: 'dashboard',
    parentId: null,
    label: 'Dashboard',
    urlPath: '/dashboard',
    path: '/dashboard',
    icon: '',
    displayOrder: -1,
    permissions: {
      canView: true,
    },
    children: [],
    fixed: true,
  }
}

function createContractListMenu(parentId = 'contracts') {
  return {
    id: 'contract-list',
    menuId: 'contract-list',
    parentId,
    label: 'Contract List',
    urlPath: '/contracts',
    path: '/contracts',
    icon: 'FileSpreadsheet',
    displayOrder: 1,
    permissions: {
      canView: true,
    },
    children: [],
    fixed: true,
  }
}

function createCreateContractMenu(parentId = 'contracts') {
  return {
    id: 'create-contract',
    menuId: 'create-contract',
    parentId,
    label: 'Create Contract',
    urlPath: '/contracts/new',
    path: '/contracts/new',
    icon: 'FilePlus2',
    displayOrder: 2,
    permissions: {
      canView: true,
      canCreate: true,
    },
    children: [],
    fixed: true,
  }
}

function createContractsMenu() {
  return {
    id: 'contracts',
    menuId: 'contracts',
    parentId: null,
    label: 'Contracts',
    urlPath: '',
    path: '/module/contracts',
    icon: 'FileText',
    displayOrder: 20,
    permissions: {
      canView: true,
    },
    children: [createContractListMenu('contracts'), createCreateContractMenu('contracts')],
    fixed: true,
  }
}

function isMenuNamed(menu, expectedLabel) {
  return getMenuLabel(menu).trim().toLowerCase() === expectedLabel
}

function ensureChildMenu(menu, expectedLabel, createMenu) {
  const childIndex = getMenuChildren(menu).findIndex((child) => isMenuNamed(child, expectedLabel))

  if (childIndex === -1) {
    return {
      ...menu,
      children: sortMenuTree([...getMenuChildren(menu), createMenu(menu.id)]),
    }
  }

  const children = getMenuChildren(menu)
  return {
    ...menu,
    children: sortMenuTree(children.map((entry, index) => (index === childIndex ? entry : entry))),
  }
}

function ensureContractsChildren(menu) {
  let updatedMenu = menu

  updatedMenu = ensureChildMenu(updatedMenu, 'contract list', createContractListMenu)
  updatedMenu = ensureChildMenu(updatedMenu, 'create contract', createCreateContractMenu)

  return {
    ...updatedMenu,
    icon: updatedMenu.icon || 'FileText',
    urlPath: '',
    path: '/module/contracts',
  }
}

function ensureContractsMenu(menuTree) {
  const contractsIndex = menuTree.findIndex((menu) => isMenuNamed(menu, 'contracts'))

  if (contractsIndex === -1) {
    return sortMenuTree([...menuTree, createContractsMenu()])
  }

  return sortMenuTree(
    menuTree.map((menu, index) => (index === contractsIndex ? ensureContractsChildren(menu) : menu)),
  )
}

function removeLegacyContractsMenus(menus) {
  return asArray(menus)
    .map((menu) => ({
      ...menu,
      children: removeLegacyContractsMenus(getMenuChildren(menu)),
    }))
    .filter((menu) => !['/contracts/active'].includes(getMenuPath(menu)))
}

export function getRootMenus(menus) {
  const menuTree = buildMenuTree(menus)
  const sanitizedMenuTree = removeLegacyContractsMenus(menuTree)

  return [getDashboardMenu(), ...ensureContractsMenu(sanitizedMenuTree)]
}

export function getRootNavigationItems(menus) {
  return getRootMenus(menus)
}

export function flattenMenuTree(menus) {
  return asArray(menus).flatMap((menu) => [menu, ...flattenMenuTree(menu.children)])
}

export function findMenuById(items, menuId) {
  return flattenMenuTree(items).find((menu) => menu.id === menuId || menu.menuId === menuId) || null
}

export function findMenuByPath(items, pathname) {
  const normalizedPath = normalizePath(pathname)
  return flattenMenuTree(items).find((menu) => menu.path === normalizedPath || menu.urlPath === normalizedPath) || null
}

export function findMenuAncestors(items, targetMenu) {
  if (!targetMenu) {
    return []
  }

  const visit = (menus, path = []) => {
    for (const menu of asArray(menus)) {
      const nextPath = [...path, menu]

      if (menu.id === targetMenu.id) {
        return nextPath
      }

      const childPath = visit(menu.children, nextPath)

      if (childPath.length > 0) {
        return childPath
      }
    }

    return []
  }

  return visit(items)
}

export function findMenuPathForRoute(items, pathname) {
  const byPath = findMenuByPath(items, pathname)

  if (byPath) {
    return findMenuAncestors(items, byPath)
  }

  if (pathname?.startsWith('/module/')) {
    const menuId = pathname.replace('/module/', '')
    const byId = findMenuById(items, menuId)
    return findMenuAncestors(items, byId)
  }

  return pathname === '/dashboard' ? [getDashboardMenu()] : []
}

export function findRootForMenu(items, menu) {
  return findMenuAncestors(items, menu)[0] || null
}

export function getMenuBreadcrumbs(items, pathname) {
  const menuPath = findMenuPathForRoute(items, pathname)

  if (menuPath.length === 0) {
    return [getDashboardMenu()]
  }

  return menuPath.map((menu) => ({
    href: isNavigableMenuPath(menu.urlPath, menu) ? menu.urlPath : '',
    label: menu.label,
    menuId: menu.menuId,
  }))
}
