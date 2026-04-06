package org.chappyGolf.services;

import org.apache.cayenne.ObjectContext;
import org.apache.cayenne.query.ObjectSelect;
import org.chappyGolf.model.cayenne.TeeTime;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Service
public class TeeTimeSeedService {

    private final ObjectContext context;

    public TeeTimeSeedService(ObjectContext context) {
        this.context = context;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void seedOnStartup() {
        seedNextTwoWeeks();
    }

    @Scheduled(cron = "0 0 6 * * SUN", zone = "America/New_York")
    public void seedNextTwoWeeks() {
        LocalDate today = LocalDate.now();
        for (int i = 0; i <= 13; i++) {
            seedForDate(today.plusDays(i));
        }
    }

    public void seedForDate(LocalDate date) {
        for (int hour = 10; hour <= 17; hour++) {
            LocalDateTime startTime = LocalDateTime.of(date, LocalTime.of(hour, 0));

            TeeTime existing = ObjectSelect.query(TeeTime.class)
                    .where(TeeTime.START_TIME.eq(startTime))
                    .selectOne(context);

            if (existing == null) {
                TeeTime teeTime = context.newObject(TeeTime.class);
                teeTime.setStartTime(startTime);
                teeTime.setCapacity(12);
            }
        }

        context.commitChanges();
    }
}