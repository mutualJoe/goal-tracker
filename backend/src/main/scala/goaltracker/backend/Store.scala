package goaltracker.backend

import goaltracker.crdt.Merge
import goaltracker.model.*
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

final class Store:
  private final case class User(id: UserId, username: String, passwordHash: String)
  private final case class Session(userId: UserId, username: String)

  private val usersByName = new ConcurrentHashMap[String, User]()
  private val tokens = new ConcurrentHashMap[String, Session]()
  private val documents = new ConcurrentHashMap[String, SyncDocument]()

  private def hash(password: String): String =
    val digest = MessageDigest.getInstance("SHA-256").digest(password.getBytes("UTF-8"))
    digest.map("%02x".format(_)).mkString

  def register(username: String, password: String): Either[String, AuthResponse] =
    val key = username.trim.toLowerCase
    if usersByName.containsKey(key) then Left("Username already taken")
    else
      val user = User(UserId(UUID.randomUUID().toString), username.trim, hash(password))
      usersByName.put(key, user)
      documents.put(user.id.value, SyncDocument.empty)
      Right(issueToken(user))

  def login(username: String, password: String): Either[String, AuthResponse] =
    val key = username.trim.toLowerCase
    Option(usersByName.get(key)) match
      case Some(user) if user.passwordHash == hash(password) => Right(issueToken(user))
      case _                                                 => Left("Invalid username or password")

  private def issueToken(user: User): AuthResponse =
    val token = UUID.randomUUID().toString
    tokens.put(token, Session(user.id, user.username))
    AuthResponse(token, user.id, user.username)

  def session(token: String): Option[(UserId, String)] =
    Option(tokens.get(token)).map(s => s.userId -> s.username)

  def sync(userId: UserId, incoming: SyncDocument): SyncResponse =
    val key = userId.value
    val existing = Option(documents.get(key)).getOrElse(SyncDocument.empty)
    val merged = Merge.mergeDocuments(existing, incoming)
    documents.put(key, merged)
    SyncResponse(merged, System.currentTimeMillis())

  def userCount: Int = usersByName.size
