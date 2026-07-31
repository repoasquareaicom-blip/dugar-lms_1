export const navColors = {
  appBar: '#092f5c',
  appBarBorder: '#154575',
  primaryText: '#1565C0',
  primaryTextDark: '#0b3f6d',
  activeBg: '#eef7ff',
  activeBgHover: '#e1f0ff',
  hoverLight: '#f5f9ff',
  border: '#d8e2ee',
  text: '#172033',
  textMuted: '#5f6f84',
  orange: '#FF9800',
}

export const navHeights = {
  top: 60,
  secondary: 44,
  footer: 28,
}

export const focusRing = {
  '&:focus-visible': {
    outline: `2px solid ${navColors.orange}`,
    outlineOffset: 2,
  },
}

export const primaryNavButtonSx = (isActive) => ({
  minWidth: 'auto',
  px: 1.05,
  py: 0.65,
  height: 36,
  color: isActive ? navColors.primaryTextDark : 'rgba(255, 255, 255, 0.8)',
  bgcolor: isActive ? navColors.activeBg : 'transparent',
  borderRadius: 1,
  flex: '0 0 auto',
  fontSize: '0.84rem',
  fontWeight: 600,
  gap: 0.65,
  lineHeight: 1.25,
  position: 'relative',
  textTransform: 'none',
  transition: 'background-color 180ms ease, color 180ms ease, border-color 180ms ease',
  whiteSpace: 'nowrap',
  '&::after': {
    position: 'absolute',
    left: 10,
    right: 10,
    bottom: 3,
    height: 2,
    borderRadius: 99,
    bgcolor: isActive ? navColors.orange : 'transparent',
    content: '""',
    transition: 'background-color 180ms ease',
  },
  '&:hover': {
    bgcolor: isActive ? navColors.activeBgHover : 'rgba(255, 255, 255, 0.12)',
    color: isActive ? navColors.primaryTextDark : '#ffffff',
  },
  ...focusRing,
})

export const secondaryItemShellSx = (isActive) => ({
  display: 'inline-flex',
  alignItems: 'center',
  bgcolor: isActive ? navColors.activeBg : 'transparent',
  borderBottom: isActive ? `2px solid ${navColors.primaryText}` : '2px solid transparent',
  borderRadius: 1,
  overflow: 'hidden',
  transition: 'background-color 180ms ease, border-color 180ms ease',
  '&:hover': {
    bgcolor: isActive ? navColors.activeBgHover : navColors.hoverLight,
  },
})

export const secondaryButtonSx = (isActive) => ({
  color: isActive ? navColors.primaryTextDark : '#435168',
  borderRadius: 1,
  fontSize: '0.84rem',
  fontWeight: 600,
  gap: 0.65,
  minHeight: 34,
  minWidth: 'auto',
  px: 1.15,
  py: 0.45,
  textTransform: 'none',
  transition: 'background-color 180ms ease, color 180ms ease',
  whiteSpace: 'nowrap',
  ...focusRing,
})

export const cascadePanelSx = {
  minWidth: 230,
  maxWidth: 320,
  py: 0.75,
  border: `1px solid ${navColors.border}`,
  borderRadius: 1,
  bgcolor: '#ffffff',
  boxShadow: '0 14px 34px rgba(15, 23, 42, 0.13)',
  overflow: 'hidden',
}

export const cascadeItemSx = (isActive) => ({
  width: '100%',
  minHeight: 40,
  justifyContent: 'flex-start',
  gap: 1,
  px: 1.25,
  py: 0.65,
  borderLeft: isActive ? `3px solid ${navColors.orange}` : '3px solid transparent',
  color: isActive ? navColors.primaryTextDark : '#334155',
  bgcolor: isActive ? navColors.activeBg : '#ffffff',
  transition: 'background-color 180ms ease, color 180ms ease',
  '&:hover': {
    bgcolor: isActive ? navColors.activeBgHover : navColors.hoverLight,
  },
  ...focusRing,
})

export const mobileItemSx = (isActive, level = 0) => ({
  bgcolor: isActive ? navColors.activeBg : 'transparent',
  borderLeft: isActive ? `3px solid ${navColors.orange}` : '3px solid transparent',
  color: isActive ? navColors.primaryTextDark : '#334155',
  minHeight: 42,
  pl: 2 + level * 1.75,
  transition: 'background-color 180ms ease, color 180ms ease',
  '&.Mui-selected': {
    bgcolor: navColors.activeBg,
  },
  '&.Mui-selected:hover': {
    bgcolor: navColors.activeBgHover,
  },
  '&:hover': {
    bgcolor: isActive ? navColors.activeBgHover : navColors.hoverLight,
  },
  ...focusRing,
})
