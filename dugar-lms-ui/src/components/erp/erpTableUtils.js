export function displayFallback(value, fallback = '-') {
  return value === null || value === undefined || value === '' ? fallback : value
}

const indianNumberFormatter = new Intl.NumberFormat('en-IN', {
  maximumFractionDigits: 2,
  minimumFractionDigits: 2,
})

export function formatIndianNumber(value) {
  if (value === null || value === undefined || value === '') {
    return '-'
  }

  const amount = Number(value)
  return Number.isFinite(amount) ? indianNumberFormatter.format(amount) : '-'
}

export function formatDateDDMMYYYY(value) {
  if (!value) {
    return '-'
  }

  const [year, month, day] = String(value).split('-')
  return year && month && day ? `${day}/${month}/${year}` : '-'
}
