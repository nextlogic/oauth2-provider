package slp.oauth.auth.dao

import java.util.Date

import javax.inject.Inject
import play.api.Logging
import scalaoauth2.provider.{AccessToken, AuthInfo, AuthorizationRequest, ClientCredential, ClientCredentialsRequest, DataHandler, InvalidClient, PasswordRequest}

import scala.concurrent.{ExecutionContext, Future}

class MyDataHandler @Inject()(clientDAO: OAuthClientDAO, accountDAO: AccountDAO,
                              accessTokenDAO: OAuthAccessTokenDAO, authCodeDAO: OAuthAuthorizationCodeDAO)
                             (implicit exec: ExecutionContext) extends DataHandler[Account] with Logging {
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
