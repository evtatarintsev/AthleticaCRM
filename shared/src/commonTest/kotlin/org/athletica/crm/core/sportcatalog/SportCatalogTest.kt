package org.athletica.crm.core.sportcatalog

import org.athletica.crm.core.Lang
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SportCatalogTest {
    private val disciplines = SportCatalog.sports.flatMap { it.disciplines }

    @Test
    fun `в каталоге 18 видов спорта`() {
        assertEquals(18, SportCatalog.sports.size)
    }

    @Test
    fun `у каждого вида есть дисциплины`() {
        SportCatalog.sports.forEach {
            assertTrue(it.disciplines.isNotEmpty(), "у вида ${it.key} нет дисциплин")
        }
    }

    @Test
    fun `у каждого элемента непустые названия на всех языках`() {
        val names = SportCatalog.sports.map { it.key to it.names } + disciplines.map { it.key to it.names }
        names.forEach { (key, value) ->
            Lang.entries.forEach {
                assertTrue(value.of(it).isNotBlank(), "у $key пустое название на ${it.code}")
            }
        }
    }

    @Test
    fun `ключи уникальны в каталоге`() {
        val keys = SportCatalog.sports.map { it.key } + disciplines.map { it.key }
        assertEquals(keys.size, keys.toSet().size, "повторяются ключи: ${keys.groupBy { it }.filterValues { it.size > 1 }.keys}")
    }

    @Test
    fun `ключи латинские`() {
        val keys = SportCatalog.sports.map { it.key } + disciplines.map { it.key }
        keys.forEach {
            assertTrue(it.matches(Regex("[a-z_]+")), "ключ $it не латинский")
        }
    }

    @Test
    fun `синонимы непустые`() {
        disciplines.forEach { d ->
            d.aliases.forEach {
                assertTrue(it.isNotBlank(), "пустой синоним у ${d.key}")
            }
        }
    }

    @Test
    fun `Jazz-funk записан латиницей и в RU`() {
        val dance = SportCatalog.sports.single { it.key == "dance" }
        val jazzFunk = dance.disciplines.single { it.key == "jazz_funk" }
        assertEquals("Jazz-funk", jazzFunk.names.of(Lang.RU))
        assertTrue("джаз-фанк" in jazzFunk.aliases)
    }
}
