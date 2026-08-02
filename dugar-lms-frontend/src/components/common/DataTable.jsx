import React, { useState } from 'react';
import { Search, Filter, Download, Plus, ChevronLeft, ChevronRight, MoreHorizontal } from 'lucide-react';

const DataTable = ({ 
  title, 
  subtitle, 
  columns, 
  data, 
  onAddClick, 
  searchPlaceholder = "Search records..." 
}) => {
  const [searchTerm, setSearchTerm] = useState("");

  // Generic filtering logic
  const filteredData = data.filter(item => 
    Object.values(item).some(val => 
      String(val).toLowerCase().includes(searchTerm.toLowerCase())
    )
  );

  return (
    <div className="flex flex-col gap-6">
      {/* 1. HEADER SECTION */}
      <div className="flex flex-col md:flex-row md:items-center justify-between gap-4">
        <div>
          <h1 className="text-2xl font-black text-slate-900 tracking-tight">
            {title} <span className="text-slate-400 font-light">{subtitle}</span>
          </h1>
        </div>
        <div className="flex items-center gap-2">
          <button className="p-2 text-slate-500 hover:bg-slate-100 rounded-lg border border-slate-200">
            <Download size={18} />
          </button>
          <button 
            onClick={onAddClick}
            className="flex items-center gap-2 bg-[#0052CC] text-white px-5 py-2.5 rounded-lg text-[11px] font-black uppercase tracking-widest hover:bg-blue-700 shadow-lg shadow-blue-100 transition-all active:scale-95"
          >
            <Plus size={18} strokeWidth={3} /> Add New
          </button>
        </div>
      </div>

      {/* 2. MODERN SEARCH & FILTER BAR */}
      <div className="flex flex-col md:flex-row gap-3 bg-white p-3 rounded-xl border border-slate-200 shadow-sm">
        <div className="flex-1 relative">
          <Search className="absolute left-3 top-1/2 -translate-y-1/2 text-slate-400" size={16} />
          <input 
            type="text" 
            placeholder={searchPlaceholder}
            value={searchTerm}
            onChange={(e) => setSearchTerm(e.target.value)}
            className="w-full pl-10 pr-4 py-2 bg-slate-50 border border-slate-200 rounded-lg text-sm focus:ring-2 focus:ring-blue-100 focus:border-blue-600 outline-none transition-all"
          />
        </div>
        <button className="flex items-center justify-center gap-2 px-4 py-2 bg-white border border-slate-200 rounded-lg text-[11px] font-black uppercase text-slate-600 hover:bg-slate-50">
          <Filter size={14} /> Filters
        </button>
      </div>

      {/* 3. THE GRID (TABLE) */}
      <div className="bg-white rounded-xl border border-slate-200 shadow-sm overflow-hidden">
        <div className="overflow-x-auto">
          <table className="w-full text-left border-collapse">
            <thead>
              <tr className="bg-slate-50/80 border-b border-slate-200">
                {columns.map((col, index) => (
                  <th key={index} className="p-4 text-[10px] font-black text-slate-500 uppercase tracking-widest">
                    {col.header}
                  </th>
                ))}
                <th className="p-4 text-[10px] font-black text-slate-500 uppercase tracking-widest text-right">Actions</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100 text-sm">
              {filteredData.map((row, rowIndex) => (
                <tr key={rowIndex} className="hover:bg-blue-50/30 transition-colors group">
                  {columns.map((col, colIndex) => (
                    <td key={colIndex} className="p-4 text-slate-700 font-medium">
                      {/* If a custom render function is provided, use it, otherwise show raw value */}
                      {col.render ? col.render(row) : row[col.key]}
                    </td>
                  ))}
                  <td className="p-4 text-right">
                    <button className="p-2 text-slate-400 hover:text-blue-600 opacity-0 group-hover:opacity-100 transition-all">
                      <MoreHorizontal size={16} />
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>
    </div>
  );
};

export default DataTable;