import ExpandLessIcon from '@mui/icons-material/ExpandLess'
import ExpandMoreIcon from '@mui/icons-material/ExpandMore'
import {
  Box,
  Collapse,
  Divider,
  Drawer,
  List,
  ListItemButton,
  ListItemIcon,
  ListItemText,
  Stack,
  Typography,
} from '@mui/material'
import { useState } from 'react'
import { getMenuChildren, getMenuId, getMenuLabel } from '../../utils/menuUtils.js'
import IconResolver from '../../utils/IconResolver.jsx'
import { mobileItemSx, navColors } from './navigationStyles.js'

function MobileMenuItem({ activePathIds = [], item, level = 0, onSelect }) {
  const [open, setOpen] = useState(() => activePathIds.includes(item.id))
  const children = getMenuChildren(item)
  const hasChildren = children.length > 0
  const isActive = activePathIds.includes(item.id)

  const handleClick = () => {
    if (hasChildren && !item.urlPath) {
      setOpen((current) => !current)
    }

    onSelect(item, hasChildren)
  }

  return (
    <>
      <ListItemButton
        onClick={handleClick}
        selected={isActive}
        sx={mobileItemSx(isActive, level)}
      >
        <ListItemIcon sx={{ color: 'inherit', minWidth: 34 }}>
          <IconResolver iconName={item.icon} size={16} />
        </ListItemIcon>
        <ListItemText
          primary={getMenuLabel(item)}
          primaryTypographyProps={{ fontSize: '0.9rem', fontWeight: isActive || level === 0 ? 600 : 500 }}
        />
        {hasChildren &&
          (open ? (
            <ExpandLessIcon fontSize="small" sx={{ color: navColors.textMuted }} />
          ) : (
            <ExpandMoreIcon fontSize="small" sx={{ color: navColors.textMuted }} />
          ))}
      </ListItemButton>
      {hasChildren && (
        <Collapse in={open} timeout="auto" unmountOnExit>
          <List disablePadding>
            {children.map((child, index) => (
              <MobileMenuItem
                activePathIds={activePathIds}
                key={getMenuId(child, `mobile-child-${index}`)}
                item={child}
                level={level + 1}
                onSelect={onSelect}
              />
            ))}
          </List>
        </Collapse>
      )}
    </>
  )
}

function MobileNavigationDrawer({ activePathIds = [], items, onClose, onSelect, open }) {
  return (
    <Drawer open={open} onClose={onClose} variant="temporary">
      <Box sx={{ width: 300, maxWidth: '86vw' }} role="presentation">
        <Stack spacing={0.5} sx={{ p: 2 }}>
          <Typography sx={{ color: navColors.appBar, fontSize: '1.05rem', fontWeight: 600 }}>LoanIntelli</Typography>
          <Typography sx={{ color: navColors.textMuted, fontSize: '0.78rem', fontWeight: 500 }}>Navigation</Typography>
        </Stack>
        <Divider />
        <List disablePadding>
          {items.map((item, index) => (
            <MobileMenuItem
              activePathIds={activePathIds}
              key={getMenuId(item, `mobile-${index}`)}
              item={item}
              onSelect={onSelect}
            />
          ))}
        </List>
      </Box>
    </Drawer>
  )
}

export default MobileNavigationDrawer
