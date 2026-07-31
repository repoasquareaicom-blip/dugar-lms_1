import { useCallback, useEffect, useState } from 'react'
import {
  Alert,
  Box,
  Button,
  Checkbox,
  CircularProgress,
  Divider,
  FormControlLabel,
  IconButton,
  LinearProgress,
  Link,
  Paper,
  Slide,
  Snackbar,
  Stack,
  TextField,
  Typography,
} from '@mui/material'
import { useNavigate } from 'react-router-dom'
import apiClient from '../api/apiClient.js'
import { useAuth } from '../context/authContextCore.js'
import logo from '../assets/images/logo.png'
import './Login.css'

const statusContent = {
  checking: {
    title: 'Checking API Server...',
    subtitle: 'Please wait while we verify the connection',
    tone: '#60758d',
  },
  online: {
    title: 'API Server Online',
    subtitle: 'Connection established successfully',
    tone: '#16864a',
  },
  offline: {
    title: 'API Server Offline',
    subtitle: 'Unable to connect to the authentication server',
    tone: '#d73535',
  },
}

function SlideDownTransition(props) {
  return <Slide {...props} direction="down" />
}

function SvgIcon({ children, size = 22 }) {
  return (
    <Box
      component="svg"
      aria-hidden="true"
      viewBox="0 0 24 24"
      sx={{ width: size, height: size, display: 'block' }}
    >
      {children}
    </Box>
  )
}

function PersonIcon() {
  return (
    <SvgIcon>
      <path
        d="M20 21a8 8 0 0 0-16 0"
        fill="none"
        stroke="currentColor"
        strokeLinecap="round"
        strokeWidth="2"
      />
      <circle cx="12" cy="7" r="4" fill="none" stroke="currentColor" strokeWidth="2" />
    </SvgIcon>
  )
}

function LockIcon() {
  return (
    <SvgIcon>
      <rect
        x="5"
        y="10"
        width="14"
        height="10"
        rx="2"
        fill="none"
        stroke="currentColor"
        strokeWidth="2"
      />
      <path
        d="M8 10V7a4 4 0 0 1 8 0v3"
        fill="none"
        stroke="currentColor"
        strokeLinecap="round"
        strokeWidth="2"
      />
    </SvgIcon>
  )
}

function ArrowRightIcon() {
  return (
    <SvgIcon size={20}>
      <path
        d="M5 12h14M13 6l6 6-6 6"
        fill="none"
        stroke="currentColor"
        strokeLinecap="round"
        strokeLinejoin="round"
        strokeWidth="2"
      />
    </SvgIcon>
  )
}

function ServerIcon() {
  return (
    <SvgIcon>
      <rect
        x="4"
        y="4"
        width="16"
        height="6"
        rx="2"
        fill="none"
        stroke="currentColor"
        strokeWidth="2"
      />
      <rect
        x="4"
        y="14"
        width="16"
        height="6"
        rx="2"
        fill="none"
        stroke="currentColor"
        strokeWidth="2"
      />
      <path d="M8 7h.01M8 17h.01" stroke="currentColor" strokeLinecap="round" strokeWidth="3" />
    </SvgIcon>
  )
}

function StatusGlyph({ status }) {
  if (status === 'online') {
    return (
      <SvgIcon>
        <circle cx="12" cy="12" r="9" fill="none" stroke="currentColor" strokeWidth="2" />
        <path
          d="M8 12.4l2.4 2.4L16.5 9"
          fill="none"
          stroke="currentColor"
          strokeLinecap="round"
          strokeLinejoin="round"
          strokeWidth="2"
        />
      </SvgIcon>
    )
  }

  if (status === 'offline') {
    return (
      <SvgIcon>
        <circle cx="12" cy="12" r="9" fill="none" stroke="currentColor" strokeWidth="2" />
        <path
          d="M9 9l6 6M15 9l-6 6"
          fill="none"
          stroke="currentColor"
          strokeLinecap="round"
          strokeWidth="2"
        />
      </SvgIcon>
    )
  }

  return <ServerIcon />
}

