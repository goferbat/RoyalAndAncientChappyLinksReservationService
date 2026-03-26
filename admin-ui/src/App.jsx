import { useMemo, useState } from "react";
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
  const [baseUrl, setBaseUrl] = useState("http://localhost:8081");
  const [apiKey, setApiKey] = useState("dev-secret-key");
  const [date, setDate] = useState("2026-03-26");
  const [search, setSearch] = useState("");
  const [rows, setRows] = useState([]);
  const [loading, setLoading] = useState(false);
  const [loadingAction, setLoadingAction] = useState({});
  const [message, setMessage] = useState("");
  const [error, setError] = useState("");

  const headers = useMemo(
    () => ({
      "Content-Type": "application/json",
      "X-ADMIN-API-KEY": apiKey,
      // If your backend expects x-api-key instead, swap the line above for:
      // "x-api-key": apiKey,
    }),
    [apiKey]
  );

  const groupedRows = useMemo(() => {
    const searchText = search.toLowerCase().trim();

    const filtered = rows.filter((row) => {
      const text = [
        row.customerName,
        row.customerEmail,
        row.slotLabel,
        row.tierName,
        row.paymentStatus,
        row.reservationId,
        row.teeTimeId,
        row.startTime,
      ]
        .filter(Boolean)
        .join(" ")
        .toLowerCase();

      return text.includes(searchText);
    });

    const groups = new Map();

    for (const row of filtered) {
      const key = `${row.teeTimeId}-${row.startTime}`;
      if (!groups.has(key)) {
        groups.set(key, {
          teeTimeId: row.teeTimeId,
          startTime: row.startTime,
          slotLabel: row.slotLabel,
          reservations: [],
        });
      }
      groups.get(key).reservations.push(row);
    }

    return Array.from(groups.values()).sort((a, b) =>
      (a.startTime || "").localeCompare(b.startTime || "")
    );
  }, [rows, search]);

  async function loadTeeSheet() {
    setLoading(true);
    setError("");
    setMessage("");

    try {
      const res = await fetch(`${baseUrl}/api/admin/tee-sheet?date=${date}`, {
        method: "GET",
        headers,
      });

      if (!res.ok) {
        throw new Error(await res.text());
      }

      const data = await res.json();
      setRows(Array.isArray(data) ? data : []);
      setMessage("Tee sheet loaded.");
    } catch (err) {
      setRows([]);
      setError(err.message || "Failed to load tee sheet.");
    } finally {
      setLoading(false);
    }
  }

  async function runAction(reservationId, action) {
    const actionKey = `${action}-${reservationId}`;
    setLoadingAction((prev) => ({ ...prev, [actionKey]: true }));
    setError("");
    setMessage("");

    try {
      const method = action === "cancel" ? "DELETE" : "POST";
      const url =
        action === "cancel"
          ? `${baseUrl}/api/admin/reservations/${reservationId}`
          : `${baseUrl}/api/admin/reservations/${reservationId}/${action}`;

      const res = await fetch(url, {
        method,
        headers,
      });

      if (!res.ok) {
        throw new Error(await res.text());
      }

      setMessage(
        `${action.charAt(0).toUpperCase() + action.slice(1)} succeeded for reservation ${reservationId}.`
      );

      await loadTeeSheet();
    } catch (err) {
      setError(err.message || `Failed to ${action}.`);
    } finally {
      setLoadingAction((prev) => ({ ...prev, [actionKey]: false }));
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

  function isActionLoading(reservationId, action) {
    return !!loadingAction[`${action}-${reservationId}`];
  }

  return (
    <div className="page">
      <div className="container">
        <h1>Royal Chappy Admin</h1>
        <p className="subtitle">Tee sheet, check-in, and payment actions</p>

        <div className="panel controls">
          <div className="field">
            <label>Backend URL</label>
            <input
              value={baseUrl}
              onChange={(e) => setBaseUrl(e.target.value)}
            />
          </div>

          <div className="field">
            <label>Admin API Key</label>
            <input
              value={apiKey}
              onChange={(e) => setApiKey(e.target.value)}
            />
          </div>

          <div className="field">
            <label>Date</label>
            <input
              type="date"
              value={date}
              onChange={(e) => setDate(e.target.value)}
            />
          </div>

          <div className="buttonRow">
            <button onClick={loadTeeSheet} disabled={loading}>
              {loading ? "Loading..." : "Load Tee Sheet"}
            </button>
            <button onClick={seedDate} disabled={loading} className="secondary">
              {loading ? "Working..." : "Seed Date"}
            </button>
          </div>

          <div className="field">
            <label>Search</label>
            <input
              placeholder="Search name, email, time, status..."
              value={search}
              onChange={(e) => setSearch(e.target.value)}
            />
          </div>
        </div>

        {message && <div className="message success">{message}</div>}
        {error && <div className="message error">{error}</div>}

        <div className="teeSheet">
          {groupedRows.length === 0 ? (
            <div className="panel">No reservations found for this date.</div>
          ) : (
            groupedRows.map((group) => (
              <div
                key={`${group.teeTimeId}-${group.startTime}`}
                className="panel teeGroup"
              >
                <div className="teeGroupHeader">
                  <div>
                    <h2>{group.slotLabel}</h2>
                    <div className="muted">Tee Time ID {group.teeTimeId}</div>
                  </div>
                </div>

                <div className="reservationList">
                  {group.reservations.map((row) => {
                    const status = normalizeStatus(row.paymentStatus);

                    return (
                      <div key={row.reservationId} className="reservationCard">
                        <div>
                          <div className="reservationTop">
                            <strong>{row.customerName}</strong>
                            <span className={statusClass(status)}>{status}</span>
                          </div>
                          <div className="muted">{row.customerEmail}</div>
                          <div className="details">
                            <span>Reservation #{row.reservationId}</span>
                            <span>Party {row.partySize}</span>
                            <span>{row.tierName}</span>
                            <span>{formatMoney(row.amountCents)}</span>
                          </div>
                        </div>

                        <div className="buttonRow">
                          <button
                            onClick={() => runAction(row.reservationId, "capture")}
                            disabled={
                              loading ||
                              isActionLoading(row.reservationId, "capture") ||
                              !canCapture(status)
                            }
                          >
                            {isActionLoading(row.reservationId, "capture")
                              ? "Capturing..."
                              : "Capture"}
                          </button>

                          <button
                            onClick={() => runAction(row.reservationId, "cancel")}
                            disabled={
                              loading ||
                              isActionLoading(row.reservationId, "cancel") ||
                              !canCancel(status)
                            }
                            className="secondary"
                          >
                            {isActionLoading(row.reservationId, "cancel")
                              ? "Cancelling..."
                              : "Cancel"}
                          </button>

                          <button
                            onClick={() => runAction(row.reservationId, "refund")}
                            disabled={
                              loading ||
                              isActionLoading(row.reservationId, "refund") ||
                              !canRefund(status)
                            }
                            className="secondary"
                          >
                            {isActionLoading(row.reservationId, "refund")
                              ? "Refunding..."
                              : "Refund"}
                          </button>
                        </div>
                      </div>
                    );
                  })}
                </div>
              </div>
            ))
          )}
        </div>
      </div>
    </div>
  );
}