import { useEffect, useMemo, useState } from "react";
import "./App.css";

function formatMoney(amountCents) {
  return new Intl.NumberFormat("en-US", {
    style: "currency",
    currency: "USD",
  }).format((amountCents || 0) / 100);
}

function statusClass(status) {
  const s = (status || "").toUpperCase();
  if (s === "COMPLETED") return "status completed";
  if (s === "APPROVED" || s === "AUTHORIZED") return "status approved";
  if (s === "CANCELED") return "status canceled";
  if (s === "REFUNDED") return "status refunded";
  return "status unknown";
}

function normalizeStatus(status) {
  return (status || "").toUpperCase();
}

function canCapture(status) {
  const s = normalizeStatus(status);
  return s === "APPROVED" || s === "AUTHORIZED";
}

function canCancel(status) {
  const s = normalizeStatus(status);
  return s === "APPROVED" || s === "AUTHORIZED";
}

function canRefund(status) {
  const s = normalizeStatus(status);
  return s === "COMPLETED";
}

export default function App() {
  const baseUrl = import.meta.env.VITE_API_BASE_URL;

  const [date, setDate] = useState(() => {
    const now = new Date();
    return now.toISOString().slice(0, 10);
  });
  const [search, setSearch] = useState("");
  const [teeTimes, setTeeTimes] = useState([]);
  const [loading, setLoading] = useState(false);
  const [loadingAction, setLoadingAction] = useState({});
  const [message, setMessage] = useState("");
  const [error, setError] = useState("");

  const [authChecked, setAuthChecked] = useState(false);
  const [currentUser, setCurrentUser] = useState(null);

  const [loginEmail, setLoginEmail] = useState("");
  const [loginPassword, setLoginPassword] = useState("");
  const [loggingIn, setLoggingIn] = useState(false);

  const headers = useMemo(
    () => ({
      "Content-Type": "application/json",
    }),
    []
  );

  const filteredTeeTimes = useMemo(() => {
    const searchText = search.toLowerCase().trim();

    if (!searchText) return teeTimes;

    return teeTimes.filter((teeTime) => {
      const reservationText = (teeTime.reservations || [])
        .flatMap((r) => [
          r.customerName,
          r.customerEmail,
          r.tierName,
          r.paymentStatus,
          r.reservationId,
        ])
        .filter(Boolean)
        .join(" ")
        .toLowerCase();

      const teeTimeText = [
        teeTime.slotLabel,
        teeTime.teeTimeId,
        teeTime.blockedReason,
        teeTime.blocked ? "blocked" : "open",
      ]
        .filter(Boolean)
        .join(" ")
        .toLowerCase();

      return `${teeTimeText} ${reservationText}`.includes(searchText);
    });
  }, [teeTimes, search]);

  useEffect(() => {
    checkAuth();
  }, []);

  async function checkAuth() {
    setError("");
    try {
      const res = await fetch(`${baseUrl}/api/auth/me`, {
        method: "GET",
        headers,
        credentials: "include",
      });

      if (!res.ok) {
        setCurrentUser(null);
        return;
      }

      const data = await res.json();
      setCurrentUser(data);
    } catch {
      setCurrentUser(null);
    } finally {
      setAuthChecked(true);
    }
  }

  async function handleLogin(e) {
    e.preventDefault();
    setLoggingIn(true);
    setError("");
    setMessage("");

    try {
      const res = await fetch(`${baseUrl}/api/auth/login`, {
        method: "POST",
        headers,
        credentials: "include",
        body: JSON.stringify({
          email: loginEmail,
          password: loginPassword,
        }),
      });

      if (!res.ok) {
        throw new Error(await res.text());
      }

      const data = await res.json();
      setCurrentUser(data);
      setLoginPassword("");
      setMessage(`Logged in as ${data.name || data.email}.`);
    } catch (err) {
      setCurrentUser(null);
      setError(err.message || "Login failed.");
    } finally {
      setLoggingIn(false);
    }
  }

  async function handleLogout() {
    setLoading(true);
    setError("");
    setMessage("");

    try {
      const res = await fetch(`${baseUrl}/api/auth/logout`, {
        method: "POST",
        headers,
        credentials: "include",
      });

      if (!res.ok) {
        throw new Error(await res.text());
      }

      setCurrentUser(null);
      setTeeTimes([]);
      setSearch("");
      setLoginPassword("");
      setMessage("Logged out.");
    } catch (err) {
      setError(err.message || "Logout failed.");
    } finally {
      setLoading(false);
    }
  }

  async function loadTeeSheet() {
    setLoading(true);
    setError("");
    setMessage("");

    try {
      const res = await fetch(`${baseUrl}/api/admin/tee-sheet?date=${date}`, {
        method: "GET",
        headers,
        credentials: "include",
      });

      if (!res.ok) {
        throw new Error(await res.text());
      }

      const data = await res.json();
      setTeeTimes(Array.isArray(data) ? data : []);
      setMessage("Tee sheet loaded.");
    } catch (err) {
      setTeeTimes([]);
      setError(err.message || "Failed to load tee sheet.");
    } finally {
      setLoading(false);
    }
  }

  async function seedDate() {
    if (!window.confirm(`Seed tee times for ${date}?`)) return;

    setLoading(true);
    setError("");
    setMessage("");

    try {
      const res = await fetch(`${baseUrl}/api/admin/tee-times/seed/${date}`, {
        method: "POST",
        headers,
        credentials: "include",
      });

      if (!res.ok) {
        throw new Error(await res.text());
      }

      setMessage(`Seeded tee times for ${date}.`);
      await loadTeeSheet();
    } catch (err) {
      setError(err.message || "Failed to seed tee times.");
    } finally {
      setLoading(false);
    }
  }

  function isActionLoading(id, action) {
    return !!loadingAction[`${action}-${id}`];
  }

  if (!authChecked) {
    return (
      <div className="page">
        <div className="container">
          <div className="panel">
            <h1>Royal Chappy Admin</h1>
            <p className="subtitle">Checking admin session...</p>
          </div>
        </div>
      </div>
    );
  }

  if (!currentUser) {
    return (
      <div className="page">
        <div className="container">
          <div className="panel" style={{ maxWidth: 480, margin: "40px auto" }}>
            <h1>Royal Chappy Admin Login</h1>
            <p className="subtitle">Sign in with your admin account</p>

            <form onSubmit={handleLogin}>
              <div className="field">
                <label>Email</label>
                <input
                  type="email"
                  value={loginEmail}
                  onChange={(e) => setLoginEmail(e.target.value)}
                />
              </div>

              <div className="field">
                <label>Password</label>
                <input
                  type="password"
                  value={loginPassword}
                  onChange={(e) => setLoginPassword(e.target.value)}
                />
              </div>

              <div className="buttonRow">
                <button type="submit" disabled={loggingIn}>
                  {loggingIn ? "Signing In..." : "Sign In"}
                </button>
              </div>
            </form>

            {error && <div className="message error">{error}</div>}
            {message && <div className="message success">{message}</div>}
          </div>
        </div>
      </div>
    );
  }

  return (
    <div className="page">
      <div className="container">
        <h1>Royal Chappy Admin</h1>

        <button onClick={handleLogout}>Log Out</button>

        <input type="date" value={date} onChange={(e) => setDate(e.target.value)} />

        <button onClick={loadTeeSheet}>Load Tee Sheet</button>
        <button onClick={seedDate}>Seed Date</button>

        {error && <div>{error}</div>}
        {message && <div>{message}</div>}
      </div>
    </div>
  );
}