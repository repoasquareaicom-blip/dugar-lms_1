import { Box, Paper, Stack, Typography } from '@mui/material'
import { erpLayout, erpTypography } from './erpTokens.js'

function ERPToolbar({ actions, searchSlot, title }) {
  return (
    <Paper
      elevation={0}
      sx={{
        border: `1px solid ${erpLayout.borderColor}`,
        borderBottom: 0,
        borderRadius: '4px 4px 0 0',
        bgcolor: erpLayout.panelBackground,
      }}
    >
      <Stack
        direction={{ xs: 'column', lg: 'row' }}
        alignItems={{ xs: 'stretch', lg: 'center' }}
        justifyContent="space-between"
        spacing={1}
        sx={{ px: 1, py: 0.75 }}
      >
        <Stack direction="row" spacing={1} alignItems="center" sx={{ minWidth: 0 }}>
          {title && (
            <>
              <Typography
                component="h1"
                sx={{
                  color: erpLayout.black,
                  fontFamily: erpTypography.fontFamily,
                  fontSize: erpTypography.headerFontSize,
                  fontWeight: 700,
                  whiteSpace: 'nowrap',
                }}
              >
                {title}
              </Typography>
              <Box sx={{ width: 1, height: 18, bgcolor: erpLayout.mutedBorder, display: { xs: 'none', md: 'block' } }} />
            </>
          )}
          <Box sx={{ display: 'flex', justifyContent: { xs: 'stretch', lg: 'flex-start' }, minWidth: 0 }}>{searchSlot}</Box>
        </Stack>
        <Box sx={{ display: 'flex', justifyContent: { xs: 'stretch', lg: 'flex-end' }, minWidth: 0, overflowX: 'auto' }}>{actions}</Box>
      </Stack>
    </Paper>
  )
}

export default ERPToolbar
