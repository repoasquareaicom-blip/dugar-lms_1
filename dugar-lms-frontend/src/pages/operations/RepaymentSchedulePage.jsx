import { useCallback, useRef, useState } from 'react';
import ContractGrid from '../Credit/transaction/contract/ContractGrid';
import {
  fetchNormalRepaymentSchedulePdf,
  fetchRepaymentSchedulePdf,
  openRepaymentSchedulePdf,
} from '../../services/repaymentScheduleService';

export default function RepaymentSchedulePage({ variant }) {
  const normal = variant === 'normal';
  const title = normal ? 'Repayment Schedule Normal' : 'Repayment Schedule With Interest';
  const fetchPdf = normal ? fetchNormalRepaymentSchedulePdf : fetchRepaymentSchedulePdf;
  const [error, setError] = useState('');
  const pending = useRef(new Set());

  const openSchedule = useCallback(async ({ contractId }) => {
    if (!contractId || pending.current.has(contractId)) return;

    pending.current.add(contractId);
    setError('');
    try {
      await openRepaymentSchedulePdf(contractId, fetchPdf);
    } catch (failure) {
      setError(failure.message || 'Unable to generate the repayment schedule.');
    } finally {
      pending.current.delete(contractId);
    }
  }, [fetchPdf]);

  return (
    <div className="flex h-full min-h-0 flex-col">
      {error && (
        <div role="alert" className="border-b border-red-200 bg-red-50 px-3 py-2 text-[12px] font-bold text-red-700">
          {error}
        </div>
      )}
      <div className="min-h-0 flex-1">
        <ContractGrid title={title} onOpenRepaymentSchedule={openSchedule} />
      </div>
    </div>
  );
}
