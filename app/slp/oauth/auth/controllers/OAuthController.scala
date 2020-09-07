package slp.oauth.auth.controllers

import java.util.Date

import javax.inject.Inject
import javax.inject.Singleton
import play.api.Logging
import play.api.libs.json.{Json, Writes}
import play.api.mvc.{Action, BaseController, ControllerComponents}
import scalaoauth2.provider.{AccessToken, AuthInfo, AuthorizationCode, AuthorizationRequest, ClientCredential, ClientCredentials, ClientCredentialsRequest, DataHandler, InvalidClient, OAuth2Provider, OAuth2ProviderActionBuilders, OAuthGrantType, Password, PasswordRequest, RefreshToken, TokenEndpoint}
import slp.oauth.auth.dao.{Account, AccountDAO, OAuthAccessToken, OAuthAccessTokenDAO, OAuthAuthorizationCodeDAO, OAuthClientDAO}

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class OAuthController @Inject()(val controllerComponents: ControllerComponents,
                               accessTokenDAO: OAuthAccessTokenDAO,
                               accountDAO: AccountDAO,
                               authCodeDAO: OAuthAuthorizationCodeDAO,
                               clientDAO: OAuthClientDAO)
                               (implicit exec: ExecutionContext) extends BaseController with OAuth2Provider with OAuth2ProviderActionBuilders with Logging {

  implicit val authInfoWrites = new Writes[AuthInfo[Account]] {
    def writes(authInfo: AuthInfo[Account]) = {
      Json.obj(
        "account" -> Json.obj(
          "email" -> authInfo.user.email
        ),
        "clientId" -> authInfo.clientId,
        "redirectUri" -> authInfo.redirectUri
      )
    }
  }

  override val tokenEndpoint = new TokenEndpoint {
    override val handlers = Map(
      OAuthGrantType.AUTHORIZATION_CODE -> new AuthorizationCode(),
      OAuthGrantType.REFRESH_TOKEN -> new RefreshToken(),
      OAuthGrantType.CLIENT_CREDENTIALS -> new ClientCredentials(),
      // OAuthGrantType.PASSWORD -> new Password()
    )
  }

  def accessToken = Action.async { implicit request =>
    issueAccessToken(new MyDataHandler())
  }

  def resources = AuthorizedAction(new MyDataHandler()) { request =>
    Ok(Json.toJson(request.authInfo))
  }

  class MyDataHandler extends DataHandler[Account] {
    // common
    override def validateClient(maybeCredential: Option[ClientCredential], request: AuthorizationRequest): Future[Boolean] = {
      maybeCredential match {
        case None => Future.successful(false)
        case Some(credential) => clientDAO.validate(credential.clientId, credential.clientSecret.getOrElse(""), request.grantType)
      }
    }

    override def findUser(maybeCredential: Option[ClientCredential], request: AuthorizationRequest): Future[Option[Account]] = {
      logger.debug("Find user...")
      request match {
        case request: PasswordRequest => accountDAO.authenticate(request.username, request.password)
        case request: ClientCredentialsRequest =>
          maybeCredential
            .map(credential => accountDAO.findByClientCredentials(credential.clientId, credential.clientSecret.getOrElse("")))
            .getOrElse(Future.successful(None))
        case _ => Future.successful(None)
      }
    }

    override def createAccessToken(authInfo: AuthInfo[Account]): Future[AccessToken] = {
      logger.debug("Creating access token...")
      val clientId = authInfo.clientId.getOrElse(throw new InvalidClient())
      clientDAO
        .findByClientId(clientId)
        .map(clientO => clientO.getOrElse{logger.debug(s"Cannot find client with $clientId"); throw new InvalidClient()})
        .flatMap(client => accessTokenDAO.create(authInfo.user.id, client.id))
        .map(toAccessToken)
    }

    override def getStoredAccessToken(authInfo: AuthInfo[Account]): Future[Option[AccessToken]] = {
      accessTokenDAO
        .findByAuthorized(authInfo.user.id, authInfo.clientId.getOrElse(""))
        .map(token => token.map(toAccessToken))
    }

    // Authorization code grant
    override def findAuthInfoByCode(code: String): Future[Option[AuthInfo[Account]]] = {
      accountDAO.findByAuthorizationCode(code)
        .map(accountO =>
          accountO.map(account =>
            AuthInfo(
              user = account.account,
              clientId = Some(account.clientId),
              scope = None,
              redirectUri = None
            )
          )
        )

    }

    override def deleteAuthCode(code: String): Future[Unit] = {
      authCodeDAO.delete(code).map(_ => ())
    }

    // Refresh token grant
    override def findAuthInfoByRefreshToken(refreshToken: String): Future[Option[AuthInfo[Account]]] = {
      logger.debug("Finding auth info by refresh token...")
      accountDAO.findByRefreshToken(refreshToken)
        .map(accountO =>
          accountO.map(account =>
            AuthInfo(
              user = account.account,
              clientId = Some(account.clientId),
              scope = None,
              redirectUri = None
            )
          )
        )
    }

    override def refreshAccessToken(authInfo: AuthInfo[Account], refreshToken: String): Future[AccessToken] = {
      logger.debug("Refreshing token...")
      val clientId = authInfo.clientId.getOrElse(throw new InvalidClient())
      clientDAO
        .findByClientId(clientId)
        .map(clientO => clientO.getOrElse(throw new InvalidClient()))
        .flatMap(client => accessTokenDAO.refresh(authInfo.user.id, client.id))
        .map(toAccessToken)
    }

    // protected resources
    override def findAuthInfoByAccessToken(accessToken: AccessToken): Future[Option[AuthInfo[Account]]] = {
      accountDAO.findByToken(accessToken.token)
        .map(accountO =>
          accountO.map(account =>
          AuthInfo(
            user = account.account,
            clientId = Some(account.clientId),
            scope = None,
            redirectUri = None
          )
        )
        )

    }

    override def findAccessToken(token: String): Future[Option[AccessToken]] = {
      accessTokenDAO.findByAccessToken(token).map(opt => opt.map(toAccessToken))
    }

    private val accessTokenExpireSeconds = 3600
    private def toAccessToken(accessToken: OAuthAccessToken) = {
      AccessToken(
        accessToken.accessToken,
        Some(accessToken.refreshToken),
        None,
        Some(accessTokenExpireSeconds),
        new Date(accessToken.createdAt.getTime)
      )
    }


  }
}
