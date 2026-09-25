import { StrictMode } from "react";
import { createRoot } from "react-dom/client";
import { createBrowserRouter, Navigate, RouterProvider } from "react-router";
import { LoginPage } from "./auth/LoginPage";
import { SignUpPage } from "./auth/SignUpPage";
import "./index.css";

const router = createBrowserRouter(
  [
    { path: "/login", element: <LoginPage /> },
    { path: "/sign-up", element: <SignUpPage /> },
    { path: "*", element: <Navigate to="/login" replace /> },
  ],
  { basename: "/web" },
);

createRoot(document.getElementById("root")!).render(
  <StrictMode>
    <RouterProvider router={router} />
  </StrictMode>,
);
