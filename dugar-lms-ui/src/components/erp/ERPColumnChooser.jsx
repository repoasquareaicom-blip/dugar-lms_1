import ViewColumnOutlinedIcon from '@mui/icons-material/ViewColumnOutlined'
import {
  Button,
  Checkbox,
  ListItemText,
  Menu,
  MenuItem,
} from '@mui/material'
import { useState } from 'react'
import { erpTypography } from './erpTokens.js'

function ERPColumnChooser({ columns = [], lockedFields = [], visibilityModel = {}, onChange }) {
  const [anchorEl, setAnchorEl] = useState(null)

  const handleToggle = (field) => {
    onChange?.({
      ...visibilityModel,
      [field]: visibilityModel[field] === false,
    })
  }

  return (
    <>
      <Button
        startIcon={<ViewColumnOutlinedIcon sx={{ fontSize: 16 }} />}
        onClick={(event) => setAnchorEl(event.currentTarget)}
        variant="outlined"
        sx={{ minWidth: 148, height: 30, fontSize: erpTypography.toolbarFontSize, fontWeight: 600 }}
      >
        Customize Columns
      </Button>
      <Menu
        anchorEl={anchorEl}
        open={Boolean(anchorEl)}
        onClose={() => setAnchorEl(null)}
        slotProps={{ paper: { sx: { minWidth: 220 } } }}
      >
        {columns.map((column) => {
          const disabled = lockedFields.includes(column.field)
          const checked = visibilityModel[column.field] !== false

          return (
            <MenuItem
              key={column.field}
              disabled={disabled}
              onClick={() => !disabled && handleToggle(column.field)}
              dense
            >
              <Checkbox size="small" checked={checked} disabled={disabled} />
              <ListItemText primary={column.headerName} primaryTypographyProps={{ fontSize: erpTypography.fontSize }} />
            </MenuItem>
          )
        })}
      </Menu>
    </>
  )
}

export default ERPColumnChooser
