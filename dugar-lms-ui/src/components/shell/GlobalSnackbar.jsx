import { Alert, Snackbar } from '@mui/material'
import { useCallback, useMemo, useState } from 'react'
import { SnackbarContext } from './globalSnackbarCore.js'

export function GlobalSnackbarProvider({ children }) {
  const [snackbar, setSnackbar] = useState({
    message: '',
    open: false,
    severity: 'info',
  })

  const showSnackbar = useCallback((message, severity = 'info') => {
    setSnackbar({ message, open: true, severity })
  }, [])

  const closeSnackbar = useCallback((_, reason) => {
    if (reason === 'clickaway') {
      return
    }

    setSnackbar((current) => ({ ...current, open: false }))
  }, [])

  const value = useMemo(
    () => ({
      error: (message) => showSnackbar(message, 'error'),
      info: (message) => showSnackbar(message, 'info'),
      success: (message) => showSnackbar(message, 'success'),
      warning: (message) => showSnackbar(message, 'warning'),
      showSnackbar,
    }),
    [showSnackbar],
  )

  return (
    <SnackbarContext.Provider value={value}>
      {children}
      <Snackbar
        open={snackbar.open}
        autoHideDuration={4000}
        onClose={closeSnackbar}
        anchorOrigin={{ vertical: 'top', horizontal: 'right' }}
      >
        <Alert severity={snackbar.severity} variant="filled" onClose={closeSnackbar} sx={{ minWidth: 320 }}>
          {snackbar.message}
        </Alert>
      </Snackbar>
    </SnackbarContext.Provider>
  )
}
