const amountFormatter = new Intl.NumberFormat('zh-TW', {
  minimumFractionDigits: 2,
  maximumFractionDigits: 2,
})

const dateTimeFormatter = new Intl.DateTimeFormat('zh-TW', {
  dateStyle: 'medium',
  timeStyle: 'short',
})

export function formatAmount(value: number): string {
  return amountFormatter.format(value)
}

export function formatDateTime(value: string): string {
  return dateTimeFormatter.format(new Date(value))
}
