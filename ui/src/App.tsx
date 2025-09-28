import './App.css'
import { TestG1 } from './components/TestG1'

function App() {
  return (
    <div className="app">
      <header className="app-header">
        <h1>Pulseboard</h1>
        <p>Real-time anomaly detection for live event streams</p>
      </header>

      <main className="app-content">
        <div className="status-card">
          <h2>Pulseboard UI ready</h2>
          <p>Backend configured at: http://localhost:8080</p>
        </div>

        <TestG1 />
      </main>

      <footer className="app-footer">
        <p>Pulseboard MVP - Event Stream Anomaly Detection</p>
      </footer>
    </div>
  )
}

export default App
