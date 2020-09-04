package slp.oauth.auth.dao

import java.security.MessageDigest
import java.sql.Timestamp

import javax.inject.Inject
import play.api.Logging
import play.api.db.slick.{DatabaseConfigProvider, DbName, HasDatabaseConfig, SlickApi}
import slick.jdbc.JdbcProfile

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


  class Accounts(tag: Tag) extends Table[Account](tag, "account") {
    def id = column[Int]("id", O.PrimaryKey, O.AutoInc)
    def email = column[String]("email")
    def password = column[String]("password")
    def createdAt = column[Timestamp]("created_at")

    def * = (id, email, password, createdAt) <> (Account.tupled, Account.unapply)
  }

  val accounts = TableQuery[Accounts]
}

case class Account(id: Int, email: String, password: String, createdAt: Timestamp)