function VisibilityIcon({ hidden }) {
  return (
    <SvgIcon>
      {hidden ? (
        <>
          <path d="M3 3l18 18" fill="none" stroke="currentColor" strokeLinecap="round" strokeWidth="2" />
          <path
            d="M10.73 5.08A10.8 10.8 0 0 1 12 5c5.52 0 9 5 9 7a8.1 8.1 0 0 1-2.1 3.38M6.61 6.61C4.37 8.08 3 10.41 3 12c0 2 3.48 7 9 7 1.81 0 3.39-.54 4.72-1.35"
            fill="none"
            stroke="currentColor"
            strokeLinecap="round"
            strokeLinejoin="round"
            strokeWidth="2"
          />
        </>
      ) : (
        <>
          <path
            d="M3 12s3.48-7 9-7 9 7 9 7-3.48 7-9 7-9-7-9-7Z"
            fill="none"
            stroke="currentColor"
            strokeLinecap="round"
            strokeLinejoin="round"
            strokeWidth="2"
          />
          <circle cx="12" cy="12" r="3" fill="none" stroke="currentColor" strokeWidth="2" />
        </>
      )}
    </SvgIcon>
  )
}

function BackgroundDecor() {
  return (
    <>
      <Box className="login-circuit-pattern" aria-hidden="true" />
      <Box className="login-dot-pattern login-dot-pattern-top" aria-hidden="true" />
      <Box className="login-dot-pattern login-dot-pattern-bottom" aria-hidden="true" />
      <Box className="login-growth-bars" aria-hidden="true">
        <span />
        <span />
        <span />
        <span />
        <span />
      </Box>
      <Box className="login-up-arrow" aria-hidden="true" />
      <Box className="login-footer-wave" aria-hidden="true" />
    </>
  )
}

function FieldIcon({ children }) {
  return <Box className="login-field-icon">{children}</Box>
}

function FormField({ label, icon, ...textFieldProps }) {
  return (
    <Stack spacing={0.55}>
      <Typography className="login-field-label">{label}</Typography>
      <TextField
        variant="outlined"
        fullWidth
        {...textFieldProps}
        slotProps={{
          ...textFieldProps.slotProps,
          input: {
            ...textFieldProps.slotProps?.input,
            startAdornment: <FieldIcon>{icon}</FieldIcon>,
          },
        }}
      />
    </Stack>
  )
}

function ApiStatusPanel({ status, onRetry }) {
  const content = statusContent[status]

  return (
    <Paper
      elevation={0}
      className="login-status-panel"
      sx={{
        border: '1px solid #e3edf7',
        borderRadius: '10px',
        bgcolor: '#ffffff',
        minHeight: 58,
        p: '10px 12px',
      }}
    >
      <Stack direction="row" spacing={1.25} alignItems="center">
        <Box
          sx={{
            width: 34,
            height: 34,
            borderRadius: '9px',
            display: 'grid',
            placeItems: 'center',
            color: content.tone,
            bgcolor: `${content.tone}14`,
            flex: '0 0 auto',
          }}
        >
          <StatusGlyph status={status} />
        </Box>
        <Box sx={{ minWidth: 0, flex: 1 }}>
          <Typography
            sx={{
              color: status === 'offline' ? content.tone : '#17304f',
              fontWeight: 800,
              lineHeight: 1.2,
              fontSize: '0.9rem',
            }}
          >
            {content.title}
          </Typography>
          <Typography
            variant="body2"
            sx={{
              color: status === 'offline' ? content.tone : '#6b7c90',
              mt: 0.2,
              fontSize: '0.76rem',
            }}
          >
            {content.subtitle}
          </Typography>
        </Box>
        {status === 'offline' && (
          <Button
            size="small"
            variant="outlined"
            onClick={onRetry}
            sx={{
              borderColor: '#d73535',
              color: '#d73535',
              borderRadius: 2,
              fontWeight: 700,
              px: 1.3,
              py: 0.35,
              textTransform: 'none',
              '&:hover': {
                borderColor: '#b72525',
                bgcolor: 'rgba(215, 53, 53, 0.06)',
              },
            }}
          >
            Retry
          </Button>
        )}
      </Stack>
      {status === 'checking' && (
        <LinearProgress
          sx={{
            mt: 1,
            height: 3,
            borderRadius: 99,
            bgcolor: '#e8f1fb',
            '& .MuiLinearProgress-bar': {
              bgcolor: '#0b74d1',
            },
          }}
        />
      )}
    </Paper>
  )
}

