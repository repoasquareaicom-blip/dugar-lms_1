import { Breadcrumbs, Link, Typography } from '@mui/material'
import { Link as RouterLink } from 'react-router-dom'
import { useMenu } from '../../context/menuContextCore.js'

function BreadcrumbBar({ breadcrumbs }) {
  const menuContext = useMenu()
  const resolvedBreadcrumbs = breadcrumbs?.length ? breadcrumbs : menuContext.breadcrumbs

  if (!resolvedBreadcrumbs?.length) {
    return null
  }

  return (
    <Breadcrumbs sx={{ color: '#64748b', fontSize: '0.82rem' }}>
      {resolvedBreadcrumbs.map((breadcrumb, index) => {
        const label = typeof breadcrumb === 'string' ? breadcrumb : breadcrumb.label
        const href = typeof breadcrumb === 'string' ? '' : breadcrumb.href
        const isLast = index === resolvedBreadcrumbs.length - 1

        if (href && !isLast) {
          return (
            <Link
              component={RouterLink}
              key={`${label}-${index}`}
              to={href}
              underline="hover"
              sx={{ color: '#64748b', fontWeight: 600 }}
            >
              {label}
            </Link>
          )
        }

        return (
          <Typography
            key={`${label}-${index}`}
            sx={{ color: isLast ? '#334155' : '#64748b', fontSize: '0.82rem', fontWeight: 700 }}
          >
            {label}
          </Typography>
        )
      })}
    </Breadcrumbs>
  )
}

export default BreadcrumbBar
