package org.athletica.crm.contracts

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonClassDiscriminator
import org.athletica.crm.core.DayOfWeek
import org.athletica.crm.core.money.Money
import org.athletica.crm.routes.getContract
import org.athletica.crm.routes.postContract
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.time.Instant
import kotlin.uuid.Uuid

/** Идентификатор тестовой сущности. */
@Serializable
@JvmInline
value class GadgetId(val value: Uuid)

/** Идентификатор другой тестовой сущности. */
@Serializable
@JvmInline
value class WidgetId(val value: Uuid)

/** Тестовое перечисление. */
@Serializable
enum class Color {
    RED,

    @SerialName("green")
    GREEN,
}

/** Sealed-иерархия с дискриминатором по умолчанию. */
@Serializable
sealed class Shape {
    /** Вариант с полем. */
    @Serializable
    @SerialName("circle")
    data class Circle(val radius: Double) : Shape()

    /** Вариант без полей. */
    @Serializable
    @SerialName("square_box")
    data object Square : Shape()
}

/** Sealed-иерархия со своим дискриминатором. */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
@JsonClassDiscriminator("kind")
sealed interface Payload {
    /** Текстовый вариант. */
    @Serializable
    @SerialName("text")
    data class Text(val value: String) : Payload
}

/** Ответ со всеми видами типов, которые знает генератор. */
@Serializable
data class Everything(
    val text: String,
    val flag: Boolean,
    val count: Int,
    val big: Long,
    val ratio: Double,
    val gadgetId: GadgetId,
    val widgetId: WidgetId,
    val at: Instant,
    val date: LocalDate,
    val time: LocalTime,
    val day: DayOfWeek,
    val color: Color,
    val maybe: String?,
    val numbers: List<Int>,
    val tags: Set<String>,
    val byName: Map<String, Int>,
    val byColor: Map<Color, Int>,
    val shape: Shape,
    val payload: Payload,
    val money: Money,
    val withDefault: Int = 1,
)

/** Тип со своим сериализатором, для которого нет правила отображения. */
@Serializable(with = OddSerializer::class)
class Odd(val raw: String)

/** Сериализатор [Odd] в строку. */
object OddSerializer : KSerializer<Odd> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("test.Odd", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: Odd) = encoder.encodeString(value.raw)

    override fun deserialize(decoder: Decoder): Odd = Odd(decoder.decodeString())
}

/** Ответ с полем неизвестного типа. */
@Serializable
data class WithOdd(val odd: Odd)

/** Класс, чьё имя в TypeScript совпадёт с [DupB]. */
@Serializable
@SerialName("org.first.Dup")
data class DupA(val a: Int)

/** Класс, чьё имя в TypeScript совпадёт с [DupA]. */
@Serializable
@SerialName("org.second.Dup")
data class DupB(val b: Int)

/** Ответ с двумя классами одного простого имени. */
@Serializable
data class WithDuplicates(val first: DupA, val second: DupB)

/** Настройки, которые и принимаются, и отдаются. */
@Serializable
data class Prefs(val pageSize: Int = 20, val color: Color)

/** Запрос, который встречается только в запросах. */
@Serializable
data class SearchRequest(val query: String, val limit: Int = 50)

/** Тесты генерации zod-схем по дескрипторам. */
class ContractGeneratorTest {
    private val everything by lazy { contractsText(listOf(Endpoint("things/detail", getContract<Unit, Everything>()))) }

    @Test
    fun `примитивы`() {
        listOf(
            "text: z.string(),",
            "flag: z.boolean(),",
            "count: z.int32(),",
            "big: z.int(),",
            "ratio: z.number(),",
        ).forEach { assertContains(everything, it) }
    }

    @Test
    fun `идентификаторы брендированы и не взаимозаменяемы`() {
        assertContains(everything, "export const GadgetIdSchema = z.uuid().brand<\"GadgetId\">();")
        assertContains(everything, "export const WidgetIdSchema = z.uuid().brand<\"WidgetId\">();")
        assertContains(everything, "gadgetId: GadgetIdSchema,")
    }

    @Test
    fun `даты и время — строки ISO со своими брендами`() {
        assertContains(everything, "export const InstantSchema = z.iso.datetime({ offset: true }).brand<\"Instant\">();")
        assertContains(everything, "export const LocalDateSchema = z.iso.date().brand<\"LocalDate\">();")
        assertContains(everything, "export const LocalTimeSchema = z.iso.time().brand<\"LocalTime\">();")
        assertContains(everything, "export const DayOfWeekSchema = z.enum([\"MONDAY\", \"TUESDAY\", \"WEDNESDAY\", \"THURSDAY\", \"FRIDAY\", \"SATURDAY\", \"SUNDAY\"]);")
    }

