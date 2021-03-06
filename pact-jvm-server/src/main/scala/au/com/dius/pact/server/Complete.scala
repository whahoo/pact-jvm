package au.com.dius.pact.server

import au.com.dius.pact.consumer._
import au.com.dius.pact.model._

import scala.collection.JavaConversions

object Complete {

  def getPort(j: Any): Option[Int] = j match {
    case map: Map[AnyRef, AnyRef] => {
      if (map.contains("port")) Some(map("port").asInstanceOf[Int])
      else None
    }
    case _ => None
  }

  def toJson(error: VerificationResult) = {
    OptionalBody.body("{\"error\": \"" + error + "\"}")
  }

  def apply(request: Request, oldState: ServerState): Result = {
    def clientError = Result(new Response(400), oldState)
    def pactWritten(response: Response, port: Int) = Result(response, oldState - port)

    val result = for {
      port <- getPort(JsonUtils.parseJsonString(request.getBody.getValue))
      mockProvider <- oldState.get(port)
      sessionResults = mockProvider.session.remainingResults
      pact <- mockProvider.pact
    } yield {
      mockProvider.stop()
      
      ConsumerPactRunner.writeIfMatching(pact, sessionResults, mockProvider.config.pactConfig) match {
        case PactVerified => pactWritten(new Response(200, JavaConversions.mapAsJavaMap(ResponseUtils.CrossSiteHeaders)),
          mockProvider.config.port)
        case error => pactWritten(new Response(400,
          JavaConversions.mapAsJavaMap(Map("Content-Type" -> "application/json")), toJson(error)),
          mockProvider.config.port)
      }
    }
    
    result getOrElse clientError
  }

}
