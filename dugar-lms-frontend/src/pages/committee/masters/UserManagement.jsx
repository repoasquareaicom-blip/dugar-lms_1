import React, { useEffect, useMemo, useRef, useState } from 'react';
import { CheckCircle2, Edit3, FilePlus2, Loader2, Save, TriangleAlert, X } from 'lucide-react';
import ServerDataTable from '../../../components/common/ServerDataTable';
import { fetchContractAreaMasterOptions } from '../../../services/contractsService';
import { fetchPermissionMenus } from '../../../services/roleMenuPermissionService';
import { createUser, fetchActiveUserContracts, fetchUsers, updateUser } from '../../../services/userManagementService';

const PAGE_SIZE_OPTIONS = [25, 50, 100, 250];
const DEFAULT_FILTERS = {};
const DEFAULT_HIDDEN_COLUMNS = {};
const emptyForm = {
  fullName: '',
  username: '',
  password: '',
  userType: '',
  contractNumber: '',
  departmentMenuId: '',
  areaCodes: [],
  roleIds: [],
  menuIds: [],
  isActive: true,
};

function isDashboardMenu(menu) {
  const text = `${menu?.menuName || ''} ${menu?.menuCode || ''}`.toLowerCase();
  return text.includes('dashboard');
}

function hasActionablePath(menu) {
  const path = String(menu?.urlPath || menu?.path || '').trim();
  return path !== '' && path !== '#';
}

function isSelectableMenu(menu) {
  const type = String(menu?.menuType || menu?.menu_type || '').toUpperCase();
  return hasActionablePath(menu) || type === 'PAGE';
}

function selectableMenuIds(menu, ids = []) {
  if (isSelectableMenu(menu)) {
    ids.push(menu.menuId);
  }
  (menu.children || []).forEach((child) => selectableMenuIds(child, ids));
  return ids;
}

function findDepartmentForMenuIds(menus, menuIds) {
  const selected = new Set(menuIds || []);
  if (selected.size === 0) return '';

  const departments = (menus || []).filter((menu) => !isDashboardMenu(menu));
  return departments.find((department) => selectableMenuIds(department).some((menuId) => selected.has(menuId)))?.menuId || '';
}

function departmentMenuIds(menus, departmentId) {
  const department = (menus || []).find((menu) => String(menu.menuId) === String(departmentId));
  return department ? selectableMenuIds(department) : [];
}

function transformUsersResponse(data) {
  const rows = Array.isArray(data?.content) ? data.content : [];
  return {
    rows,
    totalElements: Number(data?.totalElements || rows.length),
  };
}

function saveErrorMessage(error) {
  return error?.response?.data?.message || error?.response?.data?.error || error?.message || 'Unable to save user.';
}

function display(value) {
  if (value === null || value === undefined || value === '') return '-';
  return String(value);
}