function VersionDivider() {
  return (
    <Stack className="login-version-row" direction="row" alignItems="center" spacing={1.5}>
      <Divider sx={{ flex: 1, borderColor: '#e3edf7' }} />
      <Typography sx={{ color: '#7b8ca0', fontWeight: 700, fontSize: '11px', textAlign: 'center' }}>
        Version 1.0.0
      </Typography>
      <Divider sx={{ flex: 1, borderColor: '#e3edf7' }} />
    </Stack>
  )
}

function Login() {
  const navigate = useNavigate()
  const { isAuthenticated, login } = useAuth()
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [rememberMe, setRememberMe] = useState(false)
  const [showPassword, setShowPassword] = useState(false)
  const [apiStatus, setApiStatus] = useState('checking')
  const [isSigningIn, setIsSigningIn] = useState(false)
  const [notification, setNotification] = useState({
    open: false,
    message: '',
    severity: 'error',
  })
  const [validationErrors, setValidationErrors] = useState({})

  const retryApiHealth = useCallback(async () => {
    setApiStatus('checking')

    try {
      await apiClient.get('/health')
      setApiStatus('online')
    } catch {
      setApiStatus('offline')
    }
  }, [])

  useEffect(() => {
    if (isAuthenticated) {
      navigate('/dashboard', { replace: true })
      return undefined
    }

    let isActive = true

    apiClient
      .get('/health')
      .then(() => {
        if (isActive) {
          setApiStatus('online')
        }
      })
      .catch(() => {
        if (isActive) {
          setApiStatus('offline')
        }
      })

    return () => {
      isActive = false
    }
  }, [isAuthenticated, navigate])

  const validateForm = () => {
    const nextErrors = {}

    if (!username.trim()) {
      nextErrors.username = 'Username is required'
    }

    if (!password) {
      nextErrors.password = 'Password is required'
    }

    setValidationErrors(nextErrors)
    const messages = Object.values(nextErrors)

    if (messages.length > 0) {
      setNotification({
        open: true,
        message: messages.join(' and '),
        severity: 'warning',
      })
      return false
    }

    return true
  }

  const getLoginErrorMessage = (error) => {
    return (
      error.response?.data?.message ||
      error.response?.data?.error ||
      'Unable to sign in. Please check your credentials and try again.'
    )
  }

  const handleSubmit = async (event) => {
    event.preventDefault()
    setNotification((current) => ({ ...current, open: false }))

    if (!validateForm()) {
      return
    }

    setIsSigningIn(true)

    try {
      const response = await apiClient.post('/auth/login', {
        username: username.trim(),
        password,
      })

      const { token, user, menus } = response.data
      login({ token, user, menus })
      navigate('/dashboard', { replace: true })
    } catch (error) {
      setNotification({
        open: true,
        message: getLoginErrorMessage(error),
        severity: 'error',
      })
    } finally {
      setIsSigningIn(false)
    }
  }

  const handleNotificationClose = (_, reason) => {
    if (reason === 'clickaway') {
      return
    }

    setNotification((current) => ({ ...current, open: false }))
  }

  return (
    <Box className="login-page">
      <BackgroundDecor />

      <Paper component="section" elevation={0} className="login-card">
        <Stack component="form" className="login-form" onSubmit={handleSubmit}>
          <Box className="login-header">
            <Box className="login-logo-wrap">
              <Box component="img" src={logo} alt="LoanIntelli" className="login-logo" />
            </Box>
            <Typography component="h1" className="login-title">
              Welcome Back
            </Typography>
            <Typography className="login-subtitle">
              Sign in to continue to LoanIntelli
            </Typography>
          </Box>

          <Stack spacing={1.75}>
            <FormField
              label="Username"
              icon={<PersonIcon />}
              placeholder="Enter your username"
              value={username}
              onChange={(event) => {
                setUsername(event.target.value)
                setValidationErrors((current) => ({ ...current, username: '' }))
              }}
              autoComplete="username"
              disabled={isSigningIn}
              error={Boolean(validationErrors.username)}
            />
            <FormField
              label="Password"
              icon={<LockIcon />}
              placeholder="Enter your password"
              type={showPassword ? 'text' : 'password'}
              value={password}
              onChange={(event) => {
                setPassword(event.target.value)
                setValidationErrors((current) => ({ ...current, password: '' }))
              }}
              autoComplete="current-password"
              disabled={isSigningIn}
              error={Boolean(validationErrors.password)}
              slotProps={{
                input: {
                  endAdornment: (
                    <IconButton
                      aria-label={showPassword ? 'Hide password' : 'Show password'}
                      edge="end"
                      disabled={isSigningIn}
                      onClick={() => setShowPassword((current) => !current)}
                    >
                      <VisibilityIcon hidden={showPassword} />
                    </IconButton>
                  ),
                },
              }}
            />

            <Stack
              className="login-options-row"
              direction="row"
              alignItems="center"
              justifyContent="space-between"
              gap={1.5}
            >
              <FormControlLabel
                control={
                  <Checkbox
                    checked={rememberMe}
                    onChange={(event) => setRememberMe(event.target.checked)}
                    sx={{
                      color: '#0b74d1',
                      '&.Mui-checked': { color: '#0b74d1' },
                    }}
                  />
                }
                label="Remember me"
                sx={{
                  m: 0,
                  color: '#111827',
                  '& .MuiFormControlLabel-label': { fontWeight: 400, fontSize: '0.88rem' },
                }}
              />
              <Link
                className="login-forgot-link"
                href="#"
                underline="hover"
                onClick={(event) => event.preventDefault()}
                sx={{ color: '#0b74d1', fontWeight: 400, fontSize: '0.88rem' }}
              >
                Forgot Password?
              </Link>
            </Stack>
          </Stack>

          <Button
            type="submit"
            fullWidth
            disabled={apiStatus !== 'online' || isSigningIn}
            endIcon={!isSigningIn && <ArrowRightIcon />}
            sx={{
              minHeight: 48,
              height: 48,
              mt: '2px',
              borderRadius: '9px',
              color: '#ffffff',
              bgcolor: '#0b74d1',
              backgroundImage: 'linear-gradient(135deg, #0b74d1 0%, #075ea9 100%)',
              boxShadow: '0 14px 26px rgba(11, 116, 209, 0.26)',
              fontSize: '1rem',
              fontWeight: 800,
              textTransform: 'none',
              position: 'relative',
              justifyContent: 'center',
              '& .MuiButton-endIcon': {
                position: 'absolute',
                right: 16,
                margin: 0,
              },
              '&:hover': {
                bgcolor: '#075ea9',
                backgroundImage: 'linear-gradient(135deg, #0a6ec5 0%, #064f93 100%)',
                boxShadow: '0 16px 30px rgba(11, 116, 209, 0.32)',
              },
              '&.Mui-disabled': {
                color: '#90a2b7',
                bgcolor: '#e4edf7',
                backgroundImage: 'none',
                boxShadow: 'none',
              },
            }}
          >
            {isSigningIn ? (
              <Stack direction="row" alignItems="center" justifyContent="center" spacing={1}>
                <CircularProgress size={20} sx={{ color: 'inherit' }} />
                <Box component="span">Signing In...</Box>
              </Stack>
            ) : (
              'Sign In'
            )}
          </Button>

          <ApiStatusPanel status={apiStatus} onRetry={retryApiHealth} />
          <VersionDivider />
        </Stack>
      </Paper>

      <Snackbar
        open={notification.open}
        autoHideDuration={4200}
        onClose={handleNotificationClose}
        anchorOrigin={{ vertical: 'top', horizontal: 'center' }}
        TransitionComponent={SlideDownTransition}
      >
        <Alert
          severity={notification.severity}
          variant="filled"
          onClose={handleNotificationClose}
          sx={{
            width: '100%',
            minWidth: { xs: 'auto', sm: 380 },
            borderRadius: 3,
            boxShadow: '0 18px 45px rgba(15, 23, 42, 0.22)',
            alignItems: 'center',
            fontWeight: 700,
          }}
        >
          {notification.message}
        </Alert>
      </Snackbar>

      <Stack className="login-footer-brand" spacing={0.7} alignItems="center">
        <Typography className="login-footer-wordmark">
          <Box component="span" sx={{ color: '#ffffff' }}>
            Loan
          </Box>
          <Box component="span" sx={{ color: '#f59a23' }}>
            Intelli
          </Box>
        </Typography>
        <Typography className="login-footer-tagline">
          AI POWERED INTEGRATED LOAN MANAGEMENT SYSTEM
        </Typography>
        <Typography className="login-footer-copy">
          &copy; 2026 LoanIntelli. All rights reserved.
        </Typography>
      </Stack>
    </Box>
  )
}

export default Login
