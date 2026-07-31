import EditOutlinedIcon from '@mui/icons-material/EditOutlined'
import VisibilityOutlinedIcon from '@mui/icons-material/VisibilityOutlined'
import { Box } from '@mui/material'
import { useCallback, useEffect, useMemo, useState } from 'react'
import apiClient from '../../api/apiClient.js'
import ERPDataTable from '../../components/erp/ERPDataTable.jsx'
import ERPFilterPanel from '../../components/erp/ERPFilterPanel.jsx'
import { formatDateDDMMYYYY, formatIndianNumber } from '../../components/erp/erpTableUtils.js'
import { navHeights } from '../../components/layout/navigationStyles.js'

const DEFAULT_PAGE_SIZE = 25
const PAGE_SIZE_OPTIONS = [10, 25, 50, 100]
const SEARCH_DEBOUNCE_MS = 400
const DEFAULT_SORT_FIELD = 'contractId'
const DEFAULT_SORT_DIRECTION = 'desc'
const EMPTY_FILTERS = {
  contractType: '',
  contractDateFrom: '',
  contractDateTo: '',
  minLoanAmount: '',
  maxLoanAmount: '',
}

function getAppliedFilterParams(filters) {
  return Object.entries(filters).reduce((params, [key, value]) => {
    if (value !== null && value !== undefined && value !== '') {
      params[key] = value
    }

    return params
  }, {})
}

