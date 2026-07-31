package goaltracker.model

import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.*

object JsonCodecs:
  given Encoder[HabitId] = Encoder.encodeString.contramap(_.value)
  given Decoder[HabitId] = Decoder.decodeString.map(HabitId.apply)

  given Encoder[GoalId] = Encoder.encodeString.contramap(_.value)
  given Decoder[GoalId] = Decoder.decodeString.map(GoalId.apply)

  given Encoder[CheckInId] = Encoder.encodeString.contramap(_.value)
  given Decoder[CheckInId] = Decoder.decodeString.map(CheckInId.apply)

  given Encoder[DeviceId] = Encoder.encodeString.contramap(_.value)
  given Decoder[DeviceId] = Decoder.decodeString.map(DeviceId.apply)

  given Encoder[UserId] = Encoder.encodeString.contramap(_.value)
  given Decoder[UserId] = Decoder.decodeString.map(UserId.apply)

  given Encoder[Habit] = deriveEncoder
  given Decoder[Habit] = deriveDecoder

  given Encoder[HabitCheckIn] = deriveEncoder
  given Decoder[HabitCheckIn] = deriveDecoder

  given Encoder[Goal] = deriveEncoder
  given Decoder[Goal] = deriveDecoder

  given Encoder[SyncDocument] = deriveEncoder
  given Decoder[SyncDocument] = deriveDecoder

  given Encoder[AuthRequest] = deriveEncoder
  given Decoder[AuthRequest] = deriveDecoder

  given Encoder[AuthResponse] = deriveEncoder
  given Decoder[AuthResponse] = deriveDecoder

  given Encoder[SyncRequest] = deriveEncoder
  given Decoder[SyncRequest] = deriveDecoder

  given Encoder[SyncResponse] = deriveEncoder
  given Decoder[SyncResponse] = deriveDecoder
