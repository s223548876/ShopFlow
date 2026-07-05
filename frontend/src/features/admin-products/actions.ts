export async function saveAndApply<T>(save: () => Promise<T>, apply: (saved: T) => void): Promise<T> {
  const saved = await save()
  apply(saved)
  return saved
}

export async function createAndNavigate(
  create: () => Promise<unknown>,
  navigate: () => Promise<unknown>,
): Promise<void> {
  await create()
  await navigate()
}

export async function confirmThenRefresh(
  confirm: () => Promise<unknown>,
  mutate: () => Promise<unknown>,
  refresh: () => Promise<void>,
): Promise<boolean> {
  try {
    await confirm()
  } catch {
    return false
  }
  await mutate()
  await refresh()
  return true
}
