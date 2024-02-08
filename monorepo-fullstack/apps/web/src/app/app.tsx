// eslint-disable-next-line @typescript-eslint/no-unused-vars
import styles from './app.module.css';
// this is the imported symbol from the shared library
import { PROJECT_NAME } from '@engineeringmindscape/shared';
import { useEffect, useState } from 'react';

export function App() {
  const [data, setData] = useState(null);
  useEffect(() => {
    // this is the environment variable from the .env file,
    // which we will populate with the API URL after first deployment
    // as that is when we will know the URL of the API
    fetch(`${import.meta.env.VITE_API_URL}`)
      .then(response => response.json())
      .then(data => setData(data));
  }, []);

  return (
    <div>
      {PROJECT_NAME}
      {data && <div>{JSON.stringify(data)}</div>}
    </div>
  );
}

export default App;
