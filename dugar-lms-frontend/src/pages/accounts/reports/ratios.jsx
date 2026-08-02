import React from 'react';

const RatioDashboard = () => {
  const ratioGroups = [
    { label: "Return on Equity", code: "ROE", value: "18", status: "emerald" },
    { label: "Return on Assets", code: "ROA", value: "4", status: "emerald" },
    { label: "Cash Flow Coverage", code: "CASH FLOW", value: "95", status: "blue" },
    { label: "Net Interest Margin", code: "NIM", value: "7", status: "emerald" },
    { label: "Provision Coverage", code: "PCR", value: "60", status: "slate" },
    { label: "Debt to Equity", code: "DEBT EQTY", value: "3", status: "rose" },
    { label: "Capital Adequacy", code: "CAR", value: "21", status: "emerald" },
    { label: "Asset Liability", code: "ALM", value: "7", status: "blue" },
    { label: "Cost of Funds", code: "COST OF FUNDS", value: "11", status: "rose" },
    { label: "Net Profit Margin", code: "NPM", value: "7", status: "emerald" },
    { label: "EBIDTA Margin", code: "EBIDTA", value: "70", status: "emerald" },
    { label: "Leverage Ratio", code: "LEVERAGE", value: "5", status: "slate" },
  ];

  const calibri = { fontFamily: 'Calibri, Candara, Segoe UI, sans-serif' };

  return (
    <div className="min-h-screen bg-[#F4F7F9] p-6" style={calibri}>
      {/* HEADER */}
      <div className="max-w-[1200px] mx-auto mb-8 flex flex-col items-center text-center animate-in fade-in slide-in-from-top duration-700">
        <div className="flex items-center gap-3 mb-2 opacity-60">
          
          
          
        </div>
        <h1 className="text-3xl font-light text-slate-700 tracking-tighter">
          Financial <span className="font-bold text-[#1e3a8a]">Performance Ratios</span>
        </h1>
      </div>

      {/* GRID */}
      <div className="max-w-[1200px] mx-auto grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
        {ratioGroups.map((ratio, index) => (
          <div 
            key={index} 
            className={`
              group relative bg-white border border-slate-200 rounded p-5 
              flex flex-col items-center text-center shadow-sm
              transition-all duration-300 ease-out
              hover:-translate-y-1.5 hover:shadow-xl hover:border-[#1e3a8a]
              cursor-default animate-in fade-in zoom-in-95 duration-500
            `}
            style={{ animationDelay: `${index * 50}ms` }}
          >
            {/* BRAND BLUE CODE TAG */}
            <div className="mb-4 transition-transform duration-300 group-hover:scale-110">
              <span className="bg-[#1e3a8a] text-white text-[24px] font-black px-4 py-1 rounded-sm tracking-[0.15em] uppercase shadow-sm">
                {ratio.code}
              </span>
            </div>

            {/* ITEM TITLE */}
            <h3 className="text-[14px] font-bold text-slate-800 uppercase tracking-tight leading-tight mb-3 transition-colors group-hover:text-[#1e3a8a]">
              {ratio.label}
            </h3>

            {/* VALUE & PERCENTAGE */}
            <div className={`
              mt-auto flex items-baseline gap-1 transition-all duration-500
              group-hover:scale-105
              ${ratio.status === 'emerald' ? 'text-emerald-700' : 
                ratio.status === 'rose' ? 'text-rose-700' : 
                ratio.status === 'blue' ? 'text-blue-700' : 'text-slate-900'}
            `}>
              <span className="text-4xl font-black tracking-tighter">
                {ratio.value}
              </span>
              {/* INCREASED FONT SIZE FOR % */}
              <span className="text-3xl font-bold opacity-30">%</span>
            </div>

            {/* EXPANDING INDICATOR LINE */}
            <div className={`
              absolute bottom-0 left-1/2 -translate-x-1/2 h-[3px] w-0 
              transition-all duration-500 group-hover:w-full
              ${ratio.status === 'emerald' ? 'bg-emerald-500' : 
                ratio.status === 'rose' ? 'bg-rose-500' : 
                ratio.status === 'blue' ? 'bg-blue-600' : 'bg-slate-400'}
            `} 
            />
          </div>
        ))}
      </div>

     
    </div>
  );
};

export default RatioDashboard;