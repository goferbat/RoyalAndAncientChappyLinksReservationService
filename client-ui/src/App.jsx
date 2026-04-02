import { useEffect, useMemo, useState } from "react";
import { CreditCard, PaymentForm } from "react-square-web-payments-sdk";
import "./App.css";

const TRANSPORTATION_PRICE_CENTS = 1000;
const API_BASE_URL =
  import.meta.env.VITE_API_BASE_URL || "http://localhost:8080";

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

function normalizePaymentError(rawMessage) {
  const message = (rawMessage || "").toLowerCase();

  if (
    message.includes("postal") ||
    message.includes("zip") ||
    message.includes("postcode")
  ) {
    return "The postal code looks invalid. Please check it and try again.";
  }

  if (message.includes("cvv") || message.includes("cvc")) {
    return "The CVV is invalid. Please re-enter the security code.";
  }

  if (
    message.includes("expiration") ||
    message.includes("expiry") ||
    message.includes("exp date")
  ) {
    return "The expiration date looks invalid. Please check it and try again.";
  }

  if (
    message.includes("card number") ||
    message.includes("pan") ||
    message.includes("invalid card")
  ) {
    return "The card number looks invalid. Please check it and try again.";
  }

  if (
    message.includes("declined") ||
    message.includes("insufficient") ||
    message.includes("do not honor")
  ) {
    return "Your card was declined. Please try another payment method.";
  }

  if (message.includes("token")) {
    return "We couldn't securely process the card details. Please try again.";
  }

  return rawMessage || "Payment failed. Please review your card details and try again.";
}

