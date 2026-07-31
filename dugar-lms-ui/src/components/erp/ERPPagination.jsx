import FirstPageIcon from '@mui/icons-material/FirstPage'
import KeyboardArrowLeftIcon from '@mui/icons-material/KeyboardArrowLeft'
import KeyboardArrowRightIcon from '@mui/icons-material/KeyboardArrowRight'
import LastPageIcon from '@mui/icons-material/LastPage'
import { Box, IconButton, MenuItem, Select, Stack, Typography } from '@mui/material'
import { erpLayout, erpTypography } from './erpTokens.js'

function ERPPagination({ page = 0, pageSize = 25, pageSizeOptions = [], totalElements = 0, onPageChange, onPageSizeChange }) {
  const totalPages = Math.max(1, Math.ceil(totalElements / pageSize) || 1)
  const pageStart = totalElements === 0 ? 0 : page * pageSize + 1
  const pageEnd = Math.min(totalElements, (page + 1) * pageSize)

  return (
    <Box
      sx={{
        border: `1px solid ${erpLayout.borderColor}`,
        borderTop: 0,
        bgcolor: erpLayout.panelBackground,
        display: 'flex',
        justifyContent: 'center',
        width: '100%',
        px: 1,
        py: 0.45,
      }}
    >
      <Stack
        direction="row"
        spacing={1}
        alignItems="center"
        justifyContent="center"
        sx={{ minHeight: 30, flex: '0 1 auto', flexWrap: 'wrap' }}
      >
        <Stack direction="row" spacing={1} alignItems="center" justifyContent="center" sx={{ minHeight: 30 }}>
          <Typography sx={{ display: 'inline-flex', alignItems: 'center', height: 28, fontFamily: erpTypography.fontFamily, fontSize: erpTypography.fontSize, lineHeight: 1, color: erpLayout.black }}>
            Rows
          </Typography>
          <Select
            size="small"
            value={pageSize}
            onChange={(event) => onPageSizeChange?.(Number(event.target.value))}
            sx={{
              display: 'inline-flex',
              alignItems: 'center',
              height: 28,
              minWidth: 82,
              fontSize: erpTypography.fontSize,
              '& .MuiOutlinedInput-notchedOutline': { borderColor: erpLayout.borderColor },
              '& .MuiSelect-select': {
                display: 'flex',
                alignItems: 'center',
                height: 28,
                minHeight: '0 !important',
                py: 0,
              },
              '& .MuiSvgIcon-root': {
                top: '50%',
                transform: 'translateY(-50%)',
              },
            }}
          >
            {pageSizeOptions.map((option) => (
              <MenuItem key={option} value={option} sx={{ fontSize: erpTypography.fontSize }}>
                {option}
              </MenuItem>
            ))}
          </Select>
          <Typography sx={{ display: 'inline-flex', alignItems: 'center', height: 28, fontFamily: erpTypography.fontFamily, fontSize: erpTypography.fontSize, lineHeight: 1, color: erpLayout.black }}>
            Showing {pageStart}-{pageEnd} of {totalElements}
          </Typography>
        </Stack>
        <Stack direction="row" spacing={0.25} alignItems="center" justifyContent="center" sx={{ minHeight: 30 }}>
          <IconButton size="small" onClick={() => onPageChange?.(0)} disabled={page <= 0} sx={{ width: 28, height: 28, p: 0 }}>
            <FirstPageIcon fontSize="inherit" />
          </IconButton>
          <IconButton size="small" onClick={() => onPageChange?.(Math.max(page - 1, 0))} disabled={page <= 0} sx={{ width: 28, height: 28, p: 0 }}>
            <KeyboardArrowLeftIcon fontSize="inherit" />
          </IconButton>
          <Typography sx={{ display: 'inline-flex', alignItems: 'center', justifyContent: 'center', height: 28, minWidth: 56, textAlign: 'center', fontFamily: erpTypography.fontFamily, fontSize: erpTypography.fontSize, lineHeight: 1 }}>
            {page + 1} / {totalPages}
          </Typography>
          <IconButton size="small" onClick={() => onPageChange?.(Math.min(page + 1, totalPages - 1))} disabled={page >= totalPages - 1} sx={{ width: 28, height: 28, p: 0 }}>
            <KeyboardArrowRightIcon fontSize="inherit" />
          </IconButton>
          <IconButton size="small" onClick={() => onPageChange?.(totalPages - 1)} disabled={page >= totalPages - 1} sx={{ width: 28, height: 28, p: 0 }}>
            <LastPageIcon fontSize="inherit" />
          </IconButton>
        </Stack>
      </Stack>
    </Box>
  )
}

export default ERPPagination
