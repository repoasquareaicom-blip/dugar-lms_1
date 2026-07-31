import {
  Box,
  Button,
  IconButton,
  InputAdornment,
  LinearProgress,
  MenuItem,
  Paper,
  Select,
  Stack,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  TextField,
  Tooltip,
  Typography,
} from '@mui/material'
import ArrowDownwardIcon from '@mui/icons-material/ArrowDownward'
import ArrowUpwardIcon from '@mui/icons-material/ArrowUpward'
import FirstPageIcon from '@mui/icons-material/FirstPage'
import KeyboardArrowLeftIcon from '@mui/icons-material/KeyboardArrowLeft'
import KeyboardArrowRightIcon from '@mui/icons-material/KeyboardArrowRight'
import LastPageIcon from '@mui/icons-material/LastPage'
import RefreshIcon from '@mui/icons-material/Refresh'
import SearchIcon from '@mui/icons-material/Search'
import { useMemo } from 'react'
import { displayFallback } from './erpTableUtils.js'

const DEFAULT_PAGE_SIZE_OPTIONS = [10, 25, 50, 100]

function getNextSortDirection(currentField, currentDirection, field) {
  if (currentField !== field) {
    return 'asc'
  }

  return currentDirection === 'asc' ? 'desc' : 'asc'
}

function getActionState(action, row) {
  return {
    disabled: typeof action.disabled === 'function' ? action.disabled(row) : Boolean(action.disabled),
    hidden: typeof action.hidden === 'function' ? action.hidden(row) : Boolean(action.hidden),
  }
}

