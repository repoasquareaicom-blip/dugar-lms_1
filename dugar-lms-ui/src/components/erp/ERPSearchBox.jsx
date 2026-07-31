import SearchIcon from '@mui/icons-material/Search'
import { InputAdornment, TextField } from '@mui/material'
import { erpLayout, erpTypography } from './erpTokens.js'

function ERPSearchBox({ onChange, placeholder = 'Search', value = '', width = 260 }) {
  return (
    <TextField
      value={value}
      onChange={(event) => onChange?.(event.target.value)}
      placeholder={placeholder}
      size="small"
      sx={{
        width,
        '& .MuiInputBase-root': {
          height: 30,
          borderRadius: 0.5,
          bgcolor: erpLayout.white,
          color: erpLayout.black,
          fontFamily: erpTypography.fontFamily,
          fontSize: erpTypography.fontSize,
        },
        '& .MuiOutlinedInput-notchedOutline': {
          borderColor: erpLayout.borderColor,
        },
        '& .MuiInputBase-root:hover .MuiOutlinedInput-notchedOutline': {
          borderColor: erpLayout.primary,
        },
        '& .MuiInputBase-input::placeholder': {
          color: erpLayout.black,
          opacity: 0.72,
        },
      }}
      slotProps={{
        input: {
          startAdornment: (
            <InputAdornment position="start">
              <SearchIcon sx={{ color: erpLayout.black, fontSize: 16 }} />
            </InputAdornment>
          ),
        },
      }}
    />
  )
}

export default ERPSearchBox