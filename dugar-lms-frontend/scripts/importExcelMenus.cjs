const fs = require('fs');
const path = require('path');
const xlsx = require('xlsx');
const mysql = require('mysql2/promise');

function readDbConfig() {
  const dataPath = path.join(__dirname, '..', 'src', 'data');
  const raw = fs.readFileSync(dataPath, 'utf8');
  const lines = raw.split(/\r?\n/).map((s) => s.trim()).filter(Boolean);

  if (lines.length < 4) {
    throw new Error('src/data must contain host, database, username, password on separate lines.');
  }

  return {
    host: lines[0],
    database: lines[1],
    user: lines[2],
    password: lines[3],
  };
}

function normalizeSheetName(name) {
  return String(name || '').replace(/\s+/g, ' ').trim();
}

function normalizeValue(value) {
  return String(value || '').replace(/\s+/g, ' ').trim();
}

function parseWorkbook(filePath) {
  const wb = xlsx.readFile(filePath);
  const tree = [];

  for (const sheetNameRaw of wb.SheetNames) {
    const sheetName = normalizeSheetName(sheetNameRaw);
    if (!sheetName) {
      continue;
    }

    const sheet = wb.Sheets[sheetNameRaw];
    const rows = xlsx.utils.sheet_to_json(sheet, { header: 1, defval: '' });

    const root = { name: sheetName, children: [] };
    let currentL1 = null;
    let currentL2 = null;

    for (const row of rows) {
      const c1 = normalizeValue(row[0]);
      const c2 = normalizeValue(row[1]);
      const c3 = normalizeValue(row[2]);

      if (!c1 && !c2 && !c3) {
        continue;
      }

      if (c1 && c1.toLowerCase() !== sheetName.toLowerCase()) {
        currentL1 = { name: c1, children: [] };
        root.children.push(currentL1);
        currentL2 = null;
      }

      if (c2) {
        const parent = currentL1 || root;
        currentL2 = { name: c2, children: [] };
        parent.children.push(currentL2);
      }

      if (c3) {
        const parent = currentL2 || currentL1 || root;
        parent.children.push({ name: c3, children: [] });
      }
    }

    tree.push(root);
  }

  return tree;
}

function slugToPath(text) {
  return String(text || '')
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, '-')
    .replace(/^-+|-+$/g, '');
}

function iconForName(name, depth) {
  const n = normalizeValue(name).toLowerCase();

  const ICON_KEYWORDS = [
    { keys: ['dashboard'], icon: 'LayoutDashboard' },
    { keys: ['master'], icon: 'FolderTree' },
    { keys: ['transaction'], icon: 'ArrowLeftRight' },
    { keys: ['contract'], icon: 'FileSpreadsheet' },
    { keys: ['voucher'], icon: 'Receipt' },
    { keys: ['receipt'], icon: 'ReceiptIndianRupee' },
    { keys: ['payment'], icon: 'Wallet' },
    { keys: ['journal'], icon: 'NotebookPen' },
    { keys: ['report', 'trial balance', 'balance sheet', 'p & l', 'ratio'], icon: 'BarChart3' },
    { keys: ['bank'], icon: 'Landmark' },
    { keys: ['insurance'], icon: 'ShieldCheck' },
    { keys: ['party', 'customer'], icon: 'Users' },
    { keys: ['user management', 'user master'], icon: 'UserCog' },
    { keys: ['password'], icon: 'KeyRound' },
    { keys: ['legal', 'litigation', 'court', 'notice'], icon: 'Scale' },
    { keys: ['collection', 'demand', 'overdue'], icon: 'HandCoins' },
    { keys: ['branch'], icon: 'Building2' },
    { keys: ['grievence', 'grievances'], icon: 'MessageSquareWarning' },
    { keys: ['task', 'department'], icon: 'ClipboardCheck' },
    { keys: ['authorisation', 'authorize', 'authorise'], icon: 'BadgeCheck' },
    { keys: ['npa', 'risk'], icon: 'ShieldAlert' },
    { keys: ['committee', 'management'], icon: 'BriefcaseBusiness' },
    { keys: ['lead'], icon: 'UserPlus' },
    { keys: ['link to pay', 'pay'], icon: 'QrCode' },
  ];

  for (const entry of ICON_KEYWORDS) {
    if (entry.keys.some((k) => n.includes(k))) {
      return entry.icon;
    }
  }

  if (depth === 0) return 'Layers3';
  if (depth === 1) return 'FolderOpen';
  if (depth === 2) return 'ListTree';
  return 'Circle';
}

