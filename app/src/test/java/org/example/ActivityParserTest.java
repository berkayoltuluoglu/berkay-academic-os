package org.example;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ActivityParserTest {
    @Test void acceptsRangesAndTurkishDurationUnits(){
        for(String dash:new String[]{"-","–","—","ile"}){
            var value=ActivityParser.parse("10:30 "+dash+" 11:15 Sinyaller çıkmış soru çözdüm");
            assertEquals(45,value.minutes());assertEquals("10:30",value.start());assertEquals("11:15",value.end());
        }
        assertEquals(90,ActivityParser.parse("1,5 saat kontrol çalıştım").minutes());
        assertEquals(90,ActivityParser.parse("1 sa 30 dk rapor yazdım").minutes());
        assertEquals(30,ActivityParser.parse("30 dk kontrol çalıştım").minutes());
        assertFalse(ActivityParser.parse("30 dakika kontrol").timed());
    }
    @Test void rejectsImpossibleTimesMissingDescriptionsAndFractionalMinutes(){
        for(String value:new String[]{"25:10–11:15 rapor","10:60–11:15 rapor","11:15–10:30 rapor","10:30 rapor","30 kontrol",
                "0 dk rapor","30 dk","10:30–11:15","17 sa rapor","0,01 saat rapor",""})
            assertThrows(IllegalArgumentException.class,()->ActivityParser.parse(value),value);
    }
}
