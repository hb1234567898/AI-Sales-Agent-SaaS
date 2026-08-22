export function RouteLoadingPage() {
  return (
    <main className="route-loading" aria-label="正在加载工作台" aria-busy="true">
      <aside className="loading-sidebar" />
      <section>
        <div className="skeleton skeleton-title" />
        <div className="skeleton skeleton-subtitle" />
        <div className="loading-metrics">
          <div className="skeleton" /><div className="skeleton" /><div className="skeleton" /><div className="skeleton" />
        </div>
        <div className="skeleton skeleton-panel" />
      </section>
    </main>
  )
}
