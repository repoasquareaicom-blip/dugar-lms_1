import { createElement } from 'react'
import { getMenuIcon } from './iconResolverCore.js'

function IconResolver({ iconName, size = 17, strokeWidth = 2 }) {
  return createElement(getMenuIcon(iconName), { size, strokeWidth })
}

export default IconResolver
