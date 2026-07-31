import { Box, Card, CardContent, Chip, Paper, Stack, Typography } from '@mui/material'
import PageWorkspace from '../components/PageWorkspace.jsx'
import { useAuth } from '../context/authContextCore.js'
import { getFullName, getRoleName } from '../utils/userUtils.js'

function SummaryCard({ label, value, tone = '#0b74d1' }) {
  return (
    <Card
      elevation={0}
      sx={{
        border: '1px solid',
        borderColor: 'divider',
        borderRadius: 1,
        minHeight: 108,
      }}
    >
      <CardContent sx={{ p: 2, '&:last-child': { pb: 2 } }}>
        <Stack spacing={1.1}>
          <Stack direction="row" spacing={0.8} alignItems="center">
            <Box sx={{ width: 8, height: 8, borderRadius: '50%', bgcolor: tone }} />
            <Typography sx={{ color: 'text.secondary', fontSize: '0.78rem', fontWeight: 600 }}>{label}</Typography>
          </Stack>
          <Typography sx={{ color: 'text.primary', fontSize: '1.05rem', fontWeight: 600, lineHeight: 1.25 }}>{value}</Typography>
        </Stack>
      </CardContent>
    </Card>
  )
}

function Dashboard() {
  const { menus, user } = useAuth()
  const fullName = getFullName(user)
  const roleName = getRoleName(user)

  return (
    <PageWorkspace title="Dashboard">
      <Stack spacing={2.5}>
        <Paper
          elevation={0}
          sx={{
            border: '1px solid',
            borderColor: 'divider',
            borderRadius: 1.5,
            bgcolor: '#ffffff',
            p: { xs: 2, md: 2.5 },
          }}
        >
          <Stack spacing={1.1}>
            <Typography sx={{ color: 'text.primary', fontSize: { xs: '1.25rem', md: '1.45rem' }, fontWeight: 600 }}>
              Welcome, {fullName}
            </Typography>
            <Typography sx={{ color: 'text.secondary', fontSize: '0.94rem' }}>You are successfully authenticated.</Typography>
            <Stack direction="row" spacing={1} sx={{ pt: 1, flexWrap: 'wrap' }}>
              <Chip label={`Role: ${roleName}`} size="small" color="primary" variant="outlined" />
              <Chip label="JWT Validated" size="small" color="success" variant="outlined" />
            </Stack>
          </Stack>
        </Paper>

        <Box
          sx={{
            display: 'grid',
            gridTemplateColumns: {
              xs: '1fr',
              sm: 'repeat(2, minmax(0, 1fr))',
              lg: 'repeat(4, minmax(0, 1fr))',
            },
            gap: { xs: 1.5, md: 2 },
            width: '100%',
          }}
        >
          <SummaryCard label="Authentication" value="Active" tone="#16864a" />
          <SummaryCard label="API Server" value="Connected" tone="#0b74d1" />
          <SummaryCard label="Role" value={roleName} tone="#f59a23" />
          <SummaryCard label="Available Modules" value={menus.length} tone="#7c3aed" />
        </Box>
      </Stack>
    </PageWorkspace>
  )
}

export default Dashboard
