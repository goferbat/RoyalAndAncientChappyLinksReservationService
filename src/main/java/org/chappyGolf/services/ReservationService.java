package org.chappyGolf.services;

import org.chappyGolf.dto.Reservation;
import org.chappyGolf.dto.TeeTimeStatus;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.*;

@Service
public class ReservationService {
    public static final int CAPACITY_PER_TIME = 8;

    private static final List<String> TEE_TIMES = List.of(
            "08:00 AM","09:00 AM","10:00 AM","11:00 AM",
            "12:00 PM","01:00 PM","02:00 PM","03:00 PM",
            "04:00 PM","05:00 PM"
    );

    private final ConcurrentMap<String, CopyOnWriteArrayList<Reservation>> buckets = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, String> idToTime = new ConcurrentHashMap<>();

    public ReservationService() {
        TEE_TIMES.forEach(t -> buckets.put(t, new CopyOnWriteArrayList<>()));
    }

    public List<String> getTimesWithAvailability() {
        return TEE_TIMES.stream().filter(t -> remaining(t) > 0).toList();
    }

    public List<TeeTimeStatus> getStatus() {
        return TEE_TIMES.stream()
                .map(t -> new TeeTimeStatus(t, CAPACITY_PER_TIME, buckets.get(t).size(),
                        Math.max(0, CAPACITY_PER_TIME - buckets.get(t).size())))
                .toList();
    }

    public synchronized Reservation reserveOne(String name, String time) {
        if (!buckets.containsKey(time)) throw new IllegalArgumentException("Invalid time slot.");
        var list = buckets.get(time);
        if (list.size() >= CAPACITY_PER_TIME) throw new IllegalStateException("Time slot full.");
        var res = new Reservation(java.util.UUID.randomUUID().toString(), name, time);
        list.add(res);
        idToTime.put(res.id(), time);
        return res;
    }

    public synchronized boolean cancelById(String reservationId) {
        var time = idToTime.remove(reservationId);
        if (time == null) return false;
        var list = buckets.get(time);
        if (list == null) return false;
        return list.removeIf(r -> reservationId.equals(r.id()));
    }

    public Map<String, List<Reservation>> getAllReservations() {
        var out = new LinkedHashMap<String, List<Reservation>>();
        TEE_TIMES.forEach(t -> out.put(t, List.copyOf(buckets.get(t))));
        return out;
    }

    private int remaining(String time) {
        return Math.max(0, CAPACITY_PER_TIME - buckets.get(time).size());
    }

    public Reservation findById(String reservationId) {
        var time = idToTime.get(reservationId);
        if (time == null) return null;
        return buckets.get(time).stream().filter(r -> r.id().equals(reservationId)).findFirst().orElse(null);
    }

}