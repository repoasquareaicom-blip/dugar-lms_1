import TaskAltOutlinedIcon from '@mui/icons-material/TaskAltOutlined'
import RestartAltOutlinedIcon from '@mui/icons-material/RestartAltOutlined'
import { Box, Button, Collapse, MenuItem, Stack, TextField, Typography } from '@mui/material'
import { erpLayout, erpTypography } from './erpTokens.js'

function ERPFilterPanel({ fields = [], onApply, onChange, onReset, open = false, values = {} }) {
  const handleFieldChange = (field, value) => {
    onChange?.({ ...values, [field]: value })
  }

  return (
    <Collapse in={open} timeout={140} unmountOnExit>
      <Box
        sx={{
          border: `1px solid ${erpLayout.borderColor}`,
          borderTop: 0,
          bgcolor: erpLayout.panelBackground,
          px: 1,
          py: 1,
        }}
      >
        <Box
          sx={{
            display: 'grid',
            gap: 1,
            gridTemplateColumns: {
              xs: '1fr',
              md: 'repeat(2, minmax(180px, 1fr))',
              xl: 'repeat(4, minmax(180px, 1fr))',
            },
          }}
        >
          {fields.map((field) => (
            <Box key={field.field}>
              <Typography sx={{ mb: 0.35, fontFamily: erpTypography.fontFamily, fontSize: erpTypography.fontSize, fontWeight: 700, color: erpLayout.black }}>
                {field.label}
              </Typography>
              <TextField
                select={field.type === 'select'}
                size="small"
                type={field.type === 'number' || field.type === 'date' ? field.type : 'text'}
                value={values[field.field] ?? ''}
                onChange={(event) => handleFieldChange(field.field, event.target.value)}
                placeholder={field.placeholder || field.label}
                sx={{
                  width: '100%',
                  '& .MuiInputBase-root': {
                    height: 30,
                    bgcolor: erpLayout.white,
                    color: erpLayout.black,
                    fontFamily: erpTypography.fontFamily,
                    fontSize: erpTypography.fontSize,
                  },
                  '& .MuiOutlinedInput-notchedOutline': {
                    borderColor: erpLayout.borderColor,
                  },
                }}
              >
                {field.type === 'select' && <MenuItem value="">All</MenuItem>}
                {(field.options || []).map((option) => {
                  const optionValue = typeof option === 'object' ? option.value : option
                  const optionLabel = typeof option === 'object' ? option.label : option
                  return (
                    <MenuItem key={optionValue} value={optionValue} sx={{ fontSize: erpTypography.fontSize }}>
                      {optionLabel}
                    </MenuItem>
                  )
                })}
              </TextField>
            </Box>
          ))}
        </Box>
        <Stack direction="row" spacing={1} justifyContent="flex-end" sx={{ mt: 1 }}>
          <Button startIcon={<TaskAltOutlinedIcon sx={{ fontSize: 16 }} />} onClick={() => onApply?.(values)} variant="contained" sx={{ height: 30, fontSize: erpTypography.fontSize, fontWeight: 700 }}>
            Apply
          </Button>
          <Button startIcon={<RestartAltOutlinedIcon sx={{ fontSize: 16 }} />} onClick={() => onReset?.()} variant="outlined" sx={{ height: 30, fontSize: erpTypography.fontSize, fontWeight: 700 }}>
            Reset
          </Button>
        </Stack>
      </Box>
    </Collapse>
  )
}

export default ERPFilterPanel
