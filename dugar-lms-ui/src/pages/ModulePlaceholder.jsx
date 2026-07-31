import { Box, Chip, Paper, Stack, Typography } from '@mui/material'
import { useLocation } from 'react-router-dom'
import PageWorkspace from '../components/PageWorkspace.jsx'
import { useMenu } from '../context/menuContextCore.js'
import IconResolver from '../utils/IconResolver.jsx'
import { findMenuById, findMenuByPath, findMenuPathForRoute } from '../utils/menuUtils.js'

function ModulePlaceholder({ description = 'This page is under development.', icon, menuId, title }) {
  const location = useLocation()
  const { breadcrumbs, rootMenus } = useMenu()
  const routeId = location.pathname.startsWith('/module/') ? location.pathname.replace('/module/', '') : ''
  const matchedMenu =
    findMenuById(rootMenus, menuId) ||
    findMenuByPath(rootMenus, location.pathname) ||
    findMenuById(rootMenus, routeId)
  const menuPath = matchedMenu ? findMenuPathForRoute(rootMenus, matchedMenu.urlPath || `/module/${matchedMenu.id}`) : []
  const pageTitle = title || matchedMenu?.label || 'Module'
  const parentModule = menuPath.length > 1 ? menuPath[menuPath.length - 2]?.label : menuPath[0]?.label || 'Dashboard'

  return (
    <PageWorkspace title={pageTitle} breadcrumbs={breadcrumbs}>
      <Paper
        elevation={0}
        sx={{
          border: '1px solid',
          borderColor: 'divider',
          borderRadius: 1.5,
          bgcolor: '#ffffff',
          p: { xs: 2, md: 2.5 },
          maxWidth: 680,
        }}
      >
        <Stack spacing={1.5} alignItems="flex-start">
          <Box
            sx={{
              width: 42,
              height: 42,
              borderRadius: 1.25,
              display: 'grid',
              placeItems: 'center',
              bgcolor: '#e8f3ff',
              color: 'primary.main',
              flex: '0 0 auto',
            }}
          >
            <IconResolver iconName={icon || matchedMenu?.icon} size={20} />
          </Box>
          <Stack direction={{ xs: 'column', sm: 'row' }} spacing={1} alignItems={{ xs: 'flex-start', sm: 'center' }}>
            <Typography variant="h6" sx={{ color: 'text.primary', fontWeight: 600 }}>
              {pageTitle}
            </Typography>
            <Chip label="Planned" size="small" color="primary" variant="outlined" />
          </Stack>
          <Typography sx={{ color: 'text.secondary', fontSize: '0.92rem' }}>{breadcrumbs.map((crumb) => crumb.label).join(' > ')}</Typography>
          <Chip label={`Parent Module: ${parentModule}`} size="small" variant="outlined" />
          <Typography sx={{ color: 'text.secondary' }}>{description}</Typography>
        </Stack>
      </Paper>
    </PageWorkspace>
  )
}

export default ModulePlaceholder
