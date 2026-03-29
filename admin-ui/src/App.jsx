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
  const [date, setDate] = useState("2026-03-29");
  const [search, setSearch] = useState("");
  const [teeTimes, setTeeTimes] = useState([]);
  const [loading, setLoading] = useState(false);
  const [loadingAction, setLoadingAction] = useState({});
  const [message, setMessage] = useState("");
  const [error, setError] = useState("");

  const headers = useMemo(
    () => ({
      "Content-Type": "application/json",
      "X-ADMIN-API-KEY": apiKey,
    }),
    [apiKey]
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
      setTeeTimes(Array.isArray(data) ? data : []);
      setMessage("Tee sheet loaded.");
    } catch (err) {
      setTeeTimes([]);
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
      const url =
        action === "cancel"
          ? `${baseUrl}/api/admin/reservations/${reservationId}/cancel`
          : `${baseUrl}/api/admin/reservations/${reservationId}/${action}`;

      const res = await fetch(url, {
        method: "POST",
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

  async function toggleBlocked(teeTime) {
    const actionKey = `block-${teeTime.teeTimeId}`;
    setLoadingAction((prev) => ({ ...prev, [actionKey]: true }));
    setError("");
    setMessage("");

    try {
      let blockedReason = teeTime.blockedReason || "";

      if (!teeTime.blocked) {
        blockedReason =
          window.prompt(
            `Reason for blocking ${teeTime.slotLabel}?`,
            teeTime.blockedReason || "Private event"
          ) || "";
      }

      const res = await fetch(`${baseUrl}/api/admin/tee-times/${teeTime.teeTimeId}/block`, {
        method: "PUT",
        headers,
        body: JSON.stringify({
          blocked: !teeTime.blocked,
          blockedReason: !teeTime.blocked ? blockedReason : null,
        }),
      });

      if (!res.ok) {
        throw new Error(await res.text());
      }

      setMessage(
        !teeTime.blocked
          ? `Blocked tee time ${teeTime.slotLabel}.`
          : `Unblocked tee time ${teeTime.slotLabel}.`
      );

      await loadTeeSheet();
    } catch (err) {
      setError(err.message || "Failed to update tee time.");
    } finally {
      setLoadingAction((prev) => ({ ...prev, [actionKey]: false }));
    }
  }

  function isActionLoading(id, action) {
    return !!loadingAction[`${action}-${id}`];
  }

  return (
    <div className="page">
      <div className="container">
        <h1>Royal Chappy Admin</h1>
        <p className="subtitle">Tee sheet, blocking, check-in, and payment actions</p>

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
          {filteredTeeTimes.length === 0 ? (
            <div className="panel">No tee times found for this date.</div>
          ) : (
            filteredTeeTimes.map((teeTime) => (
              <div
                key={`${teeTime.teeTimeId}-${teeTime.startTime}`}
                className={`panel teeGroup ${teeTime.blocked ? "blockedGroup" : ""}`}
              >
                <div className="teeGroupHeader">
                  <div>
                    <h2>{teeTime.slotLabel}</h2>
                    <div className="muted">Tee Time ID {teeTime.teeTimeId}</div>
                    <div className="muted">
                      Capacity {teeTime.capacity} · Remaining {teeTime.spotsRemaining}
                    </div>
                    {teeTime.blocked && (
                      <div className="message error inlineMessage">
                        Blocked{teeTime.blockedReason ? `: ${teeTime.blockedReason}` : ""}
                      </div>
                    )}
                  </div>

                  <div className="buttonRow">
                    <button
                      onClick={() => toggleBlocked(teeTime)}
                      disabled={loading || isActionLoading(teeTime.teeTimeId, "block")}
                      className={teeTime.blocked ? "secondary" : ""}
                    >
                      {isActionLoading(teeTime.teeTimeId, "block")
                        ? "Saving..."
                        : teeTime.blocked
                        ? "Unblock Tee Time"
                        : "Block Tee Time"}
                    </button>
                  </div>
                </div>

                {teeTime.reservations?.length === 0 ? (
                  <div className="reservationCard">
                    <div>
                      <strong>No reservations</strong>
                      <div className="muted">
                        This tee time is currently empty.
                      </div>
                    </div>
                  </div>
                ) : (
                  <div className="reservationList">
                    {teeTime.reservations.map((row) => {
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
                              <span>Party {row.partySize}</span>
                              <span>
                                Transportation: {row.transportation ? "Yes" : "No"}
                              </span>
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
                )}
              </div>
            ))
          )}
        </div>
      </div>
    </div>
  );
}