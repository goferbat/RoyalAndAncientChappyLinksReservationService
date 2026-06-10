import { useEffect, useMemo, useState } from "react";
import "./App.css";

const baseUrl = import.meta.env.VITE_API_BASE_URL;

function formatMoney(amountCents) {
  return new Intl.NumberFormat("en-US", {
    style: "currency",
    currency: "USD",
  }).format((amountCents || 0) / 100);
}

function formatSlotLabel(startTime) {
  if (!startTime) return startTime;
  const d = new Date(startTime);
  if (isNaN(d)) return startTime;
  return d.toLocaleString("en-US", {
    weekday: "long",
    month: "long",
    day: "numeric",
    hour: "numeric",
    minute: "2-digit",
    hour12: true,
  });
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

function canNoShow(status) {
  const s = normalizeStatus(status);
  return s === "APPROVED" || s === "AUTHORIZED";
}

function canMove(status) {
  const s = normalizeStatus(status);
  return s !== "CANCELED" && s !== "NO_SHOW" && s !== "REFUNDED";
}

function TierPricingRow({ tier, onSave }) {
  const [dollars, setDollars] = useState((tier.priceCents / 100).toFixed(2));

  return (
      <div className="field" style={{ marginBottom: 12 }}>
        <label>{tier.name}</label>
        <div style={{ display: "flex", gap: 8 }}>
          <input
              type="number"
              min="0"
              step="0.01"
              value={dollars}
              onChange={(e) => setDollars(e.target.value)}
              style={{ flex: 1 }}
          />
          <button onClick={() => onSave(tier.id, Math.round(parseFloat(dollars) * 100))}>
            Save
          </button>
        </div>
      </div>
  );
}

export default function App() {
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

  // Move modal state
  const [moveModal, setMoveModal] = useState(null); // { reservation, currentSlotLabel }
  const [moveDate, setMoveDate] = useState("");
  const [availableSlots, setAvailableSlots] = useState([]);
  const [loadingSlots, setLoadingSlots] = useState(false);
  const [selectedTargetId, setSelectedTargetId] = useState(null);
  const [moving, setMoving] = useState(false);

  // Pricing modal state
  const [showPricingModal, setShowPricingModal] = useState(false);
  const [tiers, setTiers] = useState([]);
  const [tierLoading, setTierLoading] = useState(false);

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

  // Fetch available slots whenever moveDate changes inside the modal
  useEffect(() => {
    if (!moveDate || !moveModal) {
      setAvailableSlots([]);
      return;
    }
    setLoadingSlots(true);
    setSelectedTargetId(null);

    fetch(`${baseUrl}/api/tee-times`, {
      headers,
      credentials: "include",
    })
        .then((r) => r.json())
        .then((slots) => {
          const filtered = (Array.isArray(slots) ? slots : []).filter(
              (s) =>
                  s.id !== moveModal.reservation.teeTimeId &&
                  (s.startTime || "").startsWith(moveDate) &&
                  s.capacity >= moveModal.reservation.partySize &&
                  !s.blocked
          );
          setAvailableSlots(filtered.sort((a, b) => a.startTime.localeCompare(b.startTime)));
        })
        .catch(() => setAvailableSlots([]))
        .finally(() => setLoadingSlots(false));
  }, [moveDate, moveModal, headers]);

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
    } catch (err) {
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

  async function loadTiers() {
    setTierLoading(true);
    try {
      const res = await fetch(`${baseUrl}/api/admin/tiers`, {
        headers,
        credentials: "include",
      });
      if (!res.ok) throw new Error(await res.text());
      setTiers(await res.json());
    } catch (err) {
      setError(err.message || "Failed to load tiers.");
    } finally {
      setTierLoading(false);
    }
  }

  async function updateTierPrice(tierId, priceCents) {
    setTierLoading(true);
    try {
      const res = await fetch(`${baseUrl}/api/admin/tiers/${tierId}/price`, {
        method: "PUT",
        headers,
        credentials: "include",
        body: JSON.stringify({ priceCents }),
      });
      if (!res.ok) throw new Error(await res.text());
      setMessage("Pricing updated.");
      await loadTiers();
    } catch (err) {
      setError(err.message || "Failed to update price.");
    } finally {
      setTierLoading(false);
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
        credentials: "include",
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

  async function handleMove() {
    if (!selectedTargetId || !moveModal) return;
    setMoving(true);
    setError("");
    setMessage("");

    try {
      const res = await fetch(
          `${baseUrl}/api/admin/reservations/${moveModal.reservation.reservationId}/move`,
          {
            method: "POST",
            headers,
            credentials: "include",
            body: JSON.stringify({ targetTeeTimeId: selectedTargetId }),
          }
      );

      if (!res.ok) {
        throw new Error(await res.text());
      }

      setMessage(
          `Reservation ${moveModal.reservation.reservationId} moved successfully. A confirmation email has been sent to ${moveModal.reservation.customerEmail}.`
      );
      closeMoveModal();
      await loadTeeSheet();
    } catch (err) {
      setError(err.message || "Failed to move reservation.");
    } finally {
      setMoving(false);
    }
  }

  function openMoveModal(row, slotLabel) {
    setMoveModal({ reservation: row, currentSlotLabel: slotLabel });
    const currentDate = (row.startTime || "").slice(0, 10);
    setMoveDate(currentDate || new Date().toISOString().slice(0, 10));
    setSelectedTargetId(null);
    setAvailableSlots([]);
    setError("");
    setMessage("");
  }

  function closeMoveModal() {
    setMoveModal(null);
    setMoveDate("");
    setAvailableSlots([]);
    setSelectedTargetId(null);
  }

  async function seedDate() {
    if (!window.confirm(`Seed tee times for ${date}?`)) return;

    setLoading(true);
    setError("");
    setMessage("");

    try {
      const res = await fetch(`${baseUrl}/api/admin/tee-times/seed/${date}`, {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
        },
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

      const res = await fetch(
          `${baseUrl}/api/admin/tee-times/${teeTime.teeTimeId}/block`,
          {
            method: "PUT",
            headers,
            credentials: "include",
            body: JSON.stringify({
              blocked: !teeTime.blocked,
              blockedReason: !teeTime.blocked ? blockedReason : null,
            }),
          }
      );

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

              <form onSubmit={handleLogin}>
                <div className="field">
                  <label>Email</label>
                  <input
                      type="email"
                      value={loginEmail}
                      onChange={(e) => setLoginEmail(e.target.value)}
                      autoComplete="username"
                  />
                </div>

                <div className="field">
                  <label>Password</label>
                  <input
                      type="password"
                      value={loginPassword}
                      onChange={(e) => setLoginPassword(e.target.value)}
                      autoComplete="current-password"
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
          <div
              className="pageHeader"
              style={{
                display: "flex",
                justifyContent: "space-between",
                gap: 16,
                alignItems: "flex-start",
                marginBottom: 20,
              }}
          >
            <div>
              <h1>Royal Chappy Admin</h1>
              <p className="subtitle">
                Tee sheet, blocking, check-in, and payment actions
              </p>
              <div className="muted">
                Signed in as {currentUser.name || currentUser.email} (
                {currentUser.role})
              </div>
            </div>

            <div className="buttonRow">
              <button
                  onClick={handleLogout}
                  className="secondary"
                  disabled={loading}
              >
                Log Out
              </button>
            </div>
          </div>

          <div className="panel controls">
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
              <button
                  onClick={() => { setShowPricingModal(true); loadTiers(); }}
                  className="secondary"
                  disabled={loading}
              >
                Manage Pricing
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
                            Capacity {teeTime.capacity} · Remaining{" "}
                            {teeTime.spotsRemaining}
                          </div>
                          {teeTime.blocked && (
                              <div className="message error inlineMessage">
                                Blocked
                                {teeTime.blockedReason
                                    ? `: ${teeTime.blockedReason}`
                                    : ""}
                              </div>
                          )}
                        </div>

                        <div className="buttonRow">
                          <button
                              onClick={() => toggleBlocked(teeTime)}
                              disabled={
                                  loading || isActionLoading(teeTime.teeTimeId, "block")
                              }
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
                                        <span className={statusClass(status)}>
                                {status}
                              </span>
                                      </div>
                                      <div className="muted">{row.customerEmail}</div>
                                      <div className="details">
                                        <span>Party {row.partySize}</span>
                                        <span>
                                Transportation:{" "}
                                          {row.transportation ? "Yes" : "No"}
                              </span>
                                        <span>{row.tierName}</span>
                                        <span>{formatMoney(row.amountCents)}</span>
                                      </div>
                                    </div>

                                    <div className="buttonRow">
                                      <button
                                          onClick={() =>
                                              runAction(row.reservationId, "capture")
                                          }
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
                                          onClick={() =>
                                              runAction(row.reservationId, "cancel")
                                          }
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
                                          onClick={() =>
                                              runAction(row.reservationId, "refund")
                                          }
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

                                      <button
                                          onClick={() =>
                                              runAction(row.reservationId, "no-show")
                                          }
                                          disabled={
                                              loading ||
                                              isActionLoading(row.reservationId, "no-show") ||
                                              !canNoShow(status)
                                          }
                                          className="secondary"
                                      >
                                        {isActionLoading(row.reservationId, "no-show")
                                            ? "Processing..."
                                            : "No Show (50%)"}
                                      </button>

                                      <button
                                          onClick={() =>
                                              openMoveModal(row, teeTime.slotLabel)
                                          }
                                          disabled={
                                              loading ||
                                              isActionLoading(row.reservationId, "move") ||
                                              !canMove(status)
                                          }
                                          className="secondary"
                                      >
                                        Move
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

        {/* ── Move Modal ── */}
        {moveModal && (
            <div className="modalOverlay" onClick={closeMoveModal}>
              <div className="modal" onClick={(e) => e.stopPropagation()}>
                <h2 style={{ marginTop: 0 }}>Move Reservation</h2>

                <div style={{ marginBottom: "1rem" }}>
                  <div>
                    <strong>{moveModal.reservation.customerName}</strong>
                    <span className="muted" style={{ marginLeft: 8 }}>
                  Party of {moveModal.reservation.partySize}
                </span>
                  </div>
                  <div className="muted" style={{ fontSize: "0.85rem", marginTop: 2 }}>
                    Currently on: <strong>{moveModal.currentSlotLabel}</strong>
                  </div>
                </div>

                <div className="field">
                  <label>Move to date</label>
                  <input
                      type="date"
                      value={moveDate}
                      onChange={(e) => setMoveDate(e.target.value)}
                  />
                </div>

                {moveDate && (
                    <div style={{ marginTop: "0.75rem" }}>
                      {loadingSlots ? (
                          <p className="muted">Loading available slots…</p>
                      ) : availableSlots.length === 0 ? (
                          <p className="muted" style={{ fontStyle: "italic" }}>
                            No available slots on this date with enough capacity.
                          </p>
                      ) : (
                          <>
                            <label style={{ display: "block", marginBottom: "0.5rem" }}>
                              Select a slot
                            </label>
                            <div className="slotList">
                              {availableSlots.map((slot) => (
                                  <div
                                      key={slot.id}
                                      className={`slotOption ${
                                          selectedTargetId === slot.id ? "slotOptionSelected" : ""
                                      }`}
                                      onClick={() => setSelectedTargetId(slot.id)}
                                  >
                                    <span>{formatSlotLabel(slot.startTime)}</span>
                                    <span className="muted" style={{ fontSize: "0.8rem" }}>
                            {slot.capacity} spot{slot.capacity !== 1 ? "s" : ""} open
                          </span>
                                  </div>
                              ))}
                            </div>
                          </>
                      )}
                    </div>
                )}

                <div className="buttonRow" style={{ marginTop: "1.5rem" }}>
                  <button onClick={handleMove} disabled={!selectedTargetId || moving}>
                    {moving ? "Moving…" : "Confirm Move"}
                  </button>
                  <button className="secondary" onClick={closeMoveModal}>
                    Cancel
                  </button>
                </div>
              </div>
            </div>
        )}

        {/* ── Pricing Modal ── */}
        {showPricingModal && (
            <div
                style={{
                  position: "fixed", inset: 0, background: "rgba(0,0,0,0.4)",
                  display: "flex", alignItems: "center", justifyContent: "center", zIndex: 1000,
                }}
                onClick={() => setShowPricingModal(false)}
            >
              <div
                  className="panel"
                  style={{ width: 420, maxWidth: "90vw" }}
                  onClick={(e) => e.stopPropagation()}
              >
                <h2 style={{ marginBottom: 16 }}>Manage Tier Pricing</h2>
                {tierLoading ? (
                    <p className="muted">Loading...</p>
                ) : (
                    tiers.map((tier) => (
                        <TierPricingRow key={tier.id} tier={tier} onSave={updateTierPrice} />
                    ))
                )}
                <div className="buttonRow" style={{ marginTop: 16 }}>
                  <button className="secondary" onClick={() => setShowPricingModal(false)}>
                    Close
                  </button>
                </div>
              </div>
            </div>
        )}
      </div>
  );
}