import { useEffect, useMemo, useState } from 'react';
import {
  ChevronDown,
  ChevronLeft,
  ChevronRight,
  ChevronsLeft,
  ChevronsRight,
  Eye,
  EyeOff,
  Filter,
  Loader2,
  Search,
} from 'lucide-react';

const DEFAULT_PAGE_SIZE_OPTIONS = [25, 50, 100, 250];

function formatCellValue(value) {
  if (value === null || value === undefined || value === '') return '-';
  if (typeof value === 'number') return value.toLocaleString('en-IN');
  return String(value);
}

const ServerDataTable = ({
  columns = [],
  defaultFilters = {},
  defaultHiddenColumns = {},
  defaultPageSize = 25,
  defaultSortColumn = '',
  defaultSortDirection = 'asc',
  fetchPage,
  filterFields = [],
  getRowId = (row) => row?.id,
  getContextMenuItems,
  loadingLabel = 'Loading records',
  pageSizeOptions = DEFAULT_PAGE_SIZE_OPTIONS,
  searchPlaceholder = 'Quick Search...',
  title,
  titleAction,
  transformResponse = (data) => ({
    rows: Array.isArray(data?.content) ? data.content : [],
    totalElements: Number(data?.totalElements || 0),
  }),
  rowUpdates,
  onRowClick,
  onRowDoubleClick,
}) => {
  const [rows, setRows] = useState([]);
  const [page, setPage] = useState(0);
  const [pageSize, setPageSize] = useState(defaultPageSize);
  const [totalElements, setTotalElements] = useState(0);
  const [keywordInput, setKeywordInput] = useState('');
  const [keyword, setKeyword] = useState('');
  const [sortColumn, setSortColumn] = useState(defaultSortColumn);
  const [sortDirection, setSortDirection] = useState(defaultSortDirection);
  const [filters, setFilters] = useState(defaultFilters);
  const [appliedFilters, setAppliedFilters] = useState(defaultFilters);
  const [filterOpen, setFilterOpen] = useState(false);
  const [columnsOpen, setColumnsOpen] = useState(false);
  const [hiddenColumns, setHiddenColumns] = useState(defaultHiddenColumns);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const [selectedRowId, setSelectedRowId] = useState(null);
  const [contextMenu, setContextMenu] = useState(null);

  useEffect(() => {
    const timeoutId = window.setTimeout(() => {
      setKeyword(keywordInput.trim());
      setPage(0);
    }, 350);

    return () => window.clearTimeout(timeoutId);
  }, [keywordInput]);

  useEffect(() => {
    let active = true;

    async function loadRows() {
      if (!fetchPage) return;

      setLoading(true);
      setError('');

      try {
        const data = await fetchPage({
          filters: appliedFilters,
          keyword,
          page,
          pageSize,
          sortColumn,
          sortDirection,
        });
        const result = transformResponse(data);

        if (!active) return;
        setRows(Array.isArray(result.rows) ? result.rows : []);
        setTotalElements(Number(result.totalElements || 0));
      } catch {
        if (!active) return;
        setRows([]);
        setTotalElements(0);
        setError('Unable to load records. Please refresh and try again.');
      } finally {
        if (active) setLoading(false);
      }
    }

    loadRows();

    return () => {
      active = false;
    };
  }, [appliedFilters, fetchPage, keyword, page, pageSize, sortColumn, sortDirection, transformResponse]);

  useEffect(() => {
    if (!contextMenu) return undefined;

    const closeContextMenu = () => setContextMenu(null);

    window.addEventListener('click', closeContextMenu);
    window.addEventListener('scroll', closeContextMenu, true);
    window.addEventListener('resize', closeContextMenu);

    return () => {
      window.removeEventListener('click', closeContextMenu);
      window.removeEventListener('scroll', closeContextMenu, true);
      window.removeEventListener('resize', closeContextMenu);
    };
  }, [contextMenu]);

  const displayedRows = useMemo(
    () => rows.map((row, index) => ({ ...row, serialNumber: page * pageSize + index + 1 })),
    [page, pageSize, rows],
  );

  useEffect(() => {
    if (!rowUpdates || Object.keys(rowUpdates).length === 0) return;
    setRows((currentRows) => currentRows.map((row) => {
      const rowId = getRowId(row);
      const patch = rowUpdates[rowId];
      return patch ? { ...row, ...patch } : row;
    }));
  }, [rowUpdates]);

  const visibleColumns = useMemo(
    () => columns.filter((column) => hiddenColumns[column.field] !== false),
    [columns, hiddenColumns],
  );

  const totalPages = Math.max(1, Math.ceil(totalElements / pageSize));
  const startRecord = totalElements === 0 ? 0 : page * pageSize + 1;
  const endRecord = Math.min(totalElements, (page + 1) * pageSize);

  const changeSort = (column) => {
    if (!column.sortable) return;

    setPage(0);
    if (sortColumn !== column.field) {
      setSortColumn(column.field);
      setSortDirection('asc');
      return;
    }

    setSortDirection((current) => (current === 'asc' ? 'desc' : 'asc'));
  };

  const resetFilters = () => {
    setFilters(defaultFilters);
    setAppliedFilters(defaultFilters);
    setKeywordInput('');
    setKeyword('');
    setPage(0);
  };

  return (
    <div className="w-full h-full min-h-0 bg-white flex flex-col border-2 border-black/20 rounded-xl overflow-hidden shadow-sm" style={{ fontFamily: 'Calibri, sans-serif' }}>
      <div className="shrink-0 px-3 py-2 bg-gradient-to-b from-white to-blue-50 border-b-2 border-black/10 flex items-center justify-between gap-3">
        <div className="flex flex-wrap items-center gap-2 min-w-0">
          <div className="relative">
            <Search size={14} className="absolute left-3 top-1/2 -translate-y-1/2 text-black/50" />
            <input
              value={keywordInput}
              onChange={(event) => setKeywordInput(event.target.value)}
              placeholder={searchPlaceholder}
              className="w-64 pl-9 pr-3 py-1.5 border-2 border-black/20 rounded-lg text-[12px] font-black uppercase outline-none focus:border-[#0052CC]"
            />
          </div>
          <button
            type="button"
            onClick={() => setFilterOpen((value) => !value)}
            className={`px-3 py-1.5 border-2 rounded-lg text-[11px] font-black uppercase flex items-center gap-2 ${filterOpen ? 'bg-[#0052CC] border-[#0052CC] text-white' : 'bg-white border-black/30 text-black'}`}
          >
            <Filter size={14} /> Advanced Filter
          </button>
          <div className="relative">
            <button
              type="button"
              onClick={() => setColumnsOpen((value) => !value)}
              className="px-3 py-1.5 border-2 border-black/30 rounded-lg text-[11px] font-black uppercase bg-white text-black flex items-center gap-2"
            >
              <Eye size={14} /> Columns
            </button>
            {columnsOpen && (
              <div className="absolute right-0 top-11 z-40 w-64 max-h-80 overflow-auto bg-white border-2 border-black/20 rounded-lg shadow-xl p-2">
                {columns.map((column) => (
                  <label key={column.field} className="flex items-center justify-between gap-3 px-2 py-1.5 text-[12px] font-bold text-black hover:bg-blue-50 rounded">
                    <span>{column.headerName}</span>
                    <input
                      type="checkbox"
                      checked={hiddenColumns[column.field] !== false}
                      onChange={(event) => setHiddenColumns((current) => ({ ...current, [column.field]: event.target.checked }))}
                    />
                  </label>
                ))}
              </div>
            )}
          </div>
        </div>

        {(title || titleAction) && (
          <div className="shrink-0 flex items-center justify-end gap-3">
            {titleAction}
            {title && (
              <div className="text-right text-[18px] font-black text-[#0052CC] uppercase tracking-tight">
                {title}
              </div>
            )}
          </div>
        )}
      </div>

      {filterOpen && (
        <div className="shrink-0 px-3 py-2 border-b border-black/10 bg-slate-50 grid grid-cols-1 md:grid-cols-4 xl:grid-cols-8 gap-2">
          {filterFields.map((field) => (
            <label key={field.field} className="space-y-1">
              <span className="text-[10px] font-black text-black/60 uppercase">{field.label}</span>
              <input
                type={field.type || 'text'}
                value={filters[field.field] || ''}
                onChange={(event) => setFilters((current) => ({ ...current, [field.field]: event.target.value }))}
                className="w-full px-2 py-1.5 border border-black/20 rounded text-[12px] font-bold outline-none focus:border-[#0052CC]"
              />
            </label>
          ))}
          <div className="flex items-end gap-2">
            <button
              type="button"
              onClick={() => {
                setAppliedFilters(filters);
                setPage(0);
              }}
              className="px-4 py-1.5 bg-[#0052CC] text-white rounded text-[11px] font-black uppercase"
            >
              Apply
            </button>
            <button
              type="button"
              onClick={resetFilters}
              className="px-4 py-1.5 bg-white border border-black/20 text-black rounded text-[11px] font-black uppercase"
            >
              Reset
            </button>
          </div>
        </div>
      )}

      <div className="relative flex-1 min-h-0 overflow-auto">
        {loading && (
          <div className="absolute inset-0 z-30 bg-white/70 backdrop-blur-[1px] grid place-items-center">
            <div className="bg-white border-2 border-blue-200 rounded-xl px-5 py-4 shadow-xl flex items-center gap-3 text-[#0052CC]">
              <Loader2 size={20} className="animate-spin" />
              <span className="text-[12px] font-black uppercase tracking-widest">{loadingLabel}</span>
            </div>
          </div>
        )}

        <table className="min-w-max text-left border-separate border-spacing-0">
          <thead className="sticky top-0 z-20">
            <tr>
              {visibleColumns.map((column) => (
                <th
                  key={column.field}
                  onClick={() => changeSort(column)}
                  style={{ minWidth: column.minWidth || 120 }}
                  className={`px-3 py-2 bg-[#dfe7f2] border-r border-b-2 border-black/30 last:border-r-0 text-[12px] font-black text-black uppercase whitespace-nowrap select-none ${column.sortable ? 'cursor-pointer hover:bg-blue-100' : ''} ${column.headerAlign === 'right' ? 'text-right' : column.headerAlign === 'center' ? 'text-center' : 'text-left'}`}
                >
                  <span className="inline-flex items-center gap-1">
                    {column.headerName}
                    {sortColumn === column.field && <ChevronDown size={13} className={sortDirection === 'asc' ? 'rotate-180' : ''} />}
                  </span>
                </th>
              ))}
            </tr>
          </thead>
          <tbody>
            {!loading && (error || displayedRows.length === 0) && (
              <tr>
                <td colSpan={visibleColumns.length || 1} className="px-4 py-12 text-center text-[13px] font-black text-black/60 uppercase">
                  {error || 'No records found'}
                </td>
              </tr>
            )}
            {displayedRows.map((row, rowIndex) => {
              const rowId = getRowId(row) ?? rowIndex;
              const isSelected = selectedRowId === rowId;

              return (
                <tr
                  key={rowId}
                  onClick={() => {
                    setSelectedRowId(rowId);
                    onRowClick?.(row);
                  }}
                  onDoubleClick={() => onRowDoubleClick?.(row)}
                  onContextMenu={(event) => {
                    if (!getContextMenuItems) return;

                    event.preventDefault();
                    setSelectedRowId(rowId);
                    setContextMenu({
                      left: Math.min(event.clientX, window.innerWidth - 220),
                      row,
                      top: Math.min(event.clientY, window.innerHeight - 180),
                    });
                  }}
                  className={`${isSelected ? 'bg-yellow-100' : rowIndex % 2 === 0 ? 'bg-white' : 'bg-blue-50/35'} hover:bg-cyan-50 transition-colors`}
                >
                  {visibleColumns.map((column) => (
                    <td
                      key={column.field}
                      style={{ minWidth: column.minWidth || 120 }}
                      className={`px-3 py-1.5 border-r border-b border-black/15 last:border-r-0 text-[12px] text-black font-bold whitespace-nowrap ${column.align === 'right' ? 'text-right' : column.align === 'center' ? 'text-center' : 'text-left'}`}
                    >
                      {column.renderCell ? column.renderCell(row) : column.formatter ? column.formatter(row[column.field], row) : formatCellValue(row[column.field])}
                    </td>
                  ))}
                </tr>
              );
            })}
          </tbody>
        </table>

        {contextMenu && (
          <div
            className="fixed z-[99999] w-52 overflow-hidden rounded-lg border-2 border-black/20 bg-white py-1 text-left shadow-2xl"
            style={{ left: contextMenu.left, top: contextMenu.top }}
            onClick={(event) => event.stopPropagation()}
          >
            {getContextMenuItems(contextMenu.row).map((item) => (
              <button
                key={item.label}
                type="button"
                onClick={() => {
                  setContextMenu(null);
                  item.onClick?.(contextMenu.row);
                }}
                className="flex w-full items-center gap-2 px-3 py-2 text-left text-[12px] font-black uppercase text-black hover:bg-[#0052CC] hover:text-white"
              >
                {item.icon}
                <span>{item.label}</span>
              </button>
            ))}
          </div>
        )}
      </div>

      <div className="shrink-0 min-h-9 px-3 py-1 bg-gradient-to-t from-blue-50 to-white border-t-2 border-black/20 flex items-center justify-between gap-3">
        <div className="flex items-center gap-2 text-[11px] font-black text-black uppercase">
          <span>Rows</span>
          <select
            value={pageSize}
            onChange={(event) => {
              setPageSize(Number(event.target.value));
              setPage(0);
            }}
            className="h-7 border border-black/30 rounded px-2 bg-white font-black outline-none"
          >
            {pageSizeOptions.map((option) => (
              <option key={option} value={option}>{option}</option>
            ))}
          </select>
          <span className="hidden sm:inline">{startRecord}-{endRecord} of {totalElements}</span>
        </div>

        <div className="flex items-center gap-1">
          <button type="button" disabled={page === 0} onClick={() => setPage(0)} className="p-1.5 disabled:opacity-30 hover:bg-white rounded">
            <ChevronsLeft size={16} />
          </button>
          <button type="button" disabled={page === 0} onClick={() => setPage((value) => Math.max(0, value - 1))} className="p-1.5 disabled:opacity-30 hover:bg-white rounded">
            <ChevronLeft size={16} />
          </button>
          <span className="px-3 py-1 bg-white border border-black/20 rounded text-[11px] font-black text-black">
            Page {page + 1} / {totalPages}
          </span>
          <button type="button" disabled={page + 1 >= totalPages} onClick={() => setPage((value) => Math.min(totalPages - 1, value + 1))} className="p-1.5 disabled:opacity-30 hover:bg-white rounded">
            <ChevronRight size={16} />
          </button>
          <button type="button" disabled={page + 1 >= totalPages} onClick={() => setPage(totalPages - 1)} className="p-1.5 disabled:opacity-30 hover:bg-white rounded">
            <ChevronsRight size={16} />
          </button>
        </div>

        <div className="hidden lg:flex items-center gap-2 text-[10px] font-black text-[#0052CC] uppercase">
          {loading ? <Loader2 size={13} className="animate-spin" /> : <EyeOff size={13} />}
          <span>{loading ? 'Syncing' : 'Ready'}</span>
        </div>
      </div>
    </div>
  );
};

export default ServerDataTable;
