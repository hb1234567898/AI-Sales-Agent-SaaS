import { WarningCircle } from '@phosphor-icons/react'
import { useRouteError } from 'react-router'

export function RouteErrorPage() {
  const error = useRouteError()
  const details = error instanceof Error ? error.message : '页面不存在或暂时无法访问。'

  return (
    <main className="route-feedback">
      <section>
        <span className="route-feedback-icon"><WarningCircle size={26} /></span>
        <h1>无法打开此页面</h1>
        <p>{details}</p>
        <button className="button button-primary" type="button" onClick={() => window.location.assign('/app/today')}>
          返回今日工作台
        </button>
      </section>
    </main>
  )
}
