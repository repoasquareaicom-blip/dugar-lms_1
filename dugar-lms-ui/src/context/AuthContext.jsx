import { useCallback, useEffect, useMemo, useState } from 'react'
import apiClient from '../api/apiClient.js'
import {
  clearAuthSession,
  getAccessToken,
  getLoggedInUser,
  getStoredMenus,
  storeAuthSession,
} from '../utils/authStorage.js'
import { AuthContext } from './authContextCore.js'

function getResponseUser(responseData) {
  return responseData?.user || responseData?.data?.user || responseData?.principal || null
}

export function AuthProvider({ children }) {
  const [token, setToken] = useState(() => getAccessToken())
  const [user, setUser] = useState(() => getLoggedInUser())
  const [menus, setMenus] = useState(() => getStoredMenus())
  const [authStatus, setAuthStatus] = useState(() => (getAccessToken() ? 'validating' : 'unauthenticated'))

  const logout = useCallback(() => {
    clearAuthSession()
    setToken(null)
    setUser(null)
    setMenus([])
    setAuthStatus('unauthenticated')
  }, [])

  const validateSession = useCallback(async () => {
    const currentToken = getAccessToken()

    if (!currentToken) {
      logout()
      return false
    }

    setAuthStatus('validating')

    try {
      const response = await apiClient.get('/test/me')
      const responseUser = getResponseUser(response.data)
      setToken(currentToken)
      setUser(responseUser || getLoggedInUser())
      setMenus(getStoredMenus())
      setAuthStatus('authenticated')
      return true
    } catch (error) {
      if (error.response?.status === 401 || error.response?.status === 403) {
        logout()
        return false
      }

      setAuthStatus('validation-error')
      return false
    }
  }, [logout])

  const login = useCallback((session) => {
    storeAuthSession(session)
    setToken(session.token)
    setUser(session.user ?? null)
    setMenus(Array.isArray(session.menus) ? session.menus : [])
    setAuthStatus('authenticated')
  }, [])

  useEffect(() => {
    const currentToken = getAccessToken()

    if (!currentToken) {
      return undefined
    }

    let isActive = true

    apiClient
      .get('/test/me')
      .then((response) => {
        if (!isActive) {
          return
        }

        const responseUser = getResponseUser(response.data)
        setToken(currentToken)
        setUser(responseUser || getLoggedInUser())
        setMenus(getStoredMenus())
        setAuthStatus('authenticated')
      })
      .catch((error) => {
        if (!isActive) {
          return
        }

        if (error.response?.status === 401 || error.response?.status === 403) {
          logout()
          return
        }

        setAuthStatus('validation-error')
      })

    return () => {
      isActive = false
    }
  }, [logout])

  const value = useMemo(
    () => ({
      authStatus,
      isAuthenticated: authStatus === 'authenticated',
      isValidating: authStatus === 'validating',
      login,
      logout,
      menus,
      token,
      user,
      validateSession,
    }),
    [authStatus, login, logout, menus, token, user, validateSession],
  )

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}
