package org.chappyGolf.services;

import org.apache.cayenne.ObjectContext;
import org.apache.cayenne.configuration.server.ServerRuntime;
import org.apache.cayenne.query.ObjectSelect;
import org.chappyGolf.model.cayenne.TeeTime;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Service
public class TeeTimeSeedService {

    private final ServerRuntime cayenneRuntime;

    public TeeTimeSeedService(ServerRuntime cayenneRuntime) {
        this.cayenneRuntime = cayenneRuntime;
    }

    // BE CAREFUL WITH THIS
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
        // Create a fresh standalone context, not request-scoped
        ObjectContext ctx = cayenneRuntime.newContext();

        for (int hour = 10; hour <= 17; hour++) {

            // Wednesday 5pm is reserved for the caddie group — skip seeding it
            if (date.getDayOfWeek() == DayOfWeek.WEDNESDAY && hour == 17) {
                continue;
            }

            LocalDateTime startTime = LocalDateTime.of(date, LocalTime.of(hour, 0));

            TeeTime existing = ObjectSelect.query(TeeTime.class)
                    .where(TeeTime.START_TIME.eq(startTime))
                    .selectOne(ctx);

            if (existing == null) {
                TeeTime teeTime = ctx.newObject(TeeTime.class);
                teeTime.setStartTime(startTime);
                teeTime.setCapacity(12);
            }
        }

        ctx.commitChanges();
    }
}