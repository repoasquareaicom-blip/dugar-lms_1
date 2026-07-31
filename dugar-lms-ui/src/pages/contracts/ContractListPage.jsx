import MoreHorizOutlinedIcon from '@mui/icons-material/MoreHorizOutlined'
import { Alert, Box, Button, Menu, MenuItem, Snackbar, Stack, Tooltip, Typography } from '@mui/material'
import { useMemo, useState } from 'react'
import ERPListView from '../../components/erp/ERPListView.jsx'
import { displayFallback, formatDateDDMMYYYY, formatIndianNumber } from '../../components/erp/erpTableUtils.js'
import { erpLayout, erpTypography } from '../../components/erp/erpTokens.js'
import { fetchContractsPage } from '../../services/contractsService.js'

const PAGE_SIZE_OPTIONS = [25, 50, 100, 250]
const DEFAULT_SORT_COLUMN = 'contractDate'
const DEFAULT_SORT_DIRECTION = 'desc'
const EMPTY_FILTERS = {
  branch: '',
  status: '',
  product: '',
  minimumLoanAmount: '',
  maximumLoanAmount: '',
  customerName: '',
  contractDateFrom: '',
  contractDateTo: '',
}
const DEFAULT_HIDDEN_COLUMNS = {
  vehicleMake: false,
  engineNumber: false,
  chassisNumber: false,
  manufactureYear: false,
  vehicleFinanceType: false,
  vehicleValue: false,
  vehicleSecurityOffered: false,
  vehicleOwnerSerialNo: false,
  vehicleRegistrationDate: false,
  customerCode: false,
  customerName: false,
  customerMobile: false,
  customerEmail: false,
  customerAddress: false,
  customerCity: false,
  customerState: false,
  customerPinCode: false,
  customerPanNumber: false,
  customerOccupation: false,
  guarantorCode: false,
  guarantorName: false,
  guarantorMobile: false,
  guarantorEmail: false,
  guarantorAddress: false,
  guarantorCity: false,
  guarantorPanNumber: false,
  guarantorState: false,
  guarantorPinCode: false,
  guarantorOccupation: false,
}

