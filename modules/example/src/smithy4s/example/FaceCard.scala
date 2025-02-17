package smithy4s.example

import smithy4s.Enumeration
import smithy4s.Hints
import smithy4s.IntEnum
import smithy4s.Schema
import smithy4s.ShapeId
import smithy4s.ShapeTag
import smithy4s.schema.Schema.enumeration

sealed abstract class FaceCard(_value: String, _name: String, _intValue: Int, _hints: Hints) extends Enumeration.Value {
  override val value: String = _value
  override val name: String = _name
  override val intValue: Int = _intValue
  override val hints: Hints = _hints
  @inline final def widen: FaceCard = this
}
object FaceCard extends Enumeration[FaceCard] with ShapeTag.Companion[FaceCard] {
  val id: ShapeId = ShapeId("smithy4s.example", "FaceCard")

  val hints: Hints = Hints(
    smithy.api.Documentation("FaceCard types"),
    IntEnum(),
  )

  case object JACK extends FaceCard("JACK", "JACK", 1, Hints())
  case object QUEEN extends FaceCard("QUEEN", "QUEEN", 2, Hints())
  case object KING extends FaceCard("KING", "KING", 3, Hints())
  case object ACE extends FaceCard("ACE", "ACE", 4, Hints())
  case object JOKER extends FaceCard("JOKER", "JOKER", 5, Hints())

  val values: List[FaceCard] = List(
    JACK,
    QUEEN,
    KING,
    ACE,
    JOKER,
  )
  implicit val schema: Schema[FaceCard] = enumeration(values).withId(id).addHints(hints)
}