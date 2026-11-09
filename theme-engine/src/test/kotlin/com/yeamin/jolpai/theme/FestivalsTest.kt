package com.yeamin.jolpai.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class FestivalsTest {
    private fun on(y: Int, m: Int, d: Int) = Festivals.on(LocalDate.of(y, m, d))?.theme

    @Test fun `national days`() {
        assertEquals(BuiltInThemes.BOISHAKH, on(2026, 4, 14))
        assertEquals(BuiltInThemes.EKUSHEY, on(2027, 2, 21))
        assertEquals(BuiltInThemes.BIJOY, on(2026, 12, 16))
        assertEquals(BuiltInThemes.BIJOY, on(2026, 3, 26)) // a national day wins over Ramadan or Eid
        assertNull(on(2026, 11, 8))
    }

    @Test fun `eid and ramadan follow the hijri calendar`() {
        assertEquals(BuiltInThemes.RAMADAN, on(2026, 3, 1)) // Ramadan 1447 began mid-February 2026
        assertEquals(BuiltInThemes.EID, on(2026, 3, 21)) // Eid ul-Fitr 1447
        assertEquals(BuiltInThemes.EID, on(2026, 5, 28)) // Eid ul-Adha 1447
        assertNull(on(2026, 7, 1))
    }

    @Test fun `festival themes can be chosen by id like built-in ones`() {
        BuiltInThemes.festivals.forEach { assertEquals(it, BuiltInThemes.byId(it.id)) }
    }
}