const UserManagement = () => {
  const [editing, setEditing] = useState(null);
  const [form, setForm] = useState(emptyForm);
  const [permissionMenus, setPermissionMenus] = useState([]);
  const [selectedDepartmentId, setSelectedDepartmentId] = useState('');
  const [pendingDepartmentId, setPendingDepartmentId] = useState('');
  const [areaOptions, setAreaOptions] = useState([]);
  const [areaSearch, setAreaSearch] = useState('');
  const [contractOptions, setContractOptions] = useState([]);
  const [contractSearch, setContractSearch] = useState('');
  const [notice, setNotice] = useState(null);
  const [noticeVisible, setNoticeVisible] = useState(false);
  const [saving, setSaving] = useState(false);
  const [editLoading, setEditLoading] = useState(false);
  const [gridVersion, setGridVersion] = useState(0);

  const reloadGrid = () => setGridVersion((value) => value + 1);

  useEffect(() => {
    let active = true;
    fetchPermissionMenus()
      .then((data) => {
        if (!active) return;
        const menus = Array.isArray(data) ? data : [];
        setPermissionMenus(menus);
        const firstDepartment = menus.find((menu) => !isDashboardMenu(menu));
        if (firstDepartment?.menuId) setSelectedDepartmentId(String(firstDepartment.menuId));
      })
      .catch(() => {
        if (active) showNotice('Unable to load menu permissions.', 'error');
      });

    return () => {
      active = false;
    };
  }, []);

  useEffect(() => {
    if (!notice) return undefined;

    setNoticeVisible(true);
    const hideTimer = window.setTimeout(() => setNoticeVisible(false), 3600);
    const clearTimer = window.setTimeout(() => setNotice(null), 4000);

    return () => {
      window.clearTimeout(hideTimer);
      window.clearTimeout(clearTimer);
    };
  }, [notice]);

  const showNotice = (text, type = 'success') => {
    setNotice({ text, type });
  };

  const openAdd = () => {
    setEditing({});
    setForm(emptyForm);
    setAreaSearch('');
    setContractSearch('');
    setNotice(null);
  };

  const openEdit = (record) => {
    setEditing(record);
    setForm({
      fullName: record.fullName || '',
      username: record.username || '',
      password: '',
      userType: record.userType || '',
      contractNumber: record.contractNumber || '',
      departmentMenuId: record.departmentMenuId ? String(record.departmentMenuId) : '',
      areaCodes: Array.isArray(record.areaCodes) ? record.areaCodes : [],
      roleIds: Array.isArray(record.roleIds) ? record.roleIds : [],
      menuIds: Array.isArray(record.menuIds) ? record.menuIds : [],
      isActive: record.isActive !== false,
    });
    setAreaSearch(Array.isArray(record.areaCodes) ? record.areaCodes.join(', ') : '');
    setContractSearch(record.contractNumber || '');
    const detectedDepartmentId = record.departmentMenuId || findDepartmentForMenuIds(permissionMenus, record.menuIds);
    if (detectedDepartmentId) setSelectedDepartmentId(String(detectedDepartmentId));
    setNotice(null);
  };

  const beginEdit = (record) => {
    setEditLoading(true);
    window.setTimeout(() => {
      openEdit(record);
      setEditLoading(false);
    }, 180);
  };

  const closeModal = () => {
    setEditing(null);
    setForm(emptyForm);
    setPendingDepartmentId('');
    setAreaSearch('');
    setContractSearch('');
  };

  const setField = (field, value) => {
    setForm((current) => ({ ...current, [field]: value }));
  };

  useEffect(() => {
    if (form.userType !== 'CUSTOMER') {
      setContractOptions([]);
      return undefined;
    }

    let active = true;
    const timeoutId = window.setTimeout(() => {
      fetchActiveUserContracts(contractSearch)
        .then((data) => {
          if (active) setContractOptions(Array.isArray(data) ? data : []);
        })
        .catch(() => {
          if (active) setContractOptions([]);
        });
    }, 250);

    return () => {
      active = false;
      window.clearTimeout(timeoutId);
    };
  }, [contractSearch, form.userType]);

  useEffect(() => {
    if (form.userType !== 'BRANCH' && form.userType !== 'STATE') {
      setAreaOptions([]);
      return undefined;
    }

    let active = true;
    const timeoutId = window.setTimeout(() => {
      fetchContractAreaMasterOptions({ keyword: areaSearch, limit: 50 })
        .then((data) => {
          if (active) setAreaOptions(Array.isArray(data) ? data : []);
        })
        .catch(() => {
          if (active) setAreaOptions([]);
        });
    }, 250);

    return () => {
      active = false;
      window.clearTimeout(timeoutId);
    };
  }, [areaSearch, form.userType]);

  useEffect(() => {
    if (editing === null || form.userType !== 'USER' || selectedDepartmentId || permissionMenus.length === 0) {
      return;
    }

    const detectedDepartmentId = form.departmentMenuId || findDepartmentForMenuIds(permissionMenus, form.menuIds);
    const firstDepartmentId = permissionMenus.find((menu) => !isDashboardMenu(menu))?.menuId;
    if (detectedDepartmentId || firstDepartmentId) {
      setSelectedDepartmentId(String(detectedDepartmentId || firstDepartmentId));
    }
  }, [editing, form.departmentMenuId, form.menuIds, form.userType, permissionMenus, selectedDepartmentId]);

  const toggleMenu = (menu, checked) => {
    const ids = selectableMenuIds(menu);
    if (ids.length === 0) return;

    setForm((current) => {
      const menuIds = new Set(current.menuIds || []);
      if (checked) {
        ids.forEach((menuId) => menuIds.add(menuId));
      } else {
        ids.forEach((menuId) => menuIds.delete(menuId));
      }
      return { ...current, menuIds: Array.from(menuIds) };
    });
  };

  const changeDepartment = (departmentId) => {
    if (String(departmentId) === String(selectedDepartmentId)) return;

    if ((form.menuIds || []).length > 0) {
      setPendingDepartmentId(String(departmentId));
      return;
    }

    setSelectedDepartmentId(String(departmentId));
    setField('departmentMenuId', String(departmentId));
  };

  const cancelDepartmentChange = () => {
    setPendingDepartmentId('');
  };

  const continueDepartmentChange = () => {
    const nextDepartmentId = pendingDepartmentId;
    setPendingDepartmentId('');
    setSelectedDepartmentId(nextDepartmentId);
    setForm((current) => ({
      ...current,
      departmentMenuId: nextDepartmentId,
      menuIds: [],
    }));
  };

  const toggleArea = (areaCode, checked) => {
    setForm((current) => {
      if (current.userType === 'BRANCH') {
        return { ...current, areaCodes: checked ? [areaCode] : [] };
      }

      const areaCodes = new Set(current.areaCodes || []);
      if (checked) {
        areaCodes.add(areaCode);
      } else {
        areaCodes.delete(areaCode);
      }
      return { ...current, areaCodes: Array.from(areaCodes) };
    });
  };

  const save = async (event) => {
    event.preventDefault();
    if (saving) return;

    const payload = {
      fullName: form.fullName.trim(),
      username: form.username.trim(),
      password: form.password,
      userType: form.userType,
      contractNumber: form.userType === 'CUSTOMER' ? form.contractNumber : '',
      departmentMenuId: form.userType === 'USER' ? Number(selectedDepartmentId || form.departmentMenuId || 0) || null : null,
      areaCodes: form.userType === 'BRANCH' || form.userType === 'STATE' ? form.areaCodes : [],
      roleIds: form.userType === 'USER' ? form.roleIds : [],
      menuIds: form.userType === 'USER'
        ? (form.menuIds || []).filter((menuId) => departmentMenuIds(permissionMenus, selectedDepartmentId).includes(menuId))
        : [],
      isActive: form.isActive,
    };

    try {
      setSaving(true);
      if (editing?.userId) {
        await updateUser(editing.userId, payload);
        showNotice('User updated successfully.');
      } else {
        await createUser(payload);
        showNotice('User created successfully.');
      }
      closeModal();
      reloadGrid();
    } catch (error) {
      showNotice(saveErrorMessage(error), 'error');
    } finally {
      setSaving(false);
    }
  };

  const filterFields = useMemo(() => [], []);

  const columns = useMemo(
    () => [
      { field: 'serialNumber', headerName: 'S.No', minWidth: 70, align: 'center', headerAlign: 'center' },
      { field: 'fullName', headerName: 'User Name', minWidth: 220, sortable: true },
      { field: 'username', headerName: 'Login ID', minWidth: 160, sortable: true },
      { field: 'userType', headerName: 'User Type', minWidth: 120, sortable: true, formatter: (value) => display(value).toUpperCase() },
      { field: 'areaCodes', headerName: 'Areas', minWidth: 180, formatter: (value) => (Array.isArray(value) && value.length ? value.join(', ') : '-') },
      { field: 'contractNumber', headerName: 'Contract No', minWidth: 150, formatter: display },
      { field: 'roleNames', headerName: 'Roles', minWidth: 260, formatter: (value) => (Array.isArray(value) && value.length ? value.join(', ') : '-') },
      { field: 'isActive', headerName: 'Status', minWidth: 110, align: 'center', headerAlign: 'center', sortable: true, formatter: (value) => (value === false ? 'Inactive' : 'Active') },
    ],
    [],
  );

  const contextMenuItems = (row) => [
    {
      label: 'Edit User',
      icon: <Edit3 size={14} />,
      onClick: () => beginEdit(row),
    },
  ];

  return (
    <div className="relative h-full min-h-0 bg-white" style={{ fontFamily: 'Calibri, sans-serif' }}>
      {notice && (
        <div className={`fixed inset-0 z-[300] grid place-items-center bg-black/35 p-4 backdrop-blur-[1px] transition-opacity duration-300 ease-out ${noticeVisible ? 'opacity-100' : 'pointer-events-none opacity-0'}`}>
          <div className={`w-full max-w-md transform overflow-hidden rounded-lg border-2 bg-white shadow-2xl transition-all duration-300 ease-out ${noticeVisible ? 'scale-100 translate-y-0' : 'scale-95 translate-y-2'} ${notice.type === 'error' ? 'border-red-200' : 'border-emerald-200'}`}>
            <div className={`flex items-center justify-between border-b px-4 py-3 ${notice.type === 'error' ? 'border-red-100 bg-red-50 text-red-900' : 'border-emerald-100 bg-emerald-50 text-emerald-900'}`}>
              <div className="flex items-center gap-3">
                <div className={`grid h-9 w-9 place-items-center rounded-full bg-white ${notice.type === 'error' ? 'text-red-700' : 'text-emerald-700'}`}>
                  {notice.type === 'error' ? <TriangleAlert size={19} /> : <CheckCircle2 size={19} />}
                </div>
                <div className="text-[13px] font-black uppercase tracking-widest">
                  {notice.type === 'error' ? 'Save Failed' : 'Saved'}
                </div>
              </div>
              <button type="button" onClick={() => setNotice(null)} className="grid h-8 w-8 place-items-center rounded hover:bg-white/70">
                <X size={16} />
              </button>
            </div>
            <div className="px-5 py-5 text-center text-[15px] font-bold leading-relaxed text-slate-800">
              {notice.text}
            </div>
            <div className="flex justify-center gap-2 border-t border-slate-200 bg-slate-50 px-5 py-3">
              <button type="button" onClick={() => setNotice(null)} className="rounded-sm border border-slate-300 bg-white px-5 py-2 text-[13px] font-black uppercase text-slate-700 hover:bg-slate-100">
                Close
              </button>
              <button type="button" onClick={() => setNotice(null)} className={`rounded-sm px-6 py-2 text-[13px] font-black uppercase text-white ${notice.type === 'error' ? 'bg-red-700 hover:bg-red-800' : 'bg-emerald-700 hover:bg-emerald-800'}`}>
                OK
              </button>
            </div>
          </div>
        </div>
      )}

      {editLoading && (
        <div className="fixed inset-0 z-[250] grid place-items-center bg-white/40 backdrop-blur-[1px]">
          <div className="flex items-center gap-3 rounded-lg border-2 border-blue-100 bg-white px-5 py-4 text-[#0052CC] shadow-2xl">
            <Loader2 size={20} className="animate-spin" />
            <span className="text-[12px] font-black uppercase tracking-widest">Opening user</span>
          </div>
        </div>
      )}

      {pendingDepartmentId && (
        <div className="fixed inset-0 z-[320] grid place-items-center bg-black/45 p-4 backdrop-blur-[1px]">
          <div className="w-full max-w-md overflow-hidden rounded-sm border-2 border-black/30 bg-white shadow-2xl">
            <div className="border-b border-black/20 bg-slate-50 px-4 py-3">
              <h3 className="text-[14px] font-black uppercase text-slate-950">Change Department</h3>
            </div>
            <div className="px-5 py-5 text-[14px] font-bold leading-relaxed text-slate-800">
              Changing the department will discard all menu permissions selected for the current department. Do you want to continue?
            </div>
            <div className="flex justify-end gap-2 border-t border-black/20 bg-slate-50 px-4 py-3">
              <button type="button" onClick={cancelDepartmentChange} className="rounded-sm border border-slate-300 bg-white px-4 py-2 text-[12px] font-black uppercase text-slate-700 hover:bg-slate-100">
                Cancel
              </button>
              <button type="button" onClick={continueDepartmentChange} className="rounded-sm bg-blue-800 px-5 py-2 text-[12px] font-black uppercase text-white hover:bg-blue-700">
                Continue
              </button>
            </div>
          </div>
        </div>
      )}

      <ServerDataTable
        key={gridVersion}
        columns={columns}
        defaultFilters={DEFAULT_FILTERS}
        defaultHiddenColumns={DEFAULT_HIDDEN_COLUMNS}
        defaultPageSize={25}
        defaultSortColumn="username"
        defaultSortDirection="asc"
        fetchPage={fetchUsers}
        filterFields={filterFields}
        getContextMenuItems={contextMenuItems}
        getRowId={(row) => row.userId}
        loadingLabel="Loading users"
        pageSizeOptions={PAGE_SIZE_OPTIONS}
        searchPlaceholder="Quick search user name, login or type..."
        title="User Management"
        titleAction={(
          <button
            type="button"
            onClick={openAdd}
            className="inline-flex items-center gap-2 rounded-lg border-2 border-[#0052CC] bg-[#0052CC] px-3 py-1.5 text-[11px] font-black uppercase text-white shadow-sm transition-all hover:bg-blue-700"
          >
            <FilePlus2 size={14} strokeWidth={3} />
            Add User
          </button>
        )}
        transformResponse={transformUsersResponse}
        onRowDoubleClick={beginEdit}
      />

      {editing !== null && (
        <div className="fixed inset-0 z-[200] flex items-center justify-center bg-black/50 p-4">
          <form onSubmit={save} className="flex max-h-[92vh] w-full max-w-2xl flex-col overflow-hidden rounded-sm border-2 border-black/30 bg-white shadow-2xl">
            <div className="flex shrink-0 items-center justify-between border-b border-black/20 bg-slate-50 px-4 py-2.5">
              <h2 className="text-[18px] font-black uppercase text-slate-950">
                {editing?.userId ? 'Edit User' : 'Add User'}
              </h2>
              <button type="button" onClick={closeModal} disabled={saving} className="inline-flex h-8 w-8 items-center justify-center rounded-sm hover:bg-slate-200 disabled:cursor-not-allowed disabled:opacity-50">
                <X size={18} />
              </button>
            </div>

            <div className="grid min-h-0 grid-cols-1 gap-3 overflow-y-auto p-4 md:grid-cols-4">
              <Field label="User Name" value={form.fullName} onChange={(value) => setField('fullName', value)} required className="md:col-span-2" />
              <Field label="Login ID / Username" value={form.username} onChange={(value) => setField('username', value)} required className="md:col-span-2" />
              <Field label="Password" value={form.password} onChange={(value) => setField('password', value)} required={!editing?.userId} className="md:col-span-2" />
              <label className="flex flex-col gap-1 md:col-span-2">
                <span className="text-[11px] font-black uppercase tracking-widest text-slate-500">User Type</span>
                <select
                  value={form.userType}
                  onChange={(event) => {
                    const nextType = event.target.value;
                    setForm((current) => ({
                      ...current,
                      userType: nextType,
                      contractNumber: nextType === 'CUSTOMER' ? current.contractNumber : '',
                      departmentMenuId: nextType === 'USER' ? current.departmentMenuId : '',
                      areaCodes: nextType === 'BRANCH' || nextType === 'STATE' ? current.areaCodes : [],
                      roleIds: nextType === 'USER' ? current.roleIds : [],
                      menuIds: nextType === 'USER' ? current.menuIds : [],
                    }));
                    if (nextType !== 'CUSTOMER') setContractSearch('');
                    if (nextType !== 'BRANCH' && nextType !== 'STATE') setAreaSearch('');
                  }}
                  className="h-9 border border-slate-300 bg-white px-3 text-[14px] font-bold uppercase outline-none focus:border-blue-700"
                  required
                >
                  <option value="">Select User Type</option>
                  <option value="USER">User</option>
                  <option value="BRANCH">Branch</option>
                  <option value="STATE">State</option>
                  <option value="CUSTOMER">Customer</option>
                </select>
              </label>
              <label className="flex items-center gap-3 md:col-span-2 md:pt-6">
                <input
                  type="checkbox"
                  checked={form.isActive}
                  onChange={(event) => setField('isActive', event.target.checked)}
                  className="h-4 w-4"
                />
                <span className="text-[12px] font-black uppercase tracking-widest text-slate-700">Active</span>
              </label>

              {form.userType === 'CUSTOMER' && (
                <div className="md:col-span-4">
                  <div className="mb-2 text-[11px] font-black uppercase tracking-widest text-slate-500">Contract Number</div>
                  <input
                    value={contractSearch}
                    onChange={(event) => setContractSearch(event.target.value)}
                    placeholder="Search active contract or borrower..."
                    className="mb-2 h-9 w-full border border-slate-300 bg-white px-3 text-[14px] font-bold outline-none focus:border-blue-700"
                  />
                  <div className="max-h-52 overflow-y-auto rounded border border-slate-300 bg-slate-50 p-2">
                    {form.contractNumber && !contractOptions.some((option) => option.contractNumber === form.contractNumber) && (
                      <button
                        type="button"
                        onClick={() => setField('contractNumber', form.contractNumber)}
                        className="mb-2 flex w-full items-center justify-between rounded border border-blue-200 bg-blue-50 px-3 py-2 text-left text-[12px] font-black uppercase text-blue-900"
                      >
                        <span>{form.contractNumber}</span>
                        <span>Selected</span>
                      </button>
                    )}
                    {contractOptions.map((option) => {
                      const selected = form.contractNumber === option.contractNumber;
                      return (
                        <button
                          key={option.contractNumber}
                          type="button"
                          onClick={() => {
                            setField('contractNumber', option.contractNumber);
                            setContractSearch(option.label || option.contractNumber);
                          }}
                          className={`mb-2 flex w-full items-center justify-between rounded border px-3 py-2 text-left text-[12px] font-black uppercase ${selected ? 'border-[#0052CC] bg-blue-50 text-[#0052CC]' : 'border-slate-200 bg-white text-slate-800 hover:bg-blue-50'}`}
                        >
                          <span>{option.label}</span>
                          {selected && <span>Selected</span>}
                        </button>
                      );
                    })}
                    {contractOptions.length === 0 && (
                      <div className="px-3 py-3 text-[12px] font-black uppercase text-slate-500">No active contracts found</div>
                    )}
                  </div>
                </div>
              )}

              {(form.userType === 'BRANCH' || form.userType === 'STATE') && (
                <div className="md:col-span-4">
                  <div className="mb-2 text-[11px] font-black uppercase tracking-widest text-slate-500">
                    {form.userType === 'BRANCH' ? 'Area' : 'Areas'}
                  </div>
                  <input
                    value={areaSearch}
                    onChange={(event) => setAreaSearch(event.target.value)}
                    placeholder="Search active area..."
                    className="mb-2 h-9 w-full border border-slate-300 bg-white px-3 text-[14px] font-bold outline-none focus:border-blue-700"
                  />
                  <div className="grid max-h-52 grid-cols-1 gap-2 overflow-y-auto rounded border border-slate-300 bg-slate-50 p-3 md:grid-cols-2">
                    {form.areaCodes.length > 0 && form.areaCodes
                      .filter((areaCode) => !areaOptions.some((option) => option.areaCode === areaCode))
                      .map((areaCode) => (
                        <label key={areaCode} className="flex items-center gap-3 rounded border border-blue-200 bg-blue-50 px-3 py-2 text-[12px] font-black uppercase text-blue-900">
                          <input
                            type={form.userType === 'BRANCH' ? 'radio' : 'checkbox'}
                            checked
                            onChange={(event) => toggleArea(areaCode, event.target.checked)}
                            className="h-4 w-4"
                          />
                          <span>{areaCode}</span>
                        </label>
                      ))}
                    {areaOptions.map((area) => {
                      const selected = (form.areaCodes || []).includes(area.areaCode);
                      return (
                        <label key={area.areaCode} className={`flex items-center gap-3 rounded border px-3 py-2 text-[12px] font-black uppercase ${selected ? 'border-[#0052CC] bg-blue-50 text-[#0052CC]' : 'border-slate-200 bg-white text-slate-800 hover:bg-blue-50'}`}>
                          <input
                            type={form.userType === 'BRANCH' ? 'radio' : 'checkbox'}
                            checked={selected}
                            onChange={(event) => toggleArea(area.areaCode, event.target.checked)}
                            className="h-4 w-4"
                          />
                          <span>{area.areaCode}{area.areaName ? ` - ${area.areaName}` : ''}</span>
                        </label>
                      );
                    })}
                    {areaOptions.length === 0 && form.areaCodes.length === 0 && (
                      <div className="text-[12px] font-black uppercase text-slate-500">No active areas found</div>
                    )}
                  </div>
                </div>
              )}

              {form.userType === 'USER' && (
                <UserMenuPermissions
                  departmentId={selectedDepartmentId}
                  menus={permissionMenus}
                  selectedMenuIds={form.menuIds || []}
                  saving={saving}
                  onDepartmentChange={changeDepartment}
                  onToggle={toggleMenu}
                />
              )}
            </div>

            <div className="flex shrink-0 justify-end gap-2 border-t border-black/20 bg-slate-50 px-4 py-2.5">
              <button type="button" onClick={closeModal} disabled={saving} className="rounded-sm border border-slate-300 bg-white px-4 py-2 text-[13px] font-black uppercase text-slate-700 disabled:cursor-not-allowed disabled:opacity-50">Cancel</button>
              <button type="submit" disabled={saving} className="inline-flex min-w-24 items-center justify-center gap-2 rounded-sm bg-blue-800 px-5 py-2 text-[13px] font-black uppercase text-white disabled:cursor-wait disabled:bg-blue-500">
                {saving ? <Loader2 size={16} className="animate-spin" /> : <Save size={16} />}
                {saving ? 'Saving' : 'Save'}
              </button>
            </div>
          </form>
        </div>
      )}
    </div>
  );
};

