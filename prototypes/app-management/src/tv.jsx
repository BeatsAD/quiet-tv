import React from 'react';
import { createRoot } from 'react-dom/client';
import { SetupExperience } from './SetupExperience.jsx';
import './styles.css';
import './setup.css';
import './tv.css';

const initialScreen = new URLSearchParams(location.search).get('restore') === 'true' ? 'recovery' : undefined;
createRoot(document.getElementById('root')).render(<SetupExperience tv initialScreen={initialScreen} />);
