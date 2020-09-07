package slp.oauth.auth.dao

import java.security.MessageDigest
import java.sql.Timestamp

import javax.inject.Inject
import play.api.Logging
import play.api.db.slick.{DatabaseConfigProvider, DbName, HasDatabaseConfig, SlickApi}
import slick.jdbc.{GetResult, JdbcProfile}

import scala.concurrent.{ExecutionContext, Future}

class AccountDAO @Inject()(slickApi: SlickApi,
                           protected val dbConfigProvider: DatabaseConfigProvider)
                          (implicit exec: ExecutionContext) extends HasDatabaseConfig[JdbcProfile] with Logging {
  val dbConfig = dbConfigProvider.get[JdbcProfile]
  import profile.api._

  private def digestString(s: String): String = {
    val md = MessageDigest.getInstance("SHA-1")
    md.update(s.getBytes)
    md.digest.foldLeft("") { (s, b) =>
      s + "%02x".format(if (b < 0) b + 256 else b)
    }
  }

  def authenticate(email: String, password: String): Future[Option[Account]] = {
    val hashedPassword = digestString(password)

    val acc = accounts.filter(a => a.email === email && a.password === hashedPassword)

    slickApi.dbConfig(DbName("default")).db.run (
      acc.result.headOption
    )
  }

  implicit val getAccountWithClientResult = GetResult(r => AccountWithClientId(Account(r.<<, r.<<, r.<<), r.<<))
  implicit val getAccountResult = GetResult(r => Account(r.<<, r.<<, r.<<))


  def findByRefreshToken(refreshToken: String): Future[Option[AccountWithClientId]] = {
    val sql = sql"SELECT a.id, a.email, a.password, c.client_id FROM account a LEFT JOIN oauth_client c ON c.owner_id = a.id WHERE EXISTS (SELECT NULL FROM oauth_access_token oat WHERE oat.account_id = a.id AND oat.refresh_token = $refreshToken)"
    slickApi.dbConfig(DbName("default")).db.run (
      sql.as[AccountWithClientId].headOption
    )
  }

  def findByToken(token: String): Future[Option[AccountWithClientId]] = {
    val sql = sql"SELECT a.id, a.email, a.password, c.client_id FROM account a LEFT JOIN oauth_client c ON c.owner_id = a.id WHERE EXISTS (SELECT NULL FROM oauth_access_token oat WHERE oat.account_id = a.id AND oat.access_token = $token)"
    slickApi.dbConfig(DbName("default")).db.run (
      sql.as[AccountWithClientId]
        .headOption
    )
  }

  def findByAuthorizationCode(code: String): Future[Option[AccountWithClientId]] = {
    val sql = sql"SELECT a.id, a.email, a.password, c.client_id FROM account a LEFT JOIN oauth_client c ON c.owner_id = a.id WHERE EXISTS (SELECT NULL FROM oauth_authorization_code oat WHERE oat.account_id = a.id AND oat.code = $code)"
    slickApi.dbConfig(DbName("default")).db.run (
      sql.as[AccountWithClientId]
        .headOption
    )
  }

  def findByClientCredentials(clientId: String, clientSecret: String): Future[Option[Account]] = {
    val sql = sql"SELECT a.id, a.email, a.password FROM account a LEFT JOIN oauth_client c ON c.owner_id = a.id WHERE c.client_id = $clientId AND c.client_secret = $clientSecret"
    slickApi.dbConfig(DbName("default")).db.run (
      sql.as[Account]
        .headOption
    )
  }


  class Accounts(tag: Tag) extends Table[Account](tag, "account") {
    def id = column[Int]("id", O.PrimaryKey, O.AutoInc)
    def email = column[String]("email")
    def password = column[String]("password")

    def * = (id, email, password) <> (Account.tupled, Account.unapply)
  }

  val accounts = TableQuery[Accounts]
}

case class Account(id: Int, email: String, password: String)
case class AccountWithClientId(account: Account, clientId: String)
