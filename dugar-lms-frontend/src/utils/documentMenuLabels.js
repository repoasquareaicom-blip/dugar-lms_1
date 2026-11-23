// Also translate labels in an existing login's cached menu tree.
// Codes and routes remain the authorization and navigation identifiers.
export function documentMenuLabel(menu) {
  const name = menu.menuName || menu.menu_name || menu.label || 'Menu';
  const code = String(menu.menuCode || menu.menu_code || '').trim().toUpperCase();
  if (code === 'ADMINISTRATION_MASTERS_WORD_TEMPLATE_MASTER'
      || name.trim().toLowerCase() === 'word template master') return 'Document Template Master';
  if (code === 'OPERATIONS_TRANSACTIONS_GENERATE_WORD_DOCUMENT'
      || name.trim().toLowerCase() === 'generate word document') return 'Generate Document';
  return name;
}
