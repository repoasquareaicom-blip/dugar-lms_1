import FilterAltOutlinedIcon from '@mui/icons-material/FilterAltOutlined'
import { Box, IconButton, Stack, Tooltip } from '@mui/material'
import { useEffect, useMemo, useState } from 'react'
import ERPColumnChooser from './ERPColumnChooser.jsx'
import ERPDataGrid from './ERPDataGrid.jsx'
import ERPFilterPanel from './ERPFilterPanel.jsx'
import ERPPagination from './ERPPagination.jsx'
import ERPSearchBox from './ERPSearchBox.jsx'
import ERPToolbar from './ERPToolbar.jsx'
import { erpLayout } from './erpTokens.js'

const DEFAULT_PAGE_SIZE = 25
const DEFAULT_PAGE_SIZE_OPTIONS = [25, 50, 100, 250]
const transformPageResponse = (data) => ({
  rows: Array.isArray(data?.content) ? data.content : [],
  totalElements: Number(data?.totalElements || 0),
})

function ERPListView({
  columns = [],
  defaultFilters = {},
  defaultVisibilityModel = {},
  defaultPageSize = DEFAULT_PAGE_SIZE,
  defaultSortColumn = '',
  defaultSortDirection = 'asc',
  fetchPage,
  fetchErrorMessage = 'Unable to load records. Please refresh and try again.',
  filterFields = [],
  getRowId = (row) => row?.id,
  lockedColumnFields = [],
  pageSizeOptions = DEFAULT_PAGE_SIZE_OPTIONS,
  renderContextMenu,
  searchPlaceholder = 'Keyword Search',
  transformResponse = transformPageResponse,
  onRowClick,
  onRowContextMenu,
  onRowDoubleClick,
}) {
  const [rows, setRows] = useState([])
  const [page, setPage] = useState(0)
  const [pageSize, setPageSize] = useState(defaultPageSize)
  const [totalElements, setTotalElements] = useState(0)
  const [keywordInput, setKeywordInput] = useState('')
  const [keyword, setKeyword] = useState('')
  const [sortColumn, setSortColumn] = useState(defaultSortColumn)
  const [sortDirection, setSortDirection] = useState(defaultSortDirection)
  const [filters, setFilters] = useState(defaultFilters)
  const [appliedFilters, setAppliedFilters] = useState(defaultFilters)
  const [filterOpen, setFilterOpen] = useState(false)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')
  const [selectedRowId, setSelectedRowId] = useState(null)
  const [visibilityModel, setVisibilityModel] = useState(defaultVisibilityModel)

  useEffect(() => {
    const timeoutId = window.setTimeout(() => {
      setKeyword(keywordInput.trim())
      setPage(0)
    }, 350)

    return () => window.clearTimeout(timeoutId)
  }, [keywordInput])

  useEffect(() => {
    let active = true

    async function loadRows() {
      if (!fetchPage) {
        return
      }

      setLoading(true)
      setError('')
      setRows([])
      setTotalElements(0)

      try {
        const data = await fetchPage({
          filters: appliedFilters,
          keyword,
          page,
          pageSize,
          sortColumn,
          sortDirection,
        })
        const result = transformResponse(data)

        if (!active) {
          return
        }

        setRows(Array.isArray(result.rows) ? result.rows : [])
        setTotalElements(Number(result.totalElements || 0))
      } catch {
        if (!active) {
          return
        }

        setRows([])
        setTotalElements(0)
        setError(fetchErrorMessage)
      } finally {
        if (active) {
          setLoading(false)
        }
      }
    }

    loadRows()

    return () => {
      active = false
    }
  }, [appliedFilters, fetchErrorMessage, fetchPage, keyword, page, pageSize, sortColumn, sortDirection, transformResponse])

  const resolvedFilterFields = useMemo(
    () => (typeof filterFields === 'function' ? filterFields({ rows }) : filterFields),
    [filterFields, rows],
  )
  const displayedRows = useMemo(
    () => rows.map((row, index) => ({ ...row, serialNumber: page * pageSize + index + 1 })),
    [page, pageSize, rows],
  )

  const resetFilters = () => {
    setFilters(defaultFilters)
    setAppliedFilters(defaultFilters)
    setKeywordInput('')
    setKeyword('')
    setPage(0)
  }

  const toolbarActions = (
    <Stack direction="row" spacing={0.75} alignItems="center" sx={{ py: 0.25 }}>
      {resolvedFilterFields.length > 0 && (
        <Tooltip title="Advanced Filter">
          <IconButton
            aria-label="Advanced Filter"
            onClick={() => setFilterOpen((value) => !value)}
            size="small"
            sx={{
              width: 30,
              height: 30,
              border: '1px solid',
              borderColor: filterOpen ? erpLayout.primary : erpLayout.borderColor,
              borderRadius: 0.75,
              bgcolor: filterOpen ? erpLayout.primary : erpLayout.white,
              color: filterOpen ? erpLayout.white : erpLayout.black,
              '&:hover': {
                bgcolor: filterOpen ? erpLayout.primary : '#eef4fa',
              },
            }}
          >
            <FilterAltOutlinedIcon sx={{ fontSize: 17 }} />
          </IconButton>
        </Tooltip>
      )}
      <ERPColumnChooser columns={columns} lockedFields={lockedColumnFields} visibilityModel={visibilityModel} onChange={setVisibilityModel} />
    </Stack>
  )

  const handleRowClick = (row) => {
    setSelectedRowId(getRowId(row))
    onRowClick?.(row)
  }

  return (
    <Box sx={{ display: 'flex', flexDirection: 'column', height: 'calc(100vh - 150px)', minHeight: 0, width: '100%' }}>
      <ERPToolbar actions={toolbarActions} searchSlot={<ERPSearchBox value={keywordInput} onChange={setKeywordInput} placeholder={searchPlaceholder} />} />
      <ERPFilterPanel
        open={filterOpen}
        fields={resolvedFilterFields}
        values={filters}
        onChange={setFilters}
        onApply={(nextFilters) => {
          setAppliedFilters(nextFilters)
          setPage(0)
        }}
        onReset={resetFilters}
      />
      <ERPDataGrid
        columns={columns}
        rows={displayedRows}
        loading={loading}
        error={error}
        getRowId={getRowId}
        selectedRowId={selectedRowId}
        sortColumn={sortColumn}
        sortDirection={sortDirection}
        visibilityModel={visibilityModel}
        onSortChange={(field, direction) => {
          setSortColumn(field)
          setSortDirection(direction)
          setPage(0)
        }}
        onRowClick={handleRowClick}
        onRowDoubleClick={onRowDoubleClick}
        onRowContextMenu={(event, row) => {
          setSelectedRowId(getRowId(row))
          onRowContextMenu?.(event, row)
        }}
      />
      <ERPPagination
        page={page}
        pageSize={pageSize}
        totalElements={totalElements}
        pageSizeOptions={pageSizeOptions}
        onPageChange={setPage}
        onPageSizeChange={(nextPageSize) => {
          setPageSize(nextPageSize)
          setPage(0)
        }}
      />
      {renderContextMenu?.({ rows: displayedRows, selectedRowId })}
    </Box>
  )
}

export default ERPListView