async function ensureTables(conn) {
  await conn.execute(`
    CREATE TABLE IF NOT EXISTS menus (
      menu_id INT UNSIGNED PRIMARY KEY,
      menu_name VARCHAR(120) NOT NULL,
      parent_id INT UNSIGNED NULL,
      url_path VARCHAR(255) NULL,
      icon VARCHAR(100) NULL,
      display_order INT NOT NULL DEFAULT 0,
      is_active TINYINT(1) NOT NULL DEFAULT 1,
      INDEX idx_menus_parent (parent_id)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
  `);

  await conn.execute(`
    CREATE TABLE IF NOT EXISTS role_permissions (
      permission_id INT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
      role_name VARCHAR(50) NOT NULL,
      menu_id INT UNSIGNED NOT NULL,
      can_view TINYINT(1) NOT NULL DEFAULT 1,
      can_add TINYINT(1) NOT NULL DEFAULT 0,
      can_edit TINYINT(1) NOT NULL DEFAULT 0,
      can_delete TINYINT(1) NOT NULL DEFAULT 0,
      UNIQUE KEY uq_role_menu (role_name, menu_id),
      INDEX idx_rp_role (role_name),
      INDEX idx_rp_menu (menu_id)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
  `);
}

async function insertMenuTree(conn, tree) {
  await conn.execute('DELETE FROM role_permissions');
  await conn.execute('DELETE FROM menus');

  let nextId = 1;
  const allMenuIds = [];

  const insertOne = async (node, parentId, depth, order) => {
    const menuId = nextId++;
    allMenuIds.push(menuId);

    let urlPath = '#';
    if (!node.children || node.children.length === 0) {
      const parentSlug = parentId ? String(parentId) : 'root';
      urlPath = `/menu/${parentSlug}/${slugToPath(node.name)}`;
    }

    await conn.execute(
      `INSERT INTO menus (menu_id, menu_name, parent_id, url_path, icon, display_order, is_active)
       VALUES (?, ?, ?, ?, ?, ?, 1)`,
      [menuId, node.name, parentId, urlPath, iconForName(node.name, depth), order]
    );

    for (let i = 0; i < (node.children || []).length; i += 1) {
      await insertOne(node.children[i], menuId, depth + 1, i + 1);
    }
  };

  for (let i = 0; i < tree.length; i += 1) {
    await insertOne(tree[i], null, 0, i + 1);
  }

  return allMenuIds;
}

async function seedPermissions(conn, menuIds) {
  const roles = [
    { role: 'ADMIN', canAdd: 1, canEdit: 1, canDelete: 1 },
    { role: 'MANAGER', canAdd: 1, canEdit: 1, canDelete: 0 },
    { role: 'USER', canAdd: 0, canEdit: 0, canDelete: 0 },
  ];

  for (const role of roles) {
    for (const menuId of menuIds) {
      await conn.execute(
        `INSERT INTO role_permissions (role_name, menu_id, can_view, can_add, can_edit, can_delete)
         VALUES (?, ?, 1, ?, ?, ?)
         ON DUPLICATE KEY UPDATE
           can_view = VALUES(can_view),
           can_add = VALUES(can_add),
           can_edit = VALUES(can_edit),
           can_delete = VALUES(can_delete)`,
        [role.role, menuId, role.canAdd, role.canEdit, role.canDelete]
      );
    }
  }
}

async function main() {
  const excelPath = path.join(__dirname, '..', 'Functionality table.xlsx');
  if (!fs.existsSync(excelPath)) {
    throw new Error(`Excel file not found: ${excelPath}`);
  }

  const db = readDbConfig();
  const conn = await mysql.createConnection(db);

  try {
    const tree = parseWorkbook(excelPath);
    await ensureTables(conn);
    const menuIds = await insertMenuTree(conn, tree);
    await seedPermissions(conn, menuIds);

    console.log(`Imported ${menuIds.length} menu nodes from ${tree.length} sheets.`);
  } finally {
    await conn.end();
  }
}

main().catch((err) => {
  console.error(err.message || err);
  process.exit(1);
});
