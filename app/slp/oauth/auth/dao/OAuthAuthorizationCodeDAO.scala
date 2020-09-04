package slp.oauth.auth.dao

import java.sql.Timestamp

import javax.inject.Inject
import org.joda.time.DateTime
import play.api.Logging
import play.api.db.slick.{DatabaseConfigProvider, DbName, HasDatabaseConfig, SlickApi}
import slick.jdbc.JdbcProfile

import scala.concurrent.{ExecutionContext, Future}

class OAuthAuthorizationCodeDAO  @Inject()(slickApi: SlickApi,
                                           protected val dbConfigProvider: DatabaseConfigProvider)
                                          (implicit exec: ExecutionContext) extends HasDatabaseConfig[JdbcProfile] with Logging {
  val dbConfig = dbConfigProvider.get[JdbcProfile]
  import profile.api._

  def findByCode(code: String): Future[Option[OAuthAuthorizationCode]] = {
    val expireAt = new Timestamp(new DateTime().minusMinutes(30).getMillis)
    slickApi.dbConfig(DbName("default")).db.run (
      oauthAuthorizationCodes.filter(t => t.code === code && t.createdAt > expireAt).result.headOption
    )
  }

  def delete(code: String): Future[Int] = {
    slickApi.dbConfig(DbName("default")).db.run (
      oauthAuthorizationCodes.filter(_.code === code).delete
    )
  }

  class OAuthAuthorizationCodes(tag: Tag) extends Table[OAuthAuthorizationCode](tag, "oauth_authorization_code") {
    def id = column[Int]("id", O.PrimaryKey, O.AutoInc)
    def accountId = column[Int]("account_id")
    def oauthClientId = column[Int]("oauth_client_id")
    def code = column[String]("code")
    def redirectUri = column[String]("redirect_uri")
    def createdAt = column[Timestamp]("created_at")

    def * = (id, accountId, oauthClientId, code, redirectUri, createdAt) <> (OAuthAuthorizationCode.tupled, OAuthAuthorizationCode.unapply)
  }
  val oauthAuthorizationCodes = TableQuery[OAuthAuthorizationCodes]
}

case class OAuthAuthorizationCode(
                                 id: Int,
                                 accountId: Int,
                                 oauthClientId: Int,
                                 code: String,
                                 redirectUri: String,
                                 createdAt: Timestamp
                                 )
