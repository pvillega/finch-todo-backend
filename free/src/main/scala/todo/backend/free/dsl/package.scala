package todo.backend.free

import cats.free.{Free, Inject}
import cats.data.Coproduct
import todo.backend.free.dsl.logging._
import todo.backend.free.dsl.logic._
import todo.backend.free.dsl.storage._

package object dsl {
  type LogF[T] = Free[LogAction, T]
  type TodoF[T] = Free[TodoAction, T]
  type StorageF[T] = Free[StorageAction, T]
  type TodoApp[T] = Coproduct[TodoAction, LogAction, T]
  type TodoAppF[T] = Free[TodoApp, T]

  implicit def logI[F[_]](implicit I: Inject[LogAction, F]): LogI[F] =
    new LogI[F]
  implicit def todoI[F[_]](implicit I: Inject[TodoAction, F]): TodoI[F] =
    new TodoI[F]
}