export default function App() {
  const squareAppId = import.meta.env.VITE_SQUARE_APP_ID;
  const squareLocationId = import.meta.env.VITE_SQUARE_LOCATION_ID;

  const [selectedDate, setSelectedDate] = useState(todayInputValue());
  const [teeTimes, setTeeTimes] = useState([]);
  const [loadingSlots, setLoadingSlots] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [success, setSuccess] = useState("");
  const [error, setError] = useState("");

  const [paymentResult, setPaymentResult] = useState({
    status: "idle", // idle | success | failure
    reservationId: null,
    paymentStatus: "",
    message: "",
  });

  const [form, setForm] = useState({
    name: "",
    email: "",
    partySize: 1,
    teeTimeId: null,
    teeTimeLabel: "",
    teeTimeTierId: null,
    teeTimeTierName: "",
    teeTimeTierPriceCents: 0,
    transportation: false,
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
    const partySize = Number(form.partySize || 0);
    const tierTotal = partySize * Number(form.teeTimeTierPriceCents || 0);
    const transportationTotal = form.transportation
      ? partySize * TRANSPORTATION_PRICE_CENTS
      : 0;

    return tierTotal + transportationTotal;
  }, [form.partySize, form.teeTimeTierPriceCents, form.transportation]);

  async function loadTeeTimes() {
    setLoadingSlots(true);
    setError("");
    setSuccess("");

    try {
      const res = await fetch(`${API_BASE_URL}/api/tee-times`);

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
    if (slot?.blocked) return;

    setForm((prev) => ({
      ...prev,
      teeTimeId: slot.id,
      teeTimeLabel: formatSlot(slot.startTime),
      teeTimeTierId: null,
      teeTimeTierName: "",
      teeTimeTierPriceCents: 0,
      transportation: false,
    }));
  }

  function selectTier(slot, tier) {
    if (slot?.blocked) return;

    setForm((prev) => ({
      ...prev,
      teeTimeId: slot.id,
      teeTimeLabel: formatSlot(slot.startTime),
      teeTimeTierId: tier.id,
      teeTimeTierName: tier.name,
      teeTimeTierPriceCents: tier.priceCents,
    }));
  }

  function toggleTransportation(slot) {
    if (slot?.blocked) return;

    setForm((prev) => {
      const sameSlot = Number(prev.teeTimeId) === Number(slot.id);

      return {
        ...prev,
        teeTimeId: slot.id,
        teeTimeLabel: formatSlot(slot.startTime),
        transportation: sameSlot ? !prev.transportation : true,
      };
    });
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
      transportation: false,
    });
    setSuccess("");
    setError("");
    setPaymentResult({
      status: "idle",
      reservationId: null,
      paymentStatus: "",
      message: "",
    });
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

    if (selectedSlot?.blocked) {
      return selectedSlot.blockedReason?.trim()
        ? `This tee time is unavailable: ${selectedSlot.blockedReason}`
        : "This tee time is currently unavailable.";
    }

    if (selectedSlot && partySize > Number(selectedSlot.capacity || 0)) {
      return "Party size is larger than remaining capacity.";
    }

    return "";
  }

  async function submitReservationWithToken(sourceId) {
    setError("");
    setSuccess("");
    setPaymentResult({
      status: "idle",
      reservationId: null,
      paymentStatus: "",
      message: "",
    });

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
        transportation: form.transportation,
        sourceId,
      };

      const res = await fetch(`${API_BASE_URL}/api/client/reservations`, {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
        },
        body: JSON.stringify(payload),
      });

      if (!res.ok) {
        const raw = await res.text();
        const friendly = normalizePaymentError(raw);

        setPaymentResult({
          status: "failure",
          reservationId: null,
          paymentStatus: "",
          message: friendly,
        });

        throw new Error(friendly);
      }

      const data = await res.json();

      setSuccess(
        `Reservation #${data.reservationId} created. Payment status: ${data.paymentStatus}.`
      );

      setPaymentResult({
        status: "success",
        reservationId: data.reservationId,
        paymentStatus: data.paymentStatus || "COMPLETED",
        message: "Your booking is confirmed.",
      });

      await loadTeeTimes();
    } catch (err) {
      setError(err.message || "Failed to create reservation.");
    } finally {
      setSubmitting(false);
    }
  }

  async function handleCardTokenize(tokenResult) {
    if (tokenResult.status !== "OK") {
      const rawMessage =
        tokenResult.errors?.map((e) => e.message).join(", ") ||
        "Card tokenization failed.";

      const friendly = normalizePaymentError(rawMessage);

      setPaymentResult({
        status: "failure",
        reservationId: null,
        paymentStatus: "",
        message: friendly,
      });

      setError(friendly);
      return;
    }

    await submitReservationWithToken(tokenResult.token);
  }

  const squareReady = squareAppId && squareLocationId;

  if (paymentResult.status === "success") {
    return (
      <div className="page">
        <div className="container">
          <section className="panel" style={{ maxWidth: 700, margin: "40px auto" }}>
            <h1>Booking Confirmed</h1>
            <p className="subtitle">
              Your payment went through and your reservation is confirmed.
            </p>

            <div className="summary">
              <div className="summaryRow">
                <span>Reservation #</span>
                <strong>{paymentResult.reservationId}</strong>
              </div>
              <div className="summaryRow">
                <span>Status</span>
                <strong>{paymentResult.paymentStatus}</strong>
              </div>
              <div className="summaryRow">
                <span>Email</span>
                <strong>{form.email}</strong>
              </div>
            </div>

            <div className="message success" style={{ marginTop: 20 }}>
              A confirmation email is on the way.
            </div>

            <div className="buttonRow" style={{ marginTop: 20 }}>
              <button type="button" onClick={resetForm}>
                Back to Booking
              </button>
            </div>
          </section>
        </div>
      </div>
    );
  }

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
                  const blocked = !!slot.blocked;
                  const unavailable = soldOut || blocked;
                  const transportationSelected =
                    isSelected && form.transportation;

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

                        <div
                          className={`badge ${
                            blocked ? "blockedBadge" : soldOut ? "soldOut" : ""
                          }`}
                        >
                          {blocked
                            ? "Blocked"
                            : soldOut
                            ? "Sold out"
                            : `${slot.capacity} spot${
                                Number(slot.capacity) === 1 ? "" : "s"
                              } left`}
                        </div>
                      </div>

                      {blocked && (
                        <div className="blockedNotice">
                          {slot.blockedReason?.trim()
                            ? `Blocked: ${slot.blockedReason}`
                            : "This tee time is unavailable."}
                        </div>
                      )}

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
                              disabled={unavailable}
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

                      <div className="tierRow">
                        <button
                          type="button"
                          className={`tierButton ${
                            transportationSelected ? "selectedTier" : ""
                          }`}
                          disabled={unavailable}
                          onClick={() => toggleTransportation(slot)}
                        >
                          <span>Transportation</span>
                          <span className="tierPrice">
                            {formatMoney(TRANSPORTATION_PRICE_CENTS)} / player
                          </span>
                        </button>
                      </div>

                      <div className="buttonRow">
                        <button
                          type="button"
                          className="secondary"
                          disabled={unavailable}
                          onClick={() => selectSlot(slot)}
                        >
                          {blocked
                            ? "Blocked"
                            : isSelected
                            ? "Selected"
                            : "Select Tee Time"}
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
                  <span>Transportation</span>
                  <strong>{form.transportation ? "Yes" : "No"}</strong>
                </div>
                <div className="summaryRow">
                  <span>Party Size</span>
                  <strong>{form.partySize || "—"}</strong>
                </div>
                <div className="summaryRow">
                  <span>Transportation Cost</span>
                  <strong>
                    {form.transportation
                      ? formatMoney(
                          Number(form.partySize || 0) *
                            TRANSPORTATION_PRICE_CENTS
                        )
                      : formatMoney(0)}
                  </strong>
                </div>
                <div className="summaryRow total">
                  <span>Total</span>
                  <strong>{formatMoney(totalAmountCents)}</strong>
                </div>
              </div>

              {!squareReady ? (
                <div className="message error">
                  Missing Square env vars. Add VITE_SQUARE_APP_ID,
                  VITE_SQUARE_LOCATION_ID, and VITE_API_BASE_URL to
                  client-ui/.env and restart Vite.
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

              {paymentResult.status === "failure" && (
                <div className="message error" style={{ marginTop: 16 }}>
                  {paymentResult.message}
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