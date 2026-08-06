import React from 'react';
import { useNavigate } from 'react-router-dom';
import ServerDataTable from '../../../components/common/ServerDataTable';
import { fetchAuthorisationQueue } from '../../../services/voucherService';

function money(value) {
  return Number(value || 0).toLocaleString('en-IN', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
}

function displayDate(value) {
  if (!value) return '-';
  const date = new Date(String(value).includes('T') ? value : `${value}T00:00:00`);
  if (Number.isNaN(date.getTime())) return value;
  return date.toLocaleString('en-GB', {
    day: '2-digit',
    month: 'short',
    year: 'numeric',
    hour: String(value).includes('T') ? '2-digit' : undefined,
    minute: String(value).includes('T') ? '2-digit' : undefined,
  }).replace(/ /g, '-');
}

const columns = [
  { field: 'serialNumber', headerName: 'S.No', minWidth: 70, align: 'center', headerAlign: 'center' },
  { field: 'voucherType', headerName: 'Voucher Type', minWidth: 130, sortable: true },
  { field: 'voucherNumber', headerName: 'Voucher Number', minWidth: 150, sortable: true },
  { field: 'voucherDate', headerName: 'Voucher Date', minWidth: 130, sortable: true, formatter: displayDate },
  { field: 'headerControlName', headerName: 'Party Name', minWidth: 220 },
  { field: 'contractNumber', headerName: 'Contract Number', minWidth: 150 },
  { field: 'voucherAmount', headerName: 'Voucher Amount', minWidth: 150, sortable: true, align: 'right', headerAlign: 'right', formatter: money },
  { field: 'submittedBy', headerName: 'Submitted By', minWidth: 130, sortable: true },
  { field: 'submittedAt', headerName: 'Submitted At', minWidth: 180, sortable: true, formatter: displayDate },
  { field: 'status', headerName: 'Current Status', minWidth: 140 },
  {
    field: 'action',
    headerName: 'Action',
    minWidth: 110,
    align: 'center',
    headerAlign: 'center',
    renderCell: () => <span className="rounded bg-blue-800 px-3 py-1 text-[11px] font-black uppercase text-white">Open</span>,
  },
];

const filterFields = [
  { field: 'voucherType', label: 'Voucher Type' },
  { field: 'voucherDateFrom', label: 'Date From', type: 'date' },
  { field: 'voucherDateTo', label: 'Date To', type: 'date' },
  { field: 'minimumAmount', label: 'Min Amount', type: 'number' },
  { field: 'maximumAmount', label: 'Max Amount', type: 'number' },
  { field: 'submittedBy', label: 'Submitted By' },
];

export default function VoucherAuthorisation() {
  const navigate = useNavigate();

  const fetchPage = async ({ filters, keyword, page, pageSize, sortColumn, sortDirection }) => fetchAuthorisationQueue({
    keyword,
    voucherType: filters.voucherType,
    voucherDateFrom: filters.voucherDateFrom,
    voucherDateTo: filters.voucherDateTo,
    minimumAmount: filters.minimumAmount,
    maximumAmount: filters.maximumAmount,
    submittedBy: filters.submittedBy,
    page,
    size: pageSize,
    sortColumn,
    sortDirection,
  });

  const openVoucher = (voucher) => {
    const type = String(voucher.voucherType || 'RECEIPT').toLowerCase();
    const mode = String(voucher.transactionType || 'CASH').toLowerCase();
    const path = type === 'journal'
      ? '/accounts/transaction/edit/journal'
      : `/accounts/transaction/edit/${type}/${mode === 'bank' ? 'bank' : 'cash'}`;

    navigate(`${path}?voucherHeaderId=${voucher.voucherHeaderId}&authorisation=true`, {
      state: {
        authorisationMode: true,
        returnTo: '/accounts/transaction/authorisation',
        voucherHeaderId: voucher.voucherHeaderId,
      },
    });
  };

  return (
    <div className="h-full min-h-0 bg-gray-100 p-2 text-black" style={{ fontFamily: 'Calibri, "Segoe UI", sans-serif' }}>
      <ServerDataTable
        columns={columns}
        defaultFilters={{ voucherType: '', voucherDateFrom: '', voucherDateTo: '', minimumAmount: '', maximumAmount: '', submittedBy: '' }}
        defaultPageSize={25}
        defaultSortColumn="submittedAt"
        defaultSortDirection="desc"
        fetchPage={fetchPage}
        filterFields={filterFields}
        getRowId={(row) => row.voucherHeaderId}
        loadingLabel="Loading pending vouchers"
        onRowClick={openVoucher}
        onRowDoubleClick={openVoucher}
        searchPlaceholder="Search voucher, party, contract..."
        title="Voucher Authorisation Queue"
        transformResponse={(data) => ({ rows: data.content || [], totalElements: data.totalElements || 0 })}
      />
    </div>
  );
}
