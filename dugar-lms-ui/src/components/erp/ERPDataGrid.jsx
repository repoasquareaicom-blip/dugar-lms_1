import ArrowDownwardIcon from '@mui/icons-material/ArrowDownward'
import ArrowUpwardIcon from '@mui/icons-material/ArrowUpward'
import DragIndicatorIcon from '@mui/icons-material/DragIndicator'
import { Box, LinearProgress, Paper, Stack, Table, TableBody, TableCell, TableContainer, TableHead, TableRow, Typography } from '@mui/material'
import { useEffect, useMemo, useRef, useState } from 'react'
import { displayFallback } from './erpTableUtils.js'
import { erpLayout, erpTypography } from './erpTokens.js'

function getNextSortDirection(sortColumn, sortDirection, field) {
  if (sortColumn !== field) {
    return 'asc'
  }

  return sortDirection === 'asc' ? 'desc' : 'asc'
}

function ERPDataGrid({
  columns = [],
  emptyMessage = 'No records found',
  error = '',
  getRowId = (row) => row?.id,
  loading = false,
  onRowClick,
  onRowContextMenu,
  onRowDoubleClick,
  onSortChange,
  rows = [],
  selectedRowId,
  sortColumn = '',
  sortDirection = 'asc',
  visibilityModel = {},
}) {
  const visibleColumns = useMemo(
    () => columns.filter((column) => visibilityModel[column.field] !== false),
    [columns, visibilityModel],
  )
  const [columnWidths, setColumnWidths] = useState({})
  const resizeStateRef = useRef(null)

  useEffect(() => {
    setColumnWidths((current) => {
      const next = { ...current }
      for (const column of columns) {
        if (!next[column.field]) {
          next[column.field] = column.width || column.minWidth || 120
        }
      }
      return next
    })
  }, [columns])

  useEffect(() => {
    const handleMouseMove = (event) => {
      const state = resizeStateRef.current
      if (!state) {
        return
      }

      const nextWidth = Math.max(state.minWidth, state.startWidth + (event.clientX - state.startX))
      setColumnWidths((current) => ({ ...current, [state.field]: nextWidth }))
    }

    const handleMouseUp = () => {
      resizeStateRef.current = null
    }

    window.addEventListener('mousemove', handleMouseMove)
    window.addEventListener('mouseup', handleMouseUp)

    return () => {
      window.removeEventListener('mousemove', handleMouseMove)
      window.removeEventListener('mouseup', handleMouseUp)
    }
  }, [])

  const headerCellSx = {
    position: 'sticky',
    top: 0,
    zIndex: 3,
    borderRight: `1px solid ${erpLayout.borderColor}`,
    borderBottom: `1px solid ${erpLayout.borderColor}`,
    bgcolor: erpLayout.headerBackground,
    color: erpLayout.black,
    fontFamily: erpTypography.fontFamily,
    fontSize: erpTypography.headerFontSize,
    fontWeight: 700,
    px: 0.75,
    py: 0.5,
    boxShadow: `0 1px 0 ${erpLayout.borderColor}`,
    userSelect: 'none',
    whiteSpace: 'nowrap',
    '&:last-of-type': {
      borderRight: 0,
    },
  }

  const bodyCellSx = {
    borderRight: `1px solid ${erpLayout.borderColor}`,
    borderBottom: `1px solid ${erpLayout.borderColor}`,
    color: erpLayout.black,
    fontFamily: erpTypography.fontFamily,
    fontSize: erpTypography.fontSize,
    px: 0.75,
    py: 0.35,
    whiteSpace: 'nowrap',
    '&:last-of-type': {
      borderRight: 0,
    },
  }

  return (
    <Paper elevation={0} sx={{ border: `1px solid ${erpLayout.borderColor}`, borderTop: 0, borderRadius: '0 0 4px 4px', flex: '1 1 auto', minHeight: 0, position: 'relative' }}>
      {loading && <LinearProgress sx={{ height: 2 }} />}
      {loading && (
        <Stack
          spacing={1.1}
          alignItems="center"
          justifyContent="center"
          sx={{
            position: 'fixed',
            top: '50%',
            left: '50%',
            transform: 'translate(-50%, -50%)',
            zIndex: 1800,
            width: 178,
            minHeight: 152,
            borderRadius: 2,
            border: '1px solid rgba(14, 165, 233, 0.26)',
            background: 'linear-gradient(145deg, rgba(255,255,255,0.94), rgba(236,254,255,0.9) 48%, rgba(219,234,254,0.92))',
            boxShadow: '0 24px 52px rgba(15,23,42,0.22), 0 8px 20px rgba(14,165,233,0.2), inset 0 1px 0 rgba(255,255,255,0.96)',
            backdropFilter: 'blur(8px)',
            pointerEvents: 'none',
          }}
        >
          <Box sx={{ position: 'relative', width: 66, height: 66 }}>
            <Box
              sx={{
                position: 'absolute',
                inset: 0,
                borderRadius: '50%',
                border: '3px solid rgba(14,165,233,0.14)',
                borderTopColor: '#0284c7',
                borderRightColor: '#22c55e',
                animation: 'erpOrbit 880ms linear infinite',
                '@keyframes erpOrbit': {
                  from: { transform: 'rotate(0deg)' },
                  to: { transform: 'rotate(360deg)' },
                },
              }}
            />
            <Box
              sx={{
                position: 'absolute',
                inset: 10,
                borderRadius: '50%',
                border: '3px solid rgba(34,197,94,0.16)',
                borderBottomColor: '#16a34a',
                borderLeftColor: '#f59e0b',
                animation: 'erpCounterOrbit 1180ms linear infinite',
                '@keyframes erpCounterOrbit': {
                  from: { transform: 'rotate(360deg)' },
                  to: { transform: 'rotate(0deg)' },
                },
              }}
            />
            <Box
              sx={{
                position: 'absolute',
                inset: 23,
                borderRadius: '50%',
                background: 'linear-gradient(145deg, #0284c7, #22c55e)',
                boxShadow: '0 0 0 8px rgba(14,165,233,0.12), inset 0 1px 2px rgba(255,255,255,0.6)',
                animation: 'erpPulseCore 1050ms ease-in-out infinite',
                '@keyframes erpPulseCore': {
                  '0%, 100%': { transform: 'scale(0.82)', opacity: 0.78 },
                  '50%': { transform: 'scale(1)', opacity: 1 },
                },
              }}
            />
          </Box>
          <Typography sx={{ color: '#075985', fontFamily: erpTypography.fontFamily, fontSize: erpTypography.fontSize, fontWeight: 900 }}>
            Loading contracts
          </Typography>
          <Typography sx={{ color: '#64748b', fontFamily: erpTypography.fontFamily, fontSize: '0.72rem', fontWeight: 800, mt: -0.45 }}>
            Preparing grid data
          </Typography>
        </Stack>
      )}
      <TableContainer
        sx={{
          height: '100%',
          minHeight: 0,
          overflowX: 'auto',
          overflowY: 'auto',
          scrollbarGutter: 'stable',
        }}
      >
        <Table stickyHeader size="small" sx={{ width: 'max-content', minWidth: 'calc(100% - 18px)', tableLayout: 'fixed' }}>
          <TableHead>
            <TableRow>
              {visibleColumns.map((column) => {
                const isSorted = sortColumn === column.field
                const width = columnWidths[column.field] || column.width || column.minWidth || 120

                return (
                  <TableCell
                    key={column.field}
                    align={column.headerAlign || column.align || 'left'}
                    onClick={() => column.sortable && onSortChange?.(column.field, getNextSortDirection(sortColumn, sortDirection, column.field))}
                    sx={{ ...headerCellSx, width, minWidth: column.minWidth || width, cursor: column.sortable ? 'pointer' : 'default' }}
                  >
                    <Box sx={{ display: 'flex', alignItems: 'center', justifyContent: column.headerAlign || column.align || 'left', gap: 0.4 }}>
                      <span>{column.headerName}</span>
                      {isSorted && (sortDirection === 'asc' ? <ArrowUpwardIcon sx={{ fontSize: 12 }} /> : <ArrowDownwardIcon sx={{ fontSize: 12 }} />)}
                    </Box>
                    <Box
                      onMouseDown={(event) => {
                        event.preventDefault()
                        event.stopPropagation()
                        resizeStateRef.current = {
                          field: column.field,
                          minWidth: column.minWidth || 90,
                          startWidth: width,
                          startX: event.clientX,
                        }
                      }}
                      sx={{
                        position: 'absolute',
                        top: 0,
                        right: 0,
                        width: 8,
                        height: '100%',
                        cursor: 'col-resize',
                        display: 'flex',
                        alignItems: 'center',
                        justifyContent: 'center',
                        color: erpLayout.mutedBorder,
                      }}
                    >
                      <DragIndicatorIcon sx={{ fontSize: 11, transform: 'rotate(90deg)' }} />
                    </Box>
                  </TableCell>
                )
              })}
            </TableRow>
          </TableHead>
          <TableBody>
            {rows.length === 0 && !loading ? (
              <TableRow>
                <TableCell colSpan={visibleColumns.length || 1} sx={{ ...bodyCellSx, textAlign: 'center', py: 2 }}>
                  <Typography sx={{ fontFamily: erpTypography.fontFamily, fontSize: erpTypography.fontSize, color: erpLayout.black }}>
                    {error || emptyMessage}
                  </Typography>
                </TableCell>
              </TableRow>
            ) : (
              rows.map((row, index) => {
                const rowId = getRowId(row)
                const isSelected = selectedRowId === rowId

                return (
                  <TableRow
                    key={rowId}
                    hover
                    onClick={() => onRowClick?.(row)}
                    onDoubleClick={() => onRowDoubleClick?.(row)}
                    onContextMenu={(event) => onRowContextMenu?.(event, row)}
                    sx={{
                      cursor: 'default',
                      bgcolor: isSelected ? erpLayout.rowSelected : index % 2 === 1 ? erpLayout.rowAlternate : erpLayout.white,
                      '&:hover': {
                        bgcolor: isSelected ? erpLayout.rowSelected : erpLayout.rowHover,
                      },
                    }}
                  >
                    {visibleColumns.map((column) => {
                      const value = row[column.field]
                      const renderedValue = column.renderCell ? column.renderCell(row) : column.formatter ? column.formatter(value, row) : displayFallback(value)
                      const width = columnWidths[column.field] || column.width || column.minWidth || 120

                      return (
                        <TableCell key={column.field} align={column.align || 'left'} sx={{ ...bodyCellSx, width, minWidth: column.minWidth || width }}>
                          {renderedValue}
                        </TableCell>
                      )
                    })}
                  </TableRow>
                )
              })
            )}
          </TableBody>
        </Table>
      </TableContainer>
    </Paper>
  )
}

export default ERPDataGrid
