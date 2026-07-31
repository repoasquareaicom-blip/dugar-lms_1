import { Box } from '@mui/material'
import PageHeader from './shell/PageHeader.jsx'

function PageWorkspace({ actions, breadcrumbs, children, stepper, subtitle, tabs, title }) {
  return (
    <Box sx={{ width: '100%' }}>
      <PageHeader actions={actions} breadcrumbs={breadcrumbs} subtitle={subtitle} title={title} />
      {tabs && <Box sx={{ mb: 2 }}>{tabs}</Box>}
      {stepper && <Box sx={{ mb: 2 }}>{stepper}</Box>}
      {children}
    </Box>
  )
}

export default PageWorkspace
