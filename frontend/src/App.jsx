import { useState, useEffect } from 'react'

function App() {
  const [organisations, setOrganisations] = useState([])

  useEffect(() => {
    fetch('http://localhost:8080/api/organisations')
        .then(res => res.json())
        .then(data => setOrganisations(data))
  }, [])

  return (
      <div>
        <h1>Court Booking</h1>
        <h2>Organisations</h2>
        <ul>
          {organisations.map(org => (
              <li key={org.id}>{org.name}</li>
          ))}
        </ul>
      </div>
  )
}

export default App