import { Avatar, Box, Divider, IconButton, Menu, MenuItem, Stack, Typography } from '@mui/material'
import { useState } from 'react'
import { getFullName, getRoleName, getUserValue } from '../../utils/userUtils.js'

function getInitials(fullName) {
  return fullName
    .split(' ')
    .filter(Boolean)
    .slice(0, 2)
    .map((part) => part[0])
    .join('')
    .toUpperCase()
}

function UserMenu({ onLogout, user }) {
  const [anchorEl, setAnchorEl] = useState(null)
  const fullName = getFullName(user)
  const roleName = getRoleName(user)
  const username = getUserValue(user, ['username'])
  const email = getUserValue(user, ['email', 'emailAddress', 'email_address'])

  const closeMenu = () => setAnchorEl(null)

  const handleLogout = () => {
    closeMenu()
    onLogout()
  }

  return (
    <>
      <Stack direction="row" spacing={1} alignItems="center">
        <Box sx={{ display: { xs: 'none', md: 'block' }, textAlign: 'right' }}>
          <Typography sx={{ color: '#ffffff', fontSize: '0.84rem', fontWeight: 600, lineHeight: 1.1 }}>
            {fullName}
          </Typography>
          <Typography sx={{ color: 'rgba(255, 255, 255, 0.68)', fontSize: '0.72rem', fontWeight: 500 }}>
            {roleName}
          </Typography>
        </Box>
        <IconButton onClick={(event) => setAnchorEl(event.currentTarget)} sx={{ p: 0 }}>
          <Avatar sx={{ width: 34, height: 34, bgcolor: 'secondary.main', color: '#092f5c', fontWeight: 600, fontSize: '0.9rem' }}>
            {getInitials(fullName) || 'U'}
          </Avatar>
        </IconButton>
      </Stack>

      <Menu
        anchorEl={anchorEl}
        open={Boolean(anchorEl)}
        onClose={closeMenu}
        slotProps={{
          paper: {
            sx: {
              mt: 1,
              border: '1px solid',
              borderColor: 'divider',
              borderRadius: 1,
              boxShadow: '0 14px 34px rgba(15, 23, 42, 0.13)',
            },
          },
        }}
      >
        <Box sx={{ px: 2, py: 1.25, minWidth: 260 }}>
          <Typography sx={{ color: 'text.primary', fontWeight: 600 }}>{fullName}</Typography>
          <Typography sx={{ color: 'text.secondary', fontSize: '0.82rem' }}>Username: {username}</Typography>
          <Typography sx={{ color: 'text.secondary', fontSize: '0.82rem' }}>Role: {roleName}</Typography>
          <Typography sx={{ color: 'text.secondary', fontSize: '0.82rem' }}>Email: {email}</Typography>
        </Box>
        <Divider />
        <MenuItem onClick={closeMenu}>My Profile</MenuItem>
        <MenuItem onClick={closeMenu}>Change Password</MenuItem>
        <Divider />
        <MenuItem onClick={handleLogout}>Logout</MenuItem>
      </Menu>
    </>
  )
}

export default UserMenu
