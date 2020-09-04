package slp.oauth.auth.dao

import javax.inject.Inject
import play.api.Logging
import play.api.db.slick.{DatabaseConfigProvider, DbName, HasDatabaseConfig, SlickApi}
import slick.jdbc.JdbcProfile

import scala.concurrent.{ExecutionContext, Future}

class OAuthClientDAO  @Inject()(slickApi: SlickApi,
                             protected val dbConfigProvider: DatabaseConfigProvider)
                            (implicit exec: ExecutionContext) extends HasDatabaseConfig[JdbcProfile] with Logging {
  val dbConfig = dbConfigProvider.get[JdbcProfile]
  import profile.api._

  def findByClientId(clientId: String): Future[Option[OAuthClient]] = {
    slickApi.dbConfig(DbName("default")).db.run (
      oauthClients.filter(_.clientId === clientId).result.headOption
    )
  }

  def findByClientCredentials(clientId: String, clientSecret: String): Future[Option[OAuthClient]] = {
    slickApi.dbConfig(DbName("default")).db.run (
      oauthClients
        .filter(c => c.clientId === clientId && c.clientSecret === clientSecret && c.grantType === "client_credentials")
        .result.headOption
    )
  }


  class OAuthClients(tag: Tag) extends Table[OAuthClient](tag, "oauth_client") {
    def id = column[Int]("id", O.PrimaryKey, O.AutoInc)
    def ownerId = column[Int]("owner_id")
    def grantType = column[String]("grant_type")
    def clientId = column[String]("client_id")
    def clientSecret = column[String]("client_secret")
    def redirectUri = column[Option[String]]("redirect_uri")

    def * = (id, ownerId, grantType, clientId, clientSecret, redirectUri) <> (OAuthClient.tupled, OAuthClient.unapply)
  }

  val oauthClients = TableQuery[OAuthClients]

}

case class OAuthClient(
                        id: Int,
                        ownerId: Int,
                        grantType: String,
                        clientId: String,
                        clientSecret: String,
                        redirectUri: Option[String],
                      )
