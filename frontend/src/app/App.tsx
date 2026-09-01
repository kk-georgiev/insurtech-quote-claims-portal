import { RouterProvider } from 'react-router';
import { router } from './browserRouter';

export function App() {
  return <RouterProvider router={router} />;
}