const Field = ({ label, value, onChange, required = false, type = 'text', className = '' }) => (
  <label className={`flex flex-col gap-1 ${className}`}>
    <span className="text-[11px] font-black uppercase tracking-widest text-slate-500">{label}</span>
    <input
      type={type}
      value={value}
      required={required}
      onChange={(event) => onChange(event.target.value)}
      className="h-9 border border-slate-300 bg-white px-3 text-[14px] font-bold outline-none focus:border-blue-700"
    />
  </label>
);

const UserMenuPermissions = ({
  departmentId,
  menus,
  selectedMenuIds,
  saving,
  onDepartmentChange,
  onToggle,
}) => {
  const departments = useMemo(() => menus.filter((menu) => !isDashboardMenu(menu)), [menus]);
  const selectedDepartment = departments.find((menu) => String(menu.menuId) === String(departmentId)) || departments[0];

  useEffect(() => {
    if (!departmentId && selectedDepartment?.menuId && selectedMenuIds.length === 0) {
      onDepartmentChange(String(selectedDepartment.menuId));
    }
  }, [departmentId, onDepartmentChange, selectedDepartment, selectedMenuIds.length]);

  const selectedSet = useMemo(() => new Set(selectedMenuIds || []), [selectedMenuIds]);
  const groups = selectedDepartment?.children || [];
  const selectedCount = selectedDepartment
    ? selectableMenuIds(selectedDepartment).filter((menuId) => selectedSet.has(menuId)).length
    : 0;

  return (
    <div className="md:col-span-4">
      <div className="mb-2 flex flex-col gap-2 md:flex-row md:items-end md:justify-between">
        <label className="flex flex-1 flex-col gap-1">
          <span className="text-[11px] font-black uppercase tracking-widest text-slate-500">Department</span>
          <select
            value={selectedDepartment ? String(selectedDepartment.menuId) : ''}
            onChange={(event) => onDepartmentChange(event.target.value)}
            disabled={saving || departments.length === 0}
            className="h-9 border border-slate-300 bg-white px-3 text-[14px] font-bold uppercase outline-none focus:border-blue-700 disabled:cursor-wait disabled:bg-slate-100"
          >
            {departments.map((department) => (
              <option key={department.menuId} value={department.menuId}>
                {department.menuName}
              </option>
            ))}
          </select>
        </label>
        <div className="rounded border border-blue-100 bg-blue-50 px-3 py-2 text-[11px] font-black uppercase tracking-widest text-[#0052CC]">
          {selectedCount} menus selected
        </div>
      </div>

      <div className="rounded border border-slate-300 bg-slate-50 p-3">
        {departments.length === 0 && (
          <div className="text-[12px] font-black uppercase text-slate-500">No active departments found</div>
        )}

        {departments.length > 0 && groups.length === 0 && (
          <div className="text-[12px] font-black uppercase text-slate-500">No menus found for selected department</div>
        )}

        {groups.length > 0 && (
          <div className="grid grid-cols-1 gap-3 md:grid-cols-2 xl:grid-cols-3">
            {groups.map((group) => (
              <div key={group.menuId} className="min-w-0 border border-slate-200 bg-white p-3">
                <MenuPermissionNode
                  menu={group}
                  selectedSet={selectedSet}
                  saving={saving}
                  onToggle={onToggle}
                />
              </div>
            ))}
          </div>
        )}
      </div>
    </div>
  );
};

