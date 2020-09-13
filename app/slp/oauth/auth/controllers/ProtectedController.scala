package slp.oauth.auth.controllers

import javax.inject.Inject
import play.api.Logging
import play.api.libs.json.{Json, Writes}
import play.api.mvc.{BaseController, ControllerComponents}
import scalaoauth2.provider.{AuthInfo, OAuth2Provider, OAuth2ProviderActionBuilders}
import slp.oauth.auth.dao.{Account, MyDataHandler}

import scala.concurrent.ExecutionContext

class ProtectedController @Inject()(val controllerComponents: ControllerComponents,
                                    dataHandler: MyDataHandler)
                                   (implicit exec: ExecutionContext) extends BaseController with OAuth2ProviderActionBuilders with Logging {

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
  def resources = AuthorizedAction(dataHandler) { request =>
    Ok(Json.toJson(request.authInfo))
  }
}