    @Test
    fun `перечисление учитывает SerialName`() {
        assertContains(everything, "export const ColorSchema = z.enum([\"RED\", \"green\"]);")
    }

    @Test
    fun `nullable и коллекции`() {
        assertContains(everything, "maybe: z.string().nullable(),")
        assertContains(everything, "numbers: z.array(z.int32()).readonly(),")
        assertContains(everything, "tags: z.array(z.string()).readonly(),")
        assertContains(everything, "byName: z.record(z.string(), z.int32()).readonly(),")
        assertContains(everything, "byColor: z.partialRecord(ColorSchema, z.int32()).readonly(),")
    }

    @Test
    fun `sealed-иерархия — размеченное объединение`() {
        assertContains(everything, "export const ShapeSchema = z.discriminatedUnion(\"type\", [ShapeCircleSchema, ShapeSquareBoxSchema]);")
        assertContains(everything, "type: z.literal(\"circle\"),")
        assertContains(everything, "export const ShapeSquareBoxSchema = z\n  .object({\n    type: z.literal(\"square_box\"),\n  })")
    }

    @Test
    fun `свой дискриминатор из JsonClassDiscriminator`() {
        assertContains(everything, "export const PayloadSchema = z.discriminatedUnion(\"kind\", [PayloadTextSchema]);")
        assertContains(everything, "kind: z.literal(\"text\"),")
    }

    @Test
    fun `свой сериализатор из таблицы правил`() {
        assertContains(everything, "export const CurrencySchema = z.enum([\"RUB\", \"USD\", \"EUR\", \"KZT\", \"BYN\", \"UAH\"]);")
        assertContains(everything, "minorUnits: z.int(),")
    }

    @Test
    fun `поле с дефолтом обязательно в ответе`() {
        assertContains(everything, "withDefault: z.int32(),")
        assertFalse(everything.contains("withDefault: z.int32().optional()"))
    }

    @Test
    fun `эндпоинт описан в объекте endpoints`() {
        assertContains(everything, "  \"things/detail\": {\n    method: \"GET\",\n    in: \"none\",\n    request: z.undefined(),\n    out: \"json\",\n    response: EverythingSchema,\n  },")
    }

    @Test
    fun `тип только в запросах получает необязательные поля с дефолтом`() {
        val text = contractsText(listOf(Endpoint("things/search", postContract<SearchRequest, Unit>())))

        assertContains(text, "export const SearchRequestSchema = z")
        assertContains(text, "limit: z.int32().optional(),")
        assertContains(text, "request: SearchRequestSchema,")
        assertFalse(text.contains("SearchRequestInput"))
    }

    @Test
    fun `тип в обеих ролях получает две схемы`() {
        val text =
            contractsText(
                listOf(
                    Endpoint("prefs", getContract<Unit, Prefs>()),
                    Endpoint("prefs/update", postContract<Prefs, Prefs>()),
                ),
            )

        assertContains(text, "export const PrefsSchema = z\n  .object({\n    pageSize: z.int32(),\n    color: ColorSchema,\n  })")
        assertContains(text, "export const PrefsInputSchema = z\n  .object({\n    pageSize: z.int32().optional(),\n    color: ColorSchema,\n  })")
        assertContains(text, "export type PrefsInput = z.output<typeof PrefsInputSchema>;")
        assertContains(text, "    request: PrefsInputSchema,\n    out: \"json\",\n    response: PrefsSchema,")
    }

    @Test
    fun `неизвестный сериализатор роняет генерацию с именем типа`() {
        val error =
            assertFailsWith<ContractGenerationException> {
                contractsText(listOf(Endpoint("odd", getContract<Unit, WithOdd>())))
            }

        assertContains(error.message.orEmpty(), "test.Odd")
    }

    @Test
    fun `совпадение простых имён роняет генерацию`() {
        val error =
            assertFailsWith<ContractGenerationException> {
                contractsText(listOf(Endpoint("dup", getContract<Unit, WithDuplicates>())))
            }

        assertContains(error.message.orEmpty(), "org.first.Dup")
        assertContains(error.message.orEmpty(), "org.second.Dup")
    }
}
