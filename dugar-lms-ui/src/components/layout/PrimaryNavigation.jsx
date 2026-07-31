import { Button, Stack } from '@mui/material'
import IconResolver from '../../utils/IconResolver.jsx'
import { primaryNavButtonSx } from './navigationStyles.js'

function PrimaryNavigation({ activeModuleId, items, onSelect }) {
  return (
    <Stack
      direction="row"
      alignItems="center"
      spacing={0.5}
      sx={{
        height: 38,
        minWidth: 0,
        maxWidth: '100%',
        alignSelf: 'center',
        overflowX: 'auto',
        overflowY: 'hidden',
        pr: 0.5,
        scrollbarWidth: 'thin',
      }}
    >
      {items.map((item) => {
        const isActive = item.id === activeModuleId

        return (
          <Button
            key={item.id}
            onClick={() => onSelect(item)}
            sx={primaryNavButtonSx(isActive)}
          >
            <IconResolver iconName={item.icon} size={16} />
            {item.label}
          </Button>
        )
      })}
    </Stack>
  )
}

export default PrimaryNavigation
