import ExpandMoreIcon from '@mui/icons-material/ExpandMore'
import { Box, Button, Paper, Stack } from '@mui/material'
import { useMenu } from '../../context/menuContextCore.js'
import { getMenuChildren, getMenuLabel } from '../../utils/menuUtils.js'
import IconResolver from '../../utils/IconResolver.jsx'
import CascadingMenu from './CascadingMenu.jsx'
import { navColors, navHeights, secondaryButtonSx, secondaryItemShellSx } from './navigationStyles.js'

function SecondaryNavigation({ activePathIds = [], activeModule, activeSecondaryId, onSelectChild }) {
  const { activeMenu } = useMenu()
  const children = getMenuChildren(activeModule)

  if (children.length === 0) {
    return null
  }

  return (
    <Paper
      elevation={0}
      square
      sx={{
        position: 'fixed',
        top: navHeights.top,
        left: 0,
        right: 0,
        zIndex: 1090,
        minHeight: navHeights.secondary,
        borderBottom: `1px solid ${navColors.border}`,
        bgcolor: '#ffffff',
        boxShadow: '0 3px 10px rgba(15, 23, 42, 0.04)',
      }}
    >
      <Box sx={{ width: '100%', px: { xs: 1.5, md: 2.5 }, py: 0.55, pr: { xs: 16, md: 24 }, overflowX: 'auto' }}>
        <Stack
          direction="row"
          spacing={1}
          alignItems="center"
          sx={{ minWidth: 'max-content' }}
        >
          <Stack direction="row" spacing={0.5}>
          {children.map((child) => {
            const childItems = getMenuChildren(child)
            const hasDropdown = childItems.length > 0
            const isActive = child.id === activeSecondaryId || activePathIds.includes(child.id)

            return (
              <Box
                key={child.id}
                sx={secondaryItemShellSx(isActive)}
              >
                {hasDropdown ? (
                  <CascadingMenu activePathIds={activePathIds} items={childItems} onSelect={onSelectChild}>
                    {({ open }) => (
                      <Button
                        sx={secondaryButtonSx(isActive)}
                      >
                        <IconResolver iconName={child.icon} size={15} />
                        {getMenuLabel(child)}
                        <ExpandMoreIcon
                          fontSize="small"
                          sx={{
                            ml: -0.25,
                            transform: open ? 'rotate(180deg)' : 'rotate(0deg)',
                            transition: 'transform 200ms ease',
                          }}
                        />
                      </Button>
                    )}
                  </CascadingMenu>
                ) : (
                  <Button
                    onClick={() => onSelectChild(child)}
                    sx={secondaryButtonSx(isActive)}
                  >
                    <IconResolver iconName={child.icon} size={15} />
                    {getMenuLabel(child)}
                  </Button>
                )}
              </Box>
            )
          })}
          </Stack>
        </Stack>
      </Box>
      <Box
        component="h1"
        sx={{
          m: 0,
          position: 'absolute',
          top: '50%',
          right: { xs: 12, md: 24 },
          transform: 'translateY(-50%)',
          color: navColors.appBar,
          fontSize: { xs: '1rem', md: '1.18rem' },
          fontWeight: 500,
          lineHeight: 1.2,
          textAlign: 'right',
          whiteSpace: 'nowrap',
        }}
      >
        {activeMenu?.label || ''}
      </Box>
    </Paper>
  )
}

export default SecondaryNavigation
