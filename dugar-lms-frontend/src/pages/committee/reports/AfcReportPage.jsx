import { useState } from 'react';
import AfcReportFilters from '../../../components/afc/AfcReportFilters';
import AfcReportGrid from '../../../components/afc/AfcReportGrid';
import AfcReportHeader from '../../../components/afc/AfcReportHeader';
import AfcReportPrintView from '../../../components/afc/AfcReportPrintView';
import { fetchAfcReport, fetchAfcReportPrint } from '../../../services/afcReportService';

const today = new Date().toISOString().slice(0, 10);
const defaultFilters = { loanNumber: '', asOnDate: today, areaCode: '' };

function errorMessage(error, fallback) {
  return error?.response?.data?.message || error?.message || fallback;
}

function excelCell(value) {
  return String(value ?? '')
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;');
}

export default function AfcReportPage() {
  const [filters, setFilters] = useState(defaultFilters);
  const [appliedFilters, setAppliedFilters] = useState(null);
  const [data, setData] = useState(null);
  const [printData, setPrintData] = useState(null);
  const [loading, setLoading] = useState(false);
  const [message, setMessage] = useState('');

  const generate = async () => {
    if (!filters.loanNumber.trim()) {
      setMessage('Loan Number is required.');
      return;
    }
    if (!filters.asOnDate) {
      setMessage('As On Date is required.');
      return;
    }
    setLoading(true);
    setMessage('');
    try {
      const response = await fetchAfcReport(filters);
      setAppliedFilters(filters);
      setData(response);
      setPrintData(null);
    } catch (error) {
      setData(null);
      setMessage(errorMessage(error, 'Unable to load AFC Report.'));
    } finally {
      setLoading(false);
    }
  };

  const reset = () => {
    setFilters(defaultFilters);
    setAppliedFilters(null);
    setData(null);
    setPrintData(null);
    setMessage('');
  };

  const loadPrint = async () => {
    const sourceFilters = appliedFilters || filters;
    if (!sourceFilters.loanNumber.trim() || !sourceFilters.asOnDate) {
      setMessage('Generate AFC Report before printing.');
      return null;
    }
    setLoading(true);
    setMessage('');
    try {
      const response = await fetchAfcReportPrint(sourceFilters);
      setPrintData(response);
      return response;
    } catch (error) {
      setMessage(errorMessage(error, 'Unable to load AFC print data.'));
      return null;
    } finally {
      setLoading(false);
    }
  };

  const print = async () => {
    const response = printData || await loadPrint();
    if (!response) return;
    window.setTimeout(() => window.print(), 150);
  };

  const exportExcel = async () => {
    const response = data || await loadPrint();
    if (!response) return;
    const header = response.header || {};
    const rows = response.rows || [];
    const title = `AFC Report as on ${header.asOnDate || filters.asOnDate}`;
    const html = `
      <html>
        <head><meta charset="utf-8" /></head>
        <body>
          <table border="1">
            <tr><th colspan="10">${excelCell(title)}</th></tr>
            <tr>
              <td colspan="2">Loan Number</td><td colspan="3">${excelCell(header.loanNumber)}</td>
              <td colspan="2">Customer Name</td><td colspan="3">${excelCell(header.customerName)}</td>
            </tr>
            <tr>
              <td colspan="2">Product Type</td><td colspan="3">${excelCell(header.productType)}</td>
              <td colspan="2">Contract Value</td><td colspan="3">${excelCell(header.contractValue)}</td>
            </tr>
            <tr>
              <th>Sl No</th>
              <th>Due Date</th>
              <th>When Paid</th>
              <th>Due Amount</th>
              <th>EMI Amount Paid</th>
              <th>Receipt Number</th>
              <th>Delay Days</th>
              <th>AFC Amount</th>
              <th>Contract Balance</th>
              <th>Current Account Balance</th>
            </tr>
            ${rows.map((row) => `
              <tr>
                <td>${excelCell(row.serialNumber)}</td>
                <td>${excelCell(row.dueDate)}</td>
                <td>${excelCell(row.whenPaid)}</td>
                <td style="text-align:right">${excelCell(row.dueAmount)}</td>
                <td style="text-align:right">${excelCell(row.emiAmountPaid)}</td>
                <td>${excelCell(row.receiptNumber)}</td>
                <td style="text-align:right">${excelCell(row.delayDays)}</td>
                <td style="text-align:right">${excelCell(row.afcAmount)}</td>
                <td style="text-align:right">${excelCell(row.contractBalance)}</td>
                <td style="text-align:right">${excelCell(row.currentAccountBalance)}</td>
              </tr>
            `).join('')}
          </table>
        </body>
      </html>
    `;
    const blob = new Blob([html], { type: 'application/vnd.ms-excel;charset=utf-8;' });
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.download = `afc-report-${header.loanNumber || filters.loanNumber}-${header.asOnDate || filters.asOnDate}.xls`;
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
    URL.revokeObjectURL(url);
  };

  return (
    <div className="flex h-full min-h-0 flex-col bg-white font-['Segoe_UI',Arial,sans-serif] text-[12px] text-black">
      <style>{`
        @page { size: A4 landscape; margin: 8mm; }
        .afc-print-root { display: none; }
        @media print {
          html, body { margin: 0 !important; padding: 0 !important; background: #fff !important; }
          body * { visibility: hidden !important; }
          .no-print { display: none !important; }
          .afc-print-root, .afc-print-root * { visibility: visible !important; }
          .afc-print-root { display: block !important; position: absolute; left: 0; top: 0; width: 100%; color: #000; font-family: "Segoe UI", Arial, sans-serif; }
          .afc-print-header h1 { margin: 0; font-size: 13px; text-align: center; }
          .afc-print-header h2 { margin: 1mm 0 2mm; font-size: 11px; text-align: center; }
          .afc-print-meta { display: grid; grid-template-columns: repeat(3, 1fr); gap: 2mm; font-size: 8px; margin-bottom: 2mm; }
          .afc-print-table { width: 100%; border-collapse: collapse; font-size: 8px; color: #000; }
          .afc-print-table th, .afc-print-table td { border: none; border-bottom: 0.1mm solid #bbb; padding: 1.2mm 1mm; vertical-align: top; }
          .afc-print-table thead { display: table-header-group; }
          .afc-print-table th { font-weight: 700; border-bottom: 0.3mm solid #000; }
          .afc-print-table tbody tr { break-inside: avoid; page-break-inside: avoid; }
          .right { text-align: right; }
          .center { text-align: center; }
          .left { text-align: left; }
        }
      `}</style>
      <div className="no-print border-b border-black/20 bg-[#f8fafc] px-2 py-1">
        <div className="text-[16px] font-bold uppercase text-[#0052CC]">AFC Report</div>
      </div>
      <AfcReportFilters filters={filters} loading={loading} onChange={setFilters} onExportExcel={exportExcel} onGenerate={generate} onPrint={print} onReset={reset} />
      {message && <div className="no-print border-b border-red-200 bg-red-50 px-2 py-1 font-bold text-red-700">{message}</div>}
      {data?.warnings?.length > 0 && <div className="no-print border-b border-amber-200 bg-amber-50 px-2 py-1 font-bold text-amber-800">{data.warnings.join(' | ')}</div>}
      <AfcReportHeader header={data?.header} />
      <AfcReportGrid rows={data?.rows || []} />
      {printData && <AfcReportPrintView data={printData} />}
    </div>
  );
}