function ActiveContracts() {
  const [contracts, setContracts] = useState([])
  const [page, setPage] = useState(0)
  const [rowsPerPage, setRowsPerPage] = useState(DEFAULT_PAGE_SIZE)
  const [totalElements, setTotalElements] = useState(0)
  const [search, setSearch] = useState('')
  const [appliedSearch, setAppliedSearch] = useState('')
  const [filterValues, setFilterValues] = useState(EMPTY_FILTERS)
  const [appliedFilters, setAppliedFilters] = useState(EMPTY_FILTERS)
  const [sortField, setSortField] = useState(DEFAULT_SORT_FIELD)
  const [sortDirection, setSortDirection] = useState(DEFAULT_SORT_DIRECTION)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')
  const [refreshKey, setRefreshKey] = useState(0)

  useEffect(() => {
    const timeoutId = window.setTimeout(() => {
      setAppliedSearch(search.trim())
      setPage(0)
    }, SEARCH_DEBOUNCE_MS)

    return () => window.clearTimeout(timeoutId)
  }, [search])

  const fetchContracts = useCallback(async () => {
    setLoading(true)
    setError('')

    try {
      const response = await apiClient.get('/contracts', {
        params: {
          page,
          ...getAppliedFilterParams(appliedFilters),
          search: appliedSearch || undefined,
          size: rowsPerPage,
          sortBy: sortField,
          sortDirection,
        },
      })

      setContracts(Array.isArray(response.data?.content) ? response.data.content : [])
      setTotalElements(Number(response.data?.totalElements || 0))
    } catch {
      setContracts([])
      setTotalElements(0)
      setError('We could not load active contracts. Please refresh and try again.')
    } finally {
      setLoading(false)
    }
  }, [appliedFilters, appliedSearch, page, rowsPerPage, sortDirection, sortField])

  useEffect(() => {
    fetchContracts()
  }, [fetchContracts, refreshKey])

  const columns = useMemo(
    () => [
      {
        field: 'contractNumber',
        headerName: 'Contract No',
        minWidth: 125,
        medium: true,
        sortable: true,
      },
      {
        field: 'contractType',
        headerName: 'Type',
        align: 'center',
        headerAlign: 'center',
        minWidth: 72,
        sortable: true,
      },
      {
        field: 'borrowerCode',
        headerName: 'Borrower',
        minWidth: 105,
        sortable: true,
      },
      {
        field: 'registrationNumber',
        headerName: 'Registration No',
        minWidth: 132,
        sortable: true,
      },
      {
        field: 'vehicleMake',
        headerName: 'Vehicle',
        align: 'center',
        headerAlign: 'center',
        minWidth: 112,
        sortable: true,
      },
      {
        field: 'equipmentModel',
        headerName: 'Model',
        align: 'center',
        headerAlign: 'center',
        minWidth: 128,
        sortable: true,
      },
      {
        field: 'loanAmount',
        headerName: 'Loan Amount',
        align: 'right',
        headerAlign: 'right',
        formatter: formatIndianNumber,
        minWidth: 116,
        sortable: true,
      },
      {
        field: 'contractDate',
        headerName: 'Contract Date',
        align: 'center',
        headerAlign: 'center',
        formatter: formatDateDDMMYYYY,
        minWidth: 116,
        sortable: true,
      },
      {
        field: 'firstEmiDate',
        headerName: 'First EMI Date',
        align: 'center',
        headerAlign: 'center',
        formatter: formatDateDDMMYYYY,
        minWidth: 116,
        sortable: true,
      },
      {
        field: '__actions',
        headerName: 'Actions',
        align: 'center',
        headerAlign: 'center',
        width: 68,
      },
    ],
    [],
  )

  const contractTypeOptions = useMemo(
    () =>
      [...new Set([...contracts.map((contract) => contract.contractType), filterValues.contractType].filter(Boolean))]
        .sort()
        .map((contractType) => ({ label: contractType, value: contractType })),
    [contracts, filterValues.contractType],
  )

  const filters = useMemo(
    () => [
      {
        field: 'contractType',
        label: 'Contract Type',
        type: 'select',
        options: contractTypeOptions,
        minWidth: 170,
      },
      {
        field: 'contractDateFrom',
        label: 'Contract Date From',
        type: 'date',
        minWidth: 170,
      },
      {
        field: 'contractDateTo',
        label: 'Contract Date To',
        type: 'date',
        minWidth: 170,
      },
      {
        field: 'minLoanAmount',
        label: 'Minimum Loan Amount',
        type: 'number',
        minWidth: 180,
      },
      {
        field: 'maxLoanAmount',
        label: 'Maximum Loan Amount',
        type: 'number',
        minWidth: 180,
      },
    ],
    [contractTypeOptions],
  )

  const rowActions = useMemo(
    () => [
      {
        key: 'view',
        label: 'View',
        icon: <VisibilityOutlinedIcon sx={{ fontSize: 17 }} />,
        disabled: true,
      },
      {
        key: 'edit',
        label: 'Edit',
        icon: <EditOutlinedIcon sx={{ fontSize: 17 }} />,
        disabled: true,
      },
    ],
    [],
  )

  const handleRefresh = () => {
    setRefreshKey((currentKey) => currentKey + 1)
  }

  const handleSearchChange = (value) => {
    setSearch(value)
  }

  const handlePageSizeChange = (nextPageSize) => {
    setRowsPerPage(nextPageSize)
    setPage(0)
  }

  const handleSortChange = (nextSortField, nextSortDirection) => {
    setSortField(nextSortField)
    setSortDirection(nextSortDirection)
    setPage(0)
  }

  const handleApplyFilters = (nextFilters) => {
    setAppliedFilters(nextFilters)
    setPage(0)
  }

  const handleClearFilters = (clearedFilters) => {
    setFilterValues(clearedFilters)
    setAppliedFilters(clearedFilters)
    setPage(0)
  }

  return (
    <Box
      sx={{
        height: {
          xs: `calc(100vh - ${navHeights.top + navHeights.secondary + navHeights.footer + 28}px)`,
          md: `calc(100vh - ${navHeights.top + navHeights.secondary + navHeights.footer + 24}px)`,
        },
        minHeight: 420,
        width: '100%',
      }}
    >
      <ERPFilterPanel
        filters={filters}
        onApply={handleApplyFilters}
        onChange={setFilterValues}
        onClear={handleClearFilters}
        values={filterValues}
      >
        {({ control, panel }) => (
          <ERPDataTable
            columns={columns}
            dense
            emptyMessage={appliedSearch ? 'No active contracts match your search.' : 'No active contracts found.'}
            enableSorting
            error={error}
            filterControl={control}
            filterPanel={panel}
            getRowId={(contract) => contract.contractId || contract.contractNumber}
            loading={loading}
            onPageChange={setPage}
            onPageSizeChange={handlePageSizeChange}
            onRefresh={handleRefresh}
            onSearchChange={handleSearchChange}
            onSortChange={handleSortChange}
            page={page}
            pageSize={rowsPerPage}
            pageSizeOptions={PAGE_SIZE_OPTIONS}
            rowActions={rowActions}
            rows={contracts}
            searchPlaceholder="Search contract, borrower, registration"
            searchValue={search}
            sortDirection={sortDirection}
            sortField={sortField}
            totalElements={totalElements}
          />
        )}
      </ERPFilterPanel>
    </Box>
  )
}

export default ActiveContracts
