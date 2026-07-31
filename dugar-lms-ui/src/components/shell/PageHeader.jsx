import { Box, Paper, Stack, Typography } from '@mui/material'
import { useMenu } from '../../context/menuContextCore.js'
import IconResolver from '../../utils/IconResolver.jsx'
import BreadcrumbBar from './BreadcrumbBar.jsx'

function PageHeader({ actions, breadcrumbs, subtitle, title }) {
  const { activeMenu } = useMenu()

  return (
    <Paper
      elevation={0}
      sx={{
        border: '1px solid',
        borderColor: 'divider',
        borderRadius: 1.5,
        bgcolor: '#ffffff',
        p: { xs: 1.5, md: 2 },
        mb: 1.5,
      }}
    >
      <Stack spacing={1.15}>
        <Stack
          direction={{ xs: 'column', md: 'row' }}
          spacing={1}
          alignItems={{ xs: 'stretch', md: 'center' }}
          justifyContent="space-between"
        >
          <BreadcrumbBar breadcrumbs={breadcrumbs} />
          {actions && <Box>{actions}</Box>}
        </Stack>

        <Stack direction="row" spacing={1.25} alignItems="center">
          <Box
            sx={{
              width: 36,
              height: 36,
              borderRadius: 1,
              display: 'grid',
              placeItems: 'center',
              bgcolor: 'primary.light',
              color: 'primary.dark',
              flex: '0 0 auto',
            }}
          >
            <IconResolver iconName={activeMenu?.icon} size={18} />
          </Box>
          <Box sx={{ minWidth: 0 }}>
            <Typography
              component="h1"
              sx={{
                color: 'text.primary',
                fontSize: { xs: '1.28rem', md: '1.45rem' },
                fontWeight: 600,
                lineHeight: 1.2,
              }}
            >
              {title}
            </Typography>
            {subtitle && <Typography sx={{ mt: 0.25, color: 'text.secondary', fontSize: '0.9rem' }}>{subtitle}</Typography>}
          </Box>
        </Stack>
      </Stack>
    </Paper>
  )
}

export default PageHeader
