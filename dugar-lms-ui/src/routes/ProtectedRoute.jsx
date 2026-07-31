import { Box, CircularProgress, Stack, Typography } from '@mui/material'
import { Navigate, Outlet } from 'react-router-dom'
import { useAuth } from '../context/authContextCore.js'

function ProtectedRoute() {
  const { authStatus, isAuthenticated, isValidating, token } = useAuth()

  if (!token) {
    return <Navigate to="/login" replace />
  }

  if (isValidating) {
    return (
      <Box sx={{ minHeight: '100vh', display: 'grid', placeItems: 'center', bgcolor: '#f7fbff' }}>
        <Stack spacing={2} alignItems="center">
          <CircularProgress />
          <Typography sx={{ color: '#475569', fontWeight: 700 }}>Validating session...</Typography>
        </Stack>
      </Box>
    )
  }

  if (authStatus === 'validation-error') {
    return (
      <Box sx={{ minHeight: '100vh', display: 'grid', placeItems: 'center', bgcolor: '#f7fbff', p: 3 }}>
        <Typography sx={{ color: '#b42318', fontWeight: 700, textAlign: 'center' }}>
          Unable to validate your session. Please check the API server connection.
        </Typography>
      </Box>
    )
  }

  if (!isAuthenticated) {
    return <Navigate to="/login" replace />
  }

  return <Outlet />
}

export default ProtectedRoute
