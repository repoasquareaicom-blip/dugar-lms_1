import ChevronRightIcon from '@mui/icons-material/ChevronRight'
import ExpandMoreIcon from '@mui/icons-material/ExpandMore'
import { Box, ButtonBase, ClickAwayListener, IconButton, Paper, Popper, Stack, Tooltip, Typography } from '@mui/material'
import { useRef, useState } from 'react'
import IconResolver from '../../utils/IconResolver.jsx'
import { getMenuChildren, getMenuLabel } from '../../utils/menuUtils.js'
import { cascadeItemSx, cascadePanelSx, navColors } from './navigationStyles.js'

const closeDelayMs = 180

function CascadingMenuItem({ activePathIds = [], depth = 0, item, onCloseAll, onSelect }) {
  const [anchorEl, setAnchorEl] = useState(null)
  const closeTimer = useRef(null)
  const children = getMenuChildren(item)
  const hasChildren = children.length > 0
  const isActive = activePathIds.includes(item.id)
  const isOpen = Boolean(anchorEl)

  const clearCloseTimer = () => {
    if (closeTimer.current) {
      clearTimeout(closeTimer.current)
      closeTimer.current = null
    }
  }

  const openSubmenu = (event) => {
    clearCloseTimer()

    if (hasChildren) {
      setAnchorEl(event.currentTarget)
    }
  }

  const scheduleClose = () => {
    clearCloseTimer()
    closeTimer.current = setTimeout(() => setAnchorEl(null), closeDelayMs)
  }

  const handleActivate = () => {
    if (item.urlPath) {
      onSelect(item)
      onCloseAll()
      return
    }

    if (hasChildren) {
      setAnchorEl((current) => current || document.activeElement)
    }
  }

  const handleKeyDown = (event) => {
    if (event.key === 'ArrowRight' && hasChildren) {
      event.preventDefault()
      setAnchorEl(event.currentTarget)
    }

    if (event.key === 'ArrowLeft') {
      event.preventDefault()
      setAnchorEl(null)
    }

    if (event.key === 'Enter') {
      event.preventDefault()
      handleActivate()
    }

    if (event.key === 'Escape') {
      event.preventDefault()
      onCloseAll()
    }
  }

  return (
    <Box onMouseEnter={openSubmenu} onMouseLeave={scheduleClose}>
      <ButtonBase
        onClick={handleActivate}
        onKeyDown={handleKeyDown}
        sx={cascadeItemSx(isActive)}
      >
        <IconResolver iconName={item.icon} size={16} />
        <Tooltip title={getMenuLabel(item)} enterDelay={500}>
          <Typography
            sx={{
              flex: 1,
              fontSize: '0.88rem',
              fontWeight: isActive ? 600 : 500,
              overflow: 'hidden',
              textAlign: 'left',
              textOverflow: 'ellipsis',
              whiteSpace: 'nowrap',
            }}
          >
            {getMenuLabel(item)}
          </Typography>
        </Tooltip>
        {hasChildren && <ChevronRightIcon fontSize="small" sx={{ color: isActive ? navColors.primaryTextDark : '#64748b' }} />}
      </ButtonBase>

      {hasChildren && (
        <CascadingMenuPanel
          activePathIds={activePathIds}
          anchorEl={anchorEl}
          depth={depth + 1}
          items={children}
          onClose={scheduleClose}
          onCloseAll={onCloseAll}
          onSelect={onSelect}
          onStayOpen={clearCloseTimer}
          open={isOpen}
        />
      )}
    </Box>
  )
}

function CascadingMenuPanel({
  activePathIds = [],
  anchorEl,
  depth = 0,
  items,
  onClose,
  onCloseAll,
  onSelect,
  onStayOpen,
  open,
}) {
  return (
    <Popper
      anchorEl={anchorEl}
      open={open}
      placement={depth === 0 ? 'bottom-start' : 'right-start'}
      modifiers={[
        { name: 'flip', enabled: true, options: { fallbackPlacements: ['left-start', 'right-start', 'bottom-start'] } },
        { name: 'preventOverflow', enabled: true, options: { boundary: 'viewport', padding: 8 } },
        { name: 'offset', enabled: true, options: { offset: depth === 0 ? [0, 8] : [4, 0] } },
      ]}
      sx={{ zIndex: 1600 }}
    >
      <Paper
        elevation={0}
        onMouseEnter={onStayOpen}
        onMouseLeave={onClose}
        sx={cascadePanelSx}
      >
        <Stack role="menu">
          {items.map((item) => (
            <CascadingMenuItem
              activePathIds={activePathIds}
              depth={depth}
              item={item}
              key={item.id}
              onCloseAll={onCloseAll}
              onSelect={onSelect}
            />
          ))}
        </Stack>
      </Paper>
    </Popper>
  )
}

function CascadingMenu({ activePathIds = [], children, items, onSelect }) {
  const [anchorEl, setAnchorEl] = useState(null)
  const open = Boolean(anchorEl)

  const closeAll = () => setAnchorEl(null)

  return (
    <ClickAwayListener onClickAway={closeAll}>
      <Box component="span">
        {children ? (
          <Box
            component="span"
            onClick={(event) => setAnchorEl(open ? null : event.currentTarget)}
            sx={{ display: 'inline-flex' }}
          >
            {typeof children === 'function' ? children({ open }) : children}
          </Box>
        ) : (
          <IconButton size="small" onClick={(event) => setAnchorEl(open ? null : event.currentTarget)}>
            <ExpandMoreIcon
              fontSize="small"
              sx={{
                transform: open ? 'rotate(180deg)' : 'rotate(0deg)',
                transition: 'transform 200ms ease',
              }}
            />
          </IconButton>
        )}
        <CascadingMenuPanel
          activePathIds={activePathIds}
          anchorEl={anchorEl}
          depth={0}
          items={items}
          onClose={() => {}}
          onCloseAll={closeAll}
          onSelect={onSelect}
          onStayOpen={() => {}}
          open={open}
        />
      </Box>
    </ClickAwayListener>
  )
}

export default CascadingMenu
