/**
 * Copyright (C) 2009-2014 Typesafe Inc. <http://www.typesafe.com>
 */

package akka.http.server

import org.reactivestreams.api.Producer
import akka.event.LoggingAdapter
import akka.stream.io.StreamTcp
import akka.stream.{ FlattenStrategy, Transformer, FlowMaterializer }
import akka.stream.scaladsl.{ Flow, Duct }
import akka.http.parsing.HttpRequestParser
import akka.http.rendering.{ ResponseRenderingContext, HttpResponseRendererFactory }
import akka.http.model.{ StatusCode, ErrorInfo, HttpRequest, HttpResponse }
import akka.http.parsing.ParserOutput._
import akka.http.Http
import akka.http.util._
import akka.http.model.headers.Host

/**
 * INTERNAL API
 */
private[http] class HttpServerPipeline(settings: ServerSettings,
                                       materializer: FlowMaterializer,
                                       log: LoggingAdapter)
  extends (StreamTcp.IncomingTcpConnection ⇒ Http.IncomingConnection) {

  val rootParser = new HttpRequestParser(settings.parserSettings, settings.rawRequestUriHeader, materializer)()
  val warnOnIllegalHeader: ErrorInfo ⇒ Unit = errorInfo ⇒
    if (settings.parserSettings.illegalHeaderWarnings)
      log.warning(errorInfo.withSummaryPrepended("Illegal request header").formatPretty)

  val responseRendererFactory = new HttpResponseRendererFactory(settings.serverHeader,
    settings.responseHeaderSizeHint, materializer, log)

  def apply(tcpConn: StreamTcp.IncomingTcpConnection): Http.IncomingConnection = {
    val (applicationBypassConsumer, applicationBypassProducer) =
      Duct[(RequestOutput, Producer[RequestOutput])]
        .collect[MessageStart with RequestOutput] { case (x: MessageStart, _) ⇒ x }
        .build(materializer)

    val requestProducer =
      Flow(tcpConn.inputStream)
        .transform(rootParser.copyWith(warnOnIllegalHeader))
        .splitWhen(_.isInstanceOf[MessageStart])
        .headAndTail(materializer)
        .tee(applicationBypassConsumer)
        .collect {
          case (RequestStart(method, uri, protocol, headers, createEntity, _), entityParts) ⇒
            val effectiveUri = HttpRequest.effectiveUri(uri, headers, securedConnection = false, settings.defaultHostHeader)
            HttpRequest(method, effectiveUri, headers, createEntity(entityParts), protocol)
        }
        .toProducer(materializer)

    val responseConsumer =
      Duct[HttpResponse]
        .merge(applicationBypassProducer)
        .transform(applyApplicationBypass)
        .transform(responseRendererFactory.newRenderer)
        .flatten(FlattenStrategy.concat)
        .transform(errorLogger(log, "Outgoing response stream error"))
        .produceTo(materializer, tcpConn.outputStream)

    Http.IncomingConnection(tcpConn.remoteAddress, requestProducer, responseConsumer)
  }

  /**
   * Combines the HttpResponse coming in from the application with the ParserOutput.RequestStart
   * produced by the request parser into a ResponseRenderingContext.
   * If the parser produced a ParserOutput.ParseError the error response is immediately dispatched to downstream.
   */
  def applyApplicationBypass =
    new Transformer[Any, ResponseRenderingContext] {
      var applicationResponse: HttpResponse = _
      var requestStart: RequestStart = _

      def onNext(elem: Any) = elem match {
        case response: HttpResponse ⇒
          requestStart match {
            case null ⇒
              applicationResponse = response
              Nil
            case x: RequestStart ⇒
              requestStart = null
              dispatch(x, response)
          }

        case requestStart: RequestStart ⇒
          applicationResponse match {
            case null ⇒
              this.requestStart = requestStart
              Nil
            case response ⇒
              applicationResponse = null
              dispatch(requestStart, response)
          }

        case ParseError(status, info) ⇒ errorResponse(status, info) :: Nil
      }

      def dispatch(requestStart: RequestStart, response: HttpResponse): List[ResponseRenderingContext] = {
        import requestStart._
        ResponseRenderingContext(response, method, protocol, closeAfterResponseCompletion) :: Nil
      }

      def errorResponse(status: StatusCode, info: ErrorInfo): ResponseRenderingContext = {
        log.warning("Illegal request, responding with status '{}': {}", status, info.formatPretty)
        val msg = if (settings.verboseErrorMessages) info.formatPretty else info.summary
        ResponseRenderingContext(HttpResponse(status, entity = msg), closeAfterResponseCompletion = true)
      }
    }
}