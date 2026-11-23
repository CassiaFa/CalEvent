package fr.calevent.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PersonNameTest {
    private val me = PersonName(last = "Durand", first = "Élodie")
    private val others = listOf("MARTIN Paul", "Lopez Ana")

    private fun find(label: String, who: PersonName = me) = findPersonByName(others + label, who)

    @Test
    fun everyLabelFormIsRecognised() {
        listOf(
            "DURAND Elodie", "Élodie DURAND", "DURAND E.", "Elodie D.", "M. DURAND", "Mme DURAND",
            "DURAND", "Elodie", "durand  élodie", "E. Durand",
        ).forEach { assertEquals(it, find(it), it) }
    }

    @Test
    fun otherFirstNameWithSameSurnameIsNotMe() {
        assertNull(find("DURAND Marc"))
        assertNull(find("DURAND M."))
    }

    @Test
    fun fullMatchBeatsInitialBeatsSurnameBeatsFirstName() {
        val people = listOf("Elodie", "DURAND", "DURAND E.", "DURAND Elodie")
        assertEquals("DURAND Elodie", findPersonByName(people, me))
        assertEquals("DURAND E.", findPersonByName(people - "DURAND Elodie", me))
        assertEquals("DURAND", findPersonByName(people - "DURAND Elodie" - "DURAND E.", me))
        assertEquals("Elodie", findPersonByName(listOf("Elodie", "Zed"), me))
    }

    @Test
    fun tieSelectsNobody() {
        val surnameOnly = PersonName("Durand")
        assertNull(findPersonByName(listOf("DURAND A.", "DURAND B."), surnameOnly))
        assertNull(findPersonByName(listOf("DURAND", "Durand"), surnameOnly))
        assertNull(findPersonByName(listOf("DURAND", "Durand"), me))
    }

    @Test
    fun firstNameEvidenceBreaksTheTie() {
        assertEquals("DURAND E.", findPersonByName(listOf("DURAND", "DURAND E."), me))
    }

    @Test
    fun noMatchGivesNull() {
        assertNull(find("ZED Yan"))
        assertNull(findPersonByName(others, null))
    }

    @Test
    fun legacySingleStringIsTheSurnameAndStillMatchesFullLabel() {
        val legacy = PersonName.decode("Élodie M.")
        assertEquals("elodie m.", findPersonByName(listOf("Sam", "elodie m."), legacy))
        assertEquals("Durand C.", findPerson(listOf("Sam", "Durand C."), "durand c."))
    }

    @Test
    fun encodeDecodeRoundTrip() {
        assertEquals(me, PersonName.decode(me.encode()))
        assertEquals("Durand", PersonName("Durand").encode())
        assertNull(PersonName("", "").encode())
        assertNull(PersonName.decode(" "))
        assertEquals("Élodie Durand", me.display)
        assertEquals("DURAND E.", findPerson(listOf("Sam", "DURAND E."), me.encode()))
    }
}
