import React from "react";
import { createRoot } from "react-dom/client";
import { App } from "./App.jsx";
import { SetupExperience } from "./SetupExperience.jsx";
import "./styles.css";
import "./setup.css";

createRoot(document.getElementById("root")).render(
  <React.StrictMode>
    {new URLSearchParams(window.location.search).get('prototype') === 'management' ? <App /> : <SetupExperience />}
  </React.StrictMode>,
);
