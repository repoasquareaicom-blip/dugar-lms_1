import AccessTimeOutlinedIcon from '@mui/icons-material/AccessTimeOutlined'
import ArrowBackOutlinedIcon from '@mui/icons-material/ArrowBackOutlined'
import { Box, Button, Paper, Stack, Typography } from '@mui/material'
import { useNavigate } from 'react-router-dom'
import { erpLayout, erpTypography } from '../../components/erp/erpTokens.js'

function ContractComingSoonPage() {
  const navigate = useNavigate()

  return (
    <Paper
      elevation={0}
      sx={{
        maxWidth: 680,
        border: `1px solid ${erpLayout.borderColor}`,
        bgcolor: erpLayout.white,
        px: 2,
        py: 2,
      }}
    >
      <Stack spacing={1.25} alignItems="flex-start">
        <Box sx={{ width: 34, height: 34, display: 'grid', placeItems: 'center', bgcolor: erpLayout.primarySoft, color: erpLayout.primary, borderRadius: 0.75 }}>
          <AccessTimeOutlinedIcon sx={{ fontSize: 18 }} />
        </Box>
        <Typography sx={{ fontFamily: erpTypography.fontFamily, fontSize: erpTypography.headerFontSize, fontWeight: 700, color: erpLayout.black }}>
          Create Contract
        </Typography>
        <Typography sx={{ fontFamily: erpTypography.fontFamily, fontSize: erpTypography.fontSize, color: erpLayout.black, lineHeight: 1.6 }}>
          Contract origination is planned next. This foundation release delivers the reusable enterprise list framework, backend paging contract, and Contracts navigation shell used by future LMS modules.
        </Typography>
        <Button startIcon={<ArrowBackOutlinedIcon sx={{ fontSize: 16 }} />} variant="contained" onClick={() => navigate('/contracts')} sx={{ height: 30, fontSize: erpTypography.toolbarFontSize, fontWeight: 700 }}>
          Back to Contract List
        </Button>
      </Stack>
    </Paper>
  )
}

export default ContractComingSoonPage