const MenuPermissionNode = ({ menu, selectedSet, saving, onToggle, level = 0 }) => {
  const checkboxRef = useRef(null);
  const ids = useMemo(() => selectableMenuIds(menu), [menu]);
  const checkedCount = ids.filter((id) => selectedSet.has(id)).length;
  const checked = ids.length > 0 && checkedCount === ids.length;
  const indeterminate = checkedCount > 0 && checkedCount < ids.length;
  const children = menu.children || [];

  useEffect(() => {
    if (checkboxRef.current) {
      checkboxRef.current.indeterminate = indeterminate;
    }
  }, [indeterminate]);

  return (
    <div className={level === 0 ? '' : 'mt-2'}>
      <label
        className={`flex min-w-0 items-center gap-2 text-[12px] font-black uppercase ${level === 0 ? 'text-slate-900' : 'text-slate-700'}`}
        style={{ paddingLeft: `${level * 16}px` }}
      >
        <input
          ref={checkboxRef}
          type="checkbox"
          checked={checked}
          disabled={saving || ids.length === 0}
          onChange={() => onToggle(menu, !checked)}
          className="h-4 w-4 shrink-0"
        />
        <span className="min-w-0 break-words">{menu.menuName}</span>
      </label>

      {children.map((child) => (
        <MenuPermissionNode
          key={child.menuId}
          menu={child}
          selectedSet={selectedSet}
          saving={saving}
          onToggle={onToggle}
          level={level + 1}
        />
      ))}
    </div>
  );
};

export default UserManagement;