function DetailTooltip({ children, details, title, tone = 'blue' }) {
  const [open, setOpen] = useState(false)
  const tones = {
    blue: { accent: '#2563eb', background: 'linear-gradient(145deg, #ffffff 0%, #eef6ff 52%, #dbeafe 100%)', border: '#93c5fd', chip: '#dbeafe', title: '#1e3a8a', glow: 'rgba(37, 99, 235, 0.24)' },
    emerald: { accent: '#059669', background: 'linear-gradient(145deg, #ffffff 0%, #ecfdf5 52%, #d1fae5 100%)', border: '#6ee7b7', chip: '#d1fae5', title: '#064e3b', glow: 'rgba(5, 150, 105, 0.24)' },
    amber: { accent: '#d97706', background: 'linear-gradient(145deg, #ffffff 0%, #fff7ed 52%, #fef3c7 100%)', border: '#fbbf24', chip: '#fef3c7', title: '#7c2d12', glow: 'rgba(217, 119, 6, 0.24)' },
  }
  const colors = tones[tone] || tones.blue

  return (
    <Tooltip
      arrow
      disableFocusListener
      disableHoverListener
      disableTouchListener
      open={open}
      placement="right"
      slotProps={{
        tooltip: {
          sx: {
            maxWidth: 360,
            border: `1px solid ${colors.border}`,
            bgcolor: colors.background,
            background: colors.background,
            borderRadius: 1.25,
            boxShadow: `0 18px 38px rgba(15, 23, 42, 0.2), 0 7px 16px ${colors.glow}, inset 0 1px 0 rgba(255, 255, 255, 0.95), inset 0 -1px 0 rgba(15, 23, 42, 0.06)`,
            p: 0,
          },
        },
        arrow: {
          sx: {
            color: '#ffffff',
            '&::before': {
              border: `1px solid ${colors.border}`,
              background: colors.background,
            },
          },
        },
      }}
      title={
        <Box sx={{ minWidth: 280, overflow: 'hidden' }}>
          <Box
            sx={{
              display: 'flex',
              alignItems: 'center',
              gap: 0.75,
              borderBottom: `1px solid ${colors.border}`,
              px: 1.1,
              py: 0.85,
              background: 'linear-gradient(180deg, rgba(255,255,255,0.72), rgba(255,255,255,0.24))',
              boxShadow: 'inset 0 1px 0 rgba(255,255,255,0.9), inset 0 -1px 0 rgba(15,23,42,0.05)',
            }}
          >
            <Box sx={{ width: 9, height: 9, borderRadius: '50%', bgcolor: colors.accent, boxShadow: `0 0 0 4px ${colors.chip}, inset 0 1px 1px rgba(255,255,255,0.55)` }} />
            <Typography sx={{ color: colors.title, fontSize: '0.8rem', fontWeight: 900, textShadow: '0 1px 0 rgba(255,255,255,0.9)' }}>
              {title}
            </Typography>
          </Box>
          <Stack spacing={0.45} sx={{ px: 1.1, py: 0.9 }}>
            {details.map((detail) => (
              <Stack key={detail.label} direction="row" spacing={1.25} justifyContent="space-between" sx={{ alignItems: 'flex-start' }}>
                <Typography sx={{ color: '#64748b', fontSize: '0.72rem', fontWeight: 700, whiteSpace: 'nowrap' }}>{detail.label}</Typography>
                <Typography sx={{ color: '#0f172a', fontSize: '0.72rem', fontWeight: 800, maxWidth: 205, textAlign: detail.label === 'Address' || detail.label === 'Security' ? 'left' : 'right', wordBreak: 'break-word' }}>
                  {displayFallback(detail.value)}
                </Typography>
              </Stack>
            ))}
          </Stack>
        </Box>
      }
    >
      <Box
        component="span"
        onClick={(event) => {
          event.stopPropagation()
          setOpen(true)
        }}
        onMouseLeave={() => setOpen(false)}
        sx={{ color: erpLayout.primary, cursor: 'pointer', fontWeight: 700 }}
      >
        {children}
      </Box>
    </Tooltip>
  )
}

function codeName(code, name) {
  const resolvedCode = displayFallback(code)
  const resolvedName = displayFallback(name)
  return resolvedName === '-' ? resolvedCode : `${resolvedCode} - ${resolvedName}`
}

function transformContractsResponse(data) {
  const rows = Array.isArray(data?.content)
    ? data.content.filter((row) => Boolean(String(row?.repaymentSchedule || '').trim()))
    : []

  return {
    rows,
    totalElements: Number(data?.totalElements || rows.length),
  }
}

function parseRepaymentSchedule(value) {
  if (!value) {
    return []
  }

  return String(value)
    .split(';')
    .map((entry) => {
      const [sequenceNo, installments, amount] = entry.split('|')
      return {
        amount,
        installments,
        sequenceNo,
      }
    })
    .filter((entry) => entry.sequenceNo && entry.installments && entry.amount)
}

