package slp.oauth.auth.dao

import java.security.SecureRandom
import java.sql.Timestamp
import java.util.Date

import javax.inject.Inject
import play.api.Logging
import play.api.db.slick.{DatabaseConfigProvider, DbName, HasDatabaseConfig, SlickApi}
import slick.jdbc.{GetResult, JdbcProfile}

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Random

class OAuthAccessTokenDAO  @Inject()(slickApi: SlickApi,
                                  protected val dbConfigProvider: DatabaseConfigProvider)
                                 (implicit exec: ExecutionContext) extends HasDatabaseConfig[JdbcProfile] with Logging {
  val dbConfig = dbConfigProvider.get[JdbcProfile]
  import profile.api._

  def create(accountId: Int, clientId: Int): Future[OAuthAccessToken] = {
    def randomString(length: Int) = new Random(new SecureRandom()).alphanumeric.take(length).mkString
    val accessToken = randomString(40)
    val refreshToken = randomString(40)

    val oauthAccessToken = OAuthAccessToken(
      id = 0,
      accountId = accountId,
      oauthClientId = clientId,
      accessToken = accessToken,
      refreshToken = refreshToken
    )

    val action = for {
      tokenId <- oauthAccessTokens returning oauthAccessTokens.map{_.id} += oauthAccessToken
    } yield oauthAccessToken.copy(id = tokenId)

    slickApi.dbConfig(DbName("default")).db.run (
      action
    )
  }

  def delete(accountId: Int, clientId: Int): Future[Int] = {
    slickApi.dbConfig(DbName("default")).db.run (
      oauthAccessTokens.filter(t => t.accountId === accountId && t.oauthClientId === clientId).delete
    )
  }

  def refresh(accountId: Int, clientId: Int): Future[OAuthAccessToken] = {
    delete(accountId, clientId).flatMap(_ => create(accountId, clientId))
  }

  def findByAccessToken(token: String): Future[Option[OAuthAccessToken]] = {
    slickApi.dbConfig(DbName("default")).db.run (
      oauthAccessTokens.filter(_.accessToken === token).result.headOption
    )
  }

  def findByRefreshToken(token: String): Future[Option[OAuthAccessToken]] = {
    // in the original, there is also a limit to 1 month old tokens...maybe we should do that too?
    slickApi.dbConfig(DbName("default")).db.run (
      oauthAccessTokens.filter(_.refreshToken === token).result.headOption
    )
  }

  implicit val getOAuthAccessToken = GetResult(r => OAuthAccessToken(r.<<, r.<<, r.<<, r.<<, r.<<, r.<<))
  def findByAuthorized(accountId: Int, clientId: String): Future[Option[OAuthAccessToken]] = {
    val sql =
      sql"""SELECT t.id, t.account_id, t.oauth_client_id, t.access_token, t.refresh_token, t.created_at
           FROM oauth_access_token t
              LEFT JOIN oauth_client c ON t.oauth_client_id = c.id
            WHERE t.account_id = $accountId AND c.client_id = $clientId
            ORDER BY t.id DESC
            LIMIT 1"""
    slickApi.dbConfig(DbName("default")).db.run (
      sql.as[OAuthAccessToken].headOption
    )
  }



  class OAuthAccessTokens(tag: Tag) extends Table[OAuthAccessToken](tag, "oauth_access_token") {
    def id = column[Int]("id", O.PrimaryKey, O.AutoInc)
    def accountId = column[Int]("account_id")
    def oauthClientId = column[Int]("oauth_client_id")
    def accessToken = column[String]("access_token")
    def refreshToken = column[String]("refresh_token")
    def createdAt = column[Timestamp]("created_at")

    def * = (id, accountId, oauthClientId, accessToken, refreshToken, createdAt) <> (OAuthAccessToken.tupled, OAuthAccessToken.unapply)
  }

  val oauthAccessTokens = TableQuery[OAuthAccessTokens]
}

case class OAuthAccessToken(
                           id: Int,
                           accountId: Int,
                           oauthClientId: Int,
                           accessToken: String,
                           refreshToken: String,
                           createdAt: Timestamp = Timestamp.from(new Date().toInstant)
                           )
