import {
  AppBar,
  Avatar,
  Box,
  IconButton,
  Menu,
  MenuItem,
  Stack,
  Toolbar,
  Typography,
} from '@mui/material'
import { useState } from 'react'
import logo from '../../assets/images/logo.png'
import { getFullName, getRoleName } from '../../utils/userUtils.js'
import { navHeights } from './navigationStyles.js'
import PrimaryNavigation from './PrimaryNavigation.jsx'

function SvgIcon({ children }) {
  return (
    <Box component="svg" aria-hidden="true" viewBox="0 0 24 24" sx={{ width: 22, height: 22, display: 'block' }}>
      {children}
    </Box>
  )
}

function MenuIcon() {
  return (
    <SvgIcon>
      <path d="M4 7h16M4 12h16M4 17h16" fill="none" stroke="currentColor" strokeLinecap="round" strokeWidth="2" />
    </SvgIcon>
  )
}

function BellIcon() {
  return (
    <SvgIcon>
      <path
        d="M18 8a6 6 0 0 0-12 0c0 7-3 7-3 9h18c0-2-3-2-3-9"
        fill="none"
        stroke="currentColor"
        strokeLinecap="round"
        strokeLinejoin="round"
        strokeWidth="2"
      />
      <path d="M10 21h4" fill="none" stroke="currentColor" strokeLinecap="round" strokeWidth="2" />
    </SvgIcon>
  )
}

function TopAppBar({ activeModuleId, items, onLogout, onMobileOpen, onSelectModule, user }) {
  const [userAnchor, setUserAnchor] = useState(null)
  const fullName = getFullName(user)
  const roleName = getRoleName(user)
  const initials = fullName
    .split(' ')
    .filter(Boolean)
    .slice(0, 2)
    .map((part) => part[0])
    .join('')
    .toUpperCase()

  const handleLogout = () => {
    setUserAnchor(null)
    onLogout()
  }

  return (
    <AppBar position="fixed" elevation={0} sx={{ bgcolor: '#092f5c', borderBottom: '1px solid #123f73' }}>
      <Toolbar sx={{ minHeight: `${navHeights.top}px !important`, px: { xs: 1.5, md: 3 }, gap: 2 }}>
        <IconButton
          color="inherit"
          onClick={onMobileOpen}
          sx={{ display: { xs: 'inline-flex', lg: 'none' }, color: '#ffffff' }}
        >
          <MenuIcon />
        </IconButton>

        <Stack direction="row" spacing={1.25} alignItems="center" sx={{ minWidth: 0, flex: '0 0 auto' }}>
          <Box
            component="img"
            src={logo}
            alt="LoanIntelli"
            sx={{ width: { xs: 112, sm: 142 }, height: 40, objectFit: 'contain', bgcolor: '#ffffff', borderRadius: 1 }}
          />
        </Stack>

        <Box sx={{ display: { xs: 'none', lg: 'block' }, flex: 1, minWidth: 0 }}>
          <PrimaryNavigation activeModuleId={activeModuleId} items={items} onSelect={onSelectModule} />
        </Box>

        <Stack direction="row" spacing={1.25} alignItems="center" sx={{ ml: 'auto' }}>
          <IconButton sx={{ color: 'rgba(255, 255, 255, 0.82)' }}>
            <BellIcon />
          </IconButton>

          <Box sx={{ display: { xs: 'none', md: 'block' }, textAlign: 'right' }}>
            <Typography sx={{ color: '#ffffff', fontSize: '0.86rem', fontWeight: 800, lineHeight: 1.1 }}>
              {fullName}
            </Typography>
            <Typography sx={{ color: 'rgba(255, 255, 255, 0.68)', fontSize: '0.72rem', fontWeight: 700 }}>
              {roleName}
            </Typography>
          </Box>

          <IconButton onClick={(event) => setUserAnchor(event.currentTarget)} sx={{ p: 0 }}>
            <Avatar sx={{ width: 36, height: 36, bgcolor: '#f59a23', color: '#092f5c', fontWeight: 900 }}>
              {initials || 'U'}
            </Avatar>
          </IconButton>
          <Menu anchorEl={userAnchor} open={Boolean(userAnchor)} onClose={() => setUserAnchor(null)}>
            <MenuItem disabled>{fullName}</MenuItem>
            <MenuItem onClick={handleLogout}>Logout</MenuItem>
          </Menu>
        </Stack>
      </Toolbar>
    </AppBar>
  )
}

export default TopAppBar
