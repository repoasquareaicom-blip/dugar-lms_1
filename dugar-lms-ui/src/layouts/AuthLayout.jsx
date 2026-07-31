import { Box } from '@mui/material'
import { Outlet } from 'react-router-dom'

function AuthLayout() {
  return (
    <Box component="main" sx={{ minHeight: '100vh' }}>
      <Outlet />
    </Box>
  )
}

export default AuthLayout
