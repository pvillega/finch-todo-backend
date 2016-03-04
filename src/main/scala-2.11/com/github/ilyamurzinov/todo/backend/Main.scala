package com.github.ilyamurzinov.todo.backend

import java.util.UUID

import com.twitter.finagle.{Http, Service}
import com.twitter.finagle.http.{Request, Response}
import com.twitter.util.Await
import io.circe.generic.auto._
import io.finch._
import io.finch.circe._

/**
  * Backend for TODO application
  * See [[http://www.todobackend.com/ TODO backend website]],
  * [[http://www.todobackend.com/specs/index.html Specification]]
  *
  * @author Murzinov Ilya [murz42@gmail.com]
  */
object Main {

  val getTodos: Endpoint[List[Todo]] = get("todos") {
    Ok(Todo.list())
  }

  val getTodo: Endpoint[Todo] = get("todos" :: uuid) { id: UUID =>
    Todo.get(id) match {
      case Some(t) => Ok(t)
      case None => throw TodoNotFound(id)
    }
  }

  def postedTodo(baseUrl: String): Endpoint[Todo] =
    body.as[Todo => Todo].map { f =>
      val id = UUID.randomUUID
      f(Todo(
        id = id,
        title = "",
        completed = false,
        order = 1,
        url = s"$baseUrl/todos/$id"
      ))
    }

  def postTodo(baseUrl: String): Endpoint[Todo] =
    post("todos" :: postedTodo(baseUrl)) { t: Todo =>
      Todo.save(t)

      Created(t)
    }

  val deleteTodo: Endpoint[Todo] = delete("todos" :: uuid) { id: UUID =>
    Todo.get(id) match {
      case Some(t) => Todo.delete(id); Ok(t)
      case None => throw new TodoNotFound(id)
    }
  }

  val deleteTodos: Endpoint[List[Todo]] = delete("todos") {
    val all: List[Todo] = Todo.list()
    all.foreach(t => Todo.delete(t.id))

    Ok(all)
  }

  val patchedTodo: Endpoint[Todo => Todo] = body.as[Todo => Todo]

  val patchTodo: Endpoint[Todo] =
    patch("todos" :: uuid :: patchedTodo) { (id: UUID, pt: Todo => Todo) =>
      Todo.get(id) match {
        case Some(currentTodo) =>
          val newTodo: Todo = pt(currentTodo)
          Todo.delete(id)
          Todo.save(newTodo)

          Ok(newTodo)
        case None => throw TodoNotFound(id)
      }
    }

  val opts: Endpoint[Unit] = options(*) {
    NoContent[Unit].withHeader(("Allow", "POST, GET, OPTIONS, DELETE, PATCH"))
  }

  def api(baseUrl: String): Service[Request, Response] = (
    getTodo :+: getTodos :+: postTodo(baseUrl) :+: deleteTodo :+: deleteTodos :+: patchTodo :+: opts
  ).handle({
    case e: TodoNotFound => NotFound(e)
  }).withHeader(
    ("Access-Control-Allow-Origin", "*")
  ).withHeader(
    ("Access-Control-Allow-Methods", "POST, GET, OPTIONS, DELETE, PATCH")
  ).withHeader(
    ("Access-Control-Max-Age", "3600")
  ).withHeader(
    (
      "Access-Control-Allow-Headers",
      """Content-Type,
        |Cache-Control,
        |Content-Language,
        |Expires,
        |Last-Modified,
        |Pragma,
        |X-Requested-With,
        |Origin,
        |Accept
      """.stripMargin.filter(_ >= ' ')
      )
  ).toService

  def main(args: Array[String]): Unit = {
    val host = Option(System.getProperty("http.host")).getOrElse("0.0.0.0")
    val port = Option(System.getProperty("http.port")).getOrElse("8081")

    val internalUrl: String = s"$host:$port"
    val externalUrl = Option(System.getProperty("external.url")).getOrElse(s"http://$internalUrl")

    val server = Http.server.serve(internalUrl, api(externalUrl))

    Await.ready(server)
  }
}
