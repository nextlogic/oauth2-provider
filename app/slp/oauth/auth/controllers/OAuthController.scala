package slp.oauth.auth.controllers

import javax.inject.{Inject, Singleton}
import play.api.Logging
import play.api.libs.json.{Json, Writes}
import play.api.mvc.{BaseController, ControllerComponents}
import scalaoauth2.provider._
import slp.oauth.auth.dao.{Account, MyDataHandler}

import scala.concurrent.ExecutionContext

@Singleton
class OAuthController @Inject()(val controllerComponents: ControllerComponents,
                               dataHandler: MyDataHandler)
                               (implicit exec: ExecutionContext) extends BaseController with OAuth2Provider with OAuth2ProviderActionBuilders with Logging {

  override val tokenEndpoint = new TokenEndpoint {
    override val handlers = Map(
      OAuthGrantType.AUTHORIZATION_CODE -> new AuthorizationCode(),
      OAuthGrantType.REFRESH_TOKEN -> new RefreshToken(),
      OAuthGrantType.CLIENT_CREDENTIALS -> new ClientCredentials(),
      // OAuthGrantType.PASSWORD -> new Password()
    )
  }

  def accessToken = Action.async { implicit request =>
    issueAccessToken(dataHandler)
  }



}
