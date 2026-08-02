import React from 'react';
import DataTable from '../../../components/common/DataTable';

const PartyCodeModify = () => {
  // Define columns
  const columns = [
    { 
      header: "Code", 
      key: "code",
      render: (row) => (
        <span className="font-black px-2 py-1 bg-blue-50 text-blue-700 rounded border border-blue-100 text-[11px]">
          {row.code}
        </span>
      )
    },
    { header: "Party Name", key: "name" },
    { header: "Category", key: "category" },
    { header: "City", key: "city" },
    { 
      header: "Status", 
      key: "status",
      render: (row) => (
        <span className={`text-[10px] font-bold px-2 py-0.5 rounded-full ${
          row.status === 'Active' ? 'bg-green-100 text-green-700' : 'bg-slate-100 text-slate-500'
        }`}>
          {row.status}
        </span>
      )
    }
  ];

  // Sample data (In future, this comes from an API)
  const data = [
    { code: 'P001', name: 'Dugar Finance Corp', category: 'Main', city: 'Chennai', status: 'Active' },
    { code: 'P002', name: 'Reliable Valuations', category: 'Agency', city: 'Madurai', status: 'Active' },
  ];

  return (
    <DataTable 
      title="Party Code" 
      subtitle="Modify" 
      columns={columns} 
      data={data}
      onAddClick={() => alert("Open Add Modal")}
      searchPlaceholder="Search by name, code or city..."
    />
  );
};

export default PartyCodeModify;