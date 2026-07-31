const ACCESS_TOKEN_KEY = 'accessToken'
const LOGGED_IN_USER_KEY = 'loggedInUser'
const MENUS_KEY = 'menus'

export function getAccessToken() {
  return localStorage.getItem(ACCESS_TOKEN_KEY)
}

export function getLoggedInUser() {
  const storedUser = localStorage.getItem(LOGGED_IN_USER_KEY)

  if (!storedUser) {
    return null
  }

  try {
    return JSON.parse(storedUser)
  } catch {
    return null
  }
}

export function getStoredMenus() {
  const storedMenus = localStorage.getItem(MENUS_KEY)

  if (!storedMenus) {
    return []
  }

  try {
    const menus = JSON.parse(storedMenus)
    if (Array.isArray(menus)) {
      return menus
    }

    if (Array.isArray(menus?.menus)) {
      return menus.menus
    }

    if (Array.isArray(menus?.items)) {
      return menus.items
    }

    if (Array.isArray(menus?.data)) {
      return menus.data
    }

    return []
  } catch {
    return []
  }
}

export function storeAuthSession({ token, user, menus }) {
  localStorage.setItem(ACCESS_TOKEN_KEY, token)
  localStorage.setItem(LOGGED_IN_USER_KEY, JSON.stringify(user ?? null))
  localStorage.setItem(MENUS_KEY, JSON.stringify(menus ?? []))
}

export function clearAuthSession() {
  localStorage.removeItem(ACCESS_TOKEN_KEY)
  localStorage.removeItem(LOGGED_IN_USER_KEY)
  localStorage.removeItem(MENUS_KEY)
}