function ERPDataTable({
  actions,
  columns = [],
  dense = true,
  emptyMessage = 'No records found',
  enableSorting = false,
  error = '',
  filterControl,
  filterPanel,
  getRowId = (row) => row?.id,
  height,
  loading = false,
  maxHeight,
  onPageChange,
  onPageSizeChange,
  onRefresh,
  onRowClick,
  onSearchChange,
  onSortChange,
  page = 0,
  pageSize = 25,
  pageSizeOptions = DEFAULT_PAGE_SIZE_OPTIONS,
  rowActions = [],
  rowClassName,
  rows = [],
  searchPlaceholder = 'Search',
  searchValue = '',
  selectedRowId,
  showRefresh = true,
  showSearch = true,
  sortDirection = 'asc',
  sortField = '',
  stickyFooter = true,
  stickyHeader = true,
  title,
  toolbarActions,
  totalElements = 0,
  totalPages,
}) {
  const visibleColumns = useMemo(() => columns.filter((column) => !column.hidden), [columns])
  const resolvedTotalPages = Math.max(1, Number(totalPages || Math.ceil(totalElements / pageSize) || 1))
  const pageStart = totalElements === 0 ? 0 : page * pageSize + 1
  const pageEnd = Math.min(totalElements, (page + 1) * pageSize)
  const rowHeight = dense ? 32 : 38
  const fontSize = dense ? '0.75rem' : '0.82rem'

  const tableCellSx = {
    borderRight: '1px solid',
    borderBottom: '1px solid #9badc0',
    borderColor: '#6f849c',
    color: '#0f172a',
    fontSize,
    lineHeight: 1.25,
    px: dense ? 0.9 : 1.2,
    py: dense ? 0.45 : 0.7,
    whiteSpace: 'nowrap',
    '&:last-of-type': {
      borderRight: 0,
    },
  }

  const handleSort = (column) => {
    if (!enableSorting || !onSortChange || !column.sortable || !column.field) {
      return
    }

    onSortChange(column.field, getNextSortDirection(sortField, sortDirection, column.field))
  }

  const goToPage = (nextPage) => {
    onPageChange?.(Math.min(Math.max(nextPage, 0), resolvedTotalPages - 1))
  }

  return (
    <Box sx={{ height: height || maxHeight || '100%', minHeight: 0, width: '100%' }}>
      <Paper
        elevation={0}
        sx={{
          border: '1px solid',
          borderColor: '#6f849c',
          borderRadius: 1,
          bgcolor: '#ffffff',
          display: 'flex',
          flexDirection: 'column',
          height: '100%',
          overflow: 'hidden',
        }}
      >
        {(title || showSearch || showRefresh || toolbarActions || filterControl) && (
          <Stack
            direction={{ xs: 'column', md: 'row' }}
            spacing={0.8}
            alignItems={{ xs: 'stretch', md: 'center' }}
            justifyContent="space-between"
            sx={{
              flex: '0 0 auto',
              minHeight: 44,
              position: 'sticky',
              top: 0,
              zIndex: 3,
              borderBottom: '1px solid',
              borderColor: '#6f849c',
              bgcolor: '#ffffff',
              px: { xs: 1, md: 1.25 },
              py: 0.7,
            }}
          >
            {title && (
              <Typography sx={{ color: '#0f172a', fontSize: '0.92rem', fontWeight: 600, lineHeight: 1.2 }}>
                {title}
              </Typography>
            )}
            <Stack
              direction={{ xs: 'column', sm: 'row' }}
              spacing={1}
              alignItems={{ xs: 'stretch', sm: 'center' }}
              sx={{ flex: title ? '0 0 auto' : '1 1 auto', justifyContent: 'space-between' }}
            >
              {showSearch && (
                <TextField
                  value={searchValue}
                  onChange={(event) => onSearchChange?.(event.target.value)}
                  placeholder={searchPlaceholder}
                  size="small"
                  sx={{
                    width: { xs: '100%', sm: 260 },
                    '& .MuiInputBase-root': {
                      height: 32,
                      borderRadius: 1,
                      color: '#0f172a',
                      fontSize: '0.78rem',
                    },
                    '& .MuiOutlinedInput-notchedOutline': {
                      borderColor: '#6f849c',
                    },
                    '& .MuiInputBase-root:hover .MuiOutlinedInput-notchedOutline': {
                      borderColor: '#52657a',
                    },
                    '& .MuiInputBase-input::placeholder': {
                      color: '#0f172a',
                      opacity: 0.72,
                    },
                  }}
                  slotProps={{
                    input: {
                      startAdornment: (
                        <InputAdornment position="start">
                          <SearchIcon sx={{ color: '#0f172a', fontSize: 17 }} />
                        </InputAdornment>
                      ),
                    },
                  }}
                />
              )}
              <Stack direction="row" spacing={1} alignItems="center" justifyContent="flex-end">
                {filterControl}
                {toolbarActions}
                {actions}
                {showRefresh && (
                  <Tooltip title="Refresh">
                    <span>
                      <IconButton
                        aria-label="Refresh"
                        onClick={onRefresh}
                        disabled={loading || !onRefresh}
                        size="small"
                        sx={{
                          width: 32,
                          height: 32,
                          border: '1px solid',
                          borderColor: '#6f849c',
                          borderRadius: 1,
                          color: '#0f172a',
                        }}
                      >
                        <RefreshIcon sx={{ fontSize: 18 }} />
                      </IconButton>
                    </span>
                  </Tooltip>
                )}
              </Stack>
            </Stack>
          </Stack>
        )}

        {filterPanel && (
          <Box sx={{ flex: '0 0 auto', position: 'relative', zIndex: 4 }}>
            {filterPanel}
          </Box>
        )}

        <Box sx={{ height: 2, flex: '0 0 auto' }}>{loading && <LinearProgress sx={{ height: 2 }} />}</Box>

        <TableContainer sx={{ position: 'relative', flex: '1 1 auto', minHeight: 0, overflow: 'auto' }}>
          <Table stickyHeader={stickyHeader} size="small" sx={{ minWidth: '100%', width: 'max-content', tableLayout: 'auto' }}>
            <TableHead>
              <TableRow>
                {visibleColumns.map((column) => {
                  const isSorted = sortField === column.field
                  const canSort = enableSorting && Boolean(onSortChange) && column.sortable && column.field

                  return (
                    <TableCell
                      key={column.field || column.headerName}
                      align={column.headerAlign || column.align || 'left'}
                      className={column.headerClassName}
                      onClick={() => handleSort(column)}
                      scope="col"
                      sx={{
                        ...tableCellSx,
                        width: column.width || 'auto',
                        minWidth: column.minWidth,
                        maxWidth: column.maxWidth,
                        bgcolor: '#d9e4ef',
                        borderBottom: '1px solid #6f849c',
                        color: '#0f172a',
                        cursor: canSort ? 'pointer' : 'default',
                        fontWeight: 800,
                        height: 34,
                        py: 0.55,
                        position: stickyHeader ? 'sticky' : 'static',
                        top: 0,
                        userSelect: 'none',
                        zIndex: 2,
                      }}
                    >
                      <Box
                        sx={{
                          display: 'inline-flex',
                          alignItems: 'center',
                          justifyContent: column.headerAlign === 'right' || column.align === 'right' ? 'flex-end' : column.headerAlign === 'center' || column.align === 'center' ? 'center' : 'flex-start',
                          gap: 0.35,
                          width: '100%',
                        }}
                      >
                        {column.headerName}
                        {isSorted && (sortDirection === 'asc' ? <ArrowUpwardIcon sx={{ fontSize: 13 }} /> : <ArrowDownwardIcon sx={{ fontSize: 13 }} />)}
                      </Box>
                    </TableCell>
                  )
                })}
              </TableRow>
            </TableHead>
            <TableBody>
              {!loading && (error || rows.length === 0) && (
                <TableRow>
                  <TableCell colSpan={visibleColumns.length} sx={{ borderBottom: 0 }}>
                    <Stack spacing={0.75} alignItems="center" sx={{ py: 4 }}>
                      <Typography sx={{ color: '#0f172a', fontSize: '0.84rem', fontWeight: 700 }}>
                        {error || emptyMessage}
                      </Typography>
                      {error && onRefresh && (
                        <Button size="small" variant="outlined" onClick={onRefresh} sx={{ minHeight: 28 }}>
                          Retry
                        </Button>
                      )}
                    </Stack>
                  </TableCell>
                </TableRow>
              )}

              {rows.map((row, index) => {
                const rowId = getRowId(row) ?? index
                const selected = selectedRowId !== undefined && selectedRowId === rowId

                return (
                  <TableRow
                    className={typeof rowClassName === 'function' ? rowClassName(row) : rowClassName}
                    hover
                    key={rowId}
                    onClick={onRowClick ? () => onRowClick(row) : undefined}
                    selected={selected}
                    sx={{
                      bgcolor: selected ? '#dcecff' : index % 2 === 0 ? '#ffffff' : '#eef4fa',
                      cursor: onRowClick ? 'pointer' : 'default',
                      height: rowHeight,
                      '&:hover td': {
                        bgcolor: '#dcecff',
                      },
                    }}
                  >
                    {visibleColumns.map((column) => {
                      const rawValue = column.field ? row?.[column.field] : undefined
                      const content = column.renderCell
                        ? column.renderCell({ row, value: rawValue, field: column.field })
                        : column.formatter
                          ? column.formatter(rawValue, row)
                          : displayFallback(rawValue)

                      return (
                        <TableCell
                          align={column.align || 'left'}
                          className={typeof column.cellClassName === 'function' ? column.cellClassName(row) : column.cellClassName}
                          key={column.field || column.headerName}
                          sx={{
                            ...tableCellSx,
                            fontWeight: column.medium ? 700 : 400,
                            width: column.width || 'auto',
                            minWidth: column.minWidth,
                            maxWidth: column.maxWidth,
                          }}
                        >
                          {column.field === '__actions' ? (
                            <Stack direction="row" spacing={0.25} justifyContent="center">
                              {rowActions.map((action) => {
                                const { disabled, hidden } = getActionState(action, row)

                                if (hidden) {
                                  return null
                                }

                                return (
                                  <Tooltip key={action.key} title={action.label}>
                                    <span>
                                      <IconButton
                                        aria-label={action.label}
                                        disabled={disabled}
                                        onClick={(event) => {
                                          event.stopPropagation()
                                          action.onClick?.(row)
                                        }}
                                        size="small"
                                        sx={{ color: '#0f172a', p: 0.25, '&.Mui-disabled': { color: '#0f172a', opacity: 1 } }}
                                      >
                                        {action.icon}
                                      </IconButton>
                                    </span>
                                  </Tooltip>
                                )
                              })}
                            </Stack>
                          ) : (
                            content
                          )}
                        </TableCell>
                      )
                    })}
                  </TableRow>
                )
              })}
            </TableBody>
          </Table>
        </TableContainer>

        <Stack
          direction="row"
          alignItems="center"
          justifyContent="flex-end"
          sx={{
            flex: '0 0 auto',
            width: '100%',
            height: 34,
            minHeight: 34,
            position: stickyFooter ? 'sticky' : 'static',
            bottom: 0,
            zIndex: 3,
            borderTop: '1px solid',
            borderColor: '#6f849c',
            bgcolor: '#ffffff',
            px: 1,
            py: 0,
            overflowX: 'auto',
          }}
        >
          <Stack
            direction="row"
            alignItems="center"
            justifyContent="flex-end"
            sx={{
              display: 'flex',
              width: 'auto',
              ml: 'auto',
              minHeight: 34,
              gap: 1,
              px: 0.75,
              py: 0.25,
              border: '1px solid',
              borderColor: '#9badc0',
              borderRadius: 0.75,
              bgcolor: '#f8fbfe',
              boxShadow: '0 1px 2px rgba(15, 23, 42, 0.06)',
            }}
          >
            <Typography sx={{ display: 'inline-flex', alignItems: 'center', height: 28, color: '#0f172a', fontSize: '0.75rem', lineHeight: 1 }}>
              Rows per page
            </Typography>
            <Select
              value={pageSize}
              onChange={(event) => onPageSizeChange?.(Number(event.target.value))}
              size="small"
              sx={{
                display: 'inline-flex',
                alignItems: 'center',
                height: 28,
                minWidth: 64,
                borderRadius: 0.75,
                bgcolor: '#ffffff',
                color: '#0f172a',
                fontSize: '0.75rem',
                lineHeight: 1,
                '&.MuiOutlinedInput-root': {
                  height: 28,
                  alignItems: 'center',
                },
                '& .MuiOutlinedInput-notchedOutline': {
                  borderColor: '#9badc0',
                },
                '& .MuiSelect-select': {
                  display: 'flex',
                  alignItems: 'center',
                  height: 28,
                  minHeight: '0 !important',
                  lineHeight: 1,
                  py: 0,
                  pl: 1,
                  pr: 3,
                },
                '& .MuiSvgIcon-root': {
                  top: '50%',
                  transform: 'translateY(-50%)',
                  color: '#0f172a',
                },
              }}
            >
              {pageSizeOptions.map((option) => (
                <MenuItem key={option} value={option} sx={{ color: '#0f172a', fontSize: '0.78rem' }}>
                  {option}
                </MenuItem>
              ))}
            </Select>
            <Typography sx={{ display: 'inline-flex', alignItems: 'center', height: 28, color: '#0f172a', fontSize: '0.75rem', lineHeight: 1, whiteSpace: 'nowrap' }}>
              {pageStart}-{pageEnd} of {totalElements} records
            </Typography>
            <Typography sx={{ display: 'inline-flex', alignItems: 'center', height: 28, color: '#6f849c', fontSize: '0.75rem', lineHeight: 1, mx: 0.25 }}>
              |
            </Typography>
            <Typography sx={{ display: 'inline-flex', alignItems: 'center', height: 28, minWidth: 78, color: '#0f172a', textAlign: 'center', fontSize: '0.75rem', lineHeight: 1 }}>
              Page {page + 1} / {resolvedTotalPages}
            </Typography>
            <Stack direction="row" spacing={0.25} alignItems="center" sx={{ display: 'inline-flex', height: 28 }}>
              <IconButton aria-label="First page" disabled={page === 0} size="small" onClick={() => goToPage(0)} sx={{ width: 28, height: 28, borderRadius: 0.75, color: '#0f172a', p: 0, '&:hover': { bgcolor: '#e7f1fc' } }}>
                <FirstPageIcon sx={{ fontSize: 17 }} />
              </IconButton>
              <IconButton aria-label="Previous page" disabled={page === 0} size="small" onClick={() => goToPage(page - 1)} sx={{ width: 28, height: 28, borderRadius: 0.75, color: '#0f172a', p: 0, '&:hover': { bgcolor: '#e7f1fc' } }}>
                <KeyboardArrowLeftIcon sx={{ fontSize: 17 }} />
              </IconButton>
              <IconButton
                aria-label="Next page"
                disabled={page + 1 >= resolvedTotalPages || totalElements === 0}
                size="small"
                onClick={() => goToPage(page + 1)}
                sx={{ width: 28, height: 28, borderRadius: 0.75, color: '#0f172a', p: 0, '&:hover': { bgcolor: '#e7f1fc' } }}
              >
                <KeyboardArrowRightIcon sx={{ fontSize: 17 }} />
              </IconButton>
              <IconButton
                aria-label="Last page"
                disabled={page + 1 >= resolvedTotalPages || totalElements === 0}
                size="small"
                onClick={() => goToPage(resolvedTotalPages - 1)}
                sx={{ width: 28, height: 28, borderRadius: 0.75, color: '#0f172a', p: 0, '&:hover': { bgcolor: '#e7f1fc' } }}
              >
                <LastPageIcon sx={{ fontSize: 17 }} />
              </IconButton>
            </Stack>
          </Stack>
        </Stack>
      </Paper>
    </Box>
  )
}

export default ERPDataTable
