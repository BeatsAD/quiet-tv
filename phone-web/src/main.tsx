import { createRoot } from 'react-dom/client';
import { App } from './App';
import { OperationFeedbackProvider } from './OperationFeedback';
import './styles.css';
createRoot(document.getElementById('root')!).render(<OperationFeedbackProvider><App/></OperationFeedbackProvider>);
