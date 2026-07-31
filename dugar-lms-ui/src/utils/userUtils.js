export function getUserValue(user, keys, fallback = '-') {
  const value = keys.map((key) => user?.[key]).find((entry) => entry !== undefined && entry !== null && entry !== '')
  return value ?? fallback
}

export function getFullName(user) {
  const fullName = getUserValue(user, ['fullName', 'full_name', 'name'], '')

  if (fullName) {
    return fullName
  }

  const firstName = getUserValue(user, ['firstName', 'first_name'], '')
  const lastName = getUserValue(user, ['lastName', 'last_name'], '')
  const joinedName = `${firstName} ${lastName}`.trim()

  return joinedName || getUserValue(user, ['username'], 'User')
}

export function getRoleName(user) {
  return getUserValue(user, ['roleName', 'role_name', 'role', 'roleCode', 'role_code'], 'User')
}
