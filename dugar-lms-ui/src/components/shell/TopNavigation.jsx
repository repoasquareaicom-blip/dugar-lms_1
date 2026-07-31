import { AppBar, Badge, Box, IconButton, Stack, Toolbar, Tooltip } from '@mui/material'
import logo from '../../assets/images/logo.png'
import PrimaryNavigation from '../layout/PrimaryNavigation.jsx'
import { navColors, navHeights } from '../layout/navigationStyles.js'
import UserMenu from './UserMenu.jsx'

function SvgIcon({ children }) {
  return (
    <Box component="svg" aria-hidden="true" viewBox="0 0 24 24" sx={{ width: 20, height: 20, display: 'block' }}>
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

function TopNavigation({ activeModuleId, items, onLogout, onMobileOpen, onSelectModule, user }) {
  return (
    <AppBar position="fixed" elevation={0} sx={{ bgcolor: navColors.appBar, borderBottom: `1px solid ${navColors.appBarBorder}` }}>
      <Toolbar sx={{ minHeight: `${navHeights.top}px !important`, px: { xs: 1.5, md: 2.5 }, gap: 1.25 }}>
        <Stack direction="row" spacing={1.25} alignItems="center" sx={{ minWidth: 0, flex: 1 }}>
          <IconButton
            color="inherit"
            onClick={onMobileOpen}
            sx={{ display: { xs: 'inline-flex', lg: 'none' }, color: '#ffffff', flex: '0 0 auto', p: 0.75 }}
          >
            <MenuIcon />
          </IconButton>

          <Box
            component="img"
            src={logo}
            alt="LoanIntelli"
            sx={{ width: { xs: 108, sm: 128 }, height: 36, objectFit: 'contain', bgcolor: '#ffffff', borderRadius: 1 }}
          />

          <Box
            sx={{
              display: { xs: 'none', lg: 'flex' },
              alignItems: 'center',
              alignSelf: 'center',
              minHeight: 38,
              minWidth: 0,
              flex: 1,
            }}
          >
            <PrimaryNavigation activeModuleId={activeModuleId} items={items} onSelect={onSelectModule} />
          </Box>
        </Stack>

        <Stack
          direction="row"
          spacing={1}
          alignItems="center"
          justifyContent="flex-end"
          sx={{
            flex: '0 0 auto',
            pl: { xs: 0, md: 1.5 },
            borderLeft: { xs: 'none', md: '1px solid rgba(255, 255, 255, 0.16)' },
          }}
        >
          <Tooltip title="Notifications">
            <IconButton sx={{ color: 'rgba(255, 255, 255, 0.82)', p: 0.75 }}>
              <Badge color="secondary" variant="dot" overlap="circular">
                <BellIcon />
              </Badge>
            </IconButton>
          </Tooltip>
          <UserMenu onLogout={onLogout} user={user} />
        </Stack>
      </Toolbar>
    </AppBar>
  )
}

export default TopNavigation
