import { useEffect, useMemo, useState } from "react";
import { CreditCard, PaymentForm } from "react-square-web-payments-sdk";
import "./App.css";

function formatMoney(amountCents) {
  return new Intl.NumberFormat("en-US", {
    style: "currency",
    currency: "USD",
  }).format((amountCents || 0) / 100);
}

function formatSlot(dateTime) {
  if (!dateTime) return "";
  const parsed = new Date(dateTime);
  if (Number.isNaN(parsed.getTime())) return dateTime;

  return new Intl.DateTimeFormat("en-US", {
    hour: "numeric",
    minute: "2-digit",
  }).format(parsed);
}

function formatLongDate(dateStr) {
  if (!dateStr) return "";
  const [year, month, day] = dateStr.split("-").map(Number);
  const dt = new Date(year, (month || 1) - 1, day || 1);

  return new Intl.DateTimeFormat("en-US", {
    weekday: "long",
    month: "long",
    day: "numeric",
    year: "numeric",
  }).format(dt);
}

function todayInputValue() {
  const now = new Date();
  const year = now.getFullYear();
  const month = String(now.getMonth() + 1).padStart(2, "0");
  const day = String(now.getDate()).padStart(2, "0");
  return `${year}-${month}-${day}`;
}

export default function App() {
  const squareAppId = import.meta.env.VITE_SQUARE_APP_ID;
  const squareLocationId = import.meta.env.VITE_SQUARE_LOCATION_ID;

  const [baseUrl, setBaseUrl] = useState("http://localhost:8081");
  const [selectedDate, setSelectedDate] = useState(todayInputValue());
  const [teeTimes, setTeeTimes] = useState([]);
  const [loadingSlots, setLoadingSlots] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [success, setSuccess] = useState("");
  const [error, setError] = useState("");

  const [form, setForm] = useState({
    name: "",
    email: "",
    partySize: 1,
    teeTimeId: null,
    teeTimeLabel: "",
    teeTimeTierId: null,
    teeTimeTierName: "",
    teeTimeTierPriceCents: 0,
  });

  useEffect(() => {
    loadTeeTimes();
  }, []);

  const filteredTeeTimes = useMemo(() => {
    return teeTimes
      .filter((slot) => {
        if (!slot?.startTime) return false;
        return slot.startTime.slice(0, 10) === selectedDate;
      })
      .sort((a, b) => (a.startTime || "").localeCompare(b.startTime || ""));
  }, [teeTimes, selectedDate]);

  const selectedSlot = useMemo(() => {
    return filteredTeeTimes.find(
      (slot) => Number(slot.id) === Number(form.teeTimeId)
    );
  }, [filteredTeeTimes, form.teeTimeId]);

  const totalAmountCents = useMemo(() => {
    return Number(form.partySize || 0) * Number(form.teeTimeTierPriceCents || 0);
  }, [form.partySize, form.teeTimeTierPriceCents]);

  async function loadTeeTimes() {
    setLoadingSlots(true);
    setError("");
    setSuccess("");

    try {
      const res = await fetch(`${baseUrl}/api/tee-times`);

      if (!res.ok) {
        throw new Error(await res.text());
      }

      const data = await res.json();
      setTeeTimes(Array.isArray(data) ? data : []);
    } catch (err) {
      setTeeTimes([]);
      setError(err.message || "Failed to load tee times.");
    } finally {
      setLoadingSlots(false);
    }
  }

  function updateField(key, value) {
    setForm((prev) => ({
      ...prev,
      [key]: value,
    }));
  }

  function selectSlot(slot) {
    setForm((prev) => ({
      ...prev,
      teeTimeId: slot.id,
      teeTimeLabel: formatSlot(slot.startTime),
      teeTimeTierId: null,
      teeTimeTierName: "",
      teeTimeTierPriceCents: 0,
    }));
  }

  function selectTier(slot, tier) {
    setForm((prev) => ({
      ...prev,
      teeTimeId: slot.id,
      teeTimeLabel: formatSlot(slot.startTime),
      teeTimeTierId: tier.id,
      teeTimeTierName: tier.name,
      teeTimeTierPriceCents: tier.priceCents,
    }));
  }

  function resetForm() {
    setForm({
      name: "",
      email: "",
      partySize: 1,
      teeTimeId: null,
      teeTimeLabel: "",
      teeTimeTierId: null,
      teeTimeTierName: "",
      teeTimeTierPriceCents: 0,
    });
    setSuccess("");
    setError("");
  }

  function validate() {
    if (!form.name.trim()) return "Name is required.";
    if (!form.email.trim()) return "Email is required.";

    const partySize = Number(form.partySize);
    if (partySize < 1 || partySize > 6) {
      return "Party size must be between 1 and 6.";
    }

    if (!form.teeTimeId) return "Please select a tee time.";
    if (!form.teeTimeTierId) return "Please select a tier.";

    if (selectedSlot && partySize > Number(selectedSlot.capacity || 0)) {
      return "Party size is larger than remaining capacity.";
    }

    return "";
  }

  async function submitReservationWithToken(sourceId) {
    setError("");
    setSuccess("");

    const validationError = validate();
    if (validationError) {
      setError(validationError);
      return;
    }

    setSubmitting(true);

    try {
      const payload = {
        name: form.name.trim(),
        email: form.email.trim(),
        teeTimeId: Number(form.teeTimeId),
        teeTimeTierId: Number(form.teeTimeTierId),
        partySize: Number(form.partySize),
        sourceId,
      };

      const res = await fetch(`${baseUrl}/api/client/reservations`, {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
        },
        body: JSON.stringify(payload),
      });

      if (!res.ok) {
        throw new Error(await res.text());
      }

      const data = await res.json();

      setSuccess(
        `Reservation #${data.reservationId} created. Payment status: ${data.paymentStatus}.`
      );

      await loadTeeTimes();

      setForm((prev) => ({
        ...prev,
        teeTimeId: null,
        teeTimeLabel: "",
        teeTimeTierId: null,
        teeTimeTierName: "",
        teeTimeTierPriceCents: 0,
      }));
    } catch (err) {
      setError(err.message || "Failed to create reservation.");
    } finally {
      setSubmitting(false);
    }
  }

  async function handleCardTokenize(tokenResult) {
    if (tokenResult.status !== "OK") {
      const message =
        tokenResult.errors?.map((e) => e.message).join(", ") ||
        "Card tokenization failed.";
      setError(message);
      return;
    }

    await submitReservationWithToken(tokenResult.token);
  }

  const squareReady = squareAppId && squareLocationId;

  return (
    <div className="page">
      <div className="container">
        <header className="hero">
          <h1>Royal Chappy Booking</h1>
          <p className="subtitle">
            Choose your date, pick a tee time, and reserve your round.
          </p>
        </header>

        {success && <div className="message success">{success}</div>}
        {error && <div className="message error">{error}</div>}

        <div className="layout">
          <section className="panel">
            <div className="sectionHeader">
              <div>
                <h2>Available tee times</h2>
                <p className="muted">{formatLongDate(selectedDate)}</p>
              </div>
            </div>

            <div className="controls">
              <div className="fieldRow">
                <div className="field">
                  <label>Backend URL</label>
                  <input
                    value={baseUrl}
                    onChange={(e) => setBaseUrl(e.target.value)}
                  />
                </div>

                <div className="field">
                  <label>Date</label>
                  <input
                    type="date"
                    value={selectedDate}
                    onChange={(e) => setSelectedDate(e.target.value)}
                  />
                </div>
              </div>

              <div className="buttonRow">
                <button onClick={loadTeeTimes} disabled={loadingSlots}>
                  {loadingSlots ? "Refreshing..." : "Refresh Tee Times"}
                </button>
              </div>
            </div>

            <div className="slotGrid">
              {filteredTeeTimes.length === 0 ? (
                <div className="emptyState">
                  No tee times found for this date.
                </div>
              ) : (
                filteredTeeTimes.map((slot) => {
                  const isSelected =
                    Number(form.teeTimeId) === Number(slot.id);
                  const soldOut = Number(slot.capacity || 0) < 1;

                  return (
                    <div
                      key={slot.id}
                      className={`slotCard ${isSelected ? "selected" : ""}`}
                    >
                      <div className="slotTop">
                        <div>
                          <div className="slotTime">
                            {formatSlot(slot.startTime)}
                          </div>
                          <div className="muted">Tee Time #{slot.id}</div>
                        </div>

                        <div className={`badge ${soldOut ? "soldOut" : ""}`}>
                          {soldOut
                            ? "Sold out"
                            : `${slot.capacity} spot${
                                Number(slot.capacity) === 1 ? "" : "s"
                              } left`}
                        </div>
                      </div>

                      <div className="tierRow">
                        {(slot.tiers || []).map((tier) => {
                          const tierSelected =
                            isSelected &&
                            Number(form.teeTimeTierId) === Number(tier.id);

                          return (
                            <button
                              key={tier.id}
                              type="button"
                              className={`tierButton ${
                                tierSelected ? "selectedTier" : ""
                              }`}
                              disabled={soldOut}
                              onClick={() => selectTier(slot, tier)}
                            >
                              <span>{tier.name}</span>
                              <span className="tierPrice">
                                {formatMoney(tier.priceCents)} / player
                              </span>
                            </button>
                          );
                        })}
                      </div>

                      <div className="buttonRow">
                        <button
                          type="button"
                          className="secondary"
                          disabled={soldOut}
                          onClick={() => selectSlot(slot)}
                        >
                          {isSelected ? "Selected" : "Select Tee Time"}
                        </button>
                      </div>
                    </div>
                  );
                })
              )}
            </div>
          </section>

          <section className="panel">
            <h2>Complete booking</h2>
            <p className="muted">
              Secure card entry is handled by Square.
            </p>

            <div className="bookingForm">
              <div className="field">
                <label>Full Name</label>
                <input
                  value={form.name}
                  onChange={(e) => updateField("name", e.target.value)}
                  placeholder="Declan Halbert"
                />
              </div>

              <div className="field">
                <label>Email</label>
                <input
                  type="email"
                  value={form.email}
                  onChange={(e) => updateField("email", e.target.value)}
                  placeholder="you@example.com"
                />
              </div>

              <div className="field">
                <label>Party Size</label>
                <input
                  type="number"
                  min="1"
                  max="6"
                  value={form.partySize}
                  onChange={(e) => updateField("partySize", e.target.value)}
                />
              </div>

              <div className="summary">
                <div className="summaryRow">
                  <span>Date</span>
                  <strong>{formatLongDate(selectedDate) || "—"}</strong>
                </div>
                <div className="summaryRow">
                  <span>Tee Time</span>
                  <strong>{form.teeTimeLabel || "—"}</strong>
                </div>
                <div className="summaryRow">
                  <span>Tier</span>
                  <strong>{form.teeTimeTierName || "—"}</strong>
                </div>
                <div className="summaryRow">
                  <span>Party Size</span>
                  <strong>{form.partySize || "—"}</strong>
                </div>
                <div className="summaryRow total">
                  <span>Total</span>
                  <strong>{formatMoney(totalAmountCents)}</strong>
                </div>
              </div>

              {!squareReady ? (
                <div className="message error">
                  Missing Square env vars. Add VITE_SQUARE_APP_ID and
                  VITE_SQUARE_LOCATION_ID to client-ui/.env and restart Vite.
                </div>
              ) : (
                <div className="squareWrap">
                  <PaymentForm
                    applicationId={squareAppId}
                    locationId={squareLocationId}
                    cardTokenizeResponseReceived={handleCardTokenize}
                  >
                    <CreditCard />
                  </PaymentForm>
                </div>
              )}

              <div className="buttonRow">
                <button
                  type="button"
                  className="secondary"
                  onClick={resetForm}
                  disabled={submitting}
                >
                  Reset
                </button>
              </div>
            </div>
          </section>
        </div>
      </div>
    </div>
  );
}