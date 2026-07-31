import { createContext, useContext } from 'react'

export const SnackbarContext = createContext(null)

export function useGlobalSnackbar() {
  const context = useContext(SnackbarContext)

  if (!context) {
    throw new Error('useGlobalSnackbar must be used within GlobalSnackbarProvider')
  }

  return context
}
