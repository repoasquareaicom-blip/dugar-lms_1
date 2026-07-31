import * as LucideIcons from 'lucide-react'

function normalizeIconName(iconName) {
  if (!iconName || typeof iconName !== 'string') {
    return ''
  }

  return iconName
    .replace(/[_\-\s]+(.)?/g, (_, character = '') => character.toUpperCase())
    .replace(/^[a-z]/, (character) => character.toUpperCase())
}

export function getMenuIcon(iconName) {
  const normalizedName = normalizeIconName(iconName)
  return LucideIcons[normalizedName] || LucideIcons.FileText
}
