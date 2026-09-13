import React, { useEffect, useMemo, useState } from 'react';
import { CheckCircle2, ChevronRight, Loader2, Save, ShieldCheck, TriangleAlert, X } from 'lucide-react';
import { fetchRoles } from '../../../services/roleService';
import {
  fetchPermissionMenus,
  fetchRoleMenuPermissions,
  saveRoleMenuPermissions,
} from '../../../services/roleMenuPermissionService';

function flattenMenus(menus = [], parentId = null, level = 0, rows = []) {
  menus.forEach((menu) => {
    rows.push({ ...menu, parentId, level });
    flattenMenus(menu.children || [], menu.menuId, level + 1, rows);
  });
  return rows;
}

function collectDescendants(menu, ids = []) {
  ids.push(menu.menuId);
  (menu.children || []).forEach((child) => collectDescendants(child, ids));
  return ids;
}

function display(value) {
  if (value === null || value === undefined || value === '') return '-';
  return String(value);
}

const RoleMenuPermissions = () => {
  const [roles, setRoles] = useState([]);
  const [menus, setMenus] = useState([]);
  const [selectedRoleId, setSelectedRoleId] = useState('');
  const [selectedMenuIds, setSelectedMenuIds] = useState(() => new Set());
  const [loading, setLoading] = useState(true);
  const [permissionLoading, setPermissionLoading] = useState(false);
  const [saving, setSaving] = useState(false);
  const [notice, setNotice] = useState(null);
  const [noticeVisible, setNoticeVisible] = useState(false);

  useEffect(() => {
    let active = true;
    async function loadInitialData() {
      try {
        setLoading(true);
        const [rolesData, menusData] = await Promise.all([
          fetchRoles({ filters: { isActive: true }, pageSize: 200, sortColumn: 'roleName', sortDirection: 'asc' }),
          fetchPermissionMenus(),
        ]);

        if (!active) return;
        const activeRoles = Array.isArray(rolesData?.content) ? rolesData.content : [];
        setRoles(activeRoles);
        setMenus(Array.isArray(menusData) ? menusData : []);
        if (activeRoles[0]?.roleId) {
          setSelectedRoleId(String(activeRoles[0].roleId));
        }
      } catch {
        if (active) showNotice('Unable to load role menu permissions.', 'error');
      } finally {
        if (active) setLoading(false);
      }
    }

    loadInitialData();
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

  useEffect(() => {
    if (!selectedRoleId) {
      setSelectedMenuIds(new Set());
      return undefined;
    }

    let active = true;
    async function loadPermissions() {
      try {
        setPermissionLoading(true);
        const data = await fetchRoleMenuPermissions(selectedRoleId);
        if (active) setSelectedMenuIds(new Set(Array.isArray(data?.menuIds) ? data.menuIds : []));
      } catch {
        if (active) {
          setSelectedMenuIds(new Set());
          showNotice('Unable to load permissions for selected role.', 'error');
        }
      } finally {
        if (active) setPermissionLoading(false);
      }
    }

    loadPermissions();
    return () => {
      active = false;
    };
  }, [selectedRoleId]);

  const rows = useMemo(() => flattenMenus(menus), [menus]);

  const parentMap = useMemo(() => {
    const map = new Map();
    rows.forEach((row) => {
      map.set(row.menuId, row.parentId);
    });
    return map;
  }, [rows]);

  const menuMap = useMemo(() => {
    const map = new Map();
    rows.forEach((row) => {
      map.set(row.menuId, row);
    });
    return map;
  }, [rows]);

  const selectedRole = roles.find((role) => String(role.roleId) === String(selectedRoleId));
  const isSuperAdminRole = selectedRole?.roleCode === 'SUPER_ADMIN_1';

  const showNotice = (text, type = 'success') => {
    setNotice({ text, type });
  };

  const addParents = (next, menuId) => {
    let parentId = parentMap.get(menuId);
    while (parentId) {
      next.add(parentId);
      parentId = parentMap.get(parentId);
    }
  };

  const toggleMenu = (menu, checked) => {
    setSelectedMenuIds((current) => {
      const next = new Set(current);
      const descendantIds = collectDescendants(menu);

      if (checked) {
        descendantIds.forEach((id) => next.add(id));
        addParents(next, menu.menuId);
      } else {
        descendantIds.forEach((id) => next.delete(id));
      }

      return next;
    });
  };

  const save = async () => {
    if (!selectedRoleId || saving) return;

    try {
      setSaving(true);
      const response = await saveRoleMenuPermissions(selectedRoleId, Array.from(selectedMenuIds));
      setSelectedMenuIds(new Set(Array.isArray(response?.menuIds) ? response.menuIds : []));
      showNotice('Role menu permissions saved successfully.');
    } catch (error) {
      const message = error?.response?.data?.message || error?.message || 'Unable to save role menu permissions.';
      showNotice(message, 'error');
    } finally {
      setSaving(false);
    }
  };

  return (
    <div className="relative flex h-full min-h-0 flex-col bg-white" style={{ fontFamily: 'Calibri, sans-serif' }}>
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

      <div className="shrink-0 rounded-t-xl border-2 border-b-0 border-black/20 bg-gradient-to-b from-white to-blue-50 px-4 py-3">
        <div className="flex flex-col gap-3 lg:flex-row lg:items-end lg:justify-between">
          <div className="flex flex-col gap-1">
            <span className="text-[11px] font-black uppercase tracking-widest text-slate-500">Role</span>
            <select
              value={selectedRoleId}
              onChange={(event) => setSelectedRoleId(event.target.value)}
              disabled={loading || saving}
              className="h-10 w-full min-w-80 rounded-lg border-2 border-black/20 bg-white px-3 text-[13px] font-black uppercase outline-none focus:border-[#0052CC] disabled:cursor-wait disabled:bg-slate-100"
            >
              {roles.map((role) => (
                <option key={role.roleId} value={role.roleId}>
                  {role.roleName} ({role.roleCode})
                </option>
              ))}
            </select>
          </div>

          <div className="flex items-center gap-3">
            <div className="hidden items-center gap-2 rounded-lg border border-blue-100 bg-white px-3 py-2 text-[11px] font-black uppercase text-[#0052CC] lg:flex">
              <ShieldCheck size={15} />
              <span>{selectedMenuIds.size} menus selected</span>
            </div>
            <button
              type="button"
              onClick={save}
              disabled={!selectedRoleId || saving || loading || permissionLoading || isSuperAdminRole}
              className="inline-flex min-w-28 items-center justify-center gap-2 rounded-lg border-2 border-[#0052CC] bg-[#0052CC] px-4 py-2 text-[12px] font-black uppercase text-white shadow-sm transition-all hover:bg-blue-700 disabled:cursor-not-allowed disabled:border-blue-300 disabled:bg-blue-300"
            >
              {saving ? <Loader2 size={16} className="animate-spin" /> : <Save size={16} />}
              {saving ? 'Saving' : 'Save'}
            </button>
          </div>
        </div>
      </div>

      <div className="relative min-h-0 flex-1 overflow-auto rounded-b-xl border-2 border-black/20">
        {(loading || permissionLoading) && (
          <div className="absolute inset-0 z-30 grid place-items-center bg-white/70 backdrop-blur-[1px]">
            <div className="flex items-center gap-3 rounded-xl border-2 border-blue-200 bg-white px-5 py-4 text-[#0052CC] shadow-xl">
              <Loader2 size={20} className="animate-spin" />
              <span className="text-[12px] font-black uppercase tracking-widest">Loading permissions</span>
            </div>
          </div>
        )}

        <table className="min-w-full text-left border-separate border-spacing-0">
          <thead className="sticky top-0 z-20">
            <tr>
              <th className="w-24 bg-[#dfe7f2] px-3 py-2 text-center text-[12px] font-black uppercase text-black border-b-2 border-r border-black/30">Access</th>
              <th className="bg-[#dfe7f2] px-3 py-2 text-[12px] font-black uppercase text-black border-b-2 border-black/30">Menu</th>
              <th className="hidden bg-[#dfe7f2] px-3 py-2 text-[12px] font-black uppercase text-black border-b-2 border-l border-black/30 md:table-cell">Code</th>
              <th className="hidden bg-[#dfe7f2] px-3 py-2 text-[12px] font-black uppercase text-black border-b-2 border-l border-black/30 lg:table-cell">Path</th>
            </tr>
          </thead>
          <tbody>
            {!loading && rows.length === 0 && (
              <tr>
                <td colSpan={4} className="px-4 py-12 text-center text-[13px] font-black uppercase text-black/60">No active menus found</td>
              </tr>
            )}
            {rows.map((menu, index) => {
              const hasChildren = (menu.children || []).length > 0;
              const checked = selectedMenuIds.has(menu.menuId);

              return (
                <tr key={menu.menuId} className={index % 2 === 0 ? 'bg-white' : 'bg-blue-50/35'}>
                  <td className="border-b border-r border-black/15 px-3 py-2 text-center">
                    <input
                      type="checkbox"
                      checked={checked}
                      disabled={!selectedRoleId || saving || isSuperAdminRole}
                      onChange={(event) => toggleMenu(menuMap.get(menu.menuId), event.target.checked)}
                      className="h-4 w-4"
                    />
                  </td>
                  <td className="border-b border-black/15 px-3 py-2 text-[13px] font-bold text-black">
                    <div className="flex items-center gap-2" style={{ paddingLeft: `${menu.level * 24}px` }}>
                      {hasChildren ? <ChevronRight size={14} className="text-[#0052CC]" /> : <span className="h-3.5 w-3.5" />}
                      <span className="uppercase">{display(menu.menuName)}</span>
                    </div>
                  </td>
                  <td className="hidden border-b border-l border-black/15 px-3 py-2 text-[12px] font-bold uppercase text-slate-700 md:table-cell">
                    {display(menu.menuCode)}
                  </td>
                  <td className="hidden border-b border-l border-black/15 px-3 py-2 text-[12px] font-bold text-slate-600 lg:table-cell">
                    {display(menu.urlPath)}
                  </td>
                </tr>
              );
            })}
          </tbody>
        </table>
      </div>
    </div>
  );
};

export default RoleMenuPermissions;
