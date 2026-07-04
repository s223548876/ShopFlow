export async function checkoutAndNavigate(
  create: () => Promise<{ id: number }>,
  refreshCart: () => Promise<void>,
  navigate: (orderId: number) => Promise<unknown>,
): Promise<void> {
  const order = await create()
  await refreshCart()
  await navigate(order.id)
}

export async function refreshAfter(
  mutate: () => Promise<unknown>,
  refresh: () => Promise<void>,
): Promise<void> {
  await mutate()
  await refresh()
}
