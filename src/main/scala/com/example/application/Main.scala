package com.example.application

import cats.effect._
import cats.implicits._
import com.typesafe.scalalogging.Logger
import org.apache.commons.lang3.StringUtils

import scala.concurrent.duration._
import scala.language.postfixOps

object Main extends IOApp {

  private[this] val logger = Logger("com.example.application.Main")


  override def run(arguments: List[String]): IO[ExitCode] = {
    import cats.effect.Timer
    import scala.concurrent.ExecutionContext.global
    implicit val timer: Timer[IO] = IO timer global

    def sideEffect: IO[Unit] = {

      val getHostAndPath: IO[(String, String)] = IO.suspend( IO {
        import com.typesafe.config.ConfigFactory
        val configuration = ConfigFactory.load()
        val base = configuration.getString("backend.service.base")
        val path = configuration.getString("backend.service.path")
        (base, path)
      })

      def fetchJoke(serviceHost: String, servicePath:String): IO[Unit] = {

        def responseBody: IO[String] = IO.suspend(IO {
          //sttp doesn't work with side-car proxy so utilising finagle HTTP client
          /*
            import sttp.client.asynchttpclient.cats.AsyncHttpClientCatsBackend
            import sttp.client.{HttpURLConnectionBackend, asStringAlways, basicRequest}
            import sttp.model.Uri

            AsyncHttpClientCatsBackend[IO]().flatMap { implicit backend =>
            basicRequest
            .header("Accept", "application/json")
            .get(Uri(new URI(backendServiceURL)))
            .response(asStringAlways)
            .send()
            .map(response => response.body)
            .guarantee(backend.close())}
           */

          import com.twitter.finagle.{Http, Service}
          import com.twitter.finagle.http
          import com.twitter.util.Await
          val client: Service[http.Request, http.Response] = Http.newService(serviceHost)
          val request = http.Request(http.Method.Get, servicePath)
          Await.result(client(request)).contentString
        })

        IO.sleep(3 seconds) *> responseBody.map(logger info _) >> IO.suspend(fetchJoke(serviceHost, servicePath))
      }

      for {
        hostAndPath <- getHostAndPath
        fiber <- IO{logger info s"Fetching response from URL ${hostAndPath._1}${hostAndPath._2}..."} *> fetchJoke(hostAndPath._1, hostAndPath._2).start
        result <- fiber.join
      } yield result

    }

    sideEffect *> IO {ExitCode.Success}
  }

}

