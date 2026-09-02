package io.github.aofeiliu.charsorter.client

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive

/** One of the caller's character lists, as returned by `GET /api/lists`. */
@Serializable
data class CharacterList(
    val id: Int,
    val title: String,
    @SerialName("controller_type") val controllerType: String,
    @SerialName("show_images") val showImages: Boolean
)

@Serializable
internal data class ListsResponse(val lists: List<CharacterList>)

/** A cached Google image result. Only present when the list shows images. */
@Serializable
data class CharacterImage(
    @SerialName("thumbnailLink") val thumbnailLink: String,
    @SerialName("contextLink") val contextLink: String
)

@Serializable
data class Character(
    val id: Int,
    val name: String,
    val fandom: String,
    val image: CharacterImage? = null
)

/**
 * A character in the ranked order returned by `GET /api/lists/<id>`.
 *
 * `annotation` is not one type across controllers: InsertionSortController
 * sends a string ("Unsorted", "Now Sorting") or null, GlickoRatingController
 * sends an integer rating. It is decoded to its text either way.
 */
@Serializable
data class RankedCharacter(
    val id: Int,
    val name: String,
    val fandom: String,
    val rank: Int,
    @Serializable(with = AnnotationSerializer::class) val annotation: String? = null
)

/** The ranked list: `GET /api/lists/<id>`. */
@Serializable
data class Ranking(
    val id: Int,
    val title: String,
    @SerialName("controller_type") val controllerType: String,
    @SerialName("show_images") val showImages: Boolean,
    val progress: String? = null,
    val characters: List<RankedCharacter> = emptyList()
)

/**
 * The pair to ask next: `GET /api/lists/<id>/next`.
 *
 * Glicko samples this from a softmax, so it is not re-fetchable — see
 * [CharSorterClient.nextComparison].
 */
@Serializable
data class NextComparison(
    val done: Boolean,
    val char1: Character? = null,
    val char2: Character? = null,
    val progress: String? = null
)

/**
 * A stored comparison, as returned by the `201` from
 * `POST /api/lists/<id>/comparisons`.
 *
 * The [id] is the only place a record id ever appears — there is no `GET` on
 * `/comparisons` — so undo is possible only for records this process posted.
 */
@Serializable
data class Comparison(
    val id: Int,
    val char1: Int,
    val char2: Int,
    val value: Int,
    val timestamp: String
)

/**
 * The three values `process_record` can read. A larger one diverges the
 * ratings until `math.pow` overflows and every read path for that list 500s
 * permanently, so the wire value is not caller-supplied.
 */
enum class Verdict(val wireValue: Int) {
    CHAR1_WINS(1),
    TIE(0),
    CHAR2_WINS(-1);

    companion object {
        fun fromWireValue(value: Int): Verdict = entries.first { it.wireValue == value }
    }
}

/** Renders a string-or-number-or-null annotation as its text. */
internal object AnnotationSerializer : KSerializer<String?> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("Annotation", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): String? {
        val element = (decoder as JsonDecoder).decodeJsonElement()
        if (element is JsonNull) {
            return null
        }
        return (element as JsonPrimitive).content
    }

    @OptIn(ExperimentalSerializationApi::class)
    override fun serialize(encoder: Encoder, value: String?) {
        if (value == null) encoder.encodeNull() else encoder.encodeString(value)
    }
}
