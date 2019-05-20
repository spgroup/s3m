package com.cronutils;

import com.cronutils.model.Cron;
import com.cronutils.model.definition.CronDefinition;
import com.cronutils.model.definition.CronDefinitionBuilder;
import com.cronutils.model.time.ExecutionTime;
import com.cronutils.parser.CronParser;
import org.junit.Test;
import org.threeten.bp.ZonedDateTime;
import org.junit.Before;
import static org.junit.Assert.assertEquals;

public class Issue228Test {

    private CronDefinition cronDefinition = CronDefinitionBuilder.defineCron().withMinutes().and().withHours().and().withDayOfMonth().and().withMonth().and().withDayOfWeek().withValidRange(0, 7).withMondayDoWValue(1).withIntMapping(7, 0).and().enforceStrictRanges().matchDayOfWeekAndDayOfMonth().instance();

    @Before
    public void setUp() {
        cronDefinition = CronDefinitionBuilder.defineCron().withMinutes().and().withHours().and().withDayOfMonth().supportsL().and().withMonth().and().withDayOfWeek().withValidRange(0, 7).withMondayDoWValue(1).and().enforceStrictRanges().matchDayOfWeekAndDayOfMonth().instance();
    }

    /**
     * Issue #228: dayOfWeek just isn't honored in the cron next execution evaluation and needs to be
     */
    @Test
    public void testFirstMondayOfTheMonthNextExecution() {
        CronParser parser = new CronParser(cronDefinition);
        Cron myCron = parser.parse("0 9 1-7 * 1");
        ZonedDateTime time = ZonedDateTime.parse("2017-09-29T14:46:01.166-07:00");
        assertEquals(ZonedDateTime.parse("2017-10-02T09:00-07:00"), ExecutionTime.forCron(myCron).nextExecution(time).get());
    }

    @Test
    public void testEveryWeekdayFirstWeekOfMonthNextExecution() {
        CronParser parser = new CronParser(cronDefinition);
        Cron myCron = parser.parse("0 9 1-7 * 1-5");
        ZonedDateTime time = ZonedDateTime.parse("2017-09-29T14:46:01.166-07:00");
        assertEquals(ZonedDateTime.parse("2017-10-02T09:00-07:00"), ExecutionTime.forCron(myCron).nextExecution(time).get());
    }

    @Test
    public void testEveryWeekendFirstWeekOfMonthNextExecution() {
        CronParser parser = new CronParser(cronDefinition);
        Cron myCron = parser.parse("0 9 1-7 * 6-7");
        ZonedDateTime time = ZonedDateTime.parse("2017-09-29T14:46:01.166-07:00");
        assertEquals(ZonedDateTime.parse("2017-10-01T09:00-07:00"), ExecutionTime.forCron(myCron).nextExecution(time).get());
    }

    @Test
    public void testEveryWeekdaySecondWeekOfMonthNextExecution() {
        CronParser parser = new CronParser(cronDefinition);
        Cron myCron = parser.parse("0 9 8-14 * 1-5");
        ZonedDateTime time = ZonedDateTime.parse("2017-09-29T14:46:01.166-07:00");
        assertEquals(ZonedDateTime.parse("2017-10-09T09:00-07:00"), ExecutionTime.forCron(myCron).nextExecution(time).get());
    }

    @Test
    public void testEveryWeekendForthWeekOfMonthNextExecution() {
        CronParser parser = new CronParser(cronDefinition);
        Cron myCron = parser.parse("0 9 22-28 * 6-7");
        ZonedDateTime time = ZonedDateTime.parse("2017-09-29T14:46:01.166-07:00");
        assertEquals(ZonedDateTime.parse("2017-10-22T09:00-07:00"), ExecutionTime.forCron(myCron).nextExecution(time).get());
    }
}