function TenureScheduleTooltip({ row }) {
  const [open, setOpen] = useState(false)
  const schedule = parseRepaymentSchedule(row.repaymentSchedule)

  return (
    <Tooltip
      arrow
      disableFocusListener
      disableHoverListener
      disableTouchListener
      open={open}
      placement="right"
      slotProps={{
        tooltip: {
          sx: {
            maxWidth: 380,
            border: '1px solid #c4b5fd',
            borderRadius: 1.25,
            background: 'linear-gradient(145deg, #ffffff 0%, #f5f3ff 52%, #ede9fe 100%)',
            boxShadow: '0 18px 38px rgba(15, 23, 42, 0.2), 0 7px 16px rgba(124, 58, 237, 0.22), inset 0 1px 0 rgba(255,255,255,0.95)',
            p: 0,
          },
        },
        arrow: {
          sx: {
            color: '#ffffff',
            '&::before': {
              border: '1px solid #c4b5fd',
              background: 'linear-gradient(145deg, #ffffff 0%, #f5f3ff 52%, #ede9fe 100%)',
            },
          },
        },
      }}
      title={
        <Box sx={{ minWidth: 300, overflow: 'hidden' }}>
          <Box sx={{ borderBottom: '1px solid #c4b5fd', px: 1.1, py: 0.85, background: 'linear-gradient(180deg, rgba(255,255,255,0.74), rgba(255,255,255,0.28))' }}>
            <Typography sx={{ color: '#4c1d95', fontSize: '0.8rem', fontWeight: 900, textShadow: '0 1px 0 rgba(255,255,255,0.9)' }}>
              Repayment Schedule
            </Typography>
          </Box>
          <Box sx={{ px: 1.1, py: 0.9 }}>
            {schedule.length > 0 ? (
              <Stack spacing={0.45}>
                <Stack direction="row" sx={{ color: '#64748b', fontSize: '0.7rem', fontWeight: 800 }}>
                  <Box sx={{ width: 46 }}>Slab</Box>
                  <Box sx={{ flex: 1, textAlign: 'right' }}>Installments</Box>
                  <Box sx={{ flex: 1.1, textAlign: 'right' }}>Amount</Box>
                </Stack>
                {schedule.map((entry) => (
                  <Stack key={entry.sequenceNo} direction="row" sx={{ alignItems: 'center', borderRadius: 0.75, bgcolor: 'rgba(255,255,255,0.62)', px: 0.65, py: 0.45 }}>
                    <Box sx={{ width: 46, color: '#6d28d9', fontSize: '0.72rem', fontWeight: 900 }}>{entry.sequenceNo}</Box>
                    <Box sx={{ flex: 1, color: '#0f172a', fontSize: '0.72rem', fontWeight: 800, textAlign: 'right' }}>{entry.installments}</Box>
                    <Box sx={{ flex: 1.1, color: '#0f172a', fontSize: '0.72rem', fontWeight: 900, textAlign: 'right' }}>{formatIndianNumber(entry.amount)}</Box>
                  </Stack>
                ))}
              </Stack>
            ) : (
              <Typography sx={{ color: '#0f172a', fontSize: '0.75rem', fontWeight: 800 }}>
                No repayment schedule available
              </Typography>
            )}
          </Box>
        </Box>
      }
    >
      <Box
        component="span"
        tabIndex={0}
        onClick={(event) => {
          event.stopPropagation()
          setOpen(true)
        }}
        onMouseLeave={() => setOpen(false)}
        sx={{ color: '#6d28d9', cursor: 'pointer', fontWeight: 800 }}
      >
        {displayFallback(row.tenureMonths)}
      </Box>
    </Tooltip>
  )
}

function numericValue(value) {
  if (value === null || value === undefined || value === '') {
    return null
  }

  const number = Number(value)
  return Number.isFinite(number) ? number : null
}

function rowValue(row, ...keys) {
  for (const key of keys) {
    if (row[key] !== null && row[key] !== undefined && row[key] !== '') {
      return row[key]
    }
  }

  return null
}

function TotalContractValueTooltip({ row }) {
  const [open, setOpen] = useState(false)
  const loanAmount = rowValue(row, 'loanAmount', 'loan_amount')
  const insuranceDeposit = rowValue(row, 'insuranceDeposit', 'insurance_deposit')
  const bpfcAmount = rowValue(row, 'bpfcAmount', 'bpfc_amount')
  const promptPaymentRebate = rowValue(row, 'promptPaymentRebate', 'prompt_payment_rebate')
  const totalContractValue = rowValue(row, 'totalContractValue', 'total_contract_value')
  const storedFinanceCharges = rowValue(row, 'financeCharges', 'finance_charges')

  const derivedFinanceCharges = numericValue(storedFinanceCharges) === null
    && numericValue(totalContractValue) !== null
    && numericValue(loanAmount) !== null
    ? numericValue(totalContractValue)
      - numericValue(loanAmount)
      - (numericValue(insuranceDeposit) || 0)
      - (numericValue(bpfcAmount) || 0)
      + (numericValue(promptPaymentRebate) || 0)
    : storedFinanceCharges

  const details = [
    { label: 'Loan Amount', value: formatIndianNumber(loanAmount) },
    { label: 'Finance Charges', value: formatIndianNumber(derivedFinanceCharges) },
    { label: 'Insurance Deposit', value: formatIndianNumber(insuranceDeposit) },
    { label: 'BPFC Amount', value: formatIndianNumber(bpfcAmount) },
    { label: 'Less Rebate', value: formatIndianNumber(promptPaymentRebate) },
    { label: 'Total Value', value: formatIndianNumber(totalContractValue), highlight: true },
  ]

  return (
    <Tooltip
      arrow
      disableFocusListener
      disableHoverListener
      disableTouchListener
      open={open}
      placement="right"
      slotProps={{
        tooltip: {
          sx: {
            maxWidth: 390,
            border: '1px solid #7dd3fc',
            borderRadius: 1.25,
            background: 'linear-gradient(145deg, #f8fbff 0%, #ecfeff 45%, #e0f2fe 100%)',
            boxShadow: '0 20px 42px rgba(15,23,42,0.22), 0 8px 18px rgba(14,165,233,0.24), inset 0 1px 0 rgba(255,255,255,0.96), inset 0 -1px 0 rgba(15,23,42,0.07)',
            p: 0,
          },
        },
        arrow: {
          sx: {
            color: '#f8fbff',
            '&::before': {
              border: '1px solid #7dd3fc',
              background: 'linear-gradient(145deg, #f8fbff 0%, #ecfeff 45%, #e0f2fe 100%)',
            },
          },
        },
      }}
      title={
        <Box sx={{ minWidth: 315, overflow: 'hidden' }}>
          <Box
            sx={{
              borderBottom: '1px solid #bae6fd',
              px: 1.15,
              py: 0.9,
              background: 'linear-gradient(180deg, rgba(255,255,255,0.78), rgba(255,255,255,0.3))',
              boxShadow: 'inset 0 1px 0 rgba(255,255,255,0.95), inset 0 -1px 0 rgba(15,23,42,0.05)',
            }}
          >
            <Typography sx={{ color: '#075985', fontSize: '0.8rem', fontWeight: 900, textShadow: '0 1px 0 rgba(255,255,255,0.9)' }}>
              Total Contract Value
            </Typography>
            <Typography sx={{ color: '#0e7490', fontSize: '0.67rem', fontWeight: 800, mt: 0.25 }}>
              Loan + Finance + Insurance + BPFC - Rebate
            </Typography>
          </Box>
          <Stack spacing={0.45} sx={{ px: 1.15, py: 0.9 }}>
            {details.map((detail) => (
              <Stack
                key={detail.label}
                direction="row"
                justifyContent="space-between"
                sx={{
                  alignItems: 'center',
                  borderRadius: 0.8,
                  bgcolor: detail.highlight ? 'rgba(14,165,233,0.14)' : 'rgba(255,255,255,0.58)',
                  border: detail.highlight ? '1px solid rgba(14,165,233,0.22)' : '1px solid transparent',
                  px: 0.75,
                  py: 0.42,
                }}
              >
                <Typography sx={{ color: detail.highlight ? '#075985' : '#64748b', fontSize: '0.72rem', fontWeight: 800, minWidth: 126 }}>{detail.label}</Typography>
                <Typography sx={{ color: '#0f172a', fontSize: '0.72rem', fontWeight: detail.highlight ? 950 : 850, minWidth: 128, pl: 2, textAlign: 'right' }}>
                  {displayFallback(detail.value)}
                </Typography>
              </Stack>
            ))}
          </Stack>
        </Box>
      }
    >
      <Box
        component="span"
        tabIndex={0}
        onClick={(event) => {
          event.stopPropagation()
          setOpen(true)
        }}
        onMouseLeave={() => setOpen(false)}
        sx={{ color: '#0369a1', cursor: 'pointer', fontWeight: 850 }}
      >
        {formatIndianNumber(totalContractValue)}
      </Box>
    </Tooltip>
  )
}

