import { Box } from '@mui/material'
import { useEffect, useMemo, useState } from 'react'
import { Outlet, useNavigate } from 'react-router-dom'
import MobileNavigationDrawer from '../components/layout/MobileNavigationDrawer.jsx'
import SecondaryNavigation from '../components/layout/SecondaryNavigation.jsx'
import TopAppBar from '../components/layout/TopAppBar.jsx'
import { navHeights } from '../components/layout/navigationStyles.js'
import ConfirmDialog from '../components/shell/ConfirmDialog.jsx'
import { GlobalSnackbarProvider } from '../components/shell/GlobalSnackbar.jsx'
import LoadingOverlay from '../components/shell/LoadingOverlay.jsx'
import { useAuth } from '../context/authContextCore.js'
import { MenuProvider } from '../context/MenuContext.jsx'
import { useMenu } from '../context/menuContextCore.js'
import { getMenuChildren, getMenuRoute, isNavigableMenuPath } from '../utils/menuUtils.js'

function AppFooter() {
  const { breadcrumbs } = useMenu()
  const menuPath = breadcrumbs.map((breadcrumb) => breadcrumb.label).join(' > ')

  return (
    <Box
      component="footer"
      sx={{
        position: 'fixed',
        left: 0,
        right: 0,
        bottom: 0,
        zIndex: 1080,
        height: navHeights.footer,
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'flex-start',
        borderTop: '1px solid',
        borderColor: '#6f849c',
        bgcolor: '#ffffff',
        color: '#0f172a',
        fontSize: '12px',
        fontWeight: 600,
        px: { xs: 1, md: 2 },
      }}
    >
      <Box
        sx={{
          flex: '1 1 auto',
          minWidth: 0,
          overflow: 'hidden',
          textOverflow: 'ellipsis',
          whiteSpace: 'nowrap',
        }}
      >
        {menuPath || 'LoanIntelli LMS'}
      </Box>
    </Box>
  )
}

function AppShellContent() {
  const { logout, user } = useAuth()
  const { activePath, rootMenus } = useMenu()
  const navigate = useNavigate()
  const [mobileOpen, setMobileOpen] = useState(false)
  const [selectedRootId, setSelectedRootId] = useState(null)
  const activePathIds = activePath.map((menu) => menu.id)
  const activeRoot = activePath[0] || rootMenus[0] || null
  const selectedRoot = useMemo(
    () => rootMenus.find((item) => item.id === selectedRootId) || activeRoot,
    [activeRoot, rootMenus, selectedRootId],
  )
  const selectedRootChildren = getMenuChildren(selectedRoot)
  const hasSecondaryNavigation = selectedRootChildren.length > 0

  useEffect(() => {
    if (activeRoot?.id) {
      setSelectedRootId(activeRoot.id)
    }
  }, [activeRoot?.id])

  const navigateToMenu = (item) => {
    const children = getMenuChildren(item)

    if (isNavigableMenuPath(item.urlPath, item)) {
      navigate(getMenuRoute(item))
      setMobileOpen(false)
      return
    }

    if (children.length > 0) {
      setSelectedRootId(item.id)
    }
  }

  const selectRootModule = (item) => {
    setSelectedRootId(item.id)

    if (isNavigableMenuPath(item.urlPath, item)) {
      navigate(getMenuRoute(item))
    }
  }

  const handleLogout = () => {
    logout()
    navigate('/login', { replace: true })
  }

  return (
    <Box sx={{ height: '100vh', bgcolor: '#eef1f4', overflow: 'hidden' }}>
      <GlobalSnackbarProvider>
        <TopAppBar
          activeModuleId={selectedRoot?.id}
          items={rootMenus}
          onLogout={handleLogout}
          onMobileOpen={() => setMobileOpen(true)}
          onSelectModule={selectRootModule}
          user={user}
        />
        <SecondaryNavigation
          activeModule={selectedRoot}
          activePathIds={activePathIds}
          activeSecondaryId={activePath[1]?.id}
          onSelectChild={navigateToMenu}
        />
        <MobileNavigationDrawer
          activePathIds={activePathIds}
          items={rootMenus}
          onClose={() => setMobileOpen(false)}
          onSelect={navigateToMenu}
          open={mobileOpen}
        />

        <Box
          component="main"
          sx={{
            boxSizing: 'border-box',
            height: '100vh',
            overflow: 'hidden',
            width: '100%',
            pt: `${navHeights.top + (hasSecondaryNavigation ? navHeights.secondary : 0) + 8}px`,
            px: { xs: 1, md: 1.25 },
            pb: `${navHeights.footer + 10}px`,
          }}
        >
          <Outlet />
        </Box>
        <AppFooter />
        <LoadingOverlay loading={false} />
        <ConfirmDialog open={false} title="" message="" onCancel={() => {}} onConfirm={() => {}} />
      </GlobalSnackbarProvider>
    </Box>
  )
}

function AppLayout() {
  return (
    <MenuProvider>
      <AppShellContent />
    </MenuProvider>
  )
}

export default AppLayout
