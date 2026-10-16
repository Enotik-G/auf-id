import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { BrowserRouter } from 'react-router'
import App from './App.tsx'
import './index.css'

// Точка входа: React находит в index.html пустой <div id="root"> и рисует внутри него приложение.
createRoot(document.getElementById('root')!).render(
  <StrictMode>
    {/* BrowserRouter следит за адресом в браузере и сообщает его <Routes> внутри App. */}
    <BrowserRouter>
      <App />
    </BrowserRouter>
  </StrictMode>,
)