function ContractListPage() {
  const [contextMenu, setContextMenu] = useState(null)
  const [toastMessage, setToastMessage] = useState('')

  const filterFields = useMemo(
    () => ({ rows }) => [
      { field: 'branch', label: 'Branch', type: 'select', options: [...new Set(rows.map((row) => row.branch).filter(Boolean))].sort() },
      { field: 'status', label: 'Status', type: 'select', options: [...new Set(rows.map((row) => row.status).filter(Boolean))].sort() },
      { field: 'product', label: 'Product', type: 'select', options: [...new Set(rows.map((row) => row.product).filter(Boolean))].sort() },
      { field: 'minimumLoanAmount', label: 'Loan Amount From', type: 'number' },
      { field: 'maximumLoanAmount', label: 'Loan Amount To', type: 'number' },
      { field: 'customerName', label: 'Customer Name', type: 'text' },
      { field: 'contractDateFrom', label: 'Contract Date From', type: 'date' },
      { field: 'contractDateTo', label: 'Contract Date To', type: 'date' },
    ],
    [],
  )

  const columns = useMemo(
    () => [
      { field: 'serialNumber', headerName: 'S.No', minWidth: 70, align: 'center', headerAlign: 'center' },
      { field: 'contractId', headerName: 'Contract ID', minWidth: 110, align: 'right', headerAlign: 'right', sortable: true },
      { field: 'legacyContractNumber', headerName: 'Legacy Contract No', minWidth: 160, sortable: true },
      { field: 'product', headerName: 'Product', minWidth: 120, sortable: true },
      { field: 'branch', headerName: 'Branch', minWidth: 110, sortable: true },
      { field: 'contractDate', headerName: 'Contract Date', minWidth: 120, align: 'center', headerAlign: 'center', sortable: true, formatter: formatDateDDMMYYYY },
      { field: 'loanAmount', headerName: 'Loan Amount', minWidth: 130, align: 'right', headerAlign: 'right', sortable: true, formatter: formatIndianNumber },
      { field: 'totalContractValue', headerName: 'Total Contract Value', minWidth: 170, align: 'right', headerAlign: 'right', sortable: true, renderCell: (row) => <TotalContractValueTooltip row={row} /> },
      { field: 'tenureMonths', headerName: 'Tenure', minWidth: 90, align: 'right', headerAlign: 'right', sortable: true, renderCell: (row) => <TenureScheduleTooltip row={row} /> },
      { field: 'firstEmiDate', headerName: 'First EMI Date', minWidth: 130, align: 'center', headerAlign: 'center', sortable: true, formatter: formatDateDDMMYYYY },
      { field: 'irrRate', headerName: 'IRR', minWidth: 90, align: 'right', headerAlign: 'right', sortable: true },
      {
        field: 'borrowerSummary',
        headerName: 'Borrower',
        minWidth: 240,
        renderCell: (row) => (
          <DetailTooltip
            title="Borrower Details"
            tone="blue"
            details={[
              { label: 'Code', value: row.customerCode },
              { label: 'Name', value: row.customerName },
              { label: 'Mobile', value: row.customerMobile },
              { label: 'Email', value: row.customerEmail },
              { label: 'Address', value: row.customerAddress },
              { label: 'City', value: row.customerCity },
              { label: 'State', value: row.customerState },
              { label: 'PIN', value: row.customerPinCode },
              { label: 'PAN', value: row.customerPanNumber },
              { label: 'Occupation', value: row.customerOccupation },
            ]}
          >
            {codeName(row.customerCode, row.customerName)}
          </DetailTooltip>
        ),
      },
      {
        field: 'guarantorSummary',
        headerName: 'Guarantor',
        minWidth: 240,
        renderCell: (row) => (
          <DetailTooltip
            title="Guarantor Details"
            tone="emerald"
            details={[
              { label: 'Code', value: row.guarantorCode },
              { label: 'Name', value: row.guarantorName },
              { label: 'Mobile', value: row.guarantorMobile },
              { label: 'Email', value: row.guarantorEmail },
              { label: 'Address', value: row.guarantorAddress },
              { label: 'City', value: row.guarantorCity },
              { label: 'State', value: row.guarantorState },
              { label: 'PIN', value: row.guarantorPinCode },
              { label: 'PAN', value: row.guarantorPanNumber },
              { label: 'Occupation', value: row.guarantorOccupation },
            ]}
          >
            {codeName(row.guarantorCode, row.guarantorName)}
          </DetailTooltip>
        ),
      },
      {
        field: 'vehicleRegistrationNumber',
        headerName: 'Vehicle Code',
        minWidth: 150,
        sortable: true,
        renderCell: (row) => (
          <DetailTooltip
            title="Vehicle Details"
            tone="amber"
            details={[
              { label: 'Code', value: row.vehicleRegistrationNumber },
              { label: 'Type', value: row.vehicleTypeCode },
              { label: 'Make', value: row.vehicleMake },
              { label: 'Engine', value: row.engineNumber },
              { label: 'Chassis', value: row.chassisNumber },
              { label: 'Mfg Year', value: row.manufactureYear },
              { label: 'Finance Type', value: row.vehicleFinanceType },
              { label: 'Value', value: formatIndianNumber(row.vehicleValue) },
              { label: 'Owner Serial', value: row.vehicleOwnerSerialNo },
              { label: 'Reg Date', value: formatDateDDMMYYYY(row.vehicleRegistrationDate) },
              { label: 'Security', value: row.vehicleSecurityOffered },
            ]}
          >
            {displayFallback(row.vehicleRegistrationNumber)}
          </DetailTooltip>
        ),
      },
      { field: 'vehicleTypeCode', headerName: 'Vehicle Type', minWidth: 120, sortable: true },
      { field: 'vehicleMake', headerName: 'Vehicle Make', minWidth: 135, sortable: true },
      { field: 'engineNumber', headerName: 'Engine No', minWidth: 150, sortable: true },
      { field: 'chassisNumber', headerName: 'Chassis No', minWidth: 150, sortable: true },
      { field: 'manufactureYear', headerName: 'Mfg Year', minWidth: 95, align: 'center', headerAlign: 'center', sortable: true },
      { field: 'vehicleFinanceType', headerName: 'Vehicle Finance Type', minWidth: 165, sortable: true },
      { field: 'vehicleValue', headerName: 'Vehicle Value', minWidth: 130, align: 'right', headerAlign: 'right', sortable: true, formatter: formatIndianNumber },
      { field: 'vehicleSecurityOffered', headerName: 'Vehicle Security', minWidth: 180 },
      { field: 'vehicleOwnerSerialNo', headerName: 'Owner Serial No', minWidth: 145, sortable: true },
      { field: 'vehicleRegistrationDate', headerName: 'Vehicle Reg Date', minWidth: 140, align: 'center', headerAlign: 'center', sortable: true, formatter: formatDateDDMMYYYY },
      { field: 'customerCode', headerName: 'Customer Code', minWidth: 130, sortable: true },
      { field: 'customerName', headerName: 'Customer Name', minWidth: 220, sortable: true },
      { field: 'customerMobile', headerName: 'Customer Mobile', minWidth: 140, sortable: true },
      { field: 'customerEmail', headerName: 'Customer Email', minWidth: 190, sortable: true },
      { field: 'customerAddress', headerName: 'Customer Address', minWidth: 260, sortable: true },
      { field: 'customerCity', headerName: 'Customer City', minWidth: 130, sortable: true },
      { field: 'customerState', headerName: 'Customer State', minWidth: 140, sortable: true },
      { field: 'customerPinCode', headerName: 'Customer PIN', minWidth: 120, sortable: true },
      { field: 'customerPanNumber', headerName: 'Customer PAN', minWidth: 130, sortable: true },
      { field: 'customerOccupation', headerName: 'Customer Occupation', minWidth: 165, sortable: true },
      { field: 'guarantorCode', headerName: 'Guarantor Code', minWidth: 135, sortable: true },
      { field: 'guarantorName', headerName: 'Guarantor Name', minWidth: 220, sortable: true },
      { field: 'guarantorMobile', headerName: 'Guarantor Mobile', minWidth: 150, sortable: true },
      { field: 'guarantorEmail', headerName: 'Guarantor Email', minWidth: 190, sortable: true },
      { field: 'guarantorAddress', headerName: 'Guarantor Address', minWidth: 260, sortable: true },
      { field: 'guarantorCity', headerName: 'Guarantor City', minWidth: 140, sortable: true },
      { field: 'guarantorState', headerName: 'Guarantor State', minWidth: 145, sortable: true },
      { field: 'guarantorPinCode', headerName: 'Guarantor PIN', minWidth: 125, sortable: true },
      { field: 'guarantorPanNumber', headerName: 'Guarantor PAN', minWidth: 140, sortable: true },
      { field: 'guarantorOccupation', headerName: 'Guarantor Occupation', minWidth: 175, sortable: true },
      {
        field: 'actions',
        headerName: 'Actions',
        minWidth: 90,
        align: 'center',
        headerAlign: 'center',
        renderCell: () => (
          <Button size="small" variant="text" disabled sx={{ minWidth: 0, px: 0.5, color: '#000000' }}>
            <MoreHorizOutlinedIcon sx={{ fontSize: 16 }} />
          </Button>
        ),
      },
    ],
    [],
  )

  return (
    <>
      <ERPListView
        columns={columns}
        defaultFilters={EMPTY_FILTERS}
        defaultSortColumn={DEFAULT_SORT_COLUMN}
        defaultSortDirection={DEFAULT_SORT_DIRECTION}
        defaultVisibilityModel={DEFAULT_HIDDEN_COLUMNS}
        fetchPage={fetchContractsPage}
        fetchErrorMessage="Unable to load contracts. Please refresh and try again."
        filterFields={filterFields}
        getRowId={(row) => row.contractId}
        lockedColumnFields={['serialNumber', 'actions']}
        pageSizeOptions={PAGE_SIZE_OPTIONS}
        transformResponse={transformContractsResponse}
        onRowDoubleClick={(row) => {
          setToastMessage(`Contract ${row.contractNumber} details are coming soon.`)
        }}
        onRowContextMenu={(event, row) => {
          event.preventDefault()
          setContextMenu({ mouseX: event.clientX + 2, mouseY: event.clientY - 6, row })
        }}
        renderContextMenu={() => (
          <Menu
            open={Boolean(contextMenu)}
            onClose={() => setContextMenu(null)}
            anchorReference="anchorPosition"
            anchorPosition={contextMenu ? { top: contextMenu.mouseY, left: contextMenu.mouseX } : undefined}
          >
            <MenuItem disabled sx={{ fontSize: erpTypography.fontSize }}>View Contract</MenuItem>
            <MenuItem disabled sx={{ fontSize: erpTypography.fontSize }}>Edit Contract</MenuItem>
            <MenuItem disabled sx={{ fontSize: erpTypography.fontSize }}>Context Actions Coming Soon</MenuItem>
          </Menu>
        )}
      />
      <Snackbar open={Boolean(toastMessage)} autoHideDuration={2200} onClose={() => setToastMessage('')}>
        <Alert severity="info" onClose={() => setToastMessage('')} sx={{ width: '100%' }}>
          {toastMessage}
        </Alert>
      </Snackbar>
    </>
  )
}

export default ContractListPage
