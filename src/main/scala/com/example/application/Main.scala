package com.example.application

import java.net.URI

import cats.effect._
import cats.implicits._
import com.typesafe.scalalogging.Logger
import sttp.client.asynchttpclient.cats.AsyncHttpClientCatsBackend
import sttp.client.{asStringAlways, basicRequest}
import sttp.model.Uri

import scala.concurrent.duration._
import scala.language.postfixOps

object Main extends IOApp {

  private[this] val logger = Logger("com.example.application.Main")

  import com.typesafe.config.ConfigFactory
  private[this] val backendServiceURL = ConfigFactory.load().getString("backend.service.url")

  override def run(arguments: List[String]): IO[ExitCode] = {
    import cats.effect.Timer
    import scala.concurrent.ExecutionContext.global
    implicit val timer: Timer[IO] = IO timer global

    def sideEffect: IO[Unit] = {

      def responseBody(uri:Uri): IO[String] = AsyncHttpClientCatsBackend[IO]().flatMap { implicit backend =>
        basicRequest
          .header("Accept", "text/plain")
          .get(uri)
          .response(asStringAlways)
          .send()
          .map(_.body)
          .guarantee(backend.close())
      }

      def fetchJoke: IO[Unit] = {
        IO.sleep(2 seconds) *> responseBody(Uri(new URI(backendServiceURL))).map(logger info _) >> IO.suspend(fetchJoke)
      }

      for {
        _ <- IO{logger info "Fetching jokes ..."}
        fiber <- fetchJoke.start
        result <- fiber.join
      } yield result
    }

    sideEffect *> IO {ExitCode.Success}
  }

}

