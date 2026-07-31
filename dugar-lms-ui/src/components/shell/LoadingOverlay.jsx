import { Backdrop, CircularProgress, Stack, Typography } from '@mui/material'

function LoadingOverlay({ loading, message = 'Loading...' }) {
  return (
    <Backdrop
      open={Boolean(loading)}
      sx={{
        zIndex: (theme) => theme.zIndex.modal + 1,
        bgcolor: 'rgba(15, 23, 42, 0.32)',
        backdropFilter: 'blur(2px)',
      }}
    >
      <Stack spacing={2} alignItems="center" sx={{ color: '#ffffff' }}>
        <CircularProgress color="inherit" />
        <Typography sx={{ fontWeight: 700 }}>{message}</Typography>
      </Stack>
    </Backdrop>
  )
}

export default LoadingOverlay